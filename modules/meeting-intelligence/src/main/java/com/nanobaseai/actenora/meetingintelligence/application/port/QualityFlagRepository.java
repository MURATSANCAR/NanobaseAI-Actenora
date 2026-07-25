package com.nanobaseai.actenora.meetingintelligence.application.port;

import com.nanobaseai.actenora.meetingintelligence.domain.model.QualityFlag;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.List;
import java.util.UUID;

public interface QualityFlagRepository {

    QualityFlag save(QualityFlag flag);

    List<QualityFlag> findByNoteId(UUID noteId, TenantId tenantId);
}
