package com.shivang.crm.modules.commercial.service;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.account.repository.AccountRepository;
import com.shivang.crm.modules.auth.security.TenantContext;
import com.shivang.crm.modules.commercial.dto.CommercialDocumentResponse;
import com.shivang.crm.modules.commercial.entity.IntegrationExternalRecord;
import com.shivang.crm.modules.commercial.repository.IntegrationExternalRecordRepository;
import com.shivang.crm.modules.contact.repository.ContactRepository;
import com.shivang.crm.modules.rbac.service.RecordScopeGuard;
import com.shivang.crm.shared.exception.BusinessException;
import com.shivang.crm.util.UserUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommercialDocumentQueryService {

    private final IntegrationExternalRecordRepository recordRepository;
    private final AccountRepository accountRepository;
    private final ContactRepository contactRepository;
    private final TenantContext tenantContext;
    private final RecordScopeGuard scopeGuard;

    @Transactional(readOnly = true)
    public Page<CommercialDocumentResponse> list(UUID accountId, UUID contactId, String externalType, int page, int size) {
        UUID tenantId = tenantContext.requireTenantId();
        UUID userId = UserUtil.currentUserId();

        if (accountId == null && contactId == null) {
            throw new BusinessException("VALIDATION_ERROR", "accountId or contactId is required");
        }
        if (accountId != null && contactId != null) {
            throw new BusinessException("VALIDATION_ERROR", "Provide either accountId or contactId, not both");
        }

        // Normalize externalType filter
        List<String> types;
        if (externalType == null || externalType.isBlank()) {
            types = List.of("QUOTATION", "INVOICE");
        } else {
            String t = externalType.trim().toUpperCase();
            if (!t.equals("QUOTATION") && !t.equals("INVOICE")) {
                throw new BusinessException("VALIDATION_ERROR", "externalType must be QUOTATION or INVOICE");
            }
            types = List.of(t);
        }

        // Pagination bounds (follow existing CRM conventions: default 20, cap 100)
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        Pageable pageable = PageRequest.of(safePage, safeSize);

        Page<IntegrationExternalRecord> pageResult;
        if (accountId != null) {
            // Validate account belongs to tenant and user has access
            var account = accountRepository.findByIdAndTenantId(accountId, tenantId)
                    .orElseThrow(() -> new BusinessException("NOT_FOUND", "Account not found"));
            String scope = scopeGuard.requireScope(tenantId, userId, "account", "read");
            scopeGuard.assertWithinOwnerCreatorScope(scope, tenantId, userId, account.getOwnerId(), account.getCreatedBy());
            pageResult = recordRepository.findByTenantAndAccountAndTypes(tenantId, accountId, types, pageable);
        } else {
            var contact = contactRepository.findByIdAndTenantId(contactId, tenantId)
                    .orElseThrow(() -> new BusinessException("NOT_FOUND", "Contact not found"));
            String scope = scopeGuard.requireScope(tenantId, userId, "contact", "read");
            scopeGuard.assertWithinOwnerCreatorScope(scope, tenantId, userId, contact.getOwnerId(), contact.getCreatedBy());
            pageResult = recordRepository.findByTenantAndContactAndTypes(tenantId, contactId, types, pageable);
        }

        return pageResult.map(this::toResponse);
    }

    private CommercialDocumentResponse toResponse(IntegrationExternalRecord r) {
        return CommercialDocumentResponse.builder()
                .id(r.getId())
                .externalType(r.getExternalType())
                .externalId(r.getExternalId())
                .accountId(r.getAccountId())
                .contactId(r.getContactId())
                .externalUpdatedAt(r.getExternalUpdatedAt())
                .pdfUrl(r.getPdfUrl())
                .build();
    }
}
