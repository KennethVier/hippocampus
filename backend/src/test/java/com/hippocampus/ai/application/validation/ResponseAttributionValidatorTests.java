package com.hippocampus.ai.application.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.hippocampus.ai.domain.ResponseAttributionDiscovery;
import com.hippocampus.ai.domain.ResponseAttributionVerification;
import com.hippocampus.ai.domain.ResponseEvaluationInput;
import com.hippocampus.learning.domain.StudyMissionGroundingMode;
import com.hippocampus.learning.port.AuthorizedResponseEvidence;
import com.hippocampus.learning.port.ResponseEvaluationPort;

class ResponseAttributionValidatorTests {
    private static final UUID REFERENCE = UUID.fromString("4d46ea96-e51e-48a4-b56d-24a9e10b3270");
    private static final UUID VERSION = UUID.fromString("88430ff0-283c-4c21-9dbd-2940b47073f1");
    private static final String RESPONSE =
            "The wrist extensors are paralyzed, but the median nerve is responsible.";
    private static final String SOURCE =
            "Radial nerve injury can denervate and paralyze the wrist extensor muscles.";

    private final ResponseAttributionValidator validator = new ResponseAttributionValidator(
            new ResponseAttributionValidator.Limits(8_000, 8, 32_000, 32, 64, 128, 60_000));
    private final VerifiedResponseEvaluationAggregator aggregator =
            new VerifiedResponseEvaluationAggregator();

    @Test
    void caseFiveRecoversIndependentParalysisFromZeroInitialCredit() {
        var input = input(RESPONSE);
        var evidence = evidence();
        var discovery = discovery();
        var verification = verification();
        validator.validateDiscovery(input, evidence, discovery);
        validator.validateVerification(input, evidence, discovery, verification);

        var result = aggregator.aggregate(input, discovery, verification);
        assertThat(result.outcome()).isEqualTo(ResponseEvaluationPort.Outcome.PARTIAL);
        assertThat(result.correctConcepts()).containsExactly("wrist extensor paralysis");
        assertThat(result.missingConcepts()).containsExactly(
                "Radial nerve injury causes wrist extensor paralysis");
        assertThat(result.misconceptions()).containsExactly("median nerve attribution");
        assertThat(result.feedback()).contains("wrist extensor paralysis")
                .contains("Radial nerve injury causes wrist extensor paralysis")
                .contains("median nerve attribution is incorrect")
                .contains(SOURCE);
    }

    @Test
    void omittedContradictoryClauseCannotProduceCorrectCredit() {
        String response = "Radial nerve injury paralyzes wrist extensors, but another nerve causes it.";
        int firstEnd = response.indexOf(',');
        var assertion = new ResponseAttributionDiscovery.Assertion("a", 0, firstEnd,
                response.substring(0, firstEnd), "radial nerve injury paralyzes wrist extensors",
                "AFFIRMED", List.of(), List.of(0));
        var candidate = new ResponseAttributionDiscovery.Candidate("c", List.of("a"), 0,
                "radial nerve injury paralyzes wrist extensors", "COMPLETE",
                List.of(mechanismEvidence()));
        var incomplete = new ResponseAttributionDiscovery(List.of(assertion),
                List.of(candidate), List.of(0), true, List.of());

        assertThatThrownBy(() -> validator.validateDiscovery(input(response), evidence(),
                incomplete)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing from assertion discovery");
    }

    @Test
    void broadAssertionSpanCannotHideAContradictoryClause() {
        String response = "Radial nerve injury paralyzes wrist extensors, but another nerve causes it.";
        var assertion = new ResponseAttributionDiscovery.Assertion("a", 0,
                response.length(), response, "radial nerve injury paralyzes wrist extensors",
                "AFFIRMED", List.of(), List.of(0));
        var candidate = new ResponseAttributionDiscovery.Candidate("c", List.of("a"), 0,
                "radial nerve injury paralyzes wrist extensors", "COMPLETE",
                List.of(mechanismEvidence()));
        var incomplete = new ResponseAttributionDiscovery(List.of(assertion),
                List.of(candidate), List.of(0), true, List.of());

        assertThatThrownBy(() -> validator.validateDiscovery(input(response), evidence(),
                incomplete)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing from assertion discovery");
    }

    @Test
    void fullResponseAssertionCannotHideCoordinatedWrongAttribution() {
        String response = "Radial nerve injury paralyzes wrist extensors, and the median nerve is responsible.";
        var assertion = new ResponseAttributionDiscovery.Assertion("a", 0,
                response.length(), response,
                "radial nerve injury causes wrist extensor paralysis",
                "AFFIRMED", List.of(), List.of(0));
        var candidate = new ResponseAttributionDiscovery.Candidate("c", List.of("a"), 0,
                "radial nerve injury causes wrist extensor paralysis", "COMPLETE",
                List.of(mechanismEvidence()));
        var incomplete = new ResponseAttributionDiscovery(List.of(assertion),
                List.of(candidate), List.of(0), true, List.of());

        assertThatThrownBy(() -> validator.validateDiscovery(input(response), evidence(),
                incomplete)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing from assertion discovery");
    }

    @Test
    void fullResponseAssertionCannotHideCausalClause() {
        String response = "Wrist extensors are paralyzed because the median nerve is responsible.";
        var assertion = new ResponseAttributionDiscovery.Assertion("a", 0,
                response.length(), response, "wrist extensor paralysis",
                "AFFIRMED", List.of(), List.of(0));
        var incomplete = new ResponseAttributionDiscovery(List.of(assertion),
                List.of(), List.of(0), true, List.of());

        assertThatThrownBy(() -> validator.validateDiscovery(input(response), evidence(),
                incomplete)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing from assertion discovery");
    }

    @Test
    void separatelyRepresentedCompoundAnswerCanStillBeCorrect() {
        String response = "Radial nerve injury denervates wrist extensors, and wrist extensors are paralyzed.";
        int firstEnd = response.indexOf(',');
        int secondStart = response.indexOf("wrist extensors", firstEnd);
        var discovery = new ResponseAttributionDiscovery(List.of(
                new ResponseAttributionDiscovery.Assertion("a", 0, firstEnd,
                        response.substring(0, firstEnd),
                        "radial nerve injury denervates wrist extensors", "AFFIRMED",
                        List.of(), List.of(0)),
                new ResponseAttributionDiscovery.Assertion("b", secondStart,
                        response.length() - 1,
                        response.substring(secondStart, response.length() - 1),
                        "wrist extensors are paralyzed", "AFFIRMED", List.of(), List.of(0))),
                List.of(new ResponseAttributionDiscovery.Candidate("c", List.of("a", "b"), 0,
                        "radial nerve injury causes wrist extensor paralysis", "COMPLETE",
                        List.of(mechanismEvidence()))), List.of(0), true, List.of());
        var verification = new ResponseAttributionVerification(List.of(
                new ResponseAttributionVerification.AssertionDisposition("a",
                        ResponseAttributionVerification.Disposition.SUPPORTED,
                        "causal assertion stated", ResponseAttributionVerification.Fidelity.FAITHFUL),
                new ResponseAttributionVerification.AssertionDisposition("b",
                        ResponseAttributionVerification.Disposition.SUPPORTED,
                        "paralysis stated", ResponseAttributionVerification.Fidelity.FAITHFUL)),
                List.of(new ResponseAttributionVerification.CandidateDisposition("c",
                        ResponseAttributionVerification.Disposition.SUPPORTED,
                        "source supports full relationship")),
                List.of(new ResponseAttributionVerification.ConceptCoverage(0,
                        ResponseAttributionVerification.Coverage.COMPLETE,
                        "both clauses establish the relation")),
                List.of(), true, List.of(), mechanismEvidence());

        var input = input(response);
        validator.validateDiscovery(input, evidence(), discovery);
        validator.validateVerification(input, evidence(), discovery, verification);
        assertThat(aggregator.aggregate(input, discovery, verification).outcome())
                .isEqualTo(ResponseEvaluationPort.Outcome.CORRECT);
    }

    @Test
    void yetAndWhereasDoNotRequireAssertionSpansToIncludeConnectors() {
        for (String connector : List.of("yet", "whereas")) {
            String response = "Radial nerve injury denervates wrist extensors, " + connector
                    + " wrist extensors are paralyzed.";
            int firstEnd = response.indexOf(',');
            int secondStart = response.indexOf("wrist extensors", firstEnd);
            var discovery = new ResponseAttributionDiscovery(List.of(
                    new ResponseAttributionDiscovery.Assertion("a", 0, firstEnd,
                            response.substring(0, firstEnd), "radial nerve injury denervation",
                            "AFFIRMED", List.of(), List.of(0)),
                    new ResponseAttributionDiscovery.Assertion("b", secondStart,
                            response.length() - 1,
                            response.substring(secondStart, response.length() - 1),
                            "wrist extensor paralysis", "AFFIRMED", List.of(), List.of(0))),
                    List.of(), List.of(0), true, List.of());

            validator.validateDiscovery(input(response), evidence(), discovery);
        }
    }

    @Test
    void unfaithfulOrUnresolvedAssertionMeaningCannotBeGraded() {
        var original = verification();
        for (var fidelity : List.of(ResponseAttributionVerification.Fidelity.MISMATCH,
                ResponseAttributionVerification.Fidelity.AMBIGUOUS)) {
            var unfaithful = new ResponseAttributionVerification(List.of(
                    new ResponseAttributionVerification.AssertionDisposition("a",
                            ResponseAttributionVerification.Disposition.SUPPORTED,
                            "normalized meaning lost attribution", fidelity),
                    original.assertions().get(1)), original.candidates(),
                    original.expectedConceptCoverage(), original.misconceptions(),
                    true, List.of(), mechanismEvidence());

            assertThatThrownBy(() -> validator.validateVerification(input(RESPONSE), evidence(),
                    discovery(), unfaithful)).isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not verified against the response");
        }
    }

    @Test
    void broadAssertionSpanCannotHideASecondSentence() {
        String response = "Radial nerve injury paralyzes wrist extensors. Another nerve is responsible.";
        var assertion = new ResponseAttributionDiscovery.Assertion("a", 0,
                response.length(), response, "radial nerve injury paralyzes wrist extensors",
                "AFFIRMED", List.of(), List.of(0));
        var incomplete = new ResponseAttributionDiscovery(List.of(assertion),
                List.of(), List.of(0), true, List.of());

        assertThatThrownBy(() -> validator.validateDiscovery(input(response), evidence(),
                incomplete)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing from assertion discovery");
    }

    @Test
    void exactGoldenV6CaseFiveWrongAttributionCannotDisappear() {
        String response = "Median nerve injury paralyzes the wrist extensors.";
        int paralysisStart = response.indexOf("paralyzes");
        var assertion = new ResponseAttributionDiscovery.Assertion("a", paralysisStart,
                response.length() - 1, response.substring(paralysisStart, response.length() - 1),
                "wrist extensor paralysis", "AFFIRMED", List.of(), List.of(0));
        var incomplete = new ResponseAttributionDiscovery(List.of(assertion), List.of(),
                List.of(0), true, List.of());

        assertThatThrownBy(() -> validator.validateDiscovery(input(response), evidence(),
                incomplete)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing from assertion discovery");
    }

    @Test
    void exactGoldenV6CaseFivePreservesPartialWhenWrongAttributionIsRecorded() {
        String response = "Median nerve injury paralyzes the wrist extensors.";
        int paralysisStart = response.indexOf("paralyzes");
        int sourceStart = SOURCE.indexOf("paralyze");
        var constituent = new ResponseAttributionDiscovery.Assertion("a", paralysisStart,
                response.length() - 1, response.substring(paralysisStart, response.length() - 1),
                "wrist extensor paralysis", "AFFIRMED", List.of(), List.of(0));
        var wrongAttribution = new ResponseAttributionDiscovery.Assertion("b", 0,
                response.length() - 1, response.substring(0, response.length() - 1),
                "median nerve injury causes wrist extensor paralysis", "AFFIRMED",
                List.of(), List.of(0));
        var candidate = new ResponseAttributionDiscovery.Candidate("c", List.of("a"), 0,
                "wrist extensor paralysis", "CONSTITUENT",
                List.of(new ResponseAttributionDiscovery.SourceSpan(
                        REFERENCE.toString(), VERSION.toString(), sourceStart,
                        sourceStart + "paralyze".length(), "paralyze")));
        var discovery = new ResponseAttributionDiscovery(
                List.of(constituent, wrongAttribution), List.of(candidate),
                List.of(0), true, List.of());
        var verification = new ResponseAttributionVerification(
                List.of(new ResponseAttributionVerification.AssertionDisposition("a",
                                ResponseAttributionVerification.Disposition.SUPPORTED,
                                "independent functional constituent", ResponseAttributionVerification.Fidelity.FAITHFUL),
                        new ResponseAttributionVerification.AssertionDisposition("b",
                                ResponseAttributionVerification.Disposition.UNSUPPORTED,
                                "incorrect nerve attribution", ResponseAttributionVerification.Fidelity.FAITHFUL)),
                List.of(new ResponseAttributionVerification.CandidateDisposition("c",
                        ResponseAttributionVerification.Disposition.SUPPORTED,
                        "source backed constituent")),
                List.of(new ResponseAttributionVerification.ConceptCoverage(0,
                        ResponseAttributionVerification.Coverage.MISSING,
                        "correct causal attribution absent")),
                List.of(new ResponseAttributionVerification.Misconception("b",
                        "median nerve attribution")), true, List.of(), mechanismEvidence());

        var input = input(response);
        validator.validateDiscovery(input, evidence(), discovery);
        validator.validateVerification(input, evidence(), discovery, verification);
        var result = aggregator.aggregate(input, discovery, verification);
        assertThat(result.outcome()).isEqualTo(ResponseEvaluationPort.Outcome.PARTIAL);
        assertThat(result.correctConcepts()).containsExactly("wrist extensor paralysis");
        assertThat(result.misconceptions()).containsExactly("median nerve attribution");
    }

    @Test
    void caseFiveCannotBeCorrectWhenWrongMedianAttributionIsRejected() {
        var original = verification();
        var contradictory = new ResponseAttributionVerification(
                original.assertions(), original.candidates(),
                List.of(new ResponseAttributionVerification.ConceptCoverage(0,
                        ResponseAttributionVerification.Coverage.COMPLETE,
                        "incorrectly claims the entire relation")),
                List.of(), true, List.of(), mechanismEvidence());

        assertThatThrownBy(() -> validator.validateVerification(
                input(RESPONSE), evidence(), discovery(), contradictory))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(aggregator.aggregate(input(RESPONSE), discovery(), contradictory).outcome())
                .isNotEqualTo(ResponseEvaluationPort.Outcome.CORRECT);
    }

    @Test
    void correctFeedbackCreditsKnowledgeAndExplainsAuthorizedMechanism() {
        String response = "Radial nerve injury paralyzes wrist extensors.";
        var input = input(response);
        var assertion = new ResponseAttributionDiscovery.Assertion("a", 0,
                response.length(), response, "radial nerve injury causes wrist extensor paralysis",
                "AFFIRMED", List.of(), List.of(0));
        var candidate = new ResponseAttributionDiscovery.Candidate("c", List.of("a"), 0,
                "radial nerve injury causes wrist extensor paralysis", "COMPLETE",
                List.of(mechanismEvidence()));
        var discovery = new ResponseAttributionDiscovery(List.of(assertion),
                List.of(candidate), List.of(0), true, List.of());
        var verification = new ResponseAttributionVerification(
                List.of(new ResponseAttributionVerification.AssertionDisposition("a",
                        ResponseAttributionVerification.Disposition.SUPPORTED, "stated", ResponseAttributionVerification.Fidelity.FAITHFUL)),
                List.of(new ResponseAttributionVerification.CandidateDisposition("c",
                        ResponseAttributionVerification.Disposition.SUPPORTED, "source backed")),
                List.of(new ResponseAttributionVerification.ConceptCoverage(0,
                        ResponseAttributionVerification.Coverage.COMPLETE, "full relation")),
                List.of(), true, List.of(), mechanismEvidence());

        validator.validateDiscovery(input, evidence(), discovery);
        validator.validateVerification(input, evidence(), discovery, verification);
        var result = aggregator.aggregate(input, discovery, verification);
        assertThat(result.outcome()).isEqualTo(ResponseEvaluationPort.Outcome.CORRECT);
        assertThat(result.feedback()).contains("correctly identified")
                .contains(SOURCE);
    }

    @Test
    void uncoveredArticleAndParaphraseStillPermitCompleteCredit() {
        String response = "The radial nerve lesion stops wrist extensors.";
        int start = response.indexOf("radial");
        var input = input(response);
        var assertion = new ResponseAttributionDiscovery.Assertion("a", start,
                response.length() - 1, response.substring(start, response.length() - 1),
                "radial nerve injury causes wrist extensor paralysis", "AFFIRMED",
                List.of(), List.of(0));
        var candidate = new ResponseAttributionDiscovery.Candidate("c", List.of("a"), 0,
                "radial nerve injury causes wrist extensor paralysis", "COMPLETE",
                List.of(mechanismEvidence()));
        var discovery = new ResponseAttributionDiscovery(List.of(assertion),
                List.of(candidate), List.of(0), true, List.of());
        var verification = new ResponseAttributionVerification(
                List.of(new ResponseAttributionVerification.AssertionDisposition("a",
                        ResponseAttributionVerification.Disposition.SUPPORTED,
                        "same supported causal meaning", ResponseAttributionVerification.Fidelity.FAITHFUL)),
                List.of(new ResponseAttributionVerification.CandidateDisposition("c",
                        ResponseAttributionVerification.Disposition.SUPPORTED,
                        "source supported paraphrase")),
                List.of(new ResponseAttributionVerification.ConceptCoverage(0,
                        ResponseAttributionVerification.Coverage.COMPLETE,
                        "full relation expressed")),
                List.of(), true, List.of(), mechanismEvidence());

        validator.validateDiscovery(input, evidence(), discovery);
        validator.validateVerification(input, evidence(), discovery, verification);
        assertThat(aggregator.aggregate(input, discovery, verification).outcome())
                .isEqualTo(ResponseEvaluationPort.Outcome.CORRECT);
    }

    @Test
    void incorrectFeedbackCorrectsMisconceptionUsingAuthorizedMechanism() {
        String response = "The median nerve is responsible.";
        var input = input(response);
        var assertion = new ResponseAttributionDiscovery.Assertion("a", 0,
                response.length(), response, "median nerve attribution", "AFFIRMED",
                List.of(), List.of(0));
        var discovery = new ResponseAttributionDiscovery(List.of(assertion), List.of(),
                List.of(0), true, List.of());
        var verification = new ResponseAttributionVerification(
                List.of(new ResponseAttributionVerification.AssertionDisposition("a",
                        ResponseAttributionVerification.Disposition.UNSUPPORTED,
                        "wrong nerve attribution", ResponseAttributionVerification.Fidelity.FAITHFUL)),
                List.of(), List.of(new ResponseAttributionVerification.ConceptCoverage(0,
                        ResponseAttributionVerification.Coverage.MISSING,
                        "correct relation absent")),
                List.of(new ResponseAttributionVerification.Misconception("a",
                        "median nerve attribution")), true, List.of(), mechanismEvidence());

        validator.validateDiscovery(input, evidence(), discovery);
        validator.validateVerification(input, evidence(), discovery, verification);
        var result = aggregator.aggregate(input, discovery, verification);
        assertThat(result.outcome()).isEqualTo(ResponseEvaluationPort.Outcome.INCORRECT);
        assertThat(result.feedback()).contains("missing concept or relationship")
                .contains("median nerve attribution is incorrect")
                .contains(SOURCE);
    }

    @Test
    void completeCoverageRequiresAFullSupportedRelationship() {
        var original = verification();
        var inconsistent = new ResponseAttributionVerification(
                List.of(new ResponseAttributionVerification.AssertionDisposition("a",
                                ResponseAttributionVerification.Disposition.SUPPORTED, "stated", ResponseAttributionVerification.Fidelity.FAITHFUL),
                        new ResponseAttributionVerification.AssertionDisposition("b",
                                ResponseAttributionVerification.Disposition.SUPPORTED, "stated", ResponseAttributionVerification.Fidelity.FAITHFUL)),
                original.candidates(),
                List.of(new ResponseAttributionVerification.ConceptCoverage(0,
                        ResponseAttributionVerification.Coverage.COMPLETE,
                        "only the constituent was supported")),
                List.of(), true, List.of(), mechanismEvidence());

        assertThatThrownBy(() -> validator.validateVerification(
                input(RESPONSE), evidence(), discovery(), inconsistent))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void forgedFeedbackSourceCannotBeShownAsVerifiedMechanism() {
        var original = verification();
        var forged = new ResponseAttributionVerification(original.assertions(),
                original.candidates(), original.expectedConceptCoverage(),
                original.misconceptions(), true, List.of(),
                new ResponseAttributionDiscovery.SourceSpan(UUID.randomUUID().toString(),
                        VERSION.toString(), 0, SOURCE.length(), SOURCE));

        assertThatThrownBy(() -> validator.validateVerification(
                input(RESPONSE), evidence(), discovery(), forged))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsTamperedSourceAndSurrogateBoundary() {
        var input = input(RESPONSE);
        var valid = discovery();
        var candidate = valid.candidates().getFirst();
        var span = candidate.sourceEvidence().getFirst();
        var tampered = new ResponseAttributionDiscovery(
                valid.assertions(),
                List.of(new ResponseAttributionDiscovery.Candidate(candidate.id(),
                        candidate.assertionIds(), candidate.expectedConceptIndex(),
                        candidate.constituentMeaning(), candidate.relationToExpectedConcept(),
                        List.of(new ResponseAttributionDiscovery.SourceSpan(
                                span.sourceReferenceId(), span.sourceVersion(),
                                span.sourceStart(), span.sourceEnd(), "median nerve")))),
                valid.coveredExpectedConceptIndexes(), true, List.of());
        assertThatThrownBy(() -> validator.validateDiscovery(input, evidence(), tampered))
                .isInstanceOf(IllegalArgumentException.class);

        String unicode = "🧠 denervation";
        var broken = new ResponseAttributionDiscovery(
                List.of(new ResponseAttributionDiscovery.Assertion(
                        "a", 1, 2, unicode.substring(1, 2), "brain",
                        "AFFIRMED", List.of(), List.of(0))),
                List.of(), List.of(0), true, List.of());
        assertThatThrownBy(() -> validator.validateDiscovery(input(unicode), evidence(), broken))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void caseThreeDoesNotBorrowNerveIdentityFromQuestion() {
        var input = new ResponseEvaluationInput(
                "What does radial nerve injury cause?",
                List.of("Radial nerve injury", "Denervation"),
                "Radial nerve injury causes denervation", "It causes denervation.");
        var assertion = new ResponseAttributionDiscovery.Assertion(
                "a", 0, input.studentResponse().length(), input.studentResponse(),
                "denervation", "AFFIRMED",
                List.of(), List.of(1));
        var candidate = new ResponseAttributionDiscovery.Candidate(
                "c", List.of("a"), 1, "denervation", "COMPLETE",
                List.of(new ResponseAttributionDiscovery.SourceSpan(
                        REFERENCE.toString(), VERSION.toString(),
                        SOURCE.indexOf("denervate"),
                        SOURCE.indexOf("denervate") + "denervate".length(),
                        "denervate")));
        var discovery = new ResponseAttributionDiscovery(
                List.of(assertion), List.of(candidate), List.of(0, 1), true, List.of());
        var verification = new ResponseAttributionVerification(
                List.of(new ResponseAttributionVerification.AssertionDisposition(
                        "a", ResponseAttributionVerification.Disposition.SUPPORTED, "Stated", ResponseAttributionVerification.Fidelity.FAITHFUL)),
                List.of(new ResponseAttributionVerification.CandidateDisposition(
                        "c", ResponseAttributionVerification.Disposition.SUPPORTED, "Supported")),
                List.of(new ResponseAttributionVerification.ConceptCoverage(
                                0, ResponseAttributionVerification.Coverage.MISSING, "Not stated"),
                        new ResponseAttributionVerification.ConceptCoverage(
                                1, ResponseAttributionVerification.Coverage.COMPLETE, "Stated")),
                List.of(), true, List.of(), mechanismEvidence());
        validator.validateDiscovery(input, evidence(), discovery);
        validator.validateVerification(input, evidence(), discovery, verification);
        var result = aggregator.aggregate(input, discovery, verification);
        assertThat(result.outcome()).isEqualTo(ResponseEvaluationPort.Outcome.PARTIAL);
        assertThat(result.correctConcepts()).containsExactly("denervation");
        assertThat(result.missingConcepts()).containsExactly("Radial nerve injury");
        assertThat(result.feedback()).contains("denervation")
                .contains("Radial nerve injury")
                .contains(SOURCE);
    }

    @Test
    void caseThreePronounAloneCannotSupportBorrowedRadialNerveIdentity() {
        String response = "It causes denervation.";
        var input = new ResponseEvaluationInput("What does radial nerve injury cause?",
                List.of("Radial nerve injury"), "Radial nerve injury causes denervation",
                response);
        var assertion = new ResponseAttributionDiscovery.Assertion("a", 0, 2, "It",
                "radial nerve injury", "AFFIRMED", List.of(), List.of(0));
        var remainder = new ResponseAttributionDiscovery.Assertion("b", 3,
                response.length() - 1, response.substring(3, response.length() - 1),
                "denervation", "AFFIRMED", List.of(), List.of(0));
        var candidate = new ResponseAttributionDiscovery.Candidate("c", List.of("a"), 0,
                "radial nerve injury", "COMPLETE", List.of(mechanismEvidence()));
        var discovery = new ResponseAttributionDiscovery(List.of(assertion, remainder),
                List.of(candidate), List.of(0), true, List.of());
        var verification = new ResponseAttributionVerification(
                List.of(new ResponseAttributionVerification.AssertionDisposition("a",
                        ResponseAttributionVerification.Disposition.SUPPORTED,
                        "incorrectly borrowed from question", ResponseAttributionVerification.Fidelity.FAITHFUL)),
                List.of(new ResponseAttributionVerification.CandidateDisposition("c",
                        ResponseAttributionVerification.Disposition.SUPPORTED,
                        "incorrectly borrowed from question")),
                List.of(new ResponseAttributionVerification.ConceptCoverage(0,
                        ResponseAttributionVerification.Coverage.COMPLETE, "claimed")),
                List.of(), true, List.of(), mechanismEvidence());

        validator.validateDiscovery(input, evidence(), discovery);
        assertThatThrownBy(() -> validator.validateVerification(input, evidence(),
                discovery, verification)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void caseThreeDenervationAssertionCannotBeReusedForNerveIdentification() {
        String response = "It causes denervation.";
        var input = new ResponseEvaluationInput("What does radial nerve injury cause?",
                List.of("Radial nerve injury", "Denervation"),
                "Radial nerve injury causes denervation", response);
        var assertion = new ResponseAttributionDiscovery.Assertion("a", 0,
                response.length(), response, "denervation",
                "AFFIRMED", List.of(), List.of(1));
        var borrowed = new ResponseAttributionDiscovery.Candidate("c", List.of("a"), 0,
                "radial nerve injury", "COMPLETE", List.of(mechanismEvidence()));
        var discovery = new ResponseAttributionDiscovery(List.of(assertion),
                List.of(borrowed), List.of(0, 1), true, List.of());

        assertThatThrownBy(() -> validator.validateDiscovery(input, evidence(), discovery))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void staleOrForgedSourceVersionIsRejected() {
        var valid = discovery();
        var candidate = valid.candidates().getFirst();
        var span = candidate.sourceEvidence().getFirst();
        for (String[] invalidIdentity : List.of(
                new String[] {REFERENCE.toString(), UUID.randomUUID().toString()},
                new String[] {UUID.randomUUID().toString(), VERSION.toString()})) {
            var forged = new ResponseAttributionDiscovery(valid.assertions(),
                    List.of(new ResponseAttributionDiscovery.Candidate(candidate.id(),
                            candidate.assertionIds(), candidate.expectedConceptIndex(),
                            candidate.constituentMeaning(), candidate.relationToExpectedConcept(),
                            List.of(new ResponseAttributionDiscovery.SourceSpan(
                                    invalidIdentity[0], invalidIdentity[1],
                                    span.sourceStart(), span.sourceEnd(), span.sourceText())))),
                    valid.coveredExpectedConceptIndexes(), true, List.of());
            assertThatThrownBy(() -> validator.validateDiscovery(input(RESPONSE), evidence(), forged))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void verifierAmbiguityCannotCreateGradedEvidence() {
        var discovery = discovery();
        var verification = new ResponseAttributionVerification(
                List.of(new ResponseAttributionVerification.AssertionDisposition(
                                "a", ResponseAttributionVerification.Disposition.AMBIGUOUS,
                                "meaning unresolved", ResponseAttributionVerification.Fidelity.FAITHFUL),
                        new ResponseAttributionVerification.AssertionDisposition(
                                "b", ResponseAttributionVerification.Disposition.UNSUPPORTED,
                                "wrong attribution", ResponseAttributionVerification.Fidelity.FAITHFUL)),
                List.of(new ResponseAttributionVerification.CandidateDisposition(
                        "c", ResponseAttributionVerification.Disposition.AMBIGUOUS,
                        "source relation unclear")),
                List.of(new ResponseAttributionVerification.ConceptCoverage(0,
                        ResponseAttributionVerification.Coverage.AMBIGUOUS,
                        "cannot confirm concept")),
                List.of(new ResponseAttributionVerification.Misconception(
                        "b", "median nerve attribution")),
                true, List.of());
        validator.validateDiscovery(input(RESPONSE), evidence(), discovery);
        validator.validateVerification(input(RESPONSE), evidence(), discovery, verification);
        assertThat(aggregator.aggregate(input(RESPONSE), discovery, verification).outcome())
                .isEqualTo(ResponseEvaluationPort.Outcome.UNCERTAIN);
    }

    private static ResponseEvaluationInput input(String response) {
        return new ResponseEvaluationInput("Explain wrist drop",
                List.of("Radial nerve injury causes wrist extensor paralysis"),
                "Radial nerve injury causes wrist extensor paralysis", response);
    }

    private static AuthorizedResponseEvidence evidence() {
        return new AuthorizedResponseEvidence(StudyMissionGroundingMode.STRICT_SOURCE,
                List.of(new AuthorizedResponseEvidence.Source(
                        REFERENCE, UUID.fromString("8f36470a-d70c-418f-b586-54be879c6a72"),
                        UUID.fromString("42a0800c-84aa-467d-8632-35fb195f1760"),
                        VERSION, null, 1, SOURCE, null, null,
                        "TEXT", "NATIVE", "STRONG")));
    }

    private static ResponseAttributionDiscovery discovery() {
        int firstEnd = RESPONSE.indexOf(',');
        int wrongStart = RESPONSE.indexOf("the median nerve");
        int sourceStart = SOURCE.indexOf("paralyze");
        return new ResponseAttributionDiscovery(
                List.of(
                        new ResponseAttributionDiscovery.Assertion(
                                "a", 0, firstEnd, RESPONSE.substring(0, firstEnd),
                                "wrist extensor paralysis", "AFFIRMED", List.of(), List.of(0)),
                        new ResponseAttributionDiscovery.Assertion(
                                "b", wrongStart, RESPONSE.length() - 1,
                                RESPONSE.substring(wrongStart, RESPONSE.length() - 1),
                                "median nerve attribution", "AFFIRMED", List.of(), List.of(0))),
                List.of(new ResponseAttributionDiscovery.Candidate(
                        "c", List.of("a"), 0, "wrist extensor paralysis",
                        "CONSTITUENT",
                        List.of(new ResponseAttributionDiscovery.SourceSpan(
                                REFERENCE.toString(), VERSION.toString(),
                                sourceStart, sourceStart + "paralyze".length(), "paralyze")))),
                List.of(0), true, List.of());
    }

    private static ResponseAttributionVerification verification() {
        return new ResponseAttributionVerification(
                List.of(new ResponseAttributionVerification.AssertionDisposition(
                                "a", ResponseAttributionVerification.Disposition.SUPPORTED,
                                "Paralysis stated", ResponseAttributionVerification.Fidelity.FAITHFUL),
                        new ResponseAttributionVerification.AssertionDisposition(
                                "b", ResponseAttributionVerification.Disposition.UNSUPPORTED,
                                "Wrong nerve attribution", ResponseAttributionVerification.Fidelity.FAITHFUL)),
                List.of(new ResponseAttributionVerification.CandidateDisposition(
                        "c", ResponseAttributionVerification.Disposition.SUPPORTED,
                        "Constituent supported")),
                List.of(new ResponseAttributionVerification.ConceptCoverage(
                        0, ResponseAttributionVerification.Coverage.MISSING,
                        "Correct nerve relation missing")),
                List.of(new ResponseAttributionVerification.Misconception(
                        "b", "median nerve attribution")),
                true, List.of(), mechanismEvidence());
    }

    private static ResponseAttributionDiscovery.SourceSpan mechanismEvidence() {
        return new ResponseAttributionDiscovery.SourceSpan(REFERENCE.toString(),
                VERSION.toString(), 0, SOURCE.length(), SOURCE);
    }
}
