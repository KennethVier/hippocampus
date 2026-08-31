package com.hippocampus.learning.application;

import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.hippocampus.identity.port.CurrentUser;
import com.hippocampus.learning.domain.Subtopic;
import com.hippocampus.learning.port.SubtopicRepository;

@Service
public class CreateSubtopic {
    private final CurrentUser currentUser; private final SubtopicRepository subtopics;
    public CreateSubtopic(CurrentUser currentUser, @Lazy SubtopicRepository subtopics) { this.currentUser=currentUser; this.subtopics=subtopics; }
    @Transactional public SubtopicResult execute(UUID topicId, Command command) {
        UUID ownerId=currentUser.authenticatedUser().userId();
        return subtopics.createUnderActiveOwnedTopic(Subtopic.create(topicId,command.name(),command.description(),command.sortOrder()),ownerId)
                .map(SubtopicResult::from).orElseThrow(TopicFailures::notFound);
    }
    public record Command(String name, String description, Integer sortOrder) {}
}
