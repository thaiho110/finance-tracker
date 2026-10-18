package com.financetracker.category.service;

import com.financetracker.category.dto.CategoryRequest;
import com.financetracker.category.dto.CategoryResponse;
import com.financetracker.category.dto.CategoryRuleResponse;
import com.financetracker.category.dto.CategoryUpdateRequest;
import com.financetracker.category.event.CategoryArchivedEvent;
import com.financetracker.category.model.Category;
import com.financetracker.category.model.CategoryKind;
import com.financetracker.category.model.CategoryRule;
import com.financetracker.category.repository.CategoryRepository;
import com.financetracker.category.repository.CategoryRuleRepository;
import com.financetracker.common.exception.ConflictException;
import com.financetracker.common.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * The defaults everyone shares: system categories and rules ({@code user_id IS NULL}), managed by admins
 * with {@code CATEGORY_ADMIN} (ADR-015/016). System categories are archived, never deleted, because any
 * user's transactions may use them. Callers record the audit entry.
 */
@Service
@RequiredArgsConstructor
public class SystemCategoryService {

    private final CategoryRepository categories;
    private final CategoryRuleRepository rules;
    private final CategorizationService categorization;
    private final CategoryService categoryService;
    private final ApplicationEventPublisher events;

    public List<CategoryResponse> list() {
        return categories.findSystem().stream().map(c -> CategoryResponse.of(c, false)).toList();
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        String name = request.name().strip();
        if (categories.findSystemByName(name).isPresent()) {
            throw new ConflictException("A default category named '" + name + "' already exists");
        }
        Category saved = categories.save(Category.builder()
            .name(name)
            .kind(request.kind() == null ? CategoryKind.EXPENSE : request.kind())
            .color(request.color())
            .icon(request.icon())
            .build());
        categorization.evictAll();
        return CategoryResponse.of(saved, false);
    }

    @Transactional
    public CategoryResponse update(UUID id, CategoryUpdateRequest request) {
        Category category = system(id);
        if (request.name() != null && !request.name().isBlank()) {
            String name = request.name().strip();
            if (Category.OTHER.equals(category.getName()) && !Category.OTHER.equals(name)) {
                throw new IllegalArgumentException("'Other' can't be renamed");
            }
            categories.findSystemByName(name).filter(c -> !c.getId().equals(id)).ifPresent(c -> {
                throw new ConflictException("A default category named '" + name + "' already exists");
            });
            category.setName(name);
        }
        boolean wasArchived = category.isArchived();
        CategoryService.apply(category, request);
        categorization.evictAll();
        Category saved = categories.save(category);
        if (!wasArchived && saved.isArchived()) {
            events.publishEvent(new CategoryArchivedEvent(saved.getId(), null));
        }
        return CategoryResponse.of(saved, false);
    }

    public List<CategoryRuleResponse> listRules() {
        return categoryService.toRuleResponses(rules.findSystem());
    }

    /** Creates the system rule for a keyword, or points the existing one at another system category. */
    @Transactional
    public CategoryRuleResponse saveRule(String keyword, UUID categoryId) {
        String key = CategoryService.normalizedKeyword(keyword);
        Category target = system(categoryId);
        if (target.isArchived()) {
            throw new IllegalArgumentException("Category '" + target.getName() + "' is archived");
        }
        CategoryRule rule = rules.findSystemByKeyword(key)
            .orElseGet(() -> CategoryRule.builder().keyword(key).build());
        rule.setCategoryId(target.getId());
        CategoryRule saved = rules.save(rule);
        categorization.evictAll();
        return categoryService.toRuleResponses(List.of(saved)).get(0);
    }

    /** @return the removed rule's keyword, for the audit log */
    @Transactional
    public String deleteRule(UUID ruleId) {
        CategoryRule rule = rules.findById(ruleId)
            .filter(r -> r.getUserId() == null)
            .orElseThrow(() -> new EntityNotFoundException("Rule not found: " + ruleId));
        rules.delete(rule);
        categorization.evictAll();
        return rule.getKeyword();
    }

    private Category system(UUID id) {
        return categories.findById(id)
            .filter(Category::isSystem)
            .orElseThrow(() -> new EntityNotFoundException("Category not found: " + id));
    }
}
