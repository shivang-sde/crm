package com.shivang.crm.modules.lead.controller;

import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shivang.crm.modules.auth.security.TenantContext;
import com.shivang.crm.modules.lead.dto.EntityHistoryResponse;
import com.shivang.crm.modules.lead.dto.EntityNoteResponse;
import com.shivang.crm.modules.lead.service.EntityHistoryService;
import com.shivang.crm.modules.lead.service.EntityNoteService;
import com.shivang.crm.shared.dto.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/leads/{leadId}")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Lead Activities & Notes", description = "Lead Activities and Notes Management APIs")
public class LeadEntityController {

    private final EntityHistoryService entityHistoryService;
    private final EntityNoteService entityNoteService;
    private final TenantContext tenantContext;

    /**
     * Get histories for a lead
     */
    @GetMapping("/histories")
    @Operation(summary = "Get lead histories", description = "Get all histories for a specific lead")
    public ResponseEntity<ApiResponse<java.util.List<EntityHistoryResponse>>> getHistories(
            @Parameter(description = "Lead UUID")
            @PathVariable UUID leadId,

            @Parameter(description = "Page number (0-indexed)")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "50") int size) {

        log.info("GET /api/v1/leads/{}/histories - Getting histories", leadId);

        UUID tenantId = currentTenantId();

        Page<EntityHistoryResponse> activities = entityHistoryService.getLeadHistories(leadId, tenantId, page, size);

        Map<String, Object> meta = Map.of(
            "page", activities.getNumber(),
            "size", activities.getSize(),
            "total", activities.getTotalElements()
        );

        return ResponseEntity.ok(ApiResponse.success(activities.getContent(), meta));
    }

    /**
     * Get notes for a lead
     */
    @GetMapping("/notes")
    @Operation(summary = "Get lead notes", description = "Get all notes for a specific lead")
    public ResponseEntity<ApiResponse<java.util.List<EntityNoteResponse>>> getNotes(
            @Parameter(description = "Lead UUID")
            @PathVariable UUID leadId,


            @Parameter(description = "Page number (0-indexed)")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "50") int size) {

        log.info("GET /api/v1/leads/{}/notes - Getting notes", leadId);

        UUID tenantId = currentTenantId();

        Page<EntityNoteResponse> notes = entityNoteService.getEntityNotes(leadId, "LEAD", tenantId, page, size);

        Map<String, Object> meta = Map.of(
            "page", notes.getNumber(),
            "size", notes.getSize(),
            "total", notes.getTotalElements()
        );

        return ResponseEntity.ok(ApiResponse.success(notes.getContent(), meta));
    }

    /**
     * Add a note to a lead
     */
    @PostMapping("/notes")
    @Operation(summary = "Add note to lead", description = "Add a new note to a specific lead")
    public ResponseEntity<ApiResponse<EntityNoteResponse>> addNote(
            @Parameter(description = "Lead UUID")
            @PathVariable UUID leadId,

            @RequestBody Map<String, String> request) {

        log.info("POST /api/v1/leads/{}/notes - Adding note", leadId);

        UUID tenantId = currentTenantId();
        UUID userId = currentUserId();
        String noteText = request.get("note");

        EntityNoteResponse noteResponse = entityNoteService.addEntityNote(leadId, "LEAD", tenantId, noteText, userId);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(noteResponse));
    }

    /**
     * Delete a note from a lead
     */
    @DeleteMapping("/notes/{noteId}")
    @Operation(summary = "Delete note", description = "Delete a note from a lead")
    public ResponseEntity<ApiResponse<String>> deleteNote(
            @Parameter(description = "Lead UUID")
            @PathVariable UUID leadId,

            @Parameter(description = "Note UUID")
            @PathVariable UUID noteId) {

        log.info("DELETE /api/v1/leads/{}/notes/{} - Deleting note", leadId, noteId);

        UUID tenantId = currentTenantId();
        UUID userId = currentUserId();

        entityNoteService.deleteEntityNote(noteId, leadId, "LEAD", tenantId, userId);

        return ResponseEntity.ok(ApiResponse.success("Note deleted successfully"));
    }

    private UUID currentTenantId() {
        String tenantId = tenantContext.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalStateException("Tenant context is not available");
        }
        return UUID.fromString(tenantId);
    }

    private UUID currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new IllegalStateException("User authentication is not available");
        }
        return UUID.fromString(authentication.getPrincipal().toString());
    }
}
