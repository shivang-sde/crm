package com.shivang.crm.modules.account.controller;

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

import com.shivang.crm.modules.account.dto.AccountCreateRequest;
import com.shivang.crm.modules.account.dto.AccountResponse;
import com.shivang.crm.modules.account.dto.AccountUpdateRequest;
import com.shivang.crm.modules.account.service.AccountService;
import com.shivang.crm.modules.activity.dto.ActivityResponse;
import com.shivang.crm.modules.activity.service.ActivityService;
import com.shivang.crm.modules.auth.security.TenantContext;
import com.shivang.crm.modules.contact.dto.ContactResponse;
import com.shivang.crm.modules.lead.service.EntityNoteService;
import com.shivang.crm.shared.dto.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Accounts", description = "Account Management APIs")
public class AccountController {

    private final AccountService accountService;
    private final TenantContext tenantContext;
    private final EntityNoteService entityNoteService;
    private final ActivityService activityService;

    @PostMapping
    @Operation(summary = "Create account", description = "Create a new account")
    public ResponseEntity<ApiResponse<AccountResponse>> createAccount(
            @RequestBody AccountCreateRequest request) {

        log.info("POST /api/v1/accounts - Creating account");

        UUID tenantId = currentTenantId();
        UUID userId = currentUserId();

        AccountResponse accountResponse = accountService.createAccount(tenantId, userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(accountResponse));
    }

    @GetMapping
    @Operation(summary = "List accounts", description = "Get accounts with optional filtering and pagination")
    public ResponseEntity<ApiResponse<java.util.List<AccountResponse>>> listAccounts(
            @Parameter(description = "Search term") @RequestParam(required = false) String search,
            @Parameter(description = "Owner user UUID") @RequestParam(required = false) UUID owner,
            @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {

        log.info("GET /api/v1/accounts - Listing accounts");

        UUID tenantId = currentTenantId();
        Page<AccountResponse> accounts = accountService.listAccounts(tenantId, owner, search, page, size);

        Map<String, Object> meta = Map.of(
            "page", accounts.getNumber(),
            "size", accounts.getSize(),
            "total", accounts.getTotalElements(),
            "totalPages", accounts.getTotalPages()
        );

        return ResponseEntity.ok(ApiResponse.success(accounts.getContent(), meta));
    }

    @GetMapping("/search")
    @Operation(summary = "Search accounts", description = "Search for existing accounts by name, email, or phone")
    public ResponseEntity<ApiResponse<java.util.List<AccountResponse>>> searchAccounts(
            @Parameter(description = "Search query") @RequestParam(required = false, name = "q") String query) {

        log.info("GET /api/v1/accounts/search - Searching accounts");

        UUID tenantId = currentTenantId();
        java.util.List<AccountResponse> accounts = accountService.searchAccounts(tenantId, query, 20);

        return ResponseEntity.ok(ApiResponse.success(accounts));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get account details", description = "Get details for a specific account")
    public ResponseEntity<ApiResponse<AccountResponse>> getAccount(@PathVariable UUID id) {
        log.info("GET /api/v1/accounts/{} - Getting account", id);
        UUID tenantId = currentTenantId();
        AccountResponse accountResponse = accountService.getAccountById(id, tenantId);
        return ResponseEntity.ok(ApiResponse.success(accountResponse));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update account", description = "Update an existing account")
    public ResponseEntity<ApiResponse<AccountResponse>> updateAccount(
            @PathVariable UUID id,
            @RequestBody AccountUpdateRequest request) {

        log.info("PUT /api/v1/accounts/{} - Updating account", id);
        UUID tenantId = currentTenantId();
        UUID userId = currentUserId();
        AccountResponse accountResponse = accountService.updateAccount(id, tenantId, userId, request);
        return ResponseEntity.ok(ApiResponse.success(accountResponse));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete account", description = "Delete an account")
    public ResponseEntity<ApiResponse<String>> deleteAccount(@PathVariable UUID id) {
        log.info("DELETE /api/v1/accounts/{} - Deleting account", id);
        UUID tenantId = currentTenantId();
        UUID userId = currentUserId();
        accountService.deleteAccount(id, tenantId, userId);
        return ResponseEntity.ok(ApiResponse.success("Account deleted successfully"));
    }

    @GetMapping("/{id}/contacts")
    @Operation(summary = "List account contacts", description = "Get contacts for a specific account")
    public ResponseEntity<ApiResponse<java.util.List<ContactResponse>>> getContacts(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.info("GET /api/v1/accounts/{}/contacts - Listing contacts", id);
        UUID tenantId = currentTenantId();
        Page<ContactResponse> contacts = accountService.listContacts(id, tenantId, page, size);

        Map<String, Object> meta = Map.of(
            "page", contacts.getNumber(),
            "size", contacts.getSize(),
            "total", contacts.getTotalElements(),
            "totalPages", contacts.getTotalPages()
        );

        return ResponseEntity.ok(ApiResponse.success(contacts.getContent(), meta));
    }

    @GetMapping("/{id}/activities")
    @Operation(summary = "Get account activities", description = "Get activities for a specific account")
    public ResponseEntity<ApiResponse<java.util.List<ActivityResponse>>> getActivities(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.info("GET /api/v1/accounts/{}/activities - Getting activities", id);
        UUID tenantId = currentTenantId();
        Page<ActivityResponse> activities = activityService.getEntityActivities(id, "ACCOUNT", tenantId, page, size);

        Map<String, Object> meta = Map.of(
            "page", activities.getNumber(),
            "size", activities.getSize(),
            "total", activities.getTotalElements(),
            "totalPages", activities.getTotalPages()
        );

        return ResponseEntity.ok(ApiResponse.success(activities.getContent(), meta));
    }

    @GetMapping("/{id}/notes")
    @Operation(summary = "Get account notes", description = "Get notes for a specific account")
    public ResponseEntity<ApiResponse<java.util.List<com.shivang.crm.modules.lead.dto.EntityNoteResponse>>> getNotes(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.info("GET /api/v1/accounts/{}/notes - Getting notes", id);
        UUID tenantId = currentTenantId();
        Page<com.shivang.crm.modules.lead.dto.EntityNoteResponse> notes = entityNoteService.getEntityNotes(id, "ACCOUNT", tenantId, page, size);

        Map<String, Object> meta = Map.of(
            "page", notes.getNumber(),
            "size", notes.getSize(),
            "total", notes.getTotalElements(),
            "totalPages", notes.getTotalPages()
        );

        return ResponseEntity.ok(ApiResponse.success(notes.getContent(), meta));
    }

    @PostMapping("/{id}/notes")
    @Operation(summary = "Add account note", description = "Add a note to a specific account")
    public ResponseEntity<ApiResponse<com.shivang.crm.modules.lead.dto.EntityNoteResponse>> addNote(
            @PathVariable UUID id,
            @RequestBody Map<String, String> request) {

        log.info("POST /api/v1/accounts/{}/notes - Adding note", id);
        UUID tenantId = currentTenantId();
        UUID userId = currentUserId();
        String noteText = request.get("note");
        com.shivang.crm.modules.lead.dto.EntityNoteResponse noteResponse = entityNoteService.addEntityNote(id, "ACCOUNT", tenantId, noteText, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(noteResponse));
    }

    @DeleteMapping("/{id}/notes/{noteId}")
    @Operation(summary = "Delete account note", description = "Delete a note from an account")
    public ResponseEntity<ApiResponse<String>> deleteNote(
            @PathVariable UUID id,
            @PathVariable UUID noteId) {

        log.info("DELETE /api/v1/accounts/{}/notes/{} - Deleting note", id, noteId);
        UUID tenantId = currentTenantId();
        UUID userId = currentUserId();
        entityNoteService.deleteEntityNote(noteId, id, "ACCOUNT", tenantId, userId);
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
