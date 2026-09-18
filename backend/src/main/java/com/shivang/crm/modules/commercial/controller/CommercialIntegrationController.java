package com.shivang.crm.modules.commercial.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shivang.crm.modules.auth.security.TenantContext;
import com.shivang.crm.modules.commercial.dto.CustomerSyncRequest;
import com.shivang.crm.modules.commercial.dto.CustomerSyncResponse;
import com.shivang.crm.modules.commercial.dto.DocumentSyncRequest;
import com.shivang.crm.modules.commercial.dto.DocumentSyncResponse;
import com.shivang.crm.modules.commercial.repository.IntegrationExternalRecordRepository;
import com.shivang.crm.modules.commercial.service.CommercialIntegrationFeatureService;
import com.shivang.crm.modules.commercial.service.CommercialSyncService;
import com.shivang.crm.shared.dto.ApiResponse;
import com.shivang.crm.shared.exception.NotFoundException;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/integrations/commercial")
@RequiredArgsConstructor
public class CommercialIntegrationController {

    private final CommercialSyncService syncService;
    private final IntegrationExternalRecordRepository mappingRepository;
    private final CommercialIntegrationFeatureService featureService;
    private final TenantContext tenantContext;

    @PostMapping("/customers/sync")
    public ResponseEntity<ApiResponse<CustomerSyncResponse>> syncCustomer(
            @Valid @RequestBody CustomerSyncRequest request) {
        log.info("POST /api/v1/integrations/commercial/customers/sync externalId={}", request.getExternalCustomerId());
        CustomerSyncResponse data = syncService.syncCustomer(request);
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @PostMapping("/documents/sync")
    public ResponseEntity<ApiResponse<DocumentSyncResponse>> syncDocument(
            @Valid @RequestBody DocumentSyncRequest request) {
        log.info("POST /api/v1/integrations/commercial/documents/sync type={} externalId={}",
                request.getExternalType(), request.getExternalId());
        DocumentSyncResponse data = syncService.syncDocument(request);
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @GetMapping("/documents/{externalType}/{externalId}")
    public ResponseEntity<ApiResponse<DocumentSyncResponse>> getDocument(
            @PathVariable String externalType,
            @PathVariable String externalId) {
        featureService.requireEnabled();
        var tenantId = tenantContext.requireTenantId();
        var rec = mappingRepository
                .findByTenantIdAndExternalTypeAndExternalId(tenantId, externalType.toUpperCase(), externalId)
                .orElseThrow(() -> new NotFoundException("IntegrationExternalRecord", externalType + "/" + externalId));
        // Only QUOTATION/INVOICE have pdfUrl; CUSTOMER lookup via this endpoint is not intended
        DocumentSyncResponse data = DocumentSyncResponse.builder()
                .success(true)
                .accountId(rec.getAccountId())
                .contactId(rec.getContactId())
                .pdfUrl(rec.getPdfUrl())
                .created(false)
                .build();
        return ResponseEntity.ok(ApiResponse.success(data));
    }
}
