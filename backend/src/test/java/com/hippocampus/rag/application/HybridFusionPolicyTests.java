package com.hippocampus.rag.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class HybridFusionPolicyTests {

    @Test
    void exposesInitialBalancedPolicy() {
        assertThat(HybridFusionPolicy.balanced()).isEqualTo(new HybridFusionPolicy(1.0, 1.0, 60));
    }

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1.0})
    void rejectsInvalidLexicalWeight(double weight) {
        assertThatThrownBy(() -> new HybridFusionPolicy(weight, 1, 60))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1.0})
    void rejectsInvalidVectorWeight(double weight) {
        assertThatThrownBy(() -> new HybridFusionPolicy(1, weight, 60))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAllZeroWeights() {
        assertThatThrownBy(() -> new HybridFusionPolicy(0, 0, 60))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one fusion weight must be positive");
    }

    @ParameterizedTest
    @ValueSource(ints = {Integer.MIN_VALUE, -1, 0})
    void rejectsRrfKBelowOne(int rrfK) {
        assertThatThrownBy(() -> new HybridFusionPolicy(1, 1, rrfK))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rrfK must be at least 1");
    }

    @Test
    void calculatesFinitePositiveContributionWithoutIntegerOverflowAtMaximumRrfK() {
        HybridFusionPolicy policy = new HybridFusionPolicy(1, 1, Integer.MAX_VALUE);

        assertThat(policy.lexicalContribution(1)).isFinite().isPositive();
        assertThat(policy.vectorContribution(1)).isFinite().isPositive();
    }
}
