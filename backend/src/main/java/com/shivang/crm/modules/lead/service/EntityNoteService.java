package com.shivang.crm.modules.lead.service;

import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.account.repository.AccountRepository;
import com.shivang.crm.modules.contact.repository.ContactRepository;
import com.shivang.crm.modules.deal.repository.DealRepository;
import com.shivang.crm.modules.lead.dto.EntityNoteResponse;
import com.shivang.crm.modules.lead.entity.EntityNote;
import com.shivang.crm.modules.lead.entity.Lead;
import com.shivang.crm.modules.lead.mapper.EntityNoteMapper;
import com.shivang.crm.modules.lead.repository.EntityNoteRepository;
import com.shivang.crm.modules.lead.repository.LeadRepository;
import com.shivang.crm.modules.activity.service.ActivityService;
import com.shivang.crm.shared.exception.BusinessException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class EntityNoteService {

    private final EntityNoteRepository entityNoteRepository;
    private final LeadRepository leadRepository;
    private final AccountRepository accountRepository;
    private final ContactRepository contactRepository;
    private final DealRepository dealRepository;
    private final EntityNoteMapper entityNoteMapper;
    private final ActivityService activityService;

    /**
     * Add a note to a lead
     */
    public EntityNoteResponse addEntityNote(UUID entityId, String entityType, UUID tenantId, String noteText, UUID userId) {
        log.info("Adding note for entity {} of type {} for tenant {}", entityId, entityType, tenantId);

        validateEntityExists(entityId, entityType, tenantId);

        EntityNote note = EntityNote.builder()
            .tenantId(tenantId)
            .entityId(entityId)
            .entityType(entityType)
            .note(noteText)
            .createdBy(userId)
            .updatedBy(userId)
            .build();

        EntityNote savedNote = entityNoteRepository.save(note);

        activityService.logActivity(
            tenantId, entityId, entityType, "NOTE_ADDED", "Note added: " + noteText, userId, Map.of()
        );

        return entityNoteMapper.toResponse(savedNote);
    }

    /**
     * Get notes for a lead with pagination
     */
    @Transactional(readOnly = true)
    public Page<EntityNoteResponse> getEntityNotes(UUID entityId,  String entityType, UUID tenantId, int page, int size) {
        log.info("Fetching notes for entity: {} of type {} for tenant: {}", entityId, entityType, tenantId);

        Pageable pageable = PageRequest.of(page, size);
        Page<EntityNote> notes = entityNoteRepository.findByEntityIdAndEntityTypeAndTenantId(entityId, entityType, tenantId, pageable);

        return notes.map(entityNoteMapper::toResponse);
    }

    /**
     * Update a note
     */
    public EntityNoteResponse updateNote(UUID noteId, UUID entityId,  String entityType, UUID tenantId, String noteText, UUID userId) {
        log.info("Updating note: {} for entity: {} of type {} for tenant: {}", noteId, entityId, entityType,  tenantId);

        EntityNote note = entityNoteRepository.findById(noteId)
            .orElseThrow(() -> new RuntimeException("Note not found"));

        if (!note.getTenantId().equals(tenantId) || !note.getEntityId().equals(entityId)) {
            throw new RuntimeException("Unauthorized access to note");
        }

        note.setNote(noteText);
        note.setUpdatedBy(userId);

        EntityNote updatedNote = entityNoteRepository.save(note);
        return entityNoteMapper.toResponse(updatedNote);
    }

    /**
     * Delete a note without entity type check (legacy support)
     */
    public void deleteNote(UUID noteId, UUID entityId,  String entityType, UUID tenantId, UUID userId) {
        log.info("Deleting note: {} for entity: {} of type {} for tenant: {}", noteId, entityId, entityType,  tenantId);

        EntityNote note = entityNoteRepository.findById(noteId)
            .orElseThrow(() -> new RuntimeException("Note not found"));

        if (!note.getTenantId().equals(tenantId) || !note.getEntityId().equals(entityId)) {
            throw new RuntimeException("Unauthorized access to note");
        }

        entityNoteRepository.delete(note);

        activityService.logActivity(
            tenantId, entityId, entityType, "NOTE_DELETED", "Note deleted", userId, Map.of("noteId", noteId)
        );
    }

    /**
     * Count notes for a lead
     */
    @Transactional(readOnly = true)
    public Integer countNotesForLead(UUID entityId, UUID tenantId) {
        return entityNoteRepository.countByEntityIdAndTenantId(entityId, tenantId);
    }

    public void deleteEntityNote(UUID noteId, UUID entityId, String entityType, UUID tenantId, UUID userId) {
        log.info("Deleting note {} for entity {} of type {} for tenant {}", noteId, entityId, entityType, tenantId);

        EntityNote note = entityNoteRepository.findById(noteId)
            .orElseThrow(() -> new RuntimeException("Note not found"));

        if (!note.getTenantId().equals(tenantId) || !note.getEntityId().equals(entityId) || !note.getEntityType().equals(entityType)) {
            throw new RuntimeException("Unauthorized access to note");
        }

        entityNoteRepository.delete(note);

        activityService.logActivity(
            tenantId, entityId, entityType, "NOTE_DELETED", "Note deleted", userId, Map.of("noteId", noteId)
        );
    }

    private void validateEntityExists(UUID entityId, String entityType, UUID tenantId) {
        switch (entityType) {
            case "LEAD" -> leadRepository.findByIdAndTenantId(entityId, tenantId)
                    .orElseThrow(() -> new BusinessException("NOT_FOUND", "Lead not found"));
            case "ACCOUNT" -> accountRepository.findByIdAndTenantId(entityId, tenantId)
                    .orElseThrow(() -> new BusinessException("NOT_FOUND", "Account not found"));
            case "CONTACT" -> contactRepository.findByIdAndTenantId(entityId, tenantId)
                    .orElseThrow(() -> new BusinessException("NOT_FOUND", "Contact not found"));
            case "DEAL" -> dealRepository.findByIdAndTenantId(entityId, tenantId)
                    .orElseThrow(() -> new BusinessException("NOT_FOUND", "Deal not found"));
            default -> throw new BusinessException("INVALID_ENTITY_TYPE", "Unsupported entity type: " + entityType);
        }
    }
}
