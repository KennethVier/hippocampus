package com.hippocampus.materials.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.dao.DataIntegrityViolationException;

import com.hippocampus.identity.infrastructure.persistence.UserRepository;
import com.hippocampus.materials.port.MaterialUploadPersistence;
import com.hippocampus.materials.port.MaterialUploadPersistence.InitialMaterial;
import com.hippocampus.testing.PostgresIntegrationTestSupport;
import com.hippocampus.testing.security.OwnershipTestUsers;

@SpringBootTest
class MaterialUploadAtomicFailureIntegrationTests extends PostgresIntegrationTestSupport {

    @Autowired
    private MaterialUploadPersistence persistence;

    @Autowired
    private SpringDataMaterialRepository materials;

    @Autowired
    private SpringDataMaterialVersionRepository versions;

    @MockitoBean
    private SpringDataProcessingJobRepository mockJobs;

    @Autowired
    private UserRepository userRepository;

    @Test
    void rollsBackMaterialAndVersionWhenJobPersistenceFails() {
        OwnershipTestUsers users = OwnershipTestUsers.persistWith(userRepository, "atomic-failure");
        UUID ownerId = users.userA().userId();
        InitialMaterial upload = new InitialMaterial(
                ownerId, "Atomic Failure", "PDF", "fail.pdf", "application/pdf", "key-fail", 1024L);

        doThrow(new DataIntegrityViolationException("forced job failure"))
                .when(mockJobs).saveAndFlush(any());

        assertThatThrownBy(() -> persistence.createInitialMaterial(upload))
                .isInstanceOf(RuntimeException.class);

        assertThat(materials.count()).isZero();
        assertThat(versions.count()).isZero();
    }
}
