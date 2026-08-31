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

class SubtopicControllerIntegrationTests extends PostgresIntegrationTestSupport {
    @BeforeEach void resetDatabase() throws SQLException { resetPostgresSchema(); }

    @Test void createEnforcesFullActiveOwnedChainPreservesNamesDuplicatesAndOverpostingSafety() throws Exception {
        try(var context=startApplicationWithFlyway()) {
            var users=users(context,"subtopic-create"); var subjects=context.getBean(SpringDataSubjectRepository.class);
            var topics=context.getBean(SpringDataTopicRepository.class); var subtopics=context.getBean(SpringDataSubtopicRepository.class);
            SubjectEntity owned=subject(subjects,users.userA().userId(),"Owned",LearningOrganizationStatus.ACTIVE);
            SubjectEntity foreign=subject(subjects,users.userB().userId(),"Foreign subject",LearningOrganizationStatus.ACTIVE);
            SubjectEntity archivedSubject=subject(subjects,users.userA().userId(),"Archived subject",LearningOrganizationStatus.ARCHIVED);
            TopicEntity active=topic(topics,owned,"Active",LearningOrganizationStatus.ACTIVE);
            TopicEntity foreignTopic=topic(topics,foreign,"Foreign topic secret",LearningOrganizationStatus.ACTIVE);
            TopicEntity archivedTopic=topic(topics,owned,"Archived topic",LearningOrganizationStatus.ARCHIVED);
            TopicEntity underArchived=topic(topics,archivedSubject,"Unavailable topic",LearningOrganizationStatus.ACTIVE);
            MockMvc mvc=mvc(context);
            String body="""
                    {"name":"  Duplicate  ","description":"Exact","sortOrder":3,"userId":"%s","topicId":"%s","status":"ARCHIVED"}
                    """
                    .formatted(users.userB().userId(),foreignTopic.getId());
            for(int i=0;i<2;i++) mvc.perform(post("/api/topics/{id}/subtopics",active.getId()).with(authenticatedAs(users.userA())).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("  Duplicate  ")).andExpect(jsonPath("$.topicId").value(active.getId().toString()))
                    .andExpect(jsonPath("$.status").value("ACTIVE"));
            assertThat(subtopics.findAll()).hasSize(2).allSatisfy(s -> assertThat(s.getTopic().getId()).isEqualTo(active.getId()));
            for(TopicEntity denied:new TopicEntity[]{foreignTopic,archivedTopic,underArchived})
                mvc.perform(post("/api/topics/{id}/subtopics",denied.getId()).with(authenticatedAs(users.userA())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Denied\"}"))
                        .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("TOPIC_NOT_FOUND"));
            mvc.perform(post("/api/topics/{id}/subtopics",active.getId()).with(authenticatedAs(users.userA())).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" \"}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
    }

    @Test void listUpdateAndArchiveApplyAncestorPolicyOrderingAndOwnerIsolation() throws Exception {
        try(var context=startApplicationWithFlyway()) {
            var users=users(context,"subtopic-mutate"); var subjects=context.getBean(SpringDataSubjectRepository.class);
            var topics=context.getBean(SpringDataTopicRepository.class); var subs=context.getBean(SpringDataSubtopicRepository.class);
            SubjectEntity owned=subject(subjects,users.userA().userId(),"Owned",LearningOrganizationStatus.ACTIVE);
            SubjectEntity foreign=subject(subjects,users.userB().userId(),"Foreign subject",LearningOrganizationStatus.ACTIVE);
            TopicEntity active=topic(topics,owned,"Active",LearningOrganizationStatus.ACTIVE);
            TopicEntity foreignTopic=topic(topics,foreign,"Foreign topic",LearningOrganizationStatus.ACTIVE);
            SubtopicEntity z=sub(subs,active,"Zulu",2,LearningOrganizationStatus.ACTIVE);
            SubtopicEntity a=sub(subs,active,"alpha",1,LearningOrganizationStatus.ACTIVE);
            SubtopicEntity archived=sub(subs,active,"Archived",null,LearningOrganizationStatus.ARCHIVED);
            SubtopicEntity foreignSub=sub(subs,foreignTopic,"Foreign subtopic secret",1,LearningOrganizationStatus.ACTIVE);
            MockMvc mvc=mvc(context);
            mvc.perform(get("/api/topics/{id}/subtopics?page=0&size=1",active.getId()).with(authenticatedAs(users.userA())))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].name").value("alpha"))
                    .andExpect(jsonPath("$.totalElements").value(2));
            mvc.perform(get("/api/topics/{id}/subtopics",foreignTopic.getId()).with(authenticatedAs(users.userA())))
                    .andExpect(notFoundWithoutForeignData("Foreign topic","Foreign subtopic secret"));
            mvc.perform(get("/api/topics/{id}/subtopics?page=-1",active.getId()).with(authenticatedAs(users.userA())))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("REQUEST_REJECTED"));
            mvc.perform(put("/api/subtopics/{id}",archived.getId()).with(authenticatedAs(users.userA())).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"  Exact  \",\"sortOrder\":9,\"topicId\":\""+foreignTopic.getId()+"\",\"status\":\"ACTIVE\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ARCHIVED"))
                    .andExpect(jsonPath("$.topicId").value(active.getId().toString()));
            mvc.perform(put("/api/subtopics/{id}",foreignSub.getId()).with(authenticatedAs(users.userA())).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Attack\"}"))
                    .andExpect(notFoundWithoutForeignData("Foreign subtopic secret"));
            mvc.perform(post("/api/subtopics/{id}/archive",foreignSub.getId())
                            .with(authenticatedAs(users.userA())).with(csrf()))
                    .andExpect(notFoundWithoutForeignData("Foreign subtopic secret"));
            assertThat(subs.findById(foreignSub.getId()).orElseThrow().getName()).isEqualTo("Foreign subtopic secret");
            assertThat(subs.findById(foreignSub.getId()).orElseThrow().getStatus())
                    .isEqualTo(LearningOrganizationStatus.ACTIVE);
            active.setStatus(LearningOrganizationStatus.ARCHIVED); topics.saveAndFlush(active);
            mvc.perform(put("/api/subtopics/{id}",z.getId()).with(authenticatedAs(users.userA())).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Blocked\"}"))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SUBTOPIC_NOT_FOUND"));
            owned.setStatus(LearningOrganizationStatus.ARCHIVED); subjects.saveAndFlush(owned);
            for(int i=0;i<2;i++) mvc.perform(post("/api/subtopics/{id}/archive",z.getId()).with(authenticatedAs(users.userA())).with(csrf()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ARCHIVED"));
            assertThat(subs.findById(a.getId()).orElseThrow().getStatus()).isEqualTo(LearningOrganizationStatus.ACTIVE);
        }
    }

    @Test void securityContractRequiresAuthenticationAndCsrfAndConcealsForeignSubtopicExistence() throws Exception {
        try(var context=startApplicationWithFlyway()) {
            var users=users(context,"subtopic-security");
            var subjects=context.getBean(SpringDataSubjectRepository.class);
            var topics=context.getBean(SpringDataTopicRepository.class);
            var subtopics=context.getBean(SpringDataSubtopicRepository.class);
            SubjectEntity owned=subject(subjects,users.userA().userId(),"Owned",LearningOrganizationStatus.ACTIVE);
            SubjectEntity foreign=subject(subjects,users.userB().userId(),"Foreign subject",LearningOrganizationStatus.ACTIVE);
            TopicEntity ownedTopic=topic(topics,owned,"Owned topic",LearningOrganizationStatus.ACTIVE);
            TopicEntity foreignTopic=topic(topics,foreign,"Foreign topic",LearningOrganizationStatus.ACTIVE);
            SubtopicEntity ownedSubtopic=sub(subtopics,ownedTopic,"Owned subtopic",1,LearningOrganizationStatus.ACTIVE);
            SubtopicEntity foreignSubtopic=sub(subtopics,foreignTopic,"Foreign subtopic marker",1,LearningOrganizationStatus.ACTIVE);
            MockMvc mvc=mvc(context);

            mvc.perform(get("/api/topics/{id}/subtopics",ownedTopic.getId()))
                    .andExpect(status().isUnauthorized());
            mvc.perform(put("/api/subtopics/{id}",ownedSubtopic.getId())
                            .with(authenticatedAs(users.userA()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Missing CSRF\"}"))
                    .andExpect(status().isForbidden());

            MvcResult foreignResult=mvc.perform(put("/api/subtopics/{id}",foreignSubtopic.getId())
                            .with(authenticatedAs(users.userA())).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Denied\"}"))
                    .andExpect(notFoundWithoutForeignData("Foreign subtopic marker"))
                    .andExpect(jsonPath("$.code").value("SUBTOPIC_NOT_FOUND"))
                    .andExpect(jsonPath("$.message").value("Subtopic was not found."))
                    .andExpect(jsonPath("$.details").isMap())
                    .andReturn();
            MvcResult missingResult=mvc.perform(put("/api/subtopics/{id}",UUID.randomUUID())
                            .with(authenticatedAs(users.userA())).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Denied\"}"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("SUBTOPIC_NOT_FOUND"))
                    .andExpect(jsonPath("$.message").value("Subtopic was not found."))
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
    private static SubtopicEntity sub(SpringDataSubtopicRepository r,TopicEntity t,String n,Integer order,LearningOrganizationStatus st){SubtopicEntity s=new SubtopicEntity(t,n,st);s.setSortOrder(order);return r.saveAndFlush(s);}
}
