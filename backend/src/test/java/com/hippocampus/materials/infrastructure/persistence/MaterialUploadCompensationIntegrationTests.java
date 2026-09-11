package com.hippocampus.materials.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.dao.DataIntegrityViolationException;

import com.hippocampus.identity.infrastructure.persistence.UserRepository;
import com.hippocampus.materials.application.UploadMaterial;
import com.hippocampus.materials.infrastructure.persistence.SpringDataMaterialRepository;
import com.hippocampus.materials.infrastructure.persistence.SpringDataMaterialVersionRepository;
import com.hippocampus.materials.infrastructure.persistence.SpringDataProcessingJobRepository;
import com.hippocampus.materials.port.BinaryObjectStore;
import com.hippocampus.testing.PostgresIntegrationTestSupport;
import com.hippocampus.testing.security.OwnershipTestUsers;

@SpringBootTest
class MaterialUploadCompensationIntegrationTests extends PostgresIntegrationTestSupport {

    @Autowired
    private UploadMaterial uploadMaterial;

    @Autowired
    private SpringDataMaterialRepository materials;

    @Autowired
    private SpringDataMaterialVersionRepository versions;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private SpringDataProcessingJobRepository mockJobs;

    @Autowired
    private BinaryObjectStore objectStore;

    @Test
    void removesStoredObjectWhenRelationalAcceptanceFailsAtJobPersistence() {
        OwnershipTestUsers users = OwnershipTestUsers.persistWith(userRepository, "compensation-test");
        UUID ownerId = users.userA().userId();

        doThrow(new DataIntegrityViolationException("forced job failure"))
                .when(mockJobs).saveAndFlush(any());

        UploadMaterial.Command command = new UploadMaterial.Command(
                "compensation.pdf", "application/pdf", 1024L,
                () -> new ByteArrayInputStream(new byte[1024]));

        assertThatThrownBy(() -> uploadMaterial.execute(command))
                .isInstanceOf(RuntimeException.class);

        assertThat(materials.count()).isZero();
        assertThat(versions.count()).isZero();
    }
}
