package com.shivang.crm.modules.commercial.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shivang.crm.modules.commercial.dto.CommercialDocumentResponse;
import com.shivang.crm.modules.commercial.service.CommercialDocumentQueryService;
import com.shivang.crm.shared.dto.ApiResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/commercial-documents")
@RequiredArgsConstructor
public class CommercialDocumentController {

    private final CommercialDocumentQueryService queryService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CommercialDocumentResponse>>> list(
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) UUID contactId,
            @RequestParam(required = false) String externalType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("GET /api/v1/commercial-documents accountId={} contactId={} type={} page={} size={}",
                accountId, contactId, externalType, page, size);
        Page<CommercialDocumentResponse> result = queryService.list(accountId, contactId, externalType, page, size);
        Map<String, Object> meta = Map.of(
                "page", result.getNumber(),
                "size", result.getSize(),
                "total", result.getTotalElements(),
                "totalPages", result.getTotalPages());
        return ResponseEntity.ok(ApiResponse.success(result.getContent(), meta));
    }
}
