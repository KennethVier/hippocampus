package com.hippocampus.learning.application;

import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.hippocampus.identity.port.CurrentUser;
import com.hippocampus.learning.domain.Topic;
import com.hippocampus.learning.port.TopicRepository;

@Service
public class CreateTopic {
    private final CurrentUser currentUser; private final TopicRepository topics;
    public CreateTopic(CurrentUser currentUser, @Lazy TopicRepository topics) { this.currentUser=currentUser; this.topics=topics; }
    @Transactional public TopicResult execute(UUID subjectId, Command command) {
        UUID ownerId=currentUser.authenticatedUser().userId();
        return topics.createUnderActiveOwnedSubject(Topic.create(subjectId, command.name(), command.description()), ownerId)
                .map(TopicResult::from).orElseThrow(SubjectFailures::notFound);
    }
    public record Command(String name, String description) {}
}
