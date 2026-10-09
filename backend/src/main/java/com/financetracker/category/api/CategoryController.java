package com.financetracker.category.api;

import com.financetracker.category.model.CategoryMapping;
import com.financetracker.category.repository.CategoryMappingRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
@Tag(name = "Categories", description = "Query merchant-to-category mappings and available categories")
public class CategoryController {

    private final CategoryMappingRepository repository;

    @GetMapping
    @Operation(summary = "List all distinct categories")
    public ResponseEntity<List<String>> getCategories() {
        return ResponseEntity.ok(repository.findDistinctCategories());
    }

    @GetMapping("/mappings")
    @Operation(summary = "List all keyword-to-category mappings")
    public ResponseEntity<List<CategoryMapping>> getMappings() {
        return ResponseEntity.ok(repository.findAll());
    }
}
