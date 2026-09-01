package com.hippocampus.materials.port;

import java.util.Objects;

import com.hippocampus.materials.domain.MaterialTopicLink;

public record CreateMaterialTopicLinkResult(Outcome outcome, MaterialTopicLink link) {

    public CreateMaterialTopicLinkResult {
        Objects.requireNonNull(outcome, "outcome must not be null");
        if ((outcome == Outcome.CREATED) != (link != null)) {
            throw new IllegalArgumentException("only CREATED results may contain a link");
        }
    }

    public static CreateMaterialTopicLinkResult created(MaterialTopicLink link) {
        return new CreateMaterialTopicLinkResult(Outcome.CREATED, Objects.requireNonNull(link));
    }

    public static CreateMaterialTopicLinkResult ineligible() {
        return new CreateMaterialTopicLinkResult(Outcome.INELIGIBLE, null);
    }

    public static CreateMaterialTopicLinkResult duplicateActive() {
        return new CreateMaterialTopicLinkResult(Outcome.DUPLICATE_ACTIVE, null);
    }

    public enum Outcome {
        CREATED,
        INELIGIBLE,
        DUPLICATE_ACTIVE
    }
}
