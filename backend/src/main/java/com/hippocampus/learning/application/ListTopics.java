package com.hippocampus.learning.application;

import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.hippocampus.identity.port.CurrentUser;
import com.hippocampus.learning.port.TopicPageRequest;
import com.hippocampus.learning.port.TopicRepository;

@Service
public class ListTopics {
    private final CurrentUser currentUser; private final TopicRepository topics;
    public ListTopics(CurrentUser currentUser, @Lazy TopicRepository topics) { this.currentUser=currentUser; this.topics=topics; }
    @Transactional(readOnly=true) public TopicPageResult execute(UUID subjectId, int page, int size) {
        UUID ownerId=currentUser.authenticatedUser().userId();
        return topics.findActiveByOwnedActiveSubject(subjectId, ownerId, new TopicPageRequest(page,size))
                .map(TopicPageResult::from).orElseThrow(SubjectFailures::notFound);
    }
}
