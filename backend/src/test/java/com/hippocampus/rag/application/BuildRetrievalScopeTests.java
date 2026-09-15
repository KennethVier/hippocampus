package com.hippocampus.rag.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.hippocampus.identity.domain.AuthenticatedUser;
import com.hippocampus.rag.domain.GroundingMode;
import com.hippocampus.rag.domain.RetrievalScope;
import com.hippocampus.rag.port.RetrievalScopeSource;
import com.hippocampus.rag.port.RetrievalScopeSourceRepository;

class BuildRetrievalScopeTests {
    @Test
    void derivesUserFromCurrentUserAndReturnsEmptyScopeWithoutBroadeningGeneralKnowledge() {
        UUID currentUserId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();
        CapturingRepository repository = new CapturingRepository(List.of());
        BuildRetrievalScope useCase = new BuildRetrievalScope(
                () -> new AuthenticatedUser(currentUserId), repository);

        RetrievalScope scope = useCase.execute(
                new BuildRetrievalScope.Query(topicId, GroundingMode.GENERAL_KNOWLEDGE));

        assertThat(repository.userId).isEqualTo(currentUserId);
        assertThat(repository.topicId).isEqualTo(topicId);
        assertThat(scope.userId()).isEqualTo(currentUserId);
        assertThat(scope.groundingMode()).isEqualTo(GroundingMode.GENERAL_KNOWLEDGE);
        assertThat(scope.isEmpty()).isTrue();
    }

    @Test
    void coalescesNodesAndWholeVersionDominatesInEveryRowOrder() {
        UUID wholeVersion = UUID.randomUUID();
        UUID nodeVersion = UUID.randomUUID();
        UUID firstNode = UUID.randomUUID();
        UUID secondNode = UUID.randomUUID();
        List<RetrievalScopeSource> rows = List.of(
                new RetrievalScopeSource(wholeVersion, firstNode),
                new RetrievalScopeSource(nodeVersion, firstNode),
                new RetrievalScopeSource(wholeVersion, null),
                new RetrievalScopeSource(nodeVersion, secondNode),
                new RetrievalScopeSource(wholeVersion, secondNode));
        BuildRetrievalScope useCase = new BuildRetrievalScope(
                () -> new AuthenticatedUser(UUID.randomUUID()), new CapturingRepository(rows));

        RetrievalScope scope = useCase.execute(new BuildRetrievalScope.Query(
                UUID.randomUUID(), GroundingMode.STRICT_SOURCE));

        assertThat(scope.wholeMaterialVersionIds()).containsExactly(wholeVersion);
        assertThat(scope.targets()).anySatisfy(target -> {
            assertThat(target.materialVersionId()).isEqualTo(nodeVersion);
            assertThat(target.documentNodeIds()).containsExactlyInAnyOrder(firstNode, secondNode);
        });
        assertThat(scope.groundingMode()).isEqualTo(GroundingMode.STRICT_SOURCE);
    }

    private static final class CapturingRepository implements RetrievalScopeSourceRepository {
        private final List<RetrievalScopeSource> rows;
        private UUID userId;
        private UUID topicId;

        private CapturingRepository(List<RetrievalScopeSource> rows) {
            this.rows = List.copyOf(rows);
        }

        @Override
        public List<RetrievalScopeSource> findActiveAuthorizedTargets(UUID userId, UUID topicId) {
            this.userId = userId;
            this.topicId = topicId;
            return rows;
        }
    }
}
