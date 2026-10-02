package com.hippocampus.learning.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.FilterChain;
import tools.jackson.databind.ObjectMapper;

class ActivityResponseBodySizeFilterTests {

    private static final String PATH =
            "/api/study-missions/8d98d85a-3d4d-4b43-bd8d-726ef4aaaf84/activities/"
                    + "2bb844b8-b07f-4f1b-9e6c-567a559759b4/responses";

    @Test
    void actualBytesAboveLimitAreRejectedWhenContentLengthIsUnavailable() throws Exception {
        byte[] body = "x".repeat(ActivityResponseBodySizeFilter.MAX_REQUEST_BODY_BYTES + 1)
                .getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", PATH) {
            @Override
            public int getContentLength() {
                return -1;
            }

            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };
        request.setContent(body);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        new ActivityResponseBodySizeFilter(new ObjectMapper()).doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("REQUEST_BODY_TOO_LARGE");
        verifyNoInteractions(chain);
    }
}
