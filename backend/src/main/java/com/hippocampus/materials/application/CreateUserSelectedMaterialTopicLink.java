package com.hippocampus.materials.application;

import java.util.Objects;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.hippocampus.identity.port.CurrentUser;
import com.hippocampus.materials.domain.MaterialTopicLink;
import com.hippocampus.materials.port.CreateMaterialTopicLinkResult;
import com.hippocampus.materials.port.MaterialTopicLinkRepository;

public class CreateUserSelectedMaterialTopicLink {
    private final CurrentUser currentUser;
    private final MaterialTopicLinkRepository links;

    public CreateUserSelectedMaterialTopicLink(CurrentUser currentUser, MaterialTopicLinkRepository links) {
        this.currentUser = currentUser;
        this.links = links;
    }

    @Transactional
    public MaterialTopicLink execute(Command command) {
        Objects.requireNonNull(command, "command must not be null");
        UUID ownerId = currentUser.authenticatedUser().userId();
        CreateMaterialTopicLinkResult result = links.createUserSelectedActive(
                ownerId, command.topicId(), command.materialId(), command.materialVersionId());
        return switch (result.outcome()) {
            case CREATED -> result.link();
            case INELIGIBLE -> throw MaterialFailures.notFound();
            case DUPLICATE_ACTIVE -> throw new MaterialTopicLinkAlreadyActiveException();
        };
    }

    public record Command(UUID topicId, UUID materialId, UUID materialVersionId) {
        public Command {
            Objects.requireNonNull(topicId, "topicId must not be null");
            Objects.requireNonNull(materialId, "materialId must not be null");
        }
    }
}
