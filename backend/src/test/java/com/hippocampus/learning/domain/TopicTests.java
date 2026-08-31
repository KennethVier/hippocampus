package com.hippocampus.learning.domain;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TopicTests {
    @Test void preservesSubmittedDetailsParentAndLifecycle() {
        UUID subjectId=UUID.randomUUID();
        Topic topic=Topic.create(subjectId,"  Neuro Anatomy  ","Details");
        assertThat(topic.subjectId()).isEqualTo(subjectId);
        assertThat(topic.name()).isEqualTo("  Neuro Anatomy  ");
        assertThat(topic.status()).isEqualTo(TopicStatus.ACTIVE);
        Topic archived=topic.changeDetails("Renamed","Changed").archive();
        assertThat(archived.subjectId()).isEqualTo(subjectId);
        assertThat(archived.status()).isEqualTo(TopicStatus.ARCHIVED);
        assertThat(archived.archive()).isSameAs(archived);
        assertThat(archived.changeDetails("Again",null).status()).isEqualTo(TopicStatus.ARCHIVED);
    }
}
