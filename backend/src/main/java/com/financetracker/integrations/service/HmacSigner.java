package com.financetracker.integrations.service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/** HMAC-SHA256 signing and constant-time verification for webhook payloads (ADR-023 §2/§3). */
public final class HmacSigner {

    private HmacSigner() {
    }

    /** Lowercase-hex HMAC-SHA256 of {@code payload} under {@code secret}. */
    public static String sign(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 is required but unavailable", e);
        }
    }

    /** Constant-time comparison of two hex signatures. */
    public static boolean matches(String expectedHex, String presentedHex) {
        if (expectedHex == null || presentedHex == null) {
            return false;
        }
        return MessageDigest.isEqual(
            expectedHex.getBytes(StandardCharsets.UTF_8), presentedHex.getBytes(StandardCharsets.UTF_8));
    }
}
