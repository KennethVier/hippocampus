package com.hippocampus.materials.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class BinaryObjectKeyTests {

    @Test
    void preservesValidNestedKeyExactly() {
        String value = "materials/User_1/material-2/version.3/original.PDF";

        assertThat(new BinaryObjectKey(value).value()).isEqualTo(value);
    }

    @ParameterizedTest
    @MethodSource("invalidKeys")
    void rejectsInvalidKeysWithoutNormalizingThem(String candidate) {
        assertThatThrownBy(() -> new BinaryObjectKey(candidate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid binary object key");
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> new BinaryObjectKey(null))
                .isInstanceOf(NullPointerException.class);
    }

    private static Stream<String> invalidKeys() {
        return Stream.of(
                "", "/leading", "trailing/", "a//b", ".", "..", "a/./b",
                "../outside", "a/../../outside", "..\\outside", "a\\b",
                "/tmp/file", "C:\\file", "C:/file", "\\\\server\\share",
                "nul\0value", "control\u001fvalue", "a:b", "space value", "ümlaut",
                "%2e%2e", "%2f", "a".repeat(256), "a".repeat(1025));
    }
}
