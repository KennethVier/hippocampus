package com.hippocampus.learning.api;

import com.hippocampus.learning.application.StartStudyMissionExperienceUseCase;
import com.hippocampus.learning.application.StartStudyMissionUseCase;
import com.hippocampus.learning.infrastructure.config.StudyMissionApplicationConfiguration;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(StudyMissionStartController.class)
@Import(StudyMissionStartControllerWebTests.Infrastructure.class)
@ImportAutoConfiguration(exclude=StudyMissionApplicationConfiguration.class)
class StudyMissionStartControllerWebTests {
    @Autowired MockMvc mvc;
    @MockitoBean StartStudyMissionExperienceUseCase experience;
    @MockitoBean StartStudyMissionUseCase sources;

    @Test void applicationProjectionPreservesDiscriminatedBodiesAndTransportHeaders() {
        var mission=java.util.UUID.randomUUID();var key=java.util.UUID.randomUUID();var activity=java.util.UUID.randomUUID();
        for(String outcome:java.util.List.of("ACTIVITY_CREATED","PREPARING","NO_ACTIVITY","RECOVERY_REQUIRED")) {
            var result=new StartStudyMissionExperienceUseCase.ClientResult(mission,key,"ACTIVE",false,outcome,
                    activity,"COMPLETE","TIME_LIMIT","INITIAL_COMPLETION_NOT_ADMISSIBLE",false,5);
            var response=StudyMissionStartController.response(result,true);
            org.assertj.core.api.Assertions.assertThat(response.getStatusCode().value())
                    .isEqualTo(outcome.equals("ACTIVITY_CREATED")?201:outcome.equals("PREPARING")?202:200);
            org.assertj.core.api.Assertions.assertThat(response.getHeaders().getLocation().toString()).isEqualTo("/api/study-missions/"+mission);
            org.assertj.core.api.Assertions.assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
            @SuppressWarnings("unchecked") var body=(java.util.Map<String,Object>)response.getBody();
            var keys=new java.util.HashSet<>(java.util.Set.of("missionId","startRequestId","missionStatus","replayed","outcome"));
            if(outcome.equals("ACTIVITY_CREATED")) keys.add("activityId");
            else if(outcome.equals("PREPARING")) keys.add("retryAfterSeconds");
            else {keys.addAll(java.util.Set.of("reasonCode","canRetry","retryAfterSeconds"));
                if(outcome.equals("NO_ACTIVITY"))keys.addAll(java.util.Set.of("actionType","rationaleCode"));}
            org.assertj.core.api.Assertions.assertThat(body.keySet()).containsExactlyInAnyOrderElementsOf(keys);
            org.assertj.core.api.Assertions.assertThat(response.getHeaders().getFirst("Retry-After"))
                    .isEqualTo(outcome.equals("PREPARING")?"5":null);
            if(outcome.equals("ACTIVITY_CREATED")) {
                org.assertj.core.api.Assertions.assertThat(StudyMissionStartController.response(result,false).getStatusCode().value()).isEqualTo(200);
                var replay=new StartStudyMissionExperienceUseCase.ClientResult(mission,key,"ACTIVE",true,outcome,activity,null,null,null,false,null);
                org.assertj.core.api.Assertions.assertThat(StudyMissionStartController.response(replay,true).getStatusCode().value()).isEqualTo(200);
            }
        }
    }

    @ParameterizedTest @MethodSource("invalidBodies")
    void rejectsForgedOrAmbiguousCommandsBeforeApplication(String body) throws Exception {
        mvc.perform(post("/api/study-missions").with(user("student")).with(csrf())
                .contentType("application/json").content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(experience,sources);
    }
    static Stream<String> invalidBodies() {
        String valid="""
            {"startRequestId":"10000000-0000-0000-0000-000000000001",
             "topicId":"20000000-0000-0000-0000-000000000001","learningObjectiveText":"Objective",
             "availableTimeMinutes":20,"groundingMode":"STRICT_SOURCE",
             "sourceScopes":[{"materialId":"30000000-0000-0000-0000-000000000001"}]}
            """;
        return Stream.of(valid.replace("\"availableTimeMinutes\":20","\"availableTimeMinutes\":\"20\""),
                valid.replace("\"availableTimeMinutes\":20","\"availableTimeMinutes\":20.0"),
                valid.replace("\"availableTimeMinutes\":20","\"availableTimeMinutes\":20,\"availableTimeMinutes\":20"),
                valid.replace("\"groundingMode\":\"STRICT_SOURCE\"","\"groundingMode\":\"GENERAL_KNOWLEDGE\""),
                valid.replace("\"learningObjectiveText\":\"Objective\"","\"learningObjectiveText\":null"),
                valid.replace("\"sourceScopes\":","\"ownerId\":\"other\",\"sourceScopes\":"),
                valid.replace("\"materialId\":","\"documentNodeId\":null,\"materialId\":"),
                valid+"{}", "null", "[]");
    }
    @Test void csrfAndAuthenticationAreRequiredBeforeStart() throws Exception {
        mvc.perform(post("/api/study-missions").contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/study-missions").with(csrf()).contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/study-missions").with(user("student")).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(experience,sources);
    }
    @Test void streamingByteLimitAndEncodingCannotBeBypassed() throws Exception {
        mvc.perform(post("/api/study-missions").with(user("student")).with(csrf())
                .contentType("application/json").content(" ".repeat(16385))).andExpect(status().isPayloadTooLarge());
        mvc.perform(post("/api/study-missions").with(user("student")).with(csrf())
                .contentType("application/json").header("Content-Encoding","gzip").content("{}"))
                .andExpect(status().isUnsupportedMediaType());
        verifyNoInteractions(experience,sources);
    }
    @Test void retryBodyAndSelectorCannotAcceptAuthorityFieldsOrDuplicateQueryValues() throws Exception {
        mvc.perform(post("/api/study-missions/10000000-0000-0000-0000-000000000001/initial-activity")
                .with(user("student")).with(csrf()).contentType("application/json")
                .content("{\"startRequestId\":\"10000000-0000-0000-0000-000000000001\",\"action\":\"UNDERSTAND\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/topics/10000000-0000-0000-0000-000000000001/study-mission-sources")
                .with(user("student")).param("limit","20","20")).andExpect(status().isBadRequest());
        verifyNoInteractions(experience,sources);
    }
    @TestConfiguration(proxyBeanMethods=false) @EnableWebSecurity
    static class Infrastructure {
        @Bean @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean(StudyMissionStartController.class)
        StudyMissionStartController controller(StartStudyMissionExperienceUseCase experience,StartStudyMissionUseCase sources) {
            return new StudyMissionStartController(experience,sources);
        }
        @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
            return http.authorizeHttpRequests(a->a.anyRequest().authenticated())
                    .exceptionHandling(e->e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))).build();
        }
    }
}
