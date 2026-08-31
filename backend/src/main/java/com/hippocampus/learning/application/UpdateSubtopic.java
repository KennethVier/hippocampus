package com.hippocampus.learning.application;

import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.hippocampus.identity.port.CurrentUser;
import com.hippocampus.learning.domain.Subtopic;
import com.hippocampus.learning.port.SubtopicRepository;

@Service
public class UpdateSubtopic {
    private final CurrentUser currentUser; private final SubtopicRepository subtopics;
    public UpdateSubtopic(CurrentUser currentUser,@Lazy SubtopicRepository subtopics) { this.currentUser=currentUser; this.subtopics=subtopics; }
    @Transactional public SubtopicResult execute(UUID subtopicId,Command command) {
        UUID ownerId=currentUser.authenticatedUser().userId();
        Subtopic subtopic=subtopics.findOwnedByIdWithActiveAncestors(subtopicId,ownerId).orElseThrow(SubtopicFailures::notFound);
        return subtopics.saveOwned(subtopic.changeDetails(command.name(),command.description(),command.sortOrder()),ownerId)
                .map(SubtopicResult::from).orElseThrow(SubtopicFailures::notFound);
    }
    public record Command(String name,String description,Integer sortOrder) {}
}
