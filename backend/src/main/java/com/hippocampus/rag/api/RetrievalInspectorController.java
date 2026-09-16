package com.hippocampus.rag.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hippocampus.rag.application.InspectRetrieval;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/dev/rag")
@Profile("local")
@ConditionalOnProperty(prefix = "hippocampus.rag.inspector", name = "enabled", havingValue = "true")
public class RetrievalInspectorController {
    private final InspectRetrieval inspector;

    RetrievalInspectorController(@Lazy InspectRetrieval inspector) {
        this.inspector = inspector;
    }

    @PostMapping("/inspect")
    RetrievalInspectionResponse inspect(@Valid @RequestBody RetrievalInspectionRequest request) {
        return RetrievalInspectionResponse.from(inspector.execute(new InspectRetrieval.Query(
                request.topicId(), request.query(), request.groundingMode())));
    }
}
