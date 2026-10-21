package com.hippocampus.learning.api;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.util.LinkedMultiValueMap;

class ContinuationContractVersionTests {
    @Test
    void acceptsOnlyOmissionOrExactlyOneDecodedVersionOne() {
        var query = new LinkedMultiValueMap<String, String>();
        assertThat(ContinuationContractVersion.parse(query)).isEqualTo(ContinuationContractVersion.LEGACY);
        query.add("continuationContract", "1");
        assertThat(ContinuationContractVersion.parse(query)).isEqualTo(ContinuationContractVersion.ONE);
        query.add("continuationContract", "1");
        assertThat(ContinuationContractVersion.parse(query)).isEqualTo(ContinuationContractVersion.INVALID);
        query.set("continuationContract", "");
        assertThat(ContinuationContractVersion.parse(query)).isEqualTo(ContinuationContractVersion.INVALID);
        query.set("continuationContract", "1,0");
        assertThat(ContinuationContractVersion.parse(query)).isEqualTo(ContinuationContractVersion.INVALID);
    }
}
