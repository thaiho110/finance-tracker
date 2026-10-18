package com.financetracker.auth.security;

import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Injects the signed-in {@link AuthenticatedUser} into a handler parameter.
 * {@code errorOnInvalidType} makes a request authenticated some other way fail loudly instead of passing null.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@AuthenticationPrincipal(errorOnInvalidType = true)
public @interface CurrentUser {
}
