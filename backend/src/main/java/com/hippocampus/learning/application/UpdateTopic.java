package com.hippocampus.learning.application;

import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.hippocampus.identity.port.CurrentUser;
import com.hippocampus.learning.domain.Topic;
import com.hippocampus.learning.port.TopicRepository;

@Service
public class UpdateTopic {
    private final CurrentUser currentUser; private final TopicRepository topics;
    public UpdateTopic(CurrentUser currentUser, @Lazy TopicRepository topics) { this.currentUser=currentUser; this.topics=topics; }
    @Transactional public TopicResult execute(UUID topicId, Command command) {
        UUID ownerId=currentUser.authenticatedUser().userId();
        Topic topic=topics.findOwnedByIdWithActiveSubject(topicId,ownerId).orElseThrow(TopicFailures::notFound);
        return topics.saveOwned(topic.changeDetails(command.name(),command.description()),ownerId)
                .map(TopicResult::from).orElseThrow(TopicFailures::notFound);
    }
    public record Command(String name, String description) {}
}
