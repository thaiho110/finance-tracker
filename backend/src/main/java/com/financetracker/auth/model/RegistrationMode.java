package com.financetracker.auth.model;

/**
 * Who may create an account (ADR-016). Default is {@link #INVITE_ONLY} in production and {@code OPEN} in dev
 * (via {@code app.registration.forced-mode}). The super admin changes it on the admin Settings page.
 */
public enum RegistrationMode {
    /** Anyone can register. */
    OPEN,
    /** Only someone with a valid, unused invite can register. */
    INVITE_ONLY,
    /** No one can register (the configured owner can still bootstrap their own account). */
    CLOSED
}
