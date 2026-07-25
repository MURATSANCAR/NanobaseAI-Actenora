package com.nanobaseai.actenora.template.infrastructure.persistence;

import com.nanobaseai.actenora.sharedkernel.domain.TenantId;
import com.nanobaseai.actenora.template.application.port.out.NoteTemplateLockRepository;
import com.nanobaseai.actenora.template.domain.NoteTemplateLock;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryNoteTemplateLockRepository implements NoteTemplateLockRepository {

    private final Map<String, NoteTemplateLock> locks = new ConcurrentHashMap<>();

    @Override
    public void save(NoteTemplateLock lock) {
        locks.put(key(lock.tenantId(), lock.noteId()), lock);
    }

    @Override
    public Optional<NoteTemplateLock> find(TenantId tenantId, UUID noteId) {
        return Optional.ofNullable(locks.get(key(tenantId, noteId)));
    }

    private static String key(TenantId tenantId, UUID noteId) {
        return tenantId.value() + ":" + noteId;
    }
}
