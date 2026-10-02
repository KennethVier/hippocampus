package com.hippocampus.learning.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
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

import com.hippocampus.learning.application.SubmitActivityResponseUseCase;
import com.hippocampus.learning.domain.AttemptOutcome;
import com.hippocampus.learning.domain.LearningActionType;
import com.hippocampus.learning.domain.LearningActivity;
import com.hippocampus.learning.domain.LearningActivityType;
import com.hippocampus.learning.domain.LearningDifficulty;
import com.hippocampus.learning.domain.LearningObjective;
import com.hippocampus.learning.domain.LearningObjectiveStatus;
import com.hippocampus.learning.domain.LearningStage;
import com.hippocampus.learning.domain.MissionMaterial;
import com.hippocampus.learning.domain.NextLearningAction;
import com.hippocampus.learning.domain.StudyMission;
import com.hippocampus.learning.domain.StudyMissionGroundingMode;
import com.hippocampus.learning.domain.StudyMissionStatus;
import com.hippocampus.learning.infrastructure.config.StudyMissionApplicationConfiguration;
import com.hippocampus.learning.port.ResponseEvaluationPort;
import com.hippocampus.progress.domain.StudentAttempt;
import com.hippocampus.shared.application.error.ApplicationNotFoundException;
import com.hippocampus.shared.domain.error.ErrorCode;

@WebMvcTest(ActivityResponseController.class)
@Import(ActivityResponseControllerWebTests.TestInfrastructure.class)
@ImportAutoConfiguration(exclude = StudyMissionApplicationConfiguration.class)
class ActivityResponseControllerWebTests {

    private static final Instant NOW = Instant.parse("2026-10-02T05:00:00Z");
    private static final UUID MISSION_ID = UUID.randomUUID();
    private static final UUID ACTIVITY_ID = UUID.randomUUID();
    private static final UUID OBJECTIVE_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SubmitActivityResponseUseCase submitResponse;

    @Test
    void selectedOptionSubmissionDelegatesAndReturnsLearnerSafeFeedback() throws Exception {
        when(submitResponse.execute(any())).thenReturn(result());

        String body = mvc.perform(post(
                            "/api/study-missions/{missionId}/activities/{activityId}/responses",
                            MISSION_ID, ACTIVITY_ID)
                        .with(user("student"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"selectedOption\":\"B\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.missionId").value(MISSION_ID.toString()))
                .andExpect(jsonPath("$.activityId").value(ACTIVITY_ID.toString()))
                .andExpect(jsonPath("$.outcome").value("PARTIAL"))
                .andExpect(jsonPath("$.feedback").value("Review preload."))
                .andExpect(jsonPath("$.missionStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.stage").value("UNDERSTANDING"))
                .andExpect(jsonPath("$.continuationAvailable").value(true))
                .andReturn().getResponse().getContentAsString();

        ArgumentCaptor<SubmitActivityResponseUseCase.Command> command =
                ArgumentCaptor.forClass(SubmitActivityResponseUseCase.Command.class);
        verify(submitResponse).execute(command.capture());
        assertThat(command.getValue().selectedOption()).isEqualTo("B");
        assertThat(command.getValue().responsePayload()).isNull();
        assertThat(body).doesNotContain(
                "expectedAnswer", "correctOption", "provider", "model", "prompt",
                "rationaleCode", "nextAction", "responsePayload");
    }

    @Test
    void writtenResponseSubmissionDelegatesWithoutStructuredPayload() throws Exception {
        when(submitResponse.execute(any())).thenReturn(result());

        mvc.perform(post(
                            "/api/study-missions/{missionId}/activities/{activityId}/responses",
                            MISSION_ID, ACTIVITY_ID)
                        .with(user("student"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"responseText\":\"Cardiac output depends on stroke volume.\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<SubmitActivityResponseUseCase.Command> command =
                ArgumentCaptor.forClass(SubmitActivityResponseUseCase.Command.class);
        verify(submitResponse).execute(command.capture());
        assertThat(command.getValue().responseText())
                .isEqualTo("Cardiac output depends on stroke volume.");
        assertThat(command.getValue().responsePayload()).isNull();
    }

    @Test
    void crossUserMissionStaysSafeNotFound() throws Exception {
        Mockito.doThrow(new ApplicationNotFoundException(
                        new ErrorCode("STUDY_MISSION_NOT_FOUND"), "Study mission was not found."))
                .when(submitResponse).execute(any());

        mvc.perform(post(
                            "/api/study-missions/{missionId}/activities/{activityId}/responses",
                            MISSION_ID, ACTIVITY_ID)
                        .with(user("student"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"selectedOption\":\"B\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STUDY_MISSION_NOT_FOUND"));
    }

    @Test
    void postRequiresAuthenticationAndCsrf() throws Exception {
        String path = "/api/study-missions/{missionId}/activities/{activityId}/responses";
        mvc.perform(post(path, MISSION_ID, ACTIVITY_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(path, MISSION_ID, ACTIVITY_ID)
                        .with(user("student"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    private static SubmitActivityResponseUseCase.Result result() {
        LearningActivity activity = new LearningActivity(
                ACTIVITY_ID, OBJECTIVE_ID, LearningActivityType.RETRIEVE,
                LearningActionType.RETRIEVE, "recall", "retrieval-v1", "COMPLETED",
                LearningDifficulty.FOUNDATIONAL, 1, UUID.randomUUID(), true,
                NOW.minusSeconds(60), NOW, NOW.minusSeconds(120), Set.of());
        StudyMission mission = new StudyMission(
                MISSION_ID, UUID.randomUUID(), UUID.randomUUID(), null, StudyMissionStatus.ACTIVE,
                LearningStage.UNDERSTANDING, StudyMissionGroundingMode.STRICT_SOURCE, 30,
                NOW.minusSeconds(300), null, null, ACTIVITY_ID,
                List.of(new MissionMaterial(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null)),
                List.of(new LearningObjective(
                        OBJECTIVE_ID, "Explain cardiac output", "cardiac-output", "Cardiac output",
                        1, LearningObjectiveStatus.ACTIVE, NOW.minusSeconds(300))),
                List.of(activity), NOW.minusSeconds(300), NOW);
        StudentAttempt attempt = new StudentAttempt(
                UUID.randomUUID(), mission.userId(), ACTIVITY_ID, 1, "answer", null,
                NOW, "PARTIAL", null, null, NOW);
        var evaluation = new SubmitActivityResponseUseCase.Evaluation(
                AttemptOutcome.PARTIAL, List.of("stroke volume"), List.of("preload"),
                List.of("afterload confusion"), "Review preload.",
                ResponseEvaluationPort.Certainty.SUFFICIENT,
                ResponseEvaluationPort.RecommendedAction.TARGETED_EXPLANATION, false);
        NextLearningAction action = new NextLearningAction(
                LearningActionType.UNDERSTAND, OBJECTIVE_ID, "cardiac-output",
                LearningDifficulty.FOUNDATIONAL, "TEST_FEEDBACK", false);
        return new SubmitActivityResponseUseCase.Result(
                attempt, activity, mission, evaluation, action);
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebSecurity
    static class TestInfrastructure {
        @Bean
        @ConditionalOnMissingBean(ActivityResponseController.class)
        ActivityResponseController activityResponseController(
                SubmitActivityResponseUseCase submitResponse) {
            return new ActivityResponseController(submitResponse);
        }

        @Bean
        SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            return http
                    .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                    .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(
                            new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                    .build();
        }
    }
}
