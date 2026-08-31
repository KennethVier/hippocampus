package com.hippocampus.learning.api;

import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.hippocampus.learning.application.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
public class TopicController {
    private final CreateTopic createTopic;
    private final ListTopics listTopics;
    private final UpdateTopic updateTopic;
    private final ArchiveTopic archiveTopic;

    public TopicController(CreateTopic createTopic,ListTopics listTopics,UpdateTopic updateTopic,ArchiveTopic archiveTopic) {
        this.createTopic=createTopic; this.listTopics=listTopics; this.updateTopic=updateTopic; this.archiveTopic=archiveTopic;
    }

    @PostMapping("/api/subjects/{subjectId}/topics")
    ResponseEntity<TopicResponse> create(@PathVariable UUID subjectId,@Valid @RequestBody CreateTopicRequest request) {
        TopicResponse response=TopicResponse.from(createTopic.execute(subjectId,new CreateTopic.Command(request.name(),request.description())));
        return ResponseEntity.created(URI.create("/api/topics/"+response.id())).body(response);
    }

    @GetMapping("/api/subjects/{subjectId}/topics")
    TopicPageResponse list(@PathVariable UUID subjectId,@RequestParam(defaultValue="0") @Min(0) int page,
            @RequestParam(defaultValue="50") @Min(1) @Max(100) int size) {
        return TopicPageResponse.from(listTopics.execute(subjectId,page,size));
    }

    @PutMapping("/api/topics/{topicId}")
    TopicResponse update(@PathVariable UUID topicId,@Valid @RequestBody UpdateTopicRequest request) {
        return TopicResponse.from(updateTopic.execute(topicId,new UpdateTopic.Command(request.name(),request.description())));
    }

    @PostMapping("/api/topics/{topicId}/archive")
    TopicResponse archive(@PathVariable UUID topicId) { return TopicResponse.from(archiveTopic.execute(topicId)); }
}
