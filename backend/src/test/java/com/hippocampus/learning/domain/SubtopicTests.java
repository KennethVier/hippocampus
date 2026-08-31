package com.hippocampus.learning.domain;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SubtopicTests {
    @Test void preservesSubmittedDetailsParentAndLifecycle() {
        UUID topicId=UUID.randomUUID();
        Subtopic subtopic=Subtopic.create(topicId,"  Brain Stem  ","Details",4);
        assertThat(subtopic.topicId()).isEqualTo(topicId);
        assertThat(subtopic.name()).isEqualTo("  Brain Stem  ");
        assertThat(subtopic.sortOrder()).isEqualTo(4);
        Subtopic archived=subtopic.changeDetails("Renamed",null,8).archive();
        assertThat(archived.topicId()).isEqualTo(topicId);
        assertThat(archived.status()).isEqualTo(SubtopicStatus.ARCHIVED);
        assertThat(archived.archive()).isSameAs(archived);
        assertThat(archived.changeDetails("Again",null,null).status()).isEqualTo(SubtopicStatus.ARCHIVED);
    }
}
