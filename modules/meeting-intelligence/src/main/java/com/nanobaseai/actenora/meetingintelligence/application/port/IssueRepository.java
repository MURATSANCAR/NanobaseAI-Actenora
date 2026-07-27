package com.nanobaseai.actenora.meetingintelligence.application.port;

import com.nanobaseai.actenora.meetingintelligence.domain.model.Issue;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IssueRepository {

    Issue save(Issue issue);

    Optional<Issue> findByIdAndTenantId(UUID id, TenantId tenantId);

    List<Issue> findByNoteId(UUID noteId, TenantId tenantId);
}
