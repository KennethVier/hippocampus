package com.hippocampus.learning.api;

import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
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

import com.hippocampus.learning.application.ContinueStudyMissionUseCase;
import com.hippocampus.learning.infrastructure.config.StudyMissionApplicationConfiguration;

@WebMvcTest(StudyMissionContinuationController.class)
@Import(StudyMissionContinuationControllerWebTests.TestInfrastructure.class)
@ImportAutoConfiguration(exclude = StudyMissionApplicationConfiguration.class)
class StudyMissionContinuationControllerWebTests {

    private static final UUID MISSION_ID = UUID.randomUUID();
    private static final UUID ACTIVITY_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ContinueStudyMissionUseCase continueMission;

    @Test
    void continueUsesOnlyPathIdentityAndReturnsNoContent() throws Exception {
        mvc.perform(post(
                            "/api/study-missions/{missionId}/activities/{activityId}/continue",
                            MISSION_ID, ACTIVITY_ID)
                        .with(user("student"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actionType\":\"APPLY\",\"provider\":\"client-choice\"}"))
                .andExpect(status().isNoContent());

        verify(continueMission).execute(
                new ContinueStudyMissionUseCase.Command(MISSION_ID, ACTIVITY_ID));
    }

    @Test
    void continueRequiresAuthenticationAndCsrf() throws Exception {
        String path = "/api/study-missions/{missionId}/activities/{activityId}/continue";
        mvc.perform(post(path, MISSION_ID, ACTIVITY_ID).with(csrf()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(path, MISSION_ID, ACTIVITY_ID).with(user("student")))
                .andExpect(status().isForbidden());
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebSecurity
    static class TestInfrastructure {
        @Bean
        @ConditionalOnMissingBean(StudyMissionContinuationController.class)
        StudyMissionContinuationController studyMissionContinuationController(
                ContinueStudyMissionUseCase continueMission) {
            return new StudyMissionContinuationController(continueMission);
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
