package com.hippocampus.ai.evaluation;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** A closed token grammar for Golden wrist-drop propositions, never a runtime evaluator. */
final class WristDropRelations {
    enum Entity { NERVE, INJURY, EXTENSORS, FLEXORS, EXTENSION, LOSS, DROP, PATIENT }
    enum Predicate { SUPPLIES, LOSES, CAUSES, ACTIVATES, OVERPOWERS, NORMAL }
    record Relation(Entity subject, Predicate predicate, Entity object) {}
    record Claim(Set<Relation> affirmative, Set<Relation> rejected, boolean complete) {}
    record Propositions(Set<Relation> affirmative, Set<Relation> rejected) {}
    private record Antecedent(boolean supply, boolean injury) {}

    private WristDropRelations() {}

    static boolean functionalLoss(String text, boolean allowGap) {
        Propositions evidence = propositions(text);
        boolean relevant = evidence.affirmative().stream()
                .anyMatch(relation -> relation.equals(supply()) || relation.subject() == Entity.INJURY);
        return evidence.affirmative().contains(loss())
                || evidence.affirmative().contains(new Relation(Entity.LOSS, Predicate.CAUSES, Entity.DROP))
                || (allowGap && relevant && evidence.affirmative().contains(
                        new Relation(Entity.LOSS, Predicate.LOSES, Entity.EXTENSION)));
    }

    /** Extract evidence first; the loss rubric never requests a consequence or a complete explanation. */
    static Propositions propositions(String text) {
        Set<Relation> affirmative = new HashSet<>();
        Set<Relation> rejected = new HashSet<>();
        Antecedent previous = new Antecedent(false, false);
        for (String sentence : text.split("[.!?]")) {
            Claim claim = feedbackClaim(sentence, previous);
            if (claim.complete()) {
                affirmative.addAll(claim.affirmative());
                rejected.addAll(claim.rejected());
            }
            // Reset on every sentence; bare entity mentions and unknown subjects are not antecedents.
            boolean injury = claim.complete() && java.util.stream.Stream.concat(
                    claim.affirmative().stream(), claim.rejected().stream())
                    .anyMatch(relation -> relation.subject() == Entity.INJURY);
            previous = new Antecedent(claim.complete() && claim.affirmative().contains(supply()), injury);
        }
        return new Propositions(Set.copyOf(affirmative), Set.copyOf(rejected));
    }

    private static Claim feedbackClaim(String sentence, Antecedent previous) {
        Claim full = parse(sentence, previous, false);
        if (full.complete()) { return full; }
        List<String> words = tokens(sentence);
        int boundary = words.indexOf("which");
        if (boundary < 0) { return full; }
        Claim primary = parse(String.join(" ", words.subList(0, boundary)), previous, false);
        if (!primary.complete() || !primary.affirmative().contains(loss())) { return full; }
        // An independent descriptive relative clause cannot veto an established primary loss.
        // Attached causal operators still need the closed grammar: unknown causes, mechanisms,
        // negated losses and reversed consequences must not disappear through projection.
        Set<String> operators = Set.of("because", "by", "due", "causes", "cause", "causing", "produces",
                "produce", "producing", "results", "resulting", "leads", "activates", "activate", "activating",
                "eliminates", "eliminate", "eliminating", "loss", "preserves", "preserved", "normal",
                "not", "no", "never", "without");
        if (words.subList(boundary + 1, words.size()).stream().anyMatch(operators::contains)) { return full; }
        return primary;
    }

    static boolean equivalent(String text, String alternative) {
        Claim expected = parse(alternative, false, false);
        if (!expected.complete() || expected.affirmative().isEmpty()) { return false; }
        return Arrays.stream(text.split("[.!?;]"))
                .map(sentence -> parse(sentence, false, false))
                .anyMatch(claim -> claim.complete() && (claim.affirmative().containsAll(expected.affirmative())
                        || (expected.affirmative().equals(Set.of(new Relation(Entity.LOSS, Predicate.LOSES, Entity.EXTENSION)))
                                && (claim.affirmative().contains(loss()) || claim.affirmative().contains(
                                        new Relation(Entity.LOSS, Predicate.CAUSES, Entity.DROP))))));
    }

    static boolean grounded(String learner, String misconception) {
        Claim source = parse(learner, false, true);
        Claim actual = parse(misconception, false, true);
        if (!source.complete() || !actual.complete() || actual.affirmative().isEmpty()) {
            return false;
        }
        Set<Relation> demonstrated = source.affirmative();
        return demonstrated.containsAll(actual.affirmative())
                && actual.rejected().stream().allMatch(relation -> relation.predicate() == Predicate.LOSES)
                && actual.affirmative().stream().anyMatch(relation -> relation.predicate() == Predicate.ACTIVATES
                        || relation.predicate() == Predicate.OVERPOWERS || relation.predicate() == Predicate.NORMAL);
    }

    static Claim parse(String text, boolean suppliedAntecedent, boolean learnerContext) {
        return parse(text, new Antecedent(suppliedAntecedent, false), learnerContext);
    }

    private static Claim parse(String text, Antecedent previous, boolean learnerContext) {
        Parser parser = new Parser(tokens(text), previous);
        parser.take("you correctly identified that", "you identified that");
        parser.take("this is");
        parser.take("instead", "therefore", "rather");
        parser.take("explain", "missing concept is", "missing link is");
        Entity subject;
        if (parser.take("after radial nerve injury")) {
            parser.injuryContext = true;
            subject = parser.entity();
        } else if (learnerContext && !parser.words.isEmpty() && parser.words.getFirst().equals("activates")) {
            subject = Entity.INJURY;
        } else {
            subject = parser.entity();
        }
        boolean parsed = subject != null && parser.clause(subject, false);
        return new Claim(Set.copyOf(parser.affirmative), Set.copyOf(parser.rejected),
                parsed && parser.at == parser.words.size());
    }

    private static Relation supply() { return new Relation(Entity.NERVE, Predicate.SUPPLIES, Entity.EXTENSORS); }
    private static Relation loss() { return new Relation(Entity.INJURY, Predicate.LOSES, Entity.EXTENSION); }

    private static List<String> tokens(String text) {
        String normalized = text.toLowerCase(Locale.ROOT)
                .replaceAll("\\bmuscles that (?:extend (?:or )?lift|extend|lift) (?:the )?wrist\\b", "wrist extensors")
                .replaceAll("\\bhand dropping\\b", "wrist drop")
                .replaceAll("[^a-z0-9]+", " ").trim();
        return Arrays.stream(normalized.split(" "))
                .filter(word -> !Set.of("a", "an", "the").contains(word)).toList();
    }

    private static final class Parser {
        private final List<String> words;
        private final Set<Relation> affirmative = new HashSet<>();
        private final Set<Relation> rejected = new HashSet<>();
        private boolean injuryContext;
        private boolean supplyContext;
        private boolean normalObject;
        private int at;

        Parser(List<String> words, Antecedent previous) {
            this.words = words;
            this.supplyContext = previous.supply();
            this.injuryContext = previous.injury();
        }

        boolean take(String... alternatives) {
            for (String alternative : alternatives) {
                List<String> phrase = List.of(alternative.split(" "));
                if (at + phrase.size() <= words.size() && words.subList(at, at + phrase.size()).equals(phrase)) {
                    at += phrase.size();
                    return true;
                }
            }
            return false;
        }

        Entity entity() {
            if (take("radial nerve injury", "injury to radial nerve", "loss of radial nerve function")) {
                injuryContext = true;
                return Entity.INJURY;
            }
            if (supplyContext && take("injury to this nerve")) { injuryContext = true; return Entity.INJURY; }
            if (supplyContext && take("injury")) { injuryContext = true; return Entity.INJURY; }
            if (injuryContext && take("injury", "it")) { return Entity.INJURY; }
            if (take("radial nerve")) { return Entity.NERVE; }
            if (take("loss of wrist extension", "elimination of wrist extension", "loss of wrist extensor function",
                    "paralysis of wrist extensor function", "loss of extensor function in wrist", "extensor function loss")) {
                return Entity.LOSS;
            }
            if (supplyContext && take("loss of extensor function")) { return Entity.LOSS; }
            if (take("wrist drop", "hand to drop into flexion", "hand to drop")) { return Entity.DROP; }
            if (take("wrist extension", "wrist extensor function", "extensor function in wrist")) { return Entity.EXTENSION; }
            int start = at;
            take("active", "activated");
            if (take("wrist flexors", "flexors")) { return Entity.FLEXORS; }
            at = start;
            take("otherwise");
            boolean normal = take("normal");
            if (take("wrist extensors", "extensors")) {
                normalObject = normal;
                return Entity.EXTENSORS;
            }
            at = start;
            if (take("patient")) { return Entity.PATIENT; }
            return null;
        }

        void add(Entity subject, Predicate predicate, Entity object, boolean negative) {
            (negative ? rejected : affirmative).add(new Relation(subject, predicate, object));
        }

        boolean clause(Entity subject, boolean negative) {
            take("directly", "normally");
            if (take("does not", "do not", "not", "never")) { negative = true; }
            take("directly", "normally");
            if (subject == Entity.LOSS && at == words.size()) {
                add(Entity.LOSS, Predicate.LOSES, Entity.EXTENSION, negative);
                return true;
            }
            if (subject == Entity.DROP && take("is caused by", "is produced by")) {
                Entity cause = entity();
                if (cause == Entity.LOSS) { add(cause, Predicate.CAUSES, subject, negative); }
                else if (cause == Entity.FLEXORS) {
                    add(Entity.INJURY, Predicate.CAUSES, Entity.DROP, negative);
                    if (!clause(cause, negative)) { return false; }
                } else { return false; }
            } else if (subject == Entity.INJURY && take("leaves", "leave")) {
                if (entity() != Entity.EXTENSORS || !take("otherwise normal", "normal")) { return false; }
                add(Entity.EXTENSORS, Predicate.NORMAL, Entity.EXTENSORS, negative);
            } else if (subject == Entity.EXTENSORS && take("remain", "remains", "are", "is")) {
                if (take("not")) { negative = true; }
                if (!take("otherwise normal", "normal")) { return false; }
                add(subject, Predicate.NORMAL, subject, negative);
                if (!take("during", "despite", "after") || entity() != Entity.INJURY) { return false; }
                subject = Entity.INJURY;
            } else if (subject == Entity.NERVE && take("supplies", "supply", "innervates", "innervate")) {
                // Consume only bounded supply modifiers; preserve subjects, direction and negation.
                take("directly", "normally");
                if (entity() != Entity.EXTENSORS) { return false; }
                add(subject, Predicate.SUPPLIES, Entity.EXTENSORS, negative);
                supplyContext = !negative;
            } else if (subject == Entity.INJURY && take("eliminates", "eliminate", "eliminating", "abolishes", "removes")) {
                if (entity() != Entity.EXTENSION) { return false; }
                add(subject, Predicate.LOSES, Entity.EXTENSION, negative);
            } else if (subject == Entity.INJURY && take("denervates", "denervating", "paralyzes", "paralyzing", "disabling")) {
                take("or eliminates");
                if (entity() != Entity.EXTENSORS) { return false; }
                add(subject, Predicate.LOSES, Entity.EXTENSION, negative);
            } else if ((subject == Entity.INJURY || subject == Entity.LOSS) && take(
                    "is what produces", "causes", "causing", "leads to", "results in", "resulting in", "produces", "producing")) {
                Entity object = entity();
                if (subject == Entity.INJURY && object == Entity.LOSS) {
                    add(subject, Predicate.LOSES, Entity.EXTENSION, negative);
                } else if (object == Entity.DROP) {
                    add(subject, Predicate.CAUSES, object, negative);
                } else { return false; }
            } else if (subject == Entity.INJURY && take("activates", "activate", "activating")) {
                if (entity() != Entity.FLEXORS) { return false; }
                add(subject, Predicate.ACTIVATES, Entity.FLEXORS, negative);
                subject = Entity.FLEXORS;
            } else if (subject == Entity.FLEXORS && take("overpower", "overpowers", "overpowering")) {
                if (entity() != Entity.EXTENSORS) { return false; }
                add(subject, Predicate.OVERPOWERS, Entity.EXTENSORS, negative);
                if (normalObject) { add(Entity.EXTENSORS, Predicate.NORMAL, Entity.EXTENSORS, negative); }
            } else if (subject == Entity.PATIENT && injuryContext && take("cannot extend wrist", "loses wrist extension")) {
                add(Entity.INJURY, Predicate.LOSES, Entity.EXTENSION, negative);
                subject = Entity.INJURY;
            } else { return false; }
            return tail(subject, negative);
        }

        boolean tail(Entity subject, boolean negative) {
            take("entirely");
            if (at == words.size()) { return true; }
            if (take("while leaving")) {
                if (entity() != Entity.EXTENSORS || !take("normal")) { return false; }
                add(Entity.EXTENSORS, Predicate.NORMAL, Entity.EXTENSORS, negative);
                return tail(Entity.INJURY, negative);
            }
            if (at + 1 < words.size() && words.get(at).equals("rather") && !words.get(at + 1).equals("than")
                    && take("rather")) {
                Entity next = entity();
                return next != null && clause(next, false);
            }
            if (take("so", "therefore")) {
                if (supplyContext && take("injury")) { injuryContext = true; return clause(Entity.INJURY, false); }
                Entity next = entity();
                return next != null && clause(next, false);
            }
            if (take("because")) {
                Entity next = entity();
                return next != null && clause(next, false);
            }
            if (take("rather than", "not because", "not by")) {
                take("by");
                int start = at;
                Entity next = entity();
                if (next == Entity.LOSS) { return tail(subject, negative); }
                if (next != null) { return clause(next, true); }
                at = start;
                if (take("eliminating extension", "loss of wrist extension")) { return tail(subject, negative); }
                return clause(Entity.INJURY, true);
            }
            if (take("due to")) {
                return entity() == Entity.LOSS && tail(subject, negative);
            }
            if (take("but")) {
                int start = at;
                Entity next = entity();
                if (next != null) { return clause(next, false); }
                at = start;
                return injuryContext && clause(Entity.INJURY, false);
            }
            if (take("and")) {
                if (take("wrist drop")) { return tail(subject, negative); }
                int start = at;
                Entity next = entity();
                if (next != null) { return clause(next, false); }
                at = start;
                return clause(subject == Entity.FLEXORS ? Entity.INJURY : subject, false);
            }
            if (take("which directly", "which", "to", "by")) {
                Entity next = subject;
                if (subject == Entity.INJURY && affirmative.contains(loss()) && !peekActivation()) { next = Entity.LOSS; }
                if (peekActivation()) { next = Entity.INJURY; }
                if (take("paralyzing", "denervating", "disabling")) {
                    return entity() == Entity.EXTENSORS && tail(subject, negative);
                }
                return clause(next, negative);
            }
            if (take("denervating", "paralyzing")) {
                return entity() == Entity.EXTENSORS && tail(subject, negative);
            }
            // Participles retain their actual subject or the immediately established loss.
            Entity next = subject == Entity.INJURY && affirmative.contains(loss()) ? Entity.LOSS : subject;
            return clause(next, negative);
        }

        boolean peekActivation() { return at < words.size() && words.get(at).equals("activating"); }
    }
}
