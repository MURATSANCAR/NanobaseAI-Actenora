package com.nanobaseai.actenora.identity.application.port;

import com.nanobaseai.actenora.identity.domain.User;
import com.nanobaseai.actenora.sharedkernel.domain.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepositoryPort {

    Optional<User> findById(UUID userId);

    Optional<User> findByEntraObjectId(String entraObjectId);

    List<User> listByTenant(TenantId tenantId);

    void save(User user);
}
