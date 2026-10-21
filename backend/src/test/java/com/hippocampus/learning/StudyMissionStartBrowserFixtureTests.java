package com.hippocampus.learning;

import com.hippocampus.bootstrap.*;
import com.hippocampus.identity.infrastructure.security.HippocampusPrincipal;
import com.hippocampus.identity.port.CurrentUser;
import com.hippocampus.learning.application.*;
import com.hippocampus.learning.port.*;
import com.hippocampus.materials.application.*;
import com.hippocampus.materials.port.BinaryObjectStore;
import com.hippocampus.materials.infrastructure.storage.filesystem.FileSystemBinaryObjectStore;
import com.hippocampus.rag.application.*;
import com.hippocampus.rag.port.*;
import com.hippocampus.testing.PostgresIntegrationTestSupport;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit operator-only HTTP/PostgreSQL fixture. Synthetic structure only, never medical/AI qualification. */
@EnabledIfSystemProperty(named="hippocampus.p714.browser-fixture",matches="true")
class StudyMissionStartBrowserFixtureTests {
    @Test void serveUntilPrivateStopRecord() throws Exception {
        refuseLiveConfiguration();
        Path directory=Path.of(Objects.requireNonNull(System.getProperty("hippocampus.p714.evidence"))).toAbsolutePath();
        if(!Files.isDirectory(directory) || Files.isSymbolicLink(directory)
                || Files.exists(directory.resolve("ready.json")) || Files.exists(directory.resolve("stop")))
            throw new IllegalStateException("Fresh private fixture evidence directory required");
        // The container superclass initializes only after all preflight checks above.
        Database database=new Database();
        assertTrue(InetAddress.getByName(Database.host()).isLoopbackAddress(),"Fixture database must be loopback");
        Database.reset();
        try(var context=Database.start()) {
            var environment=context.getEnvironment();
            assertEquals("false",environment.getProperty("hippocampus.ai.providers.gemini.enabled"));
            assertEquals("false",environment.getProperty("hippocampus.ai.providers.ollama-cloud.enabled"));
            assertEquals("false",environment.getProperty("hippocampus.rag.embedding.gemini.enabled"));
            assertTrue(context.getBean(ActivityAiTaskPort.class) instanceof SyntheticActivity);
            assertSame(context.getBean("syntheticEmbedding"),context.getBean(EmbeddingPort.class));
            var jdbc=context.getBean(JdbcClient.class);
            UUID owner=UUID.randomUUID(),subject=UUID.randomUUID(),topic=UUID.randomUUID();
            String email=owner+"@example.test",password="Synthetic-only-"+UUID.randomUUID();
            jdbc.sql("INSERT INTO users(id,email,status,created_at,updated_at) VALUES (:id,:email,'ACTIVE',now(),now())").param("id",owner).param("email",email).update();
            jdbc.sql("INSERT INTO user_password_credentials(user_id,password_hash,created_at,updated_at) VALUES (:id,:hash,now(),now())")
                .param("id",owner).param("hash",context.getBean(PasswordEncoder.class).encode(password)).update();
            jdbc.sql("INSERT INTO subjects(id,user_id,name,status,created_at,updated_at) VALUES (:id,:owner,'Synthetic subject','ACTIVE',now(),now())").param("id",subject).param("owner",owner).update();
            jdbc.sql("INSERT INTO topics(id,subject_id,name,status,created_at,updated_at) VALUES (:id,:subject,'Synthetic topic','ACTIVE',now(),now())").param("id",topic).param("subject",subject).update();
            SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(new HippocampusPrincipal(owner,email),null,List.of()));
            MaterialUploadResult uploaded;
            try {
                byte[] pdf=syntheticPdf();
                uploaded=context.getBean(UploadMaterial.class).execute(new UploadMaterial.Command("Synthetic concept.pdf","application/pdf",pdf.length,()->new ByteArrayInputStream(pdf)));
                var claims=context.getBean(ClaimNextProcessingJob.class);var execution=context.getBean(ExecuteClaimedProcessingJob.class);
                int stages=0;
                while(stages++<30) {var job=claims.execute("p714-browser-fixture");if(job.isEmpty())break;execution.execute(job.orElseThrow());}
                assertTrue(stages<=30,"Processing did not quiesce");
            } finally { SecurityContextHolder.clearContext(); }
            String status=jdbc.sql("SELECT status FROM materials WHERE id=:id").param("id",uploaded.materialId()).query(String.class).single();
            assertTrue(Set.of("READY","PARTIALLY_READY").contains(status),"Actual processing must produce eligible material");
            assertTrue(jdbc.sql("SELECT count(*) FROM chunks WHERE material_version_id=:id AND is_active").param("id",uploaded.versionId()).query(Integer.class).single()>0);
            jdbc.sql("INSERT INTO material_topic_links(id,topic_id,material_id,link_origin,status,created_at,updated_at) VALUES (:id,:topic,:material,'USER_SELECTED','ACTIVE',now(),now())")
                .param("id",UUID.randomUUID()).param("topic",topic).param("material",uploaded.materialId()).update();
            int port=((WebServerApplicationContext)context).getWebServer().getPort();
            var record=new LinkedHashMap<String,Object>();
            record.put("schemaVersion",1);record.put("apiBaseUrl","http://127.0.0.1:"+port);record.put("frontendBaseUrl","http://127.0.0.1:4175");
            record.put("ownerId",owner);record.put("topicId",topic);record.put("materialId",uploaded.materialId());record.put("versionId",uploaded.versionId());
            record.put("email",email);record.put("password",password);record.put("syntheticOnly",true);record.put("processedMaterialStatus",status);
            Files.writeString(directory.resolve("ready.json"),new ObjectMapper().writeValueAsString(record),StandardOpenOption.CREATE_NEW);
            Instant limit=Instant.now().plusSeconds(600);
            while(!Files.exists(directory.resolve("stop")) && Instant.now().isBefore(limit))Thread.sleep(100);
            assertTrue(Files.exists(directory.resolve("stop")),"Supervisor did not complete within ten minutes");
            // Acceptance checks against the actual database after HTTP/browser writes, never mocked responses.
            var counts=jdbc.sql("SELECT count(*) FROM study_missions WHERE user_id=:id").param("id",owner).query(Integer.class).single();
            assertEquals(8,counts,"Four structural journeys on desktop and mobile must create eight real missions");
            assertEquals(4,jdbc.sql("SELECT count(*) FROM study_mission_start_receipts WHERE owner_id=:id AND state='ACTIVITY_CREATED'").param("id",owner).query(Integer.class).single());
            assertEquals(2,jdbc.sql("SELECT count(*) FROM study_mission_start_receipts WHERE owner_id=:id AND state='NO_ACTIVITY' AND attempts_used=0 AND accepted_activity_id IS NULL").param("id",owner).query(Integer.class).single());
            assertEquals(0,jdbc.sql("SELECT count(*) FROM mission_materials mm JOIN study_missions sm ON sm.id=mm.study_mission_id WHERE sm.user_id=:owner AND (mm.material_id<>:material OR mm.material_version_id<>:version)")
                .param("owner",owner).param("material",uploaded.materialId()).param("version",uploaded.versionId()).query(Integer.class).single());
            assertEquals(0,jdbc.sql("SELECT count(*) FROM study_mission_start_receipts r JOIN learning_activities a ON a.study_mission_id=r.mission_id WHERE r.owner_id=:id AND r.accepted_activity_id<>a.id")
                .param("id",owner).query(Integer.class).single());
            Files.writeString(directory.resolve("database-acceptance.json"),new ObjectMapper().writeValueAsString(Map.of("missionCount",counts,"structuralOnly",true,"checkedAt",Instant.now().toString())),StandardOpenOption.CREATE_NEW);
        }
    }
    private static void refuseLiveConfiguration() {
        for(String key:List.of("GEMINI_API_KEY","OLLAMA_API_KEY","HIPPOCAMPUS_DATASOURCE_URL","SPRING_DATASOURCE_URL"))
            if(System.getenv(key)!=null && !System.getenv(key).isBlank())throw new IllegalStateException("Remove external configuration before fixture: "+key);
        for(String key:List.of("HIPPOCAMPUS_GEMINI_AI_ENABLED","HIPPOCAMPUS_OLLAMA_CLOUD_ENABLED","HIPPOCAMPUS_GEMINI_EMBEDDING_ENABLED"))
            if(System.getenv(key)!=null && !System.getenv(key).equalsIgnoreCase("false"))throw new IllegalStateException("Live provider must be disabled: "+key);
    }
    private static byte[] syntheticPdf() throws IOException {
        try(var doc=new PDDocument();var output=new ByteArrayOutputStream()) {
            var page=new PDPage();doc.addPage(page);
            try(var content=new PDPageContentStream(doc,page)) {
                content.beginText();content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA),12);content.newLineAtOffset(45,740);
                for(int i=0;i<14;i++){content.showText("Synthetic concept: blue markers group by shape. This is a nonmedical structural fixture.");content.newLineAtOffset(0,-20);}
                content.endText();
            }
            doc.save(output);return output.toByteArray();
        }
    }
    static final class Database extends PostgresIntegrationTestSupport {
        static String host(){return postgresHost();}
        static void reset() throws Exception {resetPostgresSchema();}
        static org.springframework.context.ConfigurableApplicationContext start(){return startApplicationWithFlywayAndArguments(new Class<?>[]{FakeAdapters.class},
            "--server.address=127.0.0.1","--hippocampus.security.cors.allowed-origins=http://127.0.0.1:4175",
            "--hippocampus.materials.processing.recovery.enabled=false",
            "--hippocampus.ai.providers.gemini.enabled=false","--hippocampus.ai.providers.gemini.api-key=",
            "--hippocampus.ai.providers.ollama-cloud.enabled=false","--hippocampus.ai.providers.ollama-cloud.api-key=",
            "--hippocampus.rag.embedding.gemini.enabled=false","--hippocampus.rag.embedding.gemini.api-key=");}
    }
    @TestConfiguration(proxyBeanMethods=false)
    static class FakeAdapters {
        @Bean BinaryObjectStore fixtureObjects() throws IOException {return new FileSystemBinaryObjectStore(Files.createTempDirectory("hippocampus-p714-objects-"));}
        @Bean SyntheticActivity syntheticActivity(){return new SyntheticActivity();}
        @Bean EmbeddingPort syntheticEmbedding(){return request->{
            var values=new ArrayList<Float>(Collections.nCopies(768,0F));values.set(0,1F);
            return new EmbeddingBatchResult(new EmbeddingModelMetadata("SYNTHETIC","p714-test","1",768),EmbeddingUsageMetadata.unavailable(),
                request.inputs().stream().map(input->new EmbeddingVectorResult(input.referenceId(),new EmbeddingVector(values))).toList());};}
        @Bean ActivityEvidencePort fixtureEvidence(BuildRetrievalScope catalog,LexicalSearchRepository lexical,VectorSearchRepository vector,
                ActiveIndexGenerationRepository generations,EmbeddingPort embeddings,BuildEvidencePackage packages,MaterializeEvidenceSourceReferences references,
                CurrentUser user,StudyMissionRepository missions,StudyMissionStartReceiptRepository receipts,InitialStudyMissionSourceAuthority sources) {
            return new LearningActivityEvidenceAdapter(catalog,lexical,vector,generations,Optional.of(embeddings),packages,references,
                new ActivityEvidenceRetrievalOptions(12,12,12,2400,8,3),user,missions,receipts,sources);
        }
        @Bean StartStudyMissionExperienceUseCase fixtureStart(CurrentUser user,StudyMissionStartTransactions tx,MaterializeLearningActivityUseCase materialize) {
            return new StartStudyMissionExperienceUseCase(user,tx,materialize,()->true);
        }
    }
    static final class SyntheticActivity implements ActivityAiTaskPort {
        @Override public ValidatedContent execute(Request request){return synthetic(request,null);}
        private ValidatedContent synthetic(Request request,PreparationDeadline deadline){
            if(request.actionType()!=com.hippocampus.learning.domain.LearningActionType.UNDERSTAND)throw new IllegalStateException("Fixture supports initial explanation only");
            assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive(),"No provider call inside transaction");
            assertFalse(request.evidence().sourceReferenceIds().isEmpty());
            if(request.objective().startsWith("P714-HOLD:")) {
                UUID control=UUID.fromString(request.objective().substring("P714-HOLD:".length()));
                Path directory=Path.of(System.getProperty("hippocampus.p714.evidence"));
                Path entered=directory.resolve("hold-"+control+".entered"),release=directory.resolve("hold-"+control+".release");
                try {
                    Files.writeString(entered,"synthetic-provider-entered",StandardOpenOption.CREATE_NEW);
                    Instant until=Instant.now().plusSeconds(120);
                    while(!Files.exists(release) && Instant.now().isBefore(until)) {
                        if(deadline!=null)deadline.check();Thread.sleep(20);
                    }
                    if(!Files.exists(release))throw new IllegalStateException("Synthetic provider was not released");
                } catch(IOException failure) {throw new IllegalStateException("Private fixture control failed",failure);}
                catch(InterruptedException interrupted) {Thread.currentThread().interrupt();throw new java.util.concurrent.CancellationException("Synthetic provider interrupted");}
            }
            String payload=new ObjectMapper().writeValueAsString(Map.of("concept","Synthetic concept","explanation","Blue markers group by shape.",
                "keyPoints",List.of("Group markers by shape."),"prerequisitesUsed",List.of(),
                "sourceReferences",request.evidence().sourceReferenceIds().stream().map(UUID::toString).toList(),"supplementalKnowledgeUsed",false,"limitations",List.of()));
            return new ValidatedContent("EXPLANATION","EXPLANATION","Blue markers group by shape.",payload,request.groundingMode().name(),
                "SOURCE_GROUNDED_GENERATED","EXPLANATION_V2","2","SYNTHETIC","p714-test","1",ValidationStatus.VALIDATED,false);
        }
        @Override public ValidatedContent execute(Request request,PreparationDeadline deadline){deadline.check();return synthetic(request,deadline);}
    }
}
