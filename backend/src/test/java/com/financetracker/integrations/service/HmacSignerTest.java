package com.financetracker.integrations.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("HmacSigner Unit Tests")
class HmacSignerTest {

    @Test
    @DisplayName("The same secret and payload verify; a tampered payload does not")
    void signAndVerify() {
        String secret = "s3cr3t-key";
        String signature = HmacSigner.sign(secret, "123.{\"a\":1}");

        assertThat(HmacSigner.matches(signature, HmacSigner.sign(secret, "123.{\"a\":1}"))).isTrue();
        assertThat(HmacSigner.matches(signature, HmacSigner.sign(secret, "123.{\"a\":2}"))).isFalse();
        assertThat(HmacSigner.matches(signature, HmacSigner.sign("other-key", "123.{\"a\":1}"))).isFalse();
        assertThat(HmacSigner.matches(signature, null)).isFalse();
    }
}
