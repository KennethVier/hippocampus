package com.hippocampus.ai.domain;

import java.util.List;
import java.util.Map;
import java.util.Objects;

final class ContractChecks {

    private ContractChecks() {}

    static String requiredText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    static <T> List<T> immutableList(List<T> values, String name) {
        Objects.requireNonNull(values, name + " must not be null");
        if (values.stream().anyMatch(Objects::isNull)) {
            throw new NullPointerException(name + " must not contain null");
        }
        return List.copyOf(values);
    }

    static Map<String, String> immutableStringMap(Map<String, String> values, String name) {
        Objects.requireNonNull(values, name + " must not be null");
        if (values.entrySet().stream().anyMatch(entry -> entry.getKey() == null || entry.getValue() == null)) {
            throw new NullPointerException(name + " must not contain null keys or values");
        }
        return Map.copyOf(values);
    }
}
