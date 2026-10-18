package com.financetracker.category.event;

import java.util.UUID;

/**
 * A category was archived: it leaves pickers and rules. Other modules react (a budget on it ends from next month).
 *
 * @param categoryId the archived category
 * @param ownerId    its owner, or null for a default (system) category
 */
public record CategoryArchivedEvent(UUID categoryId, UUID ownerId) { }
