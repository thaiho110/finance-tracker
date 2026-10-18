package com.financetracker.category.service;

import com.financetracker.category.dto.CategoryDeleteResponse;
import com.financetracker.category.dto.CategoryRequest;
import com.financetracker.category.dto.CategoryResponse;
import com.financetracker.category.dto.CategoryRulePreviewResponse;
import com.financetracker.category.dto.CategoryRuleRequest;
import com.financetracker.category.dto.CategoryRuleResponse;
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
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * A user's view of categories (ADR-015): the system defaults they haven't hidden plus their own, and
 * their own keyword rules. Another user's category or rule is reported as not found, so ids can't be probed.
 */
@Service
@RequiredArgsConstructor
public class CategoryService {

    private static final int MAX_KEYWORD = 255;

    private final CategoryRepository categories;
    private final CategoryRuleRepository rules;
    private final CategorizationService categorization;
    private final CategoryUsage usage;

    // ───────────────────────── categories ─────────────────────────

    /**
     * @param includeArchived also return archived and hidden categories (for the settings page);
     *                        pickers leave it false
     */
    public List<CategoryResponse> list(UUID userId, boolean includeArchived) {
        Set<UUID> hidden = categories.findHiddenIds(userId);
        return categories.findVisibleTo(userId).stream()
            .filter(c -> includeArchived || !c.isArchived() && !hidden.contains(c.getId()))
            .sorted(Comparator.comparing(Category::getName, String.CASE_INSENSITIVE_ORDER))
            .map(c -> CategoryResponse.of(c, hidden.contains(c.getId())))
            .toList();
    }

    @Transactional
    public CategoryResponse create(UUID userId, CategoryRequest request) {
        String name = request.name().strip();
        requireFreeName(userId, name, null);
        Category saved = categories.save(Category.builder()
            .userId(userId)
            .name(name)
            .kind(request.kind() == null ? CategoryKind.EXPENSE : request.kind())
            .color(request.color())
            .icon(request.icon())
            .build());
        return CategoryResponse.of(saved, false);
    }

    @Transactional
    public CategoryResponse update(UUID userId, UUID id, CategoryUpdateRequest request) {
        Category category = ownedForChange(userId, id);
        if (request.name() != null && !request.name().isBlank()) {
            String name = request.name().strip();
            requireFreeName(userId, name, id);
            category.setName(name);
        }
        apply(category, request);
        categorization.evictUser(userId);
        return CategoryResponse.of(categories.save(category), false);
    }

    /**
     * Removes one of the user's categories. With {@code moveTo}, its transactions move there first. A category
     * still used afterwards is archived instead of deleted, so old transactions keep their category.
     */
    @Transactional
    public CategoryDeleteResponse delete(UUID userId, UUID id, UUID moveTo) {
        Category category = ownedForChange(userId, id);
        int moved = 0;
        if (moveTo != null) {
            if (moveTo.equals(id)) {
                throw new IllegalArgumentException("Pick a different category to move the transactions to");
            }
            moved = usage.reassign(userId, id, usableBy(userId, moveTo).getId());
        }
        categorization.evictUser(userId);
        if (usage.countUsing(id) == 0) {
            categories.delete(category);
            return new CategoryDeleteResponse(CategoryDeleteResponse.Outcome.DELETED, moved);
        }
        category.setArchived(true);
        categories.save(category);
        return new CategoryDeleteResponse(CategoryDeleteResponse.Outcome.ARCHIVED, moved);
    }

    /** Hides or shows a system category for this user only. */
    @Transactional
    public void setHidden(UUID userId, UUID id, boolean hide) {
        Category category = visible(userId, id);
        if (!category.isSystem()) {
            throw new IllegalArgumentException("Only default categories can be hidden; archive your own instead");
        }
        if (hide && Category.OTHER.equals(category.getName())) {
            throw new IllegalArgumentException("'Other' is where unmatched transactions go, so it can't be hidden");
        }
        if (hide) {
            categories.hide(userId, id);
        } else {
            categories.unhide(userId, id);
        }
        categorization.evictUser(userId);
    }

    /**
     * The category a new or edited transaction should get. An id must be a category the user can pick;
     * a name (older clients) matches a visible category or creates one of the user's own; neither means Other.
     */
    @Transactional
    public CategoryRef resolveForTransaction(UUID userId, UUID categoryId, String name) {
        if (categoryId != null) {
            return CategoryRef.of(usableBy(userId, categoryId));
        }
        if (name == null || name.isBlank()) {
            return categorization.other();
        }
        String clean = name.strip();
        Optional<Category> existing = categories.findOwnByName(userId, clean)
            .or(() -> categories.findSystemByName(clean));
        return CategoryRef.of(existing.orElseGet(() -> categories.save(Category.builder()
            .userId(userId).name(clean).kind(CategoryKind.EXPENSE).build())));
    }

    /** Categories by id, for showing transactions; includes archived ones. */
    public Map<UUID, CategoryRef> refs(Set<UUID> ids) {
        return categories.findByIdIn(ids).stream()
            .collect(Collectors.toMap(Category::getId, CategoryRef::of, (a, b) -> a));
    }

    /** One category the user can see (system or own), archived included. */
    public CategoryRef visibleRef(UUID userId, UUID id) {
        return CategoryRef.of(visible(userId, id));
    }

    // ───────────────────────── rules ─────────────────────────

    public List<CategoryRuleResponse> listRules(UUID userId, boolean system) {
        List<CategoryRule> source = system ? rules.findSystem() : rules.findByUserIdOrderByKeyword(userId);
        return toRuleResponses(source);
    }

    /** How many past transactions still in Other a keyword would move. */
    public CategoryRulePreviewResponse preview(UUID userId, String keyword) {
        String key = normalizedKeyword(keyword);
        int matching = usage.countMatching(userId, categorization.other().id(), matcher(key));
        return new CategoryRulePreviewResponse(key, matching);
    }

    /** Creates the user's rule for the keyword, or points the existing one at a new category. */
    @Transactional
    public CategoryRuleSaveResponse saveRule(UUID userId, CategoryRuleRequest request) {
        String key = normalizedKeyword(request.keyword());
        Category target = usableBy(userId, request.categoryId());
        CategoryRule rule = rules.findByUserIdAndKeyword(userId, key)
            .orElseGet(() -> CategoryRule.builder().userId(userId).keyword(key).build());
        rule.setCategoryId(target.getId());
        CategoryRule saved = rules.save(rule);
        categorization.evictUser(userId);

        int moved = 0;
        UUID otherId = categorization.other().id();
        if (request.shouldApplyToPast() && !otherId.equals(target.getId())) {
            moved = usage.reassignMatching(userId, otherId, target.getId(), matcher(key));
        }
        return new CategoryRuleSaveResponse(toRuleResponses(List.of(saved)).get(0), moved);
    }

    @Transactional
    public void deleteRule(UUID userId, UUID ruleId) {
        CategoryRule rule = rules.findById(ruleId)
            .filter(r -> userId.equals(r.getUserId()))
            .orElseThrow(() -> new EntityNotFoundException("Rule not found: " + ruleId));
        rules.delete(rule);
        categorization.evictUser(userId);
    }

    // ───────────────────────── shared with the admin side ─────────────────────────

    /** Keyword in stored form; rejects keywords that are empty once normalized. */
    static String normalizedKeyword(String keyword) {
        String key = CategorizationService.normalize(keyword == null ? "" : keyword);
        if (key.isEmpty() || key.length() > MAX_KEYWORD) {
            throw new IllegalArgumentException("Keyword must have 1–255 letters or digits");
        }
        return key;
    }

    static void apply(Category category, CategoryUpdateRequest request) {
        if (request.kind() != null) {
            category.setKind(request.kind());
        }
        if (request.color() != null) {
            category.setColor(request.color().isBlank() ? null : request.color());
        }
        if (request.icon() != null) {
            category.setIcon(request.icon().isBlank() ? null : request.icon());
        }
        if (request.archived() != null) {
            if (request.archived() && category.isSystem() && Category.OTHER.equals(category.getName())) {
                throw new IllegalArgumentException("'Other' is where unmatched transactions go, so it can't be archived");
            }
            category.setArchived(request.archived());
        }
    }

    List<CategoryRuleResponse> toRuleResponses(List<CategoryRule> source) {
        Map<UUID, Category> byId = categories.findByIdIn(source.stream().map(CategoryRule::getCategoryId).toList())
            .stream().collect(Collectors.toMap(Category::getId, Function.identity()));
        return source.stream()
            .map(r -> new CategoryRuleResponse(r.getId(), r.getKeyword(), r.getCategoryId(),
                byId.containsKey(r.getCategoryId()) ? byId.get(r.getCategoryId()).getName() : null, r.getUserId() == null))
            .toList();
    }

    // ───────────────────────── helpers ─────────────────────────

    private Predicate<String> matcher(String normalizedKey) {
        return merchant -> categorization.matches(normalizedKey, merchant);
    }

    /** A system category or one of the user's own; anything else is "not found". */
    private Category visible(UUID userId, UUID id) {
        return categories.findById(id)
            .filter(c -> c.isSystem() || c.isOwnedBy(userId))
            .orElseThrow(() -> new EntityNotFoundException("Category not found: " + id));
    }

    /** A category the user may put transactions or rules in: visible, not archived, not hidden. */
    private Category usableBy(UUID userId, UUID id) {
        Category category = visible(userId, id);
        if (category.isArchived() || category.isSystem() && categories.findHiddenIds(userId).contains(id)) {
            throw new IllegalArgumentException("Category '" + category.getName() + "' is archived or hidden");
        }
        return category;
    }

    /** One of the user's own categories; a system category can be seen but not changed. */
    private Category ownedForChange(UUID userId, UUID id) {
        Category category = visible(userId, id);
        if (category.isSystem()) {
            throw new AccessDeniedException("Default categories can't be changed; hide it instead");
        }
        return category;
    }

    private void requireFreeName(UUID userId, String name, UUID except) {
        boolean takenByOwn = categories.findOwnByName(userId, name).filter(c -> !c.getId().equals(except)).isPresent();
        if (takenByOwn || categories.findSystemByName(name).isPresent()) {
            throw new ConflictException("A category named '" + name + "' already exists");
        }
    }
}
