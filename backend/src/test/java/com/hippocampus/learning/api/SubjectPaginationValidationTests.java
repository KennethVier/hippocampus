package com.hippocampus.learning.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.hippocampus.learning.application.ArchiveSubject;
import com.hippocampus.learning.application.CreateSubject;
import com.hippocampus.learning.application.GetSubject;
import com.hippocampus.learning.application.ListSubjects;
import com.hippocampus.learning.application.UpdateSubject;

@WebMvcTest(SubjectController.class)
class SubjectPaginationValidationTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreateSubject createSubject;

    @MockitoBean
    private ListSubjects listSubjects;

    @MockitoBean
    private GetSubject getSubject;

    @MockitoBean
    private UpdateSubject updateSubject;

    @MockitoBean
    private ArchiveSubject archiveSubject;

    @ParameterizedTest
    @ValueSource(strings = {"page=-1", "size=0", "size=101"})
    void invalidPaginationReturnsSafeBadRequest(String query) throws Exception {
        mockMvc.perform(get("/api/subjects?" + query).with(user("student")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_REJECTED"));
    }
}
