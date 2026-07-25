package com.nanobaseai.actenora.approval.application.port;

import com.nanobaseai.actenora.approval.domain.ParticipantDispute;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ParticipantDisputeRepository {

    ParticipantDispute save(ParticipantDispute dispute);

    Optional<ParticipantDispute> findById(UUID tenantId, UUID disputeId);

    List<ParticipantDispute> findBySubject(UUID tenantId, UUID subjectId);
}
