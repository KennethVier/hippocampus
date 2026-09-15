package com.hippocampus.materials.application;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.hippocampus.identity.port.CurrentUser;
import com.hippocampus.materials.domain.SourceReference;
import com.hippocampus.materials.domain.SourceReferenceTarget;
import com.hippocampus.materials.port.SourceReferenceRepository;
import com.hippocampus.materials.port.SourceReferenceSeed;

public class CreateSourceReferences {
    private static final String LABEL_SEPARATOR = " · ";

    private final CurrentUser currentUser;
    private final SourceReferenceRepository references;

    public CreateSourceReferences(CurrentUser currentUser, SourceReferenceRepository references) {
        this.currentUser = Objects.requireNonNull(currentUser);
        this.references = Objects.requireNonNull(references);
    }

    @Transactional
    public List<SourceReference> execute(Command command) {
        Objects.requireNonNull(command, "command must not be null");
        rejectDuplicateTargets(command.targets());
        UUID userId = currentUser.authenticatedUser().userId();
        List<SourceReference> result = new ArrayList<>(command.targets().size());
        for (SourceReferenceTarget target : command.targets()) {
            SourceReferenceSeed seed = references.findAuthorizedTarget(userId, target)
                    .orElseThrow(SourceReferenceFailures::notFound);
            result.add(references.upsert(seed, displayLabel(seed)));
        }
        return List.copyOf(result);
    }

    private static void rejectDuplicateTargets(List<SourceReferenceTarget> targets) {
        Set<SourceReferenceTarget> unique = new HashSet<>();
        if (targets.stream().anyMatch(target -> !unique.add(target))) {
            throw new IllegalArgumentException("targets must not contain duplicate primary targets");
        }
    }

    static String displayLabel(SourceReferenceSeed seed) {
        List<String> parts = new ArrayList<>();
        parts.add(seed.materialTitle().strip());
        if (seed.pageNumber() != null) {
            parts.add("Page " + seed.pageNumber());
        }
        if (seed.documentNodeTitle() != null && !seed.documentNodeTitle().isBlank()) {
            parts.add(seed.documentNodeTitle().strip());
        }
        return String.join(LABEL_SEPARATOR, parts);
    }

    public record Command(List<SourceReferenceTarget> targets) {
        public Command {
            targets = List.copyOf(Objects.requireNonNull(targets, "targets must not be null"));
            if (targets.stream().anyMatch(Objects::isNull)) {
                throw new NullPointerException("targets must not contain null");
            }
        }
    }
}
