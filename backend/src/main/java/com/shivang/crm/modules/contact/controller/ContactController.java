package com.shivang.crm.modules.contact.controller;

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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shivang.crm.modules.activity.dto.ActivityResponse;
import com.shivang.crm.modules.activity.service.ActivityService;
import com.shivang.crm.modules.auth.security.TenantContext;
import com.shivang.crm.modules.contact.dto.ContactCreateRequest;
import com.shivang.crm.modules.contact.dto.ContactResponse;
import com.shivang.crm.modules.contact.dto.ContactUpdateRequest;
import com.shivang.crm.modules.contact.service.ContactService;
import com.shivang.crm.modules.lead.service.EntityNoteService;
import com.shivang.crm.shared.dto.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/contacts")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Contacts", description = "Contact Management APIs")
public class ContactController {

    private final ContactService contactService;
    private final TenantContext tenantContext;
    private final EntityNoteService entityNoteService;
    private final ActivityService activityService;

    @PostMapping
    @Operation(summary = "Create contact", description = "Create a new contact")
    public ResponseEntity<ApiResponse<ContactResponse>> createContact(
            @RequestBody ContactCreateRequest request) {

        log.info("POST /api/v1/contacts - Creating contact");
        UUID tenantId = currentTenantId();
        UUID userId = currentUserId();

        ContactResponse contactResponse = contactService.createContact(tenantId, userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(contactResponse));
    }

    @GetMapping
    @Operation(summary = "List contacts", description = "Get contacts with filtering and pagination")
    public ResponseEntity<ApiResponse<java.util.List<ContactResponse>>> listContacts(
            @Parameter(description = "Search term") @RequestParam(required = false) String search,
            @Parameter(description = "Owner user UUID") @RequestParam(required = false) UUID owner,
            @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {

        log.info("GET /api/v1/contacts - Listing contacts");
        UUID tenantId = currentTenantId();
        Page<ContactResponse> contacts = contactService.listContacts(tenantId, owner, search, page, size);

        Map<String, Object> meta = Map.of(
            "page", contacts.getNumber(),
            "size", contacts.getSize(),
            "total", contacts.getTotalElements(),
            "totalPages", contacts.getTotalPages()
        );

        return ResponseEntity.ok(ApiResponse.success(contacts.getContent(), meta));
    }

    @GetMapping("/search")
    @Operation(summary = "Search contacts", description = "Search for existing contacts by first name, last name, email, or phone")
    public ResponseEntity<ApiResponse<java.util.List<ContactResponse>>> searchContacts(
            @Parameter(description = "Search query") @RequestParam(required = false, name = "q") String query) {

        log.info("GET /api/v1/contacts/search - Searching contacts");
        UUID tenantId = currentTenantId();
        java.util.List<ContactResponse> contacts = contactService.searchContacts(tenantId, query, 20);

        return ResponseEntity.ok(ApiResponse.success(contacts));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get contact details", description = "Get details for a specific contact")
    public ResponseEntity<ApiResponse<ContactResponse>> getContact(@PathVariable UUID id) {
        log.info("GET /api/v1/contacts/{} - Getting contact", id);
        UUID tenantId = currentTenantId();
        ContactResponse contactResponse = contactService.getContactById(id, tenantId);
        return ResponseEntity.ok(ApiResponse.success(contactResponse));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update contact", description = "Update an existing contact")
    public ResponseEntity<ApiResponse<ContactResponse>> updateContact(
            @PathVariable UUID id,
            @RequestBody ContactUpdateRequest request) {

        log.info("PUT /api/v1/contacts/{} - Updating contact", id);
        UUID tenantId = currentTenantId();
        UUID userId = currentUserId();
        ContactResponse contactResponse = contactService.updateContact(id, tenantId, userId, request);
        return ResponseEntity.ok(ApiResponse.success(contactResponse));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete contact", description = "Delete a contact")
    public ResponseEntity<ApiResponse<String>> deleteContact(@PathVariable UUID id) {
        log.info("DELETE /api/v1/contacts/{} - Deleting contact", id);
        UUID tenantId = currentTenantId();
        UUID userId = currentUserId();
        contactService.deleteContact(id, tenantId, userId);
        return ResponseEntity.ok(ApiResponse.success("Contact deleted successfully"));
    }

    @GetMapping("/{id}/activities")
    @Operation(summary = "Get contact activities", description = "Get activities for a specific contact")
    public ResponseEntity<ApiResponse<java.util.List<ActivityResponse>>> getActivities(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.info("GET /api/v1/contacts/{}/activities - Getting activities", id);
        UUID tenantId = currentTenantId();
        Page<ActivityResponse> activities = activityService.getEntityActivities(id, "CONTACT", tenantId, page, size);

        Map<String, Object> meta = Map.of(
            "page", activities.getNumber(),
            "size", activities.getSize(),
            "total", activities.getTotalElements(),
            "totalPages", activities.getTotalPages()
        );

        return ResponseEntity.ok(ApiResponse.success(activities.getContent(), meta));
    }

    @GetMapping("/{id}/notes")
    @Operation(summary = "Get contact notes", description = "Get notes for a specific contact")
    public ResponseEntity<ApiResponse<java.util.List<com.shivang.crm.modules.lead.dto.EntityNoteResponse>>> getNotes(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.info("GET /api/v1/contacts/{}/notes - Getting notes", id);
        UUID tenantId = currentTenantId();
        Page<com.shivang.crm.modules.lead.dto.EntityNoteResponse> notes = entityNoteService.getEntityNotes(id, "CONTACT", tenantId, page, size);

        Map<String, Object> meta = Map.of(
            "page", notes.getNumber(),
            "size", notes.getSize(),
            "total", notes.getTotalElements(),
            "totalPages", notes.getTotalPages()
        );

        return ResponseEntity.ok(ApiResponse.success(notes.getContent(), meta));
    }

    @PostMapping("/{id}/notes")
    @Operation(summary = "Add contact note", description = "Add a note to a specific contact")
    public ResponseEntity<ApiResponse<com.shivang.crm.modules.lead.dto.EntityNoteResponse>> addNote(
            @PathVariable UUID id,
            @RequestBody Map<String, String> request) {

        log.info("POST /api/v1/contacts/{}/notes - Adding note", id);
        UUID tenantId = currentTenantId();
        UUID userId = currentUserId();
        String noteText = request.get("note");
        com.shivang.crm.modules.lead.dto.EntityNoteResponse noteResponse = entityNoteService.addEntityNote(id, "CONTACT", tenantId, noteText, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(noteResponse));
    }

    @DeleteMapping("/{id}/notes/{noteId}")
    @Operation(summary = "Delete contact note", description = "Delete a note from a contact")
    public ResponseEntity<ApiResponse<String>> deleteNote(
            @PathVariable UUID id,
            @PathVariable UUID noteId) {

        log.info("DELETE /api/v1/contacts/{}/notes/{} - Deleting note", id, noteId);
        UUID tenantId = currentTenantId();
        UUID userId = currentUserId();
        entityNoteService.deleteEntityNote(noteId, id, "CONTACT", tenantId, userId);
        return ResponseEntity.ok(ApiResponse.success("Note deleted successfully"));
    }

    private UUID currentTenantId() {
        return tenantContext.getTenantId(); 
    }

    private UUID currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new IllegalStateException("User authentication is not available");
        }
        return UUID.fromString(authentication.getPrincipal().toString());
    }
}
