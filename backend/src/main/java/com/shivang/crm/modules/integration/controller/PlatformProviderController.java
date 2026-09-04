package com.shivang.crm.modules.integration.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.shivang.crm.modules.auth.security.TenantContext;
import com.shivang.crm.modules.integration.entity.ProviderActionDefinition;
import com.shivang.crm.modules.integration.entity.ProviderDefinition;
import com.shivang.crm.modules.integration.repository.ProviderActionDefinitionRepository;
import com.shivang.crm.modules.integration.repository.ProviderDefinitionRepository;
import com.shivang.crm.shared.exception.BusinessException;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/platform/providers")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPERADMIN')")
public class PlatformProviderController {

    private final ProviderDefinitionRepository providerRepository;
    private final ProviderActionDefinitionRepository actionRepository;
    private final TenantContext tenantContext;

    public record ProviderRequest(String providerKey, String providerName, String description, String category, Boolean isActive, Boolean supportsClickToCall) {}
    public record ProviderResponse(UUID id, String providerKey, String providerName, String description, String category, Boolean isActive, Boolean supportsClickToCall) {}

    @GetMapping
    public ResponseEntity<List<ProviderResponse>> list() {
        List<ProviderResponse> list = providerRepository.findAll().stream()
            .map(this::toResponse)
            .toList();
        return ResponseEntity.ok(list);
    }

    @PostMapping
    public ResponseEntity<ProviderResponse> create(@RequestBody ProviderRequest req) {
        if (req.providerKey() == null || req.providerKey().isBlank()) throw new BusinessException("VALIDATION_ERROR", "Provider key is required");
        if (req.providerName() == null || req.providerName().isBlank()) throw new BusinessException("VALIDATION_ERROR", "Provider name is required");
        String key = req.providerKey().trim().toLowerCase();
        if (providerRepository.findByProviderKey(key).isPresent()) throw new BusinessException("DUPLICATE", "Provider key already exists");
        ProviderDefinition provider = ProviderDefinition.builder()
            .providerKey(key)
            .providerName(req.providerName().trim())
            .description(req.description())
            .category(req.category() != null ? req.category() : "CALLING")
            .isActive(req.isActive() == null ? true : req.isActive())
            .defaultConfig(Map.of())
            .build();
        provider = providerRepository.save(provider);
        if (Boolean.TRUE.equals(req.supportsClickToCall())) {
            upsertClickToCallAction(provider);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(provider));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProviderResponse> update(@PathVariable UUID id, @RequestBody ProviderRequest req) {
        ProviderDefinition provider = providerRepository.findById(id)
            .orElseThrow(() -> new BusinessException("NOT_FOUND", "Provider not found"));
        if (req.providerName() != null && !req.providerName().isBlank()) provider.setProviderName(req.providerName().trim());
        if (req.description() != null) provider.setDescription(req.description());
        if (req.category() != null) provider.setCategory(req.category());
        if (req.isActive() != null) provider.setIsActive(req.isActive());
        // providerKey is immutable once created to avoid breaking tenant instances
        provider = providerRepository.save(provider);
        if (req.supportsClickToCall() != null) {
            if (Boolean.TRUE.equals(req.supportsClickToCall())) {
                upsertClickToCallAction(provider);
            } else {
                actionRepository.findByProviderIdAndActionKey(provider.getId(), "CLICK_TO_CALL")
                    .ifPresent(a -> { a.setIsActive(false); actionRepository.save(a); });
            }
        }
        return ResponseEntity.ok(toResponse(provider));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ProviderResponse> updateStatus(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        ProviderDefinition provider = providerRepository.findById(id)
            .orElseThrow(() -> new BusinessException("NOT_FOUND", "Provider not found"));
        Object active = body.get("isActive");
        if (active == null) active = body.get("active");
        if (active != null) provider.setIsActive(Boolean.parseBoolean(String.valueOf(active)));
        provider = providerRepository.save(provider);
        return ResponseEntity.ok(toResponse(provider));
    }

    private ProviderResponse toResponse(ProviderDefinition p) {
        boolean supportsClickToCall = actionRepository.findByProviderIdAndActionKey(p.getId(), "CLICK_TO_CALL")
            .map(a -> Boolean.TRUE.equals(a.getIsActive()))
            .orElse(false);
        return new ProviderResponse(p.getId(), p.getProviderKey(), p.getProviderName(), p.getDescription(), p.getCategory(), Boolean.TRUE.equals(p.getIsActive()), supportsClickToCall);
    }

    private void upsertClickToCallAction(ProviderDefinition provider) {
        ProviderActionDefinition action = actionRepository.findByProviderIdAndActionKey(provider.getId(), "CLICK_TO_CALL")
            .orElseGet(ProviderActionDefinition::new);
        action.setProvider(provider);
        action.setActionKey("CLICK_TO_CALL");
        action.setActionName("Click to Call");
        action.setDescription("Click to Call capability for " + provider.getProviderName());
        action.setIsActive(true);
        action.setEndpointTemplate("/DialConnect/clicktocall");
        action.setHttpMethod("POST");
        action.setRequestTemplate(Map.of("userId", "{{credential.userId}}", "password", "{{credential.password}}", "number", "{{input.phoneNumber}}", "leadId", "{{input.leadId}}"));
        actionRepository.save(action);
    }
}
