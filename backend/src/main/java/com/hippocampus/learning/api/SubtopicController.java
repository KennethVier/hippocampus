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
public class SubtopicController {
    private final CreateSubtopic createSubtopic;
    private final ListSubtopics listSubtopics;
    private final UpdateSubtopic updateSubtopic;
    private final ArchiveSubtopic archiveSubtopic;

    public SubtopicController(CreateSubtopic createSubtopic,ListSubtopics listSubtopics,
            UpdateSubtopic updateSubtopic,ArchiveSubtopic archiveSubtopic) {
        this.createSubtopic=createSubtopic; this.listSubtopics=listSubtopics;
        this.updateSubtopic=updateSubtopic; this.archiveSubtopic=archiveSubtopic;
    }

    @PostMapping("/api/topics/{topicId}/subtopics")
    ResponseEntity<SubtopicResponse> create(@PathVariable UUID topicId,@Valid @RequestBody CreateSubtopicRequest request) {
        SubtopicResponse response=SubtopicResponse.from(createSubtopic.execute(topicId,
                new CreateSubtopic.Command(request.name(),request.description(),request.sortOrder())));
        return ResponseEntity.created(URI.create("/api/subtopics/"+response.id())).body(response);
    }

    @GetMapping("/api/topics/{topicId}/subtopics")
    SubtopicPageResponse list(@PathVariable UUID topicId,@RequestParam(defaultValue="0") @Min(0) int page,
            @RequestParam(defaultValue="50") @Min(1) @Max(100) int size) {
        return SubtopicPageResponse.from(listSubtopics.execute(topicId,page,size));
    }

    @PutMapping("/api/subtopics/{subtopicId}")
    SubtopicResponse update(@PathVariable UUID subtopicId,@Valid @RequestBody UpdateSubtopicRequest request) {
        return SubtopicResponse.from(updateSubtopic.execute(subtopicId,
                new UpdateSubtopic.Command(request.name(),request.description(),request.sortOrder())));
    }

    @PostMapping("/api/subtopics/{subtopicId}/archive")
    SubtopicResponse archive(@PathVariable UUID subtopicId) { return SubtopicResponse.from(archiveSubtopic.execute(subtopicId)); }
}
