package com.financetracker.category.service;

import com.financetracker.category.dto.CategoryDeleteResponse;
import com.financetracker.category.dto.CategoryRequest;
import com.financetracker.category.dto.CategoryResponse;
import com.financetracker.category.dto.CategoryRuleRequest;
import com.financetracker.category.dto.CategoryRuleSaveResponse;
import com.financetracker.category.dto.CategoryUpdateRequest;
import com.financetracker.category.model.Category;
import com.financetracker.category.model.CategoryKind;
import com.financetracker.category.model.CategoryRef;
import com.financetracker.category.model.CategoryRule;
import com.financetracker.category.repository.CategoryRepository;
import com.financetracker.category.repository.CategoryRuleRepository;
import com.financetracker.common.exception.ConflictException;
import com.financetracker.common.exception.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CategoryService")
class CategoryServiceTest {

    private static final UUID USER = UUID.randomUUID();
    private static final UUID STRANGER = UUID.randomUUID();

    @Mock private CategoryRepository categories;
    @Mock private CategoryRuleRepository rules;
    @Mock private CategorizationService categorization;
    @Mock private CategoryUsage usage;

    private CategoryService service;
    private Category other;
    private Category coffee;
    private Category pets;
    private Category strangersCategory;

    @BeforeEach
    void setUp() {
        service = new CategoryService(categories, rules, categorization, usage);
        other = Category.builder().id(UUID.randomUUID()).name("Other").kind(CategoryKind.EXPENSE).build();
        coffee = Category.builder().id(UUID.randomUUID()).name("Coffee Shop").kind(CategoryKind.EXPENSE).build();
        pets = Category.builder().id(UUID.randomUUID()).userId(USER).name("Pets").kind(CategoryKind.EXPENSE).build();
        strangersCategory = Category.builder().id(UUID.randomUUID()).userId(STRANGER).name("Secret").build();
        for (Category c : List.of(other, coffee, pets, strangersCategory)) {
            when(categories.findById(c.getId())).thenReturn(Optional.of(c));
        }
        when(categories.findHiddenIds(any())).thenReturn(Set.of());
        when(categories.findOwnByName(any(), anyString())).thenReturn(Optional.empty());
        // the repository compares names case-insensitively
        when(categories.findSystemByName(anyString())).thenAnswer(inv -> Optional.of(coffee)
            .filter(c -> c.getName().equalsIgnoreCase(inv.getArgument(0))));
        when(categories.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(categorization.other()).thenReturn(CategoryRef.of(other));
        when(categories.findVisibleTo(USER)).thenReturn(List.of(other, coffee, pets));
    }

    @Test
    @DisplayName("lists defaults and own categories, without archived or hidden ones by default")
    void listsVisible() {
        Category oldOwn = Category.builder().id(UUID.randomUUID()).userId(USER).name("Old").archived(true).build();
        when(categories.findVisibleTo(USER)).thenReturn(List.of(other, coffee, pets, oldOwn));
        when(categories.findHiddenIds(USER)).thenReturn(Set.of(coffee.getId()));

        assertThat(service.list(USER, false)).extracting(CategoryResponse::name).containsExactly("Other", "Pets");
        assertThat(service.list(USER, true)).extracting(CategoryResponse::name).containsExactly("Coffee Shop", "Old", "Other", "Pets");
        assertThat(service.list(USER, true)).filteredOn(CategoryResponse::hidden).extracting(CategoryResponse::name)
            .containsExactly("Coffee Shop");
    }

    @Test
    @DisplayName("creates an own category, rejecting a name already used by a default")
    void createsOwnCategory() {
        CategoryResponse created = service.create(USER, new CategoryRequest("  Kids ", null, "#123456", null));
        assertThat(created.name()).isEqualTo("Kids");
        assertThat(created.kind()).isEqualTo(CategoryKind.EXPENSE);
        assertThat(created.system()).isFalse();

        assertThatThrownBy(() -> service.create(USER, new CategoryRequest("coffee shop", null, null, null)))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("another user's category is not found; a default one can't be changed")
    void ownershipRules() {
        CategoryUpdateRequest rename = new CategoryUpdateRequest("X", null, null, null, null);
        assertThatThrownBy(() -> service.update(USER, strangersCategory.getId(), rename))
            .isInstanceOf(EntityNotFoundException.class);
        assertThatThrownBy(() -> service.update(USER, coffee.getId(), rename))
            .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.delete(USER, strangersCategory.getId(), null))
            .isInstanceOf(EntityNotFoundException.class);
        verify(categories, never()).delete(any());
    }

    @Test
    @DisplayName("deletes an unused category but archives a used one")
    void deleteOrArchive() {
        when(usage.countUsing(pets.getId())).thenReturn(0L);
        assertThat(service.delete(USER, pets.getId(), null).outcome()).isEqualTo(CategoryDeleteResponse.Outcome.DELETED);
        verify(categories).delete(pets);

        when(usage.countUsing(pets.getId())).thenReturn(4L);
        assertThat(service.delete(USER, pets.getId(), null).outcome()).isEqualTo(CategoryDeleteResponse.Outcome.ARCHIVED);
        assertThat(pets.isArchived()).isTrue();
    }

    @Test
    @DisplayName("moves transactions to the replacement before deleting")
    void movesThenDeletes() {
        when(usage.reassign(USER, pets.getId(), coffee.getId())).thenReturn(3);
        when(usage.countUsing(pets.getId())).thenReturn(0L);

        CategoryDeleteResponse result = service.delete(USER, pets.getId(), coffee.getId());

        assertThat(result.moved()).isEqualTo(3);
        assertThat(result.outcome()).isEqualTo(CategoryDeleteResponse.Outcome.DELETED);
    }

    @Test
    @DisplayName("hides only default categories, and never Other")
    void hideRules() {
        service.setHidden(USER, coffee.getId(), true);
        verify(categories).hide(USER, coffee.getId());

        assertThatThrownBy(() -> service.setHidden(USER, other.getId(), true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.setHidden(USER, pets.getId(), true)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("saving a rule can move past transactions still in Other")
    void ruleAppliesToPast() {
        when(rules.findByUserIdAndKeyword(USER, "HIGHLANDS COFFEE")).thenReturn(Optional.empty());
        when(rules.save(any())).thenAnswer(inv -> {
            CategoryRule rule = inv.getArgument(0);
            rule.setId(UUID.randomUUID());
            return rule;
        });
        when(categories.findByIdIn(any())).thenReturn(List.of(coffee));
        when(usage.reassignMatching(eq(USER), eq(other.getId()), eq(coffee.getId()), any())).thenReturn(12);

        CategoryRuleSaveResponse saved = service.saveRule(USER, new CategoryRuleRequest("Highlands Coffee", coffee.getId(), true));

        assertThat(saved.rule().keyword()).isEqualTo("HIGHLANDS COFFEE");
        assertThat(saved.rule().categoryName()).isEqualTo("Coffee Shop");
        assertThat(saved.moved()).isEqualTo(12);
        verify(categorization).evictUser(USER);
    }

    @Test
    @DisplayName("a rule can't point at an archived category or at someone else's")
    void ruleTargetMustBeUsable() {
        pets.setArchived(true);
        assertThatThrownBy(() -> service.saveRule(USER, new CategoryRuleRequest("VET", pets.getId(), false)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.saveRule(USER, new CategoryRuleRequest("VET", strangersCategory.getId(), false)))
            .isInstanceOf(EntityNotFoundException.class);
        assertThatThrownBy(() -> service.saveRule(USER, new CategoryRuleRequest("  ", coffee.getId(), false)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("resolves a transaction's category by id, by name (older clients), or falls back to Other")
    void resolvesForTransactions() {
        assertThat(service.resolveForTransaction(USER, coffee.getId(), "ignored").name()).isEqualTo("Coffee Shop");
        assertThat(service.resolveForTransaction(USER, null, "coffee shop").id()).isEqualTo(coffee.getId());
        assertThat(service.resolveForTransaction(USER, null, null).name()).isEqualTo("Other");

        CategoryRef created = service.resolveForTransaction(USER, null, "Gifts");
        assertThat(created.name()).isEqualTo("Gifts");
        verify(categories).save(any(Category.class));

        assertThatThrownBy(() -> service.resolveForTransaction(USER, strangersCategory.getId(), null))
            .isInstanceOf(EntityNotFoundException.class);
    }
}
