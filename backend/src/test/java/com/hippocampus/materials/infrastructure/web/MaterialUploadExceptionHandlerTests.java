package com.hippocampus.materials.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

class MaterialUploadExceptionHandlerTests {

    @Test
    void mapsContainerMaximumUploadExceptionToSanitizedPayloadTooLarge() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/materials");
        var response = new MaterialUploadExceptionHandler().handleMaximumUploadSize(request);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getProperties()).containsEntry("code", "UPLOAD_TOO_LARGE");
        assertThat(response.getBody().getDetail()).doesNotContain("/var/", "storage key", "provider");
    }
}
