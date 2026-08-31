package com.hippocampus.learning.application;

import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.hippocampus.identity.port.CurrentUser;
import com.hippocampus.learning.port.SubtopicPageRequest;
import com.hippocampus.learning.port.SubtopicRepository;

@Service
public class ListSubtopics {
    private final CurrentUser currentUser; private final SubtopicRepository subtopics;
    public ListSubtopics(CurrentUser currentUser, @Lazy SubtopicRepository subtopics) { this.currentUser=currentUser; this.subtopics=subtopics; }
    @Transactional(readOnly=true) public SubtopicPageResult execute(UUID topicId,int page,int size) {
        UUID ownerId=currentUser.authenticatedUser().userId();
        return subtopics.findActiveByOwnedActiveTopic(topicId,ownerId,new SubtopicPageRequest(page,size))
                .map(SubtopicPageResult::from).orElseThrow(TopicFailures::notFound);
    }
}
