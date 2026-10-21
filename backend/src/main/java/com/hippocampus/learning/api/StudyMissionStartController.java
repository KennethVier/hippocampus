package com.hippocampus.learning.api;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hippocampus.learning.application.StartStudyMissionExperienceUseCase;
import com.hippocampus.learning.application.StartStudyMissionUseCase;
import com.hippocampus.learning.application.StudyMissionStartException;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.UnsupportedMediaTypeStatusException;

@RestController
@ConditionalOnBean(StartStudyMissionExperienceUseCase.class)
public class StudyMissionStartController {
    private final StartStudyMissionExperienceUseCase experience;
    private final StartStudyMissionUseCase sources;
    private final ObjectMapper strict=new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    public StudyMissionStartController(StartStudyMissionExperienceUseCase experience,StartStudyMissionUseCase sources) {
        this.experience=experience;this.sources=sources;
    }

    @PostMapping(value="/api/study-missions",consumes="application/json",produces="application/json")
    ResponseEntity<?> start(HttpServletRequest request) {
        JsonNode body=body(request,16384);
        fields(body,Set.of("startRequestId","topicId","learningObjectiveText","availableTimeMinutes","groundingMode","sourceScopes"));
        if(!"STRICT_SOURCE".equals(text(body,"groundingMode"))) throw malformed();
        JsonNode minutes=body.get("availableTimeMinutes"),scopes=body.get("sourceScopes");
        if(!minutes.isIntegralNumber() || !minutes.canConvertToInt() || !scopes.isArray()
                || scopes.isEmpty() || scopes.size()>8) throw malformed();
        var materials=new ArrayList<UUID>();
        for(JsonNode scope:scopes) { fields(scope,Set.of("materialId"));materials.add(id(text(scope,"materialId"))); }
        var command=new StartStudyMissionExperienceUseCase.Command(id(text(body,"startRequestId")),id(text(body,"topicId")),
                text(body,"learningObjectiveText"),minutes.intValue(),materials);
        return response(experience.startForClient(command),true);
    }

    @PostMapping(value="/api/study-missions/{missionId}/initial-activity",consumes="application/json",produces="application/json")
    ResponseEntity<?> retry(@PathVariable String missionId,HttpServletRequest request) {
        JsonNode body=body(request,128);fields(body,Set.of("startRequestId"));
        return response(experience.retryForClient(id(missionId),id(text(body,"startRequestId"))),false);
    }

    @GetMapping("/api/topics/{topicId}/study-mission-sources")
    ResponseEntity<?> sources(@PathVariable String topicId,@RequestParam MultiValueMap<String,String> query) {
        if(query.keySet().stream().anyMatch(k->!Set.of("limit","afterMaterialId").contains(k))
                || query.values().stream().anyMatch(v->v.size()!=1)) throw malformed();
        int limit=20;
        if(query.containsKey("limit")) {
            String raw=query.getFirst("limit");
            if(raw==null || !raw.matches("[1-9][0-9]?")) throw malformed();
            limit=Integer.parseInt(raw);if(limit>50) throw malformed();
        }
        UUID after=query.containsKey("afterMaterialId")?id(query.getFirst("afterMaterialId")):null;
        var result=sources.eligibleForClient(id(topicId),limit,after);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
    }

    static ResponseEntity<?> response(StartStudyMissionExperienceUseCase.ClientResult result,boolean start) {
        Map<String,Object> body=new LinkedHashMap<>();
        body.put("missionId",result.missionId());body.put("startRequestId",result.startRequestId());
        body.put("missionStatus",result.missionStatus());body.put("replayed",result.replayed());
        body.put("outcome",result.outcome());
        int status=200;
        switch(result.outcome()) {
            case "ACTIVITY_CREATED" -> {
                body.put("activityId",result.activityId());
                if(start && !result.replayed()) status=201;
            }
            case "PREPARING" -> { body.put("retryAfterSeconds",result.retryAfterSeconds());status=202; }
            case "NO_ACTIVITY", "RECOVERY_REQUIRED" -> {
                if("NO_ACTIVITY".equals(result.outcome())) {
                    body.put("actionType",result.actionType());body.put("rationaleCode",result.rationaleCode());
                }
                body.put("reasonCode",result.reasonCode());body.put("canRetry",result.canRetry());
                body.put("retryAfterSeconds",result.retryAfterSeconds());
            }
            default -> throw new IllegalStateException("Unknown initial activity outcome");
        }
        var response=ResponseEntity.status(status).cacheControl(CacheControl.noStore())
                .location(URI.create("/api/study-missions/"+result.missionId()));
        if(status==202) response.header("Retry-After",result.retryAfterSeconds().toString());
        return response.body(body);
    }

    private JsonNode body(HttpServletRequest request,int maximum) {
        var encodings=Collections.list(request.getHeaders("Content-Encoding"));
        if(encodings.size()>1 || (!encodings.isEmpty() && !"identity".equalsIgnoreCase(encodings.getFirst())))
            throw new UnsupportedMediaTypeStatusException("Unsupported content encoding");
        try {
            byte[] bytes=request.getInputStream().readNBytes(maximum+1);
            if(bytes.length>maximum) throw new StudyMissionStartException(StudyMissionStartException.Kind.TOO_LARGE,null);
            String utf8=java.nio.charset.StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(bytes)).toString();
            JsonNode parsed=strict.readTree(utf8);
            if(parsed==null || !parsed.isObject()) throw malformed();
            return parsed;
        } catch(IOException invalid) { throw malformed(); }
    }
    static UUID id(String value) {
        if(value==null || !value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
                || value.equals("00000000-0000-0000-0000-000000000000")) throw malformed();
        return UUID.fromString(value);
    }
    private static String text(JsonNode node,String field) {
        JsonNode value=node.get(field);if(value==null || !value.isTextual()) throw malformed();return value.textValue();
    }
    private static void fields(JsonNode node,Set<String> expected) {
        if(!node.isObject()) throw malformed();
        Set<String> actual=new HashSet<>();node.fieldNames().forEachRemaining(actual::add);
        if(!actual.equals(expected)) throw malformed();
    }
    private static StudyMissionStartException malformed() { return new StudyMissionStartException(StudyMissionStartException.Kind.MALFORMED,null); }
}
