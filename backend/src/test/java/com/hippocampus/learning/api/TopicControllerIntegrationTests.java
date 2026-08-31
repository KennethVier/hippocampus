package com.hippocampus.learning.api;

import static com.hippocampus.testing.security.OwnershipAssertions.notFoundWithoutForeignData;
import static com.hippocampus.testing.security.OwnershipTestRequests.authenticatedAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import com.hippocampus.identity.infrastructure.persistence.UserRepository;
import com.hippocampus.learning.infrastructure.persistence.*;
import com.hippocampus.testing.PostgresIntegrationTestSupport;
import com.hippocampus.testing.security.OwnershipTestUsers;

class TopicControllerIntegrationTests extends PostgresIntegrationTestSupport {
    @BeforeEach void resetDatabase() throws SQLException { resetPostgresSchema(); }

    @Test void createValidatesOwnedActiveParentPreservesNameAllowsDuplicatesAndBlocksOverposting() throws Exception {
        try (var context=startApplicationWithFlyway()) {
            var users=users(context,"topic-create"); var subjects=context.getBean(SpringDataSubjectRepository.class);
            SubjectEntity owned=subject(subjects,users.userA().userId(),"Owned",LearningOrganizationStatus.ACTIVE);
            SubjectEntity foreign=subject(subjects,users.userB().userId(),"Foreign secret",LearningOrganizationStatus.ACTIVE);
            SubjectEntity archived=subject(subjects,users.userA().userId(),"Archived secret",LearningOrganizationStatus.ARCHIVED);
            MockMvc mvc=mvc(context);
            String body="""
                    {"name":"  Duplicate  ","description":"Exact","userId":"%s","subjectId":"%s","status":"ARCHIVED"}
                    """
                    .formatted(users.userB().userId(),foreign.getId());
            for (int i=0;i<2;i++) mvc.perform(post("/api/subjects/{id}/topics",owned.getId()).with(authenticatedAs(users.userA())).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("  Duplicate  ")).andExpect(jsonPath("$.subjectId").value(owned.getId().toString()))
                    .andExpect(jsonPath("$.status").value("ACTIVE"));
            assertThat(context.getBean(SpringDataTopicRepository.class).findAll()).hasSize(2)
                    .allSatisfy(t -> assertThat(t.getSubject().getId()).isEqualTo(owned.getId()));
            mvc.perform(post("/api/subjects/{id}/topics",foreign.getId()).with(authenticatedAs(users.userA())).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Denied\"}"))
                    .andExpect(notFoundWithoutForeignData("Foreign secret")).andExpect(jsonPath("$.code").value("SUBJECT_NOT_FOUND"));
            mvc.perform(post("/api/subjects/{id}/topics",archived.getId()).with(authenticatedAs(users.userA())).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Denied\"}"))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SUBJECT_NOT_FOUND"));
            mvc.perform(post("/api/subjects/{id}/topics",owned.getId()).with(authenticatedAs(users.userA())).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"   \"}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
    }

    @Test void listUpdateAndArchiveEnforceOwnerAncestorAndIndependentLifecycle() throws Exception {
        try (var context=startApplicationWithFlyway()) {
            var users=users(context,"topic-mutate"); var subjects=context.getBean(SpringDataSubjectRepository.class);
            var topics=context.getBean(SpringDataTopicRepository.class);
            SubjectEntity owned=subject(subjects,users.userA().userId(),"Owned",LearningOrganizationStatus.ACTIVE);
            SubjectEntity foreign=subject(subjects,users.userB().userId(),"Foreign subject",LearningOrganizationStatus.ACTIVE);
            TopicEntity z=topic(topics,owned,"Zulu",LearningOrganizationStatus.ACTIVE);
            TopicEntity a=topic(topics,owned,"alpha",LearningOrganizationStatus.ACTIVE);
            TopicEntity archived=topic(topics,owned,"Archived",LearningOrganizationStatus.ARCHIVED);
            TopicEntity foreignTopic=topic(topics,foreign,"Foreign topic secret",LearningOrganizationStatus.ACTIVE);
            MockMvc mvc=mvc(context);
            mvc.perform(get("/api/subjects/{id}/topics?page=0&size=1",owned.getId()).with(authenticatedAs(users.userA())))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].name").value("alpha"))
                    .andExpect(jsonPath("$.totalElements").value(2));
            mvc.perform(get("/api/subjects/{id}/topics",foreign.getId()).with(authenticatedAs(users.userA())))
                    .andExpect(notFoundWithoutForeignData("Foreign subject","Foreign topic secret"));
            mvc.perform(get("/api/subjects/{id}/topics?size=101",owned.getId()).with(authenticatedAs(users.userA())))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("REQUEST_REJECTED"));
            mvc.perform(put("/api/topics/{id}",archived.getId()).with(authenticatedAs(users.userA())).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"  Kept Exact  \",\"status\":\"ACTIVE\",\"subjectId\":\""+foreign.getId()+"\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ARCHIVED"))
                    .andExpect(jsonPath("$.subjectId").value(owned.getId().toString()));
            mvc.perform(put("/api/topics/{id}",foreignTopic.getId()).with(authenticatedAs(users.userA())).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Attack\"}"))
                    .andExpect(notFoundWithoutForeignData("Foreign topic secret"));
            mvc.perform(post("/api/topics/{id}/archive",foreignTopic.getId())
                            .with(authenticatedAs(users.userA())).with(csrf()))
                    .andExpect(notFoundWithoutForeignData("Foreign topic secret"));
            assertThat(topics.findById(foreignTopic.getId()).orElseThrow().getName()).isEqualTo("Foreign topic secret");
            assertThat(topics.findById(foreignTopic.getId()).orElseThrow().getStatus())
                    .isEqualTo(LearningOrganizationStatus.ACTIVE);
            owned.setStatus(LearningOrganizationStatus.ARCHIVED); subjects.saveAndFlush(owned);
            mvc.perform(put("/api/topics/{id}",z.getId()).with(authenticatedAs(users.userA())).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Blocked\"}"))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("TOPIC_NOT_FOUND"));
            for (int i=0;i<2;i++) mvc.perform(post("/api/topics/{id}/archive",z.getId()).with(authenticatedAs(users.userA())).with(csrf()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ARCHIVED"));
            assertThat(topics.findById(a.getId()).orElseThrow().getStatus()).isEqualTo(LearningOrganizationStatus.ACTIVE);
        }
    }

    @Test void securityContractRequiresAuthenticationAndCsrfAndConcealsForeignSubjectExistence() throws Exception {
        try (var context=startApplicationWithFlyway()) {
            var users=users(context,"topic-security");
            var subjects=context.getBean(SpringDataSubjectRepository.class);
            SubjectEntity owned=subject(subjects,users.userA().userId(),"Owned",LearningOrganizationStatus.ACTIVE);
            SubjectEntity foreign=subject(subjects,users.userB().userId(),"Foreign subject marker",LearningOrganizationStatus.ACTIVE);
            MockMvc mvc=mvc(context);

            mvc.perform(get("/api/subjects/{id}/topics",owned.getId()))
                    .andExpect(status().isUnauthorized());
            mvc.perform(post("/api/subjects/{id}/topics",owned.getId())
                            .with(authenticatedAs(users.userA()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Missing CSRF\"}"))
                    .andExpect(status().isForbidden());

            MvcResult foreignResult=mvc.perform(get("/api/subjects/{id}/topics",foreign.getId())
                            .with(authenticatedAs(users.userA())))
                    .andExpect(notFoundWithoutForeignData("Foreign subject marker"))
                    .andExpect(jsonPath("$.code").value("SUBJECT_NOT_FOUND"))
                    .andExpect(jsonPath("$.message").value("Subject was not found."))
                    .andExpect(jsonPath("$.details").isMap())
                    .andReturn();
            MvcResult missingResult=mvc.perform(get("/api/subjects/{id}/topics",UUID.randomUUID())
                            .with(authenticatedAs(users.userA())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("SUBJECT_NOT_FOUND"))
                    .andExpect(jsonPath("$.message").value("Subject was not found."))
                    .andExpect(jsonPath("$.details").isMap())
                    .andReturn();

            assertEquivalentNotFound(context,foreignResult,missingResult);
        }
    }

    private static void assertEquivalentNotFound(
            ConfigurableApplicationContext context,MvcResult foreignResult,MvcResult missingResult) throws Exception {
        var objectMapper=context.getBean(tools.jackson.databind.ObjectMapper.class);
        var foreignProblem=objectMapper.readTree(foreignResult.getResponse().getContentAsByteArray());
        var missingProblem=objectMapper.readTree(missingResult.getResponse().getContentAsByteArray());
        assertThat(foreignResult.getResponse().getStatus()).isEqualTo(missingResult.getResponse().getStatus());
        assertThat(foreignProblem.get("code")).isEqualTo(missingProblem.get("code"));
        assertThat(foreignProblem.get("message")).isEqualTo(missingProblem.get("message"));
        assertThat(foreignProblem.get("details")).isEqualTo(missingProblem.get("details"));
    }

    private static OwnershipTestUsers users(ConfigurableApplicationContext c,String s){return OwnershipTestUsers.persistWith(c.getBean(UserRepository.class),s);}
    private static MockMvc mvc(ConfigurableApplicationContext c){return MockMvcBuilders.webAppContextSetup((WebApplicationContext)c).apply(springSecurity()).build();}
    private static SubjectEntity subject(SpringDataSubjectRepository r,UUID u,String n,LearningOrganizationStatus s){return r.saveAndFlush(new SubjectEntity(u,n,s));}
    private static TopicEntity topic(SpringDataTopicRepository r,SubjectEntity s,String n,LearningOrganizationStatus st){return r.saveAndFlush(new TopicEntity(s,n,st));}
}
