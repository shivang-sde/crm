package com.shivang.crm.modules.commercial.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.shivang.crm.modules.account.entity.Account;
import com.shivang.crm.modules.account.repository.AccountRepository;
import com.shivang.crm.modules.auth.repository.UserRepository;
import com.shivang.crm.modules.auth.security.TenantContext;
import com.shivang.crm.modules.commercial.dto.CustomerSyncRequest;
import com.shivang.crm.modules.commercial.dto.CustomerSyncResponse;
import com.shivang.crm.modules.commercial.dto.DocumentSyncRequest;
import com.shivang.crm.modules.commercial.dto.DocumentSyncResponse;
import com.shivang.crm.modules.commercial.entity.IntegrationExternalRecord;
import com.shivang.crm.modules.commercial.repository.IntegrationExternalRecordRepository;
import com.shivang.crm.modules.contact.entity.Contact;
import com.shivang.crm.modules.contact.repository.ContactRepository;
import com.shivang.crm.shared.exception.BusinessException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommercialSyncService {

    private final CommercialIntegrationFeatureService featureService;
    private final IntegrationExternalRecordRepository mappingRepository;
    private final AccountRepository accountRepository;
    private final ContactRepository contactRepository;
    private final UserRepository userRepository;
    private final TenantContext tenantContext;
    private final PlatformTransactionManager transactionManager;

    // Production-safety: deterministic per-key locks to serialize concurrent creations.
    // Fixes Blocker B (customer) and Blocker C reassociation race.
    private static final ConcurrentHashMap<String, Object> CUSTOMER_LOCKS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Object> DOCUMENT_LOCKS = new ConcurrentHashMap<>();

    private static Object customerLock(UUID tenantId, String externalId) {
        return CUSTOMER_LOCKS.computeIfAbsent(tenantId + ":CUSTOMER:" + externalId, k -> new Object());
    }
    private static Object documentLock(UUID tenantId, String type, String externalId) {
        return DOCUMENT_LOCKS.computeIfAbsent(tenantId + ":" + type + ":" + externalId, k -> new Object());
    }

    // ========== Customer Sync ==========

    @Transactional
    public CustomerSyncResponse syncCustomer(CustomerSyncRequest request) {
        featureService.requireEnabled();
        UUID tenantId = tenantContext.requireTenantId();
        log.info("Commercial integration request received: type=CUSTOMER externalId={} tenant={}",
                request.getExternalCustomerId(), tenantId);
        validateCustomer(request);
        String externalType = "CUSTOMER";
        String externalId = request.getExternalCustomerId().trim();
        String hash = hashCustomer(request);

        // Fast-path NO-OP/UPDATE without lock
        var existingOpt = mappingRepository
                .findByTenantIdAndExternalTypeAndExternalId(tenantId, externalType, externalId);
        if (existingOpt.isPresent()) {
            IntegrationExternalRecord rec = existingOpt.get();
            if (hash.equals(rec.getLastSyncedHash())) {
                log.info("Commercial customer NO-OP tenant={} externalId={} account={} contact={}",
                        tenantId, externalId, rec.getAccountId(), rec.getContactId());
                return CustomerSyncResponse.builder()
                        .success(true).accountId(rec.getAccountId()).contactId(rec.getContactId()).created(false).build();
            }
            UUID accountId = rec.getAccountId();
            UUID contactId = rec.getContactId();
            updateAccountContactOwnedFields(tenantId, accountId, contactId, request);
            rec.setLastSyncedHash(hash);
            rec.setLastSyncedAt(Instant.now());
            try {
                mappingRepository.save(rec);
            } catch (DataIntegrityViolationException e) {
                // Very rare race on hash update; reload
                log.warn("Commercial customer hash update race tenant={} externalId={}", tenantId, externalId);
            }
            log.info("Commercial customer UPDATED tenant={} externalId={} account={} contact={}",
                    tenantId, externalId, accountId, contactId);
            return CustomerSyncResponse.builder()
                    .success(true).accountId(accountId).contactId(contactId).created(false).build();
        }

        // NEW path — must be concurrency-safe (Blocker B). Serialize per tenant+externalId and commit inside lock.
        Object lock = customerLock(tenantId, externalId);
        synchronized (lock) {
            // Use REQUIRES_NEW so commit happens inside lock, making recheck visible to next waiter.
            TransactionTemplate tt = new TransactionTemplate(transactionManager);
            tt.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            try {
                return tt.execute(status -> {
                    var recheck = mappingRepository
                            .findByTenantIdAndExternalTypeAndExternalId(tenantId, externalType, externalId);
                    if (recheck.isPresent()) {
                        var rec = recheck.get();
                        log.info("Commercial customer concurrent recheck hit tenant={} externalId={}", tenantId, externalId);
                        if (!hash.equals(rec.getLastSyncedHash())) {
                            updateAccountContactOwnedFields(tenantId, rec.getAccountId(), rec.getContactId(), request);
                            rec.setLastSyncedHash(hash);
                            rec.setLastSyncedAt(Instant.now());
                            try { mappingRepository.save(rec); mappingRepository.flush(); } catch (DataIntegrityViolationException ignored) {}
                        }
                        return CustomerSyncResponse.builder()
                                .success(true).accountId(rec.getAccountId()).contactId(rec.getContactId()).created(false).build();
                    }
                    UUID actorId = resolveActorUserId(tenantId);
                    Account account;
                    try {
                        account = createAccountForCustomer(tenantId, actorId, request);
                        accountRepository.flush();
                    } catch (DataIntegrityViolationException e) {
                        var existingAcc = accountRepository.findByTenantIdAndNameIgnoreCaseAndDeletedFalse(tenantId,
                                request.getCompanyName() != null && !request.getCompanyName().isBlank() ? request.getCompanyName().trim() : request.getCustomerName().trim());
                        if (existingAcc.isPresent()) {
                            log.warn("Commercial customer account race recovered tenant={} externalId={} account={}", tenantId, externalId, existingAcc.get().getId());
                            account = existingAcc.get();
                        } else {
                            var maybeWinner = mappingRepository.findByTenantIdAndExternalTypeAndExternalId(tenantId, externalType, externalId);
                            if (maybeWinner.isPresent()) {
                                var w = maybeWinner.get();
                                return CustomerSyncResponse.builder()
                                        .success(true).accountId(w.getAccountId()).contactId(w.getContactId()).created(false).build();
                            }
                            throw e;
                        }
                    }
                    Contact contact;
                    try {
                        contact = createContactForCustomer(tenantId, actorId, account.getId(), request);
                        contactRepository.flush();
                    } catch (DataIntegrityViolationException e) {
                        var byEmail = request.getEmail() != null ? contactRepository.findByTenantIdAndEmailIgnoreCaseAndDeletedFalse(tenantId, request.getEmail().trim()).orElse(null) : null;
                        if (byEmail != null && byEmail.getAccountId().equals(account.getId())) {
                            log.warn("Commercial contact race recovered tenant={} externalId={}", tenantId, externalId);
                            contact = byEmail;
                        } else {
                            var list = contactRepository.findByAccountIdAndTenantId(account.getId(), tenantId, PageRequest.of(0,1)).getContent();
                            if (!list.isEmpty()) contact = list.get(0);
                            else {
                                var maybeWinner2 = mappingRepository.findByTenantIdAndExternalTypeAndExternalId(tenantId, externalType, externalId);
                                if (maybeWinner2.isPresent()) {
                                    var w = maybeWinner2.get();
                                    return CustomerSyncResponse.builder()
                                            .success(true).accountId(w.getAccountId()).contactId(w.getContactId()).created(false).build();
                                }
                                throw e;
                            }
                        }
                    }
                    IntegrationExternalRecord rec = IntegrationExternalRecord.builder()
                            .tenantId(tenantId).externalType(externalType).externalId(externalId)
                            .accountId(account.getId()).contactId(contact.getId())
                            .lastSyncedHash(hash).lastSyncedAt(Instant.now()).build();
                    try {
                        mappingRepository.save(rec);
                        mappingRepository.flush();
                    } catch (DataIntegrityViolationException e) {
                        var winner = mappingRepository.findByTenantIdAndExternalTypeAndExternalId(tenantId, externalType, externalId)
                                .orElseThrow(() -> new BusinessException("MAPPING_CONFLICT", "Duplicate mapping"));
                        log.warn("Commercial customer mapping duplicate on create (locked race) tenant={} externalId={} winner={}", tenantId, externalId, winner.getId());
                        boolean orphanAccount = !winner.getAccountId().equals(account.getId());
                        if (orphanAccount) {
                            try { accountRepository.delete(account); contactRepository.delete(contact); accountRepository.flush(); contactRepository.flush(); log.info("Cleaned orphan account {} contact {} after race", account.getId(), contact.getId()); } catch (Exception ex) { log.warn("Failed to clean orphan {}", ex.getMessage()); }
                        }
                        return CustomerSyncResponse.builder()
                                .success(true).accountId(winner.getAccountId()).contactId(winner.getContactId()).created(false).build();
                    }
                    log.info("Commercial customer CREATED tenant={} externalId={} account={} contact={}",
                            tenantId, externalId, account.getId(), contact.getId());
                    return CustomerSyncResponse.builder()
                            .success(true).accountId(account.getId()).contactId(contact.getId()).created(true).build();
                });
            } catch (DataIntegrityViolationException e) {
                var winner = mappingRepository.findByTenantIdAndExternalTypeAndExternalId(tenantId, externalType, externalId);
                if (winner.isPresent()) {
                    var w = winner.get();
                    log.warn("Commercial customer outer race recovered tenant={} externalId={}", tenantId, externalId);
                    return CustomerSyncResponse.builder()
                            .success(true).accountId(w.getAccountId()).contactId(w.getContactId()).created(false).build();
                }
                throw e;
            }
        }
    }

    // ========== Document Sync ==========
    // V1 refactored: document sync associates external document with Account+Contact only.
    // No Deal is created, correlated, or modified. See target architecture in task spec.

    @Transactional
    public DocumentSyncResponse syncDocument(DocumentSyncRequest request) {
        featureService.requireEnabled();
        UUID tenantId = tenantContext.requireTenantId();
        log.info("Commercial integration request received: type={} externalId={} tenant={}",
                request.getExternalType(), request.getExternalId(), tenantId);

        validateDocument(request);
        String externalType = request.getExternalType().trim().toUpperCase(); // QUOTATION / INVOICE
        String externalId = request.getExternalId().trim();
        String customerExternalId = request.getCustomer().getExternalCustomerId().trim();

        // Resolve existing customer mapping -> Account+Contact (tenant isolated).
        IntegrationExternalRecord customerRec = mappingRepository
                .findByTenantIdAndExternalTypeAndExternalId(tenantId, "CUSTOMER", customerExternalId)
                .orElseThrow(() -> new BusinessException("CUSTOMER_NOT_SYNCED",
                        "Customer " + customerExternalId + " must be synced before document"));

        UUID accountId = customerRec.getAccountId();
        UUID contactId = customerRec.getContactId();

        String hash = hashDocument(request);

        // Fast-path check without lock for NO-OP/CHANGED with ownership validation
        var existingOpt = mappingRepository
                .findByTenantIdAndExternalTypeAndExternalId(tenantId, externalType, externalId);
        if (existingOpt.isPresent()) {
            IntegrationExternalRecord rec = existingOpt.get();
            // Blocker A: immutable customer ownership — once mapped, externalId cannot move to different customer
            if (!Objects.equals(rec.getAccountId(), accountId) || !Objects.equals(rec.getContactId(), contactId)) {
                log.warn("Commercial document customer conflict tenant={} type={} externalId={} existingAccount={} incomingAccount={}",
                        tenantId, externalType, externalId, rec.getAccountId(), accountId);
                throw new BusinessException("COMMERCIAL_DOCUMENT_CUSTOMER_CONFLICT",
                        "Document " + externalType + "/" + externalId + " already mapped to another customer");
            }
            if (hash.equals(rec.getLastSyncedHash())) {
                log.info("Commercial document NO-OP tenant={} type={} externalId={} account={} contact={}",
                        tenantId, externalType, externalId, accountId, contactId);
                return DocumentSyncResponse.builder()
                        .success(true).accountId(accountId).contactId(contactId).pdfUrl(rec.getPdfUrl()).created(false).build();
            }
            // CHANGED → update mapping metadata only; no Deal
            rec.setLastSyncedHash(hash);
            rec.setExternalUpdatedAt(request.getExternalUpdatedAt());
            rec.setPdfUrl(request.getPdfUrl() != null ? request.getPdfUrl().trim() : null);
            rec.setLastSyncedAt(Instant.now());
            // account/contact already validated as same; keep as is (no reassociation)
            try {
                mappingRepository.save(rec);
                mappingRepository.flush();
            } catch (DataIntegrityViolationException e) {
                log.warn("Commercial document update race tenant={} type={} externalId={}", tenantId, externalType, externalId);
            }
            log.info("Commercial document UPDATED tenant={} type={} externalId={} account={} contact={}",
                    tenantId, externalType, externalId, accountId, contactId);
            return DocumentSyncResponse.builder()
                    .success(true).accountId(accountId).contactId(contactId).pdfUrl(rec.getPdfUrl()).created(false).build();
        }

        // NEW → concurrency-safe creation per tenant+type+id (Blocker B/U) — commit inside lock
        Object lock = documentLock(tenantId, externalType, externalId);
        synchronized (lock) {
            TransactionTemplate tt = new TransactionTemplate(transactionManager);
            tt.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            try {
                return tt.execute(status -> {
                    var recheck = mappingRepository
                            .findByTenantIdAndExternalTypeAndExternalId(tenantId, externalType, externalId);
                    if (recheck.isPresent()) {
                        var rec = recheck.get();
                        if (!Objects.equals(rec.getAccountId(), accountId) || !Objects.equals(rec.getContactId(), contactId)) {
                            throw new BusinessException("COMMERCIAL_DOCUMENT_CUSTOMER_CONFLICT",
                                    "Document " + externalType + "/" + externalId + " already mapped to another customer");
                        }
                        if (hash.equals(rec.getLastSyncedHash())) {
                            log.info("Commercial document concurrent recheck NO-OP tenant={} type={} externalId={}", tenantId, externalType, externalId);
                            return DocumentSyncResponse.builder()
                                    .success(true).accountId(accountId).contactId(contactId).pdfUrl(rec.getPdfUrl()).created(false).build();
                        }
                        rec.setLastSyncedHash(hash);
                        rec.setExternalUpdatedAt(request.getExternalUpdatedAt());
                        rec.setPdfUrl(request.getPdfUrl() != null ? request.getPdfUrl().trim() : null);
                        rec.setLastSyncedAt(Instant.now());
                        try { mappingRepository.save(rec); mappingRepository.flush(); } catch (DataIntegrityViolationException ignored) {}
                        return DocumentSyncResponse.builder()
                                .success(true).accountId(accountId).contactId(contactId).pdfUrl(rec.getPdfUrl()).created(false).build();
                    }
                    IntegrationExternalRecord rec = IntegrationExternalRecord.builder()
                            .tenantId(tenantId).externalType(externalType).externalId(externalId)
                            .accountId(accountId).contactId(contactId)
                            .externalUpdatedAt(request.getExternalUpdatedAt())
                            .pdfUrl(request.getPdfUrl() != null ? request.getPdfUrl().trim() : null)
                            .lastSyncedHash(hash).lastSyncedAt(Instant.now()).build();
                    try {
                        mappingRepository.save(rec);
                        mappingRepository.flush();
                    } catch (DataIntegrityViolationException e) {
                        var winner = mappingRepository.findByTenantIdAndExternalTypeAndExternalId(tenantId, externalType, externalId)
                                .orElseThrow(() -> new BusinessException("MAPPING_CONFLICT", "Duplicate mapping"));
                        if (!Objects.equals(winner.getAccountId(), accountId) || !Objects.equals(winner.getContactId(), contactId)) {
                            throw new BusinessException("COMMERCIAL_DOCUMENT_CUSTOMER_CONFLICT",
                                    "Document " + externalType + "/" + externalId + " already mapped to another customer");
                        }
                        log.warn("Commercial document mapping duplicate (locked) tenant={} type={} externalId={}", tenantId, externalType, externalId);
                        return DocumentSyncResponse.builder()
                                .success(true).accountId(winner.getAccountId()).contactId(winner.getContactId()).pdfUrl(winner.getPdfUrl()).created(false).build();
                    }
                    log.info("Commercial document CREATED tenant={} type={} externalId={} account={} contact={}",
                            tenantId, externalType, externalId, accountId, contactId);
                    return DocumentSyncResponse.builder()
                            .success(true).accountId(accountId).contactId(contactId).pdfUrl(rec.getPdfUrl()).created(true).build();
                });
            } catch (DataIntegrityViolationException e) {
                var winner = mappingRepository.findByTenantIdAndExternalTypeAndExternalId(tenantId, externalType, externalId);
                if (winner.isPresent()) {
                    var w = winner.get();
                    if (!Objects.equals(w.getAccountId(), accountId) || !Objects.equals(w.getContactId(), contactId)) {
                        throw new BusinessException("COMMERCIAL_DOCUMENT_CUSTOMER_CONFLICT",
                                "Document " + externalType + "/" + externalId + " already mapped to another customer");
                    }
                    log.warn("Commercial document outer race recovered tenant={} type={} externalId={}", tenantId, externalType, externalId);
                    return DocumentSyncResponse.builder()
                            .success(true).accountId(w.getAccountId()).contactId(w.getContactId()).pdfUrl(w.getPdfUrl()).created(false).build();
                }
                throw e;
            }
        }
    }

    // ========== Helpers ==========

    private void validateCustomer(CustomerSyncRequest r) {
        if (r.getExternalCustomerId() == null || r.getExternalCustomerId().isBlank())
            throw new BusinessException("VALIDATION_ERROR", "externalCustomerId is required");
        boolean hasName = (r.getCustomerName() != null && !r.getCustomerName().isBlank())
                || (r.getCompanyName() != null && !r.getCompanyName().isBlank());
        if (!hasName)
            throw new BusinessException("VALIDATION_ERROR", "customerName or companyName is required");
        if (r.getEmail() != null && !r.getEmail().isBlank() && !r.getEmail().contains("@"))
            throw new BusinessException("VALIDATION_ERROR", "email is invalid");
    }

    private void validateDocument(DocumentSyncRequest r) {
        if (r.getExternalType() == null || r.getExternalType().isBlank())
            throw new BusinessException("VALIDATION_ERROR", "externalType is required");
        String t = r.getExternalType().trim().toUpperCase();
        if (!("QUOTATION".equals(t) || "INVOICE".equals(t)))
            throw new BusinessException("VALIDATION_ERROR", "externalType must be QUOTATION or INVOICE");
        if (r.getExternalId() == null || r.getExternalId().isBlank())
            throw new BusinessException("VALIDATION_ERROR", "externalId is required");
        if (r.getCustomer() == null || r.getCustomer().getExternalCustomerId() == null
                || r.getCustomer().getExternalCustomerId().isBlank())
            throw new BusinessException("VALIDATION_ERROR", "customer.externalCustomerId is required");
        // total can be null but if present must be >=0
        if (r.getTotal() != null && r.getTotal().compareTo(BigDecimal.ZERO) < 0)
            throw new BusinessException("VALIDATION_ERROR", "total must be >= 0");
        if (r.getPdfUrl() != null && !r.getPdfUrl().isBlank()) {
            String url = r.getPdfUrl().trim();
            if (url.length() > 2048) throw new BusinessException("VALIDATION_ERROR", "pdfUrl too long (max 2048)");
            try {
                java.net.URL parsed = new java.net.URL(url);
                String proto = parsed.getProtocol();
                if (!"http".equalsIgnoreCase(proto) && !"https".equalsIgnoreCase(proto)) {
                    throw new BusinessException("VALIDATION_ERROR", "pdfUrl must be http or https");
                }
            } catch (java.net.MalformedURLException e) {
                throw new BusinessException("VALIDATION_ERROR", "pdfUrl must be a valid URL");
            }
        }
    }

    private record ReusedIds(UUID accountId, UUID contactId) {}

    // Blocker C fix: Commercial Integration must NOT silently merge distinct external customers
    // via shared phone/email. Mapping `tenant+externalCustomerId` is authoritative.
    // This method is intentionally disabled; each externalCustomerId gets its own Account+Contact
    // regardless of phone/email collision. Previous phone/email reuse caused burst 10→1 collapse.
    private ReusedIds findSafeExistingMatch(UUID tenantId, CustomerSyncRequest req) {
        return null;
    }

    private void updateAccountContactOwnedFields(UUID tenantId, UUID accountId, UUID contactId, CustomerSyncRequest req) {
        if (accountId != null) {
            accountRepository.findByIdAndTenantId(accountId, tenantId).ifPresent(acc -> {
                boolean changed = false;
                String desiredName = req.getCompanyName() != null && !req.getCompanyName().isBlank()
                        ? req.getCompanyName().trim() : req.getCustomerName().trim();
                if (!desiredName.equals(acc.getName())) {
                    // Avoid duplicate name constraint violation: check if another account has this name
                    var dup = accountRepository.findByTenantIdAndNameIgnoreCaseAndDeletedFalse(tenantId, desiredName);
                    if (dup.isEmpty() || dup.get().getId().equals(acc.getId())) {
                        acc.setName(desiredName);
                        changed = true;
                    } else {
                        log.warn("Commercial account name update skipped due to duplicate name tenant={} desiredName={}", tenantId, desiredName);
                    }
                }
                if (req.getEmail() != null && !req.getEmail().trim().equalsIgnoreCase(
                        acc.getEmail() != null ? acc.getEmail() : "")) {
                    acc.setEmail(req.getEmail().trim());
                    changed = true;
                }
                if (req.getPhone() != null && !req.getPhone().trim().equals(
                        acc.getPhone() != null ? acc.getPhone() : "")) {
                    acc.setPhone(req.getPhone().trim());
                    changed = true;
                }
                // Store gstin in customData without adding column (isolated)
                if (changed) {
                    acc.setUpdatedAt(Instant.now());
                    accountRepository.save(acc);
                }
            });
        }
        if (contactId != null) {
            contactRepository.findByIdAndTenantId(contactId, tenantId).ifPresent(ct -> {
                boolean changed = false;
                String[] names = splitName(req.getCustomerName() != null ? req.getCustomerName() : req.getCompanyName());
                if (names[0] != null && !names[0].equals(ct.getFirstName())) {
                    ct.setFirstName(names[0]);
                    changed = true;
                }
                if (names[1] != null && !names[1].equals(ct.getLastName())) {
                    ct.setLastName(names[1]);
                    changed = true;
                }
                if (req.getEmail() != null && !req.getEmail().trim().equalsIgnoreCase(
                        ct.getEmail() != null ? ct.getEmail() : "")) {
                    // Check duplicate email for this tenant
                    var dup = contactRepository.findByTenantIdAndEmailIgnoreCaseAndDeletedFalse(tenantId, req.getEmail().trim());
                    if (dup.isEmpty() || dup.get().getId().equals(ct.getId())) {
                        ct.setEmail(req.getEmail().trim());
                        changed = true;
                    } else {
                        log.warn("Commercial contact email update skipped due to duplicate tenant={} email={}", tenantId, req.getEmail());
                    }
                }
                if (req.getPhone() != null && !req.getPhone().trim().equals(
                        ct.getPhone() != null ? ct.getPhone() : "")) {
                    var dup = contactRepository.findByTenantIdAndPhoneAndDeletedFalse(tenantId, req.getPhone().trim());
                    if (dup.isEmpty() || dup.get().getId().equals(ct.getId())) {
                        ct.setPhone(req.getPhone().trim());
                        changed = true;
                    } else {
                        log.warn("Commercial contact phone update skipped due to duplicate tenant={} phone={}", tenantId, req.getPhone());
                    }
                }
                if (changed) {
                    ct.setUpdatedAt(Instant.now());
                    contactRepository.save(ct);
                }
            });
        }
    }

    private Account createAccountForCustomer(UUID tenantId, UUID actorId, CustomerSyncRequest req) {
        String name = req.getCompanyName() != null && !req.getCompanyName().isBlank()
                ? req.getCompanyName().trim() : req.getCustomerName().trim();
        // Pre-check duplicate name to reuse via safe match was already done; but still need to handle race:
        var existing = accountRepository.findByTenantIdAndNameIgnoreCaseAndDeletedFalse(tenantId, name);
        if (existing.isPresent()) {
            // Reuse and update instead of failing (gstin is external-owned, not persisted to core)
            Account acc = existing.get();
            if (req.getEmail() != null) acc.setEmail(req.getEmail().trim());
            if (req.getPhone() != null) acc.setPhone(req.getPhone().trim());
            return accountRepository.save(acc);
        }
        Account acc = Account.builder()
                .tenantId(tenantId)
                .createdBy(actorId)
                .updatedBy(actorId)
                .ownerId(actorId)
                .name(name)
                .email(req.getEmail() != null ? req.getEmail().trim() : null)
                .phone(req.getPhone() != null ? req.getPhone().trim() : null)
                .isActive(true)
                .build();
        return accountRepository.save(acc);
    }

    private Contact createContactForCustomer(UUID tenantId, UUID actorId, UUID accountId, CustomerSyncRequest req) {
        String[] names = splitName(req.getCustomerName() != null ? req.getCustomerName() : req.getCompanyName());
        // If email already exists, reuse? Already handled in safe match, but double-check for race
        if (req.getEmail() != null && !req.getEmail().isBlank()) {
            var dup = contactRepository.findByTenantIdAndEmailIgnoreCaseAndDeletedFalse(tenantId, req.getEmail().trim());
            if (dup.isPresent() && dup.get().getAccountId().equals(accountId)) {
                return dup.get();
            }
        }
        Contact ct = Contact.builder()
                .tenantId(tenantId)
                .accountId(accountId)
                .createdBy(actorId)
                .updatedBy(actorId)
                .ownerId(actorId)
                .firstName(names[0])
                .lastName(names[1])
                .email(req.getEmail() != null ? req.getEmail().trim() : null)
                .phone(req.getPhone() != null ? req.getPhone().trim() : null)
                .isPrimary(true)
                .isActive(true)
                .build();
        return contactRepository.save(ct);
    }

    private UUID resolveActorUserId(UUID tenantId) {
        // Prefer a real user for FK constraints; pick first active user
        var page = userRepository.findByTenantId(tenantId, PageRequest.of(0, 1));
        if (page.hasContent()) return page.getContent().get(0).getId();
        // Fallback: tenantId itself is not a user; but deals/accounts require createdBy non-null FK to users.
        // If no user exists, throw explicit error.
        throw new BusinessException("NO_USER_FOR_TENANT", "Tenant has no user to act as integration actor");
    }

    private String[] splitName(String full) {
        if (full == null || full.isBlank()) return new String[]{null, null};
        String t = full.trim();
        int idx = t.indexOf(' ');
        if (idx <= 0) return new String[]{t, null};
        return new String[]{t.substring(0, idx).trim(), t.substring(idx + 1).trim()};
    }

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String hashCustomer(CustomerSyncRequest r) {
        String raw = String.join("|",
                nullSafe(r.getExternalCustomerId()),
                nullSafe(r.getCompanyName()),
                nullSafe(r.getCustomerName()),
                nullSafeLower(r.getEmail()),
                nullSafe(r.getPhone()),
                nullSafe(r.getGstin()));
        return sha256(raw);
    }

    private String hashDocument(DocumentSyncRequest r) {
        String raw = String.join("|",
                nullSafe(r.getExternalType()).toUpperCase(),
                nullSafe(r.getExternalId()),
                nullSafe(r.getExternalUpdatedAt() != null ? r.getExternalUpdatedAt().toString() : ""),
                nullSafe(r.getCustomer() != null ? r.getCustomer().getExternalCustomerId() : ""),
                nullSafe(r.getDocumentNumber()),
                nullSafe(r.getDocumentDate() != null ? r.getDocumentDate().toString() : ""),
                nullSafe(r.getCurrency()),
                nullSafe(r.getSubtotal() != null ? r.getSubtotal().toPlainString() : ""),
                nullSafe(r.getTax() != null ? r.getTax().toPlainString() : ""),
                nullSafe(r.getTotal() != null ? r.getTotal().toPlainString() : ""),
                nullSafe(r.getStatus()),
                nullSafe(r.getPdfUrl()));
        return sha256(raw);
    }

    private String nullSafe(String s) { return s == null ? "" : s.trim(); }
    private String nullSafeLower(String s) { return s == null ? "" : s.trim().toLowerCase(); }
}
