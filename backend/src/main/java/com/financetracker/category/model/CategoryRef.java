package com.financetracker.category.model;

import java.util.UUID;

/** The parts of a category other modules show next to a transaction. */
public record CategoryRef(UUID id, String name, CategoryKind kind, String color) {

    public static CategoryRef of(Category category) {
        return new CategoryRef(category.getId(), category.getName(), category.getKind(), category.getColor());
    }
}
