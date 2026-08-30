package com.hippocampus.learning.api;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hippocampus.learning.application.ArchiveSubject;
import com.hippocampus.learning.application.CreateSubject;
import com.hippocampus.learning.application.GetSubject;
import com.hippocampus.learning.application.ListSubjects;
import com.hippocampus.learning.application.SubjectResult;
import com.hippocampus.learning.application.UpdateSubject;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/subjects")
public class SubjectController {
    private final CreateSubject createSubject;
    private final ListSubjects listSubjects;
    private final GetSubject getSubject;
    private final UpdateSubject updateSubject;
    private final ArchiveSubject archiveSubject;

    public SubjectController(
            CreateSubject createSubject,
            ListSubjects listSubjects,
            GetSubject getSubject,
            UpdateSubject updateSubject,
            ArchiveSubject archiveSubject) {
        this.createSubject = createSubject;
        this.listSubjects = listSubjects;
        this.getSubject = getSubject;
        this.updateSubject = updateSubject;
        this.archiveSubject = archiveSubject;
    }

    @PostMapping
    ResponseEntity<SubjectResponse> create(@Valid @RequestBody CreateSubjectRequest request) {
        SubjectResult result = createSubject.execute(
                new CreateSubject.Command(request.name(), request.description(), request.sortOrder()));
        return ResponseEntity.created(URI.create("/api/subjects/" + result.id()))
                .body(SubjectResponse.from(result));
    }

    @GetMapping
    SubjectPageResponse list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        return SubjectPageResponse.from(listSubjects.execute(page, size));
    }

    @GetMapping("/{subjectId}")
    SubjectResponse get(@PathVariable UUID subjectId) {
        return SubjectResponse.from(getSubject.execute(subjectId));
    }

    @PutMapping("/{subjectId}")
    SubjectResponse update(@PathVariable UUID subjectId, @Valid @RequestBody UpdateSubjectRequest request) {
        return SubjectResponse.from(updateSubject.execute(subjectId,
                new UpdateSubject.Command(request.name(), request.description(), request.sortOrder())));
    }

    @PostMapping("/{subjectId}/archive")
    SubjectResponse archive(@PathVariable UUID subjectId) {
        return SubjectResponse.from(archiveSubject.execute(subjectId));
    }
}
