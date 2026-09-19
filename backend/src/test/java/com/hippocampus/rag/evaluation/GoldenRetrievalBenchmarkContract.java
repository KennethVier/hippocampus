package com.hippocampus.rag.evaluation;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;

final class GoldenRetrievalBenchmarkContract {
    private GoldenRetrievalBenchmarkContract() {}

    record Baseline(
            String datasetVersion,
            int primaryK,
            List<Integer> kValues,
            Map<String, CaseBaseline> cases
    ) {}

    record CaseBaseline(
            ChannelBaseline lexical,
            ChannelBaseline vector,
            ChannelBaseline hybrid,
            Double expectedSectionHitRate,
            Double explicitIrrelevantContextRate
    ) {}

    record ChannelBaseline(
            MetricResult k1,
            MetricResult k3,
            MetricResult k5,
            Double plainMrr
    ) {}

    record MetricResult(Double recall, Double precision, Double mrr) {}

    static Baseline load(ObjectMapper objectMapper, String resourcePath) throws IOException {
        try (InputStream input = GoldenRetrievalBenchmarkContract.class.getResourceAsStream(resourcePath)) {
            if (input == null) throw new IOException("Benchmark resource not found: " + resourcePath);
            return objectMapper.readValue(input, Baseline.class);
        }
    }

    static void validate(Baseline benchmark, GoldenRetrievalDataset dataset, String resourceName) {
        require(benchmark != null, resourceName + " must contain an object");
        require(Objects.equals(dataset.datasetVersion(), benchmark.datasetVersion()),
                resourceName + " datasetVersion must equal dataset.json");
        require(dataset.primaryK() == benchmark.primaryK(),
                resourceName + " primaryK must equal dataset.json");
        require(Objects.equals(dataset.kValues(), benchmark.kValues()),
                resourceName + " kValues must equal dataset.json");
        require(benchmark.cases() != null, resourceName + " cases must be present");

        Set<String> datasetCaseIds = dataset.cases().stream()
                .map(GoldenRetrievalDataset.Case::id)
                .collect(Collectors.toUnmodifiableSet());
        require(datasetCaseIds.equals(benchmark.cases().keySet()),
                resourceName + " case IDs must exactly equal dataset.json case IDs");

        benchmark.cases().forEach((caseId, caseBaseline) -> validateCase(caseId, caseBaseline, resourceName));
    }

    static void verifyBaselineAsThreshold(Baseline baseline, Baseline thresholds, double tolerance) {
        require(tolerance > 0.0 && Double.isFinite(tolerance), "numeric tolerance must be finite and positive");
        require(Objects.equals(baseline.datasetVersion(), thresholds.datasetVersion()),
                "threshold datasetVersion must equal baseline");
        require(baseline.primaryK() == thresholds.primaryK(), "threshold primaryK must equal baseline");
        require(Objects.equals(baseline.kValues(), thresholds.kValues()), "threshold kValues must equal baseline");
        require(Objects.equals(baseline.cases().keySet(), thresholds.cases().keySet()),
                "threshold case IDs must equal baseline");

        baseline.cases().forEach((caseId, expected) ->
                compareCase(caseId, "threshold policy", expected, thresholds.cases().get(caseId), tolerance));
    }

    static void verifyObserved(
            Baseline baseline,
            Map<String, GoldenRetrievalMetrics.CaseResult> observed,
            double tolerance
    ) {
        require(Objects.equals(baseline.cases().keySet(), observed.keySet()),
                "observed case IDs must exactly equal committed baseline case IDs");
        baseline.cases().forEach((caseId, expected) -> {
            GoldenRetrievalMetrics.CaseResult actual = observed.get(caseId);
            compareChannel(caseId, "LEXICAL", expected.lexical(), actual.lexical(), tolerance);
            compareChannel(caseId, "VECTOR", expected.vector(), actual.vector(), tolerance);
            compareChannel(caseId, "HYBRID", expected.hybrid(), actual.hybrid(), tolerance);
            compare(caseId, "expectedSectionHitRate", expected.expectedSectionHitRate(),
                    actual.expectedSectionHitRate(), tolerance);
            compare(caseId, "explicitIrrelevantContextRate", expected.explicitIrrelevantContextRate(),
                    actual.explicitIrrelevantContextRate(), tolerance);
        });
    }

    private static void validateCase(String caseId, CaseBaseline value, String resourceName) {
        require(value != null, resourceName + " case " + caseId + " must be present");
        validateChannel(caseId, "lexical", value.lexical(), resourceName);
        validateChannel(caseId, "vector", value.vector(), resourceName);
        validateChannel(caseId, "hybrid", value.hybrid(), resourceName);
        validateNormalized(caseId, "expectedSectionHitRate", value.expectedSectionHitRate(), resourceName);
        validateNormalized(caseId, "explicitIrrelevantContextRate", value.explicitIrrelevantContextRate(), resourceName);
    }

    private static void validateChannel(String caseId, String channel, ChannelBaseline value, String resourceName) {
        require(value != null, resourceName + " case " + caseId + " missing " + channel);
        validateMetric(caseId, channel + ".k1", value.k1(), resourceName);
        validateMetric(caseId, channel + ".k3", value.k3(), resourceName);
        validateMetric(caseId, channel + ".k5", value.k5(), resourceName);
        validateNormalized(caseId, channel + ".plainMrr", value.plainMrr(), resourceName);
        require(Double.compare(value.k1().mrr(), value.plainMrr()) == 0
                        && Double.compare(value.k3().mrr(), value.plainMrr()) == 0
                        && Double.compare(value.k5().mrr(), value.plainMrr()) == 0,
                resourceName + " case " + caseId + " " + channel
                        + " MRR fields must all use the plain full-ranked-list value");
    }

    private static void validateMetric(String caseId, String metricName, MetricResult value, String resourceName) {
        require(value != null, resourceName + " case " + caseId + " missing " + metricName);
        validateNormalized(caseId, metricName + ".recall", value.recall(), resourceName);
        validateNormalized(caseId, metricName + ".precision", value.precision(), resourceName);
        validateNormalized(caseId, metricName + ".mrr", value.mrr(), resourceName);
    }

    private static void validateNormalized(String caseId, String metricName, Double value, String resourceName) {
        require(value != null, resourceName + " case " + caseId + " missing " + metricName);
        require(Double.isFinite(value), resourceName + " case " + caseId + " " + metricName + " must be finite");
        require(value >= 0.0 && value <= 1.0,
                resourceName + " case " + caseId + " " + metricName + " must be within [0,1]");
    }

    private static void compareCase(
            String caseId,
            String comparison,
            CaseBaseline expected,
            CaseBaseline actual,
            double tolerance
    ) {
        compareChannel(caseId, comparison + " LEXICAL", expected.lexical(), actual.lexical(), tolerance);
        compareChannel(caseId, comparison + " VECTOR", expected.vector(), actual.vector(), tolerance);
        compareChannel(caseId, comparison + " HYBRID", expected.hybrid(), actual.hybrid(), tolerance);
        compare(caseId, comparison + " expectedSectionHitRate", expected.expectedSectionHitRate(),
                actual.expectedSectionHitRate(), tolerance);
        compare(caseId, comparison + " explicitIrrelevantContextRate", expected.explicitIrrelevantContextRate(),
                actual.explicitIrrelevantContextRate(), tolerance);
    }

    private static void compareChannel(
            String caseId,
            String channel,
            ChannelBaseline expected,
            GoldenRetrievalMetrics.ChannelResults actual,
            double tolerance
    ) {
        compareMetric(caseId, channel + " K=1", expected.k1(), actual.k1(), tolerance);
        compareMetric(caseId, channel + " K=3", expected.k3(), actual.k3(), tolerance);
        compareMetric(caseId, channel + " K=5", expected.k5(), actual.k5(), tolerance);
        compare(caseId, channel + " plainMrr", expected.plainMrr(), actual.plainMrr(), tolerance);
    }

    private static void compareChannel(
            String caseId,
            String channel,
            ChannelBaseline expected,
            ChannelBaseline actual,
            double tolerance
    ) {
        compareMetric(caseId, channel + " k1", expected.k1(), actual.k1(), tolerance);
        compareMetric(caseId, channel + " k3", expected.k3(), actual.k3(), tolerance);
        compareMetric(caseId, channel + " k5", expected.k5(), actual.k5(), tolerance);
        compare(caseId, channel + " plainMrr", expected.plainMrr(), actual.plainMrr(), tolerance);
    }

    private static void compareMetric(
            String caseId,
            String metricName,
            MetricResult expected,
            GoldenRetrievalMetrics.MetricResult actual,
            double tolerance
    ) {
        compare(caseId, metricName + " recall", expected.recall(), actual.recall(), tolerance);
        compare(caseId, metricName + " precision", expected.precision(), actual.precision(), tolerance);
        compare(caseId, metricName + " mrr", expected.mrr(), actual.mrr(), tolerance);
    }

    private static void compareMetric(
            String caseId,
            String metricName,
            MetricResult expected,
            MetricResult actual,
            double tolerance
    ) {
        compare(caseId, metricName + " recall", expected.recall(), actual.recall(), tolerance);
        compare(caseId, metricName + " precision", expected.precision(), actual.precision(), tolerance);
        compare(caseId, metricName + " mrr", expected.mrr(), actual.mrr(), tolerance);
    }

    private static void compare(String caseId, String metricName, double expected, double actual, double tolerance) {
        if (!Double.isFinite(actual) || Math.abs(expected - actual) > tolerance) {
            throw new AssertionError("Case " + caseId + " " + metricName
                    + " differs: committed=" + expected + ", observed=" + actual);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
