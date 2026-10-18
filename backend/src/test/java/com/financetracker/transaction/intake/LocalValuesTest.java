package com.financetracker.transaction.intake;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LocalValues Unit Tests")
class LocalValuesTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = '|', value = {
        "184.500         | 184500",
        "1,240,000       | 1240000",
        "1.240.000       | 1240000",
        "65000           | 65000",
        "12.50           | 12.50",
        "99.99           | 99.99",
        "12,5            | 12.5",
        "1,240,000.50    | 1240000.50",
        "1.240.000,50    | 1240000.50",
        "-45.000 ₫       | -45000",
        "45.000 VND      | 45000",
        "(45,000)        | -45000",
        "45.000-         | -45000",
        "+12.50          | 12.50"
    })
    @DisplayName("Should read amounts written with Vietnamese and international separators")
    void shouldParseAmounts(String text, String expected) {
        assertThat(LocalValues.parseAmount(text)).isEqualByComparingTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"abc", "12.5.x", "--5"})
    @DisplayName("Should return null for text that is not an amount")
    void shouldRejectNonAmounts(String text) {
        assertThat(LocalValues.parseAmount(text)).isNull();
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = '|', value = {
        "05/09/2026        | 2026-09-05",
        "5-9-26            | 2026-09-05",
        "05.09.2026        | 2026-09-05",
        "2026-09-05        | 2026-09-05",
        "2026/09/05        | 2026-09-05",
        "05/09/2026 14:30  | 2026-09-05",
        "2026-09-05T14:30  | 2026-09-05",
        "01/15/2024        | 2024-01-15"
    })
    @DisplayName("Should read day-first dates, and month-first only when day-first is impossible")
    void shouldParseDates(String text, LocalDate expected) {
        assertThat(LocalValues.parseDate(text)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"not a date", "31/02/2026", "13/13/2026", "2026"})
    @DisplayName("Should return null for text that is not a valid date")
    void shouldRejectNonDates(String text) {
        assertThat(LocalValues.parseDate(text)).isNull();
    }
}
