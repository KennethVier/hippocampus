package com.hippocampus.rag.application;

import com.hippocampus.materials.application.CreateSourceReferences;
import com.hippocampus.materials.domain.*;
import com.hippocampus.materials.port.SourceReferenceRepository;
import com.hippocampus.rag.domain.*;
import com.hippocampus.rag.port.FrozenMissionSourceBinding;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class MaterializeEvidenceSourceReferencesTests {
    @Test void initialBindingAndBothExactFrozenTargetsReachOnlyInitialBatch() {
        UUID material=UUID.randomUUID(),version=UUID.randomUUID(),chunk=UUID.randomUUID(),visual=UUID.randomUUID();
        var evidence=mock(EvidencePackage.class);
        when(evidence.sourceReferences()).thenReturn(List.of(
            new EvidenceSourceReference(EvidenceReferenceKind.CHUNK,material,version,null,chunk,null,1),
            new EvidenceSourceReference(EvidenceReferenceKind.VISUAL,material,version,null,null,visual,1)));
        var useCase=mock(CreateSourceReferences.class);
        var binding=new FrozenMissionSourceBinding(UUID.randomUUID(),3,UUID.randomUUID());
        var expected=new SourceReferenceRepository.FrozenMissionBinding(binding.missionId(),binding.generation(),binding.claimToken());
        when(useCase.executeForInitialMission(any(),eq(expected))).thenReturn(List.of());
        assertThat(new MaterializeEvidenceSourceReferences(useCase).execute(evidence,binding)).isEmpty();
        var capture=org.mockito.ArgumentCaptor.forClass(CreateSourceReferences.Command.class);
        verify(useCase).executeForInitialMission(capture.capture(),eq(expected));
        assertThat(capture.getValue().targets()).containsExactly(new ChunkSourceTarget(material,version,chunk),new VisualSourceTarget(material,version,visual));
        verifyNoMoreInteractions(useCase);
    }
    @Test void missingBindingNeverFallsBackToGeneralCreation() {
        var useCase=mock(CreateSourceReferences.class);
        assertThatThrownBy(()->new MaterializeEvidenceSourceReferences(useCase).execute(mock(EvidencePackage.class),null))
            .isInstanceOf(NullPointerException.class);
        verifyNoInteractions(useCase);
    }
}
