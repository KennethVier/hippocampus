package com.hippocampus.learning.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.hippocampus.learning.application.GetStudyMissionUseCase;
import com.hippocampus.learning.infrastructure.config.StudyMissionApplicationConfiguration;
import com.hippocampus.shared.application.error.ApplicationNotFoundException;
import com.hippocampus.shared.domain.error.ErrorCode;

@WebMvcTest(StudyMissionReadController.class)
@Import(StudyMissionReadControllerWebTests.TestInfrastructure.class)
@ImportAutoConfiguration(exclude = StudyMissionApplicationConfiguration.class)
class StudyMissionReadControllerWebTests {

    private static final UUID MISSION_ID = UUID.randomUUID();
    private static final UUID ACTIVITY_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private GetStudyMissionUseCase getStudyMissionUseCase;

    @Test
    void exactInitialContractOptInIsIndependentAndRejectsDuplicates() throws Exception {
        Mockito.when(getStudyMissionUseCase.execute(MISSION_ID,true,true)).thenReturn(result());
        mvc.perform(get("/api/study-missions/{missionId}",MISSION_ID).param("continuationContract","1").param("initialActivityContract","1").with(user("student")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.initialActivity").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control","no-store"));
        Mockito.verify(getStudyMissionUseCase).execute(MISSION_ID,true,true);
        Mockito.clearInvocations(getStudyMissionUseCase);
        for(String[] values:new String[][]{{"1","1"},{"01"},{""},{"2"}})
            mvc.perform(get("/api/study-missions/{missionId}",MISSION_ID).param("initialActivityContract",values).with(user("student"))).andExpect(status().isBadRequest());
        Mockito.verifyNoInteractions(getStudyMissionUseCase);
    }

    @Test
    void authenticatedGetReturnsTypedLearnerSafePresentation() throws Exception {
        Mockito.doReturn(result())
                .when(getStudyMissionUseCase)
                .execute(MISSION_ID);

        String response = mvc.perform(get("/api/study-missions/{missionId}", MISSION_ID)
                        .with(user("student")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.currentActivity.type").value("RETRIEVAL"))
                .andExpect(jsonPath("$.currentActivity.content.subtype").value("MCQ"))
                .andExpect(jsonPath("$.currentActivity.sources[0].materialTitle")
                        .value("Upper Limb Lecture"))
                .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain(
                "provider", "model", "prompt", "chunk", "expectedAnswer", "correctOption", "continuation");
    }

    @Test
    void optInReadAddsCurrentDecisionWithoutChangingLegacyPresentation() throws Exception {
        var old = result();
        var current = new GetStudyMissionUseCase.Result(old.id(), old.status(), old.stage(),
                old.currentActivity(), old.availableTimeMinutes(), old.startedAt(), old.completedAt(),
                old.stoppedAt(), old.updatedAt(), null,
                new GetStudyMissionUseCase.Continuation("COMMUNICATE_LIMITATION", "ASSESSMENT_UNAVAILABLE", false));
        Mockito.when(getStudyMissionUseCase.execute(MISSION_ID, true)).thenReturn(current);
        mvc.perform(get("/api/study-missions/{missionId}", MISSION_ID)
                        .param("continuationContract", "1").with(user("student")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.continuation.actionType").value("COMMUNICATE_LIMITATION"))
                .andExpect(jsonPath("$.continuation.canMaterialize").value(false));
        Mockito.verify(getStudyMissionUseCase).execute(MISSION_ID, true);
    }

    @Test
    void invalidReadVersionDoesNotCallApplication() throws Exception {
        mvc.perform(get("/api/study-missions/{missionId}", MISSION_ID)
                        .param("continuationContract", "1", "1").with(user("student")))
                .andExpect(status().isBadRequest());
        Mockito.verifyNoInteractions(getStudyMissionUseCase);
    }

    @Test
    void crossUserSafeNotFoundIsPreserved() throws Exception {
        Mockito.doThrow(new ApplicationNotFoundException(
                        new ErrorCode("STUDY_MISSION_NOT_FOUND"), "Study mission was not found."))
                .when(getStudyMissionUseCase)
                .execute(MISSION_ID);

        mvc.perform(get("/api/study-missions/{missionId}", MISSION_ID).with(user("student")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STUDY_MISSION_NOT_FOUND"));
    }

    @Test
    void unauthenticatedGetIsRejected() throws Exception {
        mvc.perform(get("/api/study-missions/{missionId}", MISSION_ID))
                .andExpect(status().isUnauthorized());
    }

    private static GetStudyMissionUseCase.Result result() {
        Instant now = Instant.parse("2026-10-01T09:00:00Z");
        var content = new GetStudyMissionUseCase.RetrievalContent(
                "MCQ", "Brachial plexus", "Which roots form the upper trunk?",
                List.of(new GetStudyMissionUseCase.Option("A", "C5-C6")),
                "FOUNDATIONAL", List.of());
        var source = new GetStudyMissionUseCase.SourcePresentation(
                UUID.randomUUID(), "Upper Limb Lecture", 14, "Posterior Cord");
        var activity = new GetStudyMissionUseCase.CurrentActivity(
                ACTIVITY_ID, "RETRIEVAL", "ACTIVE", "FOUNDATIONAL",
                "SOURCE_GROUNDED_GENERATED", content, List.of(source));
        return new GetStudyMissionUseCase.Result(
                MISSION_ID, "ACTIVE", "RETRIEVAL", activity, 25,
                now.minusSeconds(600), null, null, now);
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebSecurity
    static class TestInfrastructure {
        @Bean
        @ConditionalOnMissingBean(StudyMissionReadController.class)
        StudyMissionReadController studyMissionReadController(
                GetStudyMissionUseCase getStudyMissionUseCase) {
            return new StudyMissionReadController(getStudyMissionUseCase);
        }

        @Bean
        SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            return http
                    .csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                    .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(
                            new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                    .build();
        }
    }
}
