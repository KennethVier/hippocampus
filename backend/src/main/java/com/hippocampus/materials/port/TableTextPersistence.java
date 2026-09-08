package com.hippocampus.materials.port;

import java.util.UUID;

import com.hippocampus.materials.domain.TableTextDraft;

public interface TableTextPersistence {
    void persistOrVerify(UUID materialVersionId, int pageCount, TableTextDraft table);
    void finalizeExtraction(UUID materialVersionId, int pageCount, int expectedTableCount);
}
