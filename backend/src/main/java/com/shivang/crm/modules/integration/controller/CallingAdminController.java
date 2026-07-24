package com.shivang.crm.modules.integration.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shivang.crm.modules.auth.security.TenantContext;
import com.shivang.crm.modules.dialer.entity.CallConnectTrigger;
import com.shivang.crm.modules.dialer.entity.CallLayoutConfig;
import com.shivang.crm.modules.dialer.repository.CallConnectTriggerRepository;
import com.shivang.crm.modules.dialer.service.CallConnectTriggerService;
import com.shivang.crm.modules.dialer.service.CallLayoutConfigService;
import com.shivang.crm.modules.integration.entity.ConnectorCredential;
import com.shivang.crm.modules.integration.entity.ConnectorInstance;
import com.shivang.crm.modules.integration.entity.ConnectorWebhookConfig;
import com.shivang.crm.modules.integration.entity.ProviderDefinition;
import com.shivang.crm.modules.integration.repository.ProviderActionDefinitionRepository;
import com.shivang.crm.modules.integration.repository.ProviderDefinitionRepository;
import com.shivang.crm.modules.integration.repository.ProviderTriggerDefinitionRepository;
import com.shivang.crm.modules.integration.service.ConnectorCredentialService;
import com.shivang.crm.modules.integration.service.ConnectorInstanceService;
import com.shivang.crm.modules.integration.service.ConnectorWebhookConfigService;
import com.shivang.crm.modules.integration.service.ProviderRegistryService;
import com.shivang.crm.shared.exception.BusinessException;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
@org.springframework.security.access.prepost.PreAuthorize("hasPermission('admin', 'settings')")
public class CallingAdminController {

    private final TenantContext tenantContext;
    private final ProviderRegistryService providerRegistryService;
    private final ConnectorInstanceService connectorInstanceService;
    private final ConnectorCredentialService connectorCredentialService;
    private final ConnectorWebhookConfigService connectorWebhookConfigService;
    private final CallConnectTriggerService callConnectTriggerService;
    private final CallLayoutConfigService callLayoutConfigService;
    private final ProviderDefinitionRepository providerDefinitionRepository;
    private final ProviderActionDefinitionRepository providerActionDefinitionRepository;
    private final ProviderTriggerDefinitionRepository providerTriggerDefinitionRepository;
    private final CallConnectTriggerRepository callConnectTriggerRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping("/integrations/providers")
    public ResponseEntity<List<ProviderResponse>> listProviders(@RequestParam(required = false, defaultValue = "CALLING") String category) {
        requireTenantId();
        List<ProviderDefinition> providers = providerDefinitionRepository.findAll().stream()
            .filter(provider -> Boolean.TRUE.equals(provider.getIsActive()) || provider.getIsActive() == null)
            .sorted((left, right) -> String.CASE_INSENSITIVE_ORDER.compare(left.getProviderName(), right.getProviderName()))
            .toList();
        List<ProviderResponse> response = providers.stream()
            .filter(provider -> matchesCategory(provider, category))
            .map(this::toProviderResponse)
            .toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/integrations/providers/{providerKey}")
    public ResponseEntity<ProviderResponse> getProvider(@PathVariable String providerKey) {
        requireTenantId();
        ProviderDefinition provider = providerRegistryService.findByProviderKey(providerKey)
            .orElseThrow(() -> new BusinessException("NOT_FOUND", "Provider not found"));
        return ResponseEntity.ok(toProviderResponse(provider));
    }

    @GetMapping("/integrations/connector-instances")
    public ResponseEntity<List<ConnectorInstanceResponse>> listConnectorInstances(@RequestParam(required = false, defaultValue = "CALLING") String category) {
        UUID tenantId = requireTenantId();
        List<ConnectorInstanceResponse> response = connectorInstanceService.findByTenantId(tenantId).stream()
            .filter(instance -> matchesCategory(instance.getProvider(), category))
            .map(this::toConnectorInstanceResponse)
            .toList();
        return ResponseEntity.ok(response);
    }

    @PostMapping("/integrations/connector-instances")
    public ResponseEntity<ConnectorInstanceResponse> createConnectorInstance(@RequestBody ConnectorInstanceRequest request) {
        UUID tenantId = requireTenantId();
        UUID userId = requireUserId();
        ProviderDefinition provider = providerRegistryService.findByProviderKey(request.providerKey())
            .orElseThrow(() -> new BusinessException("NOT_FOUND", "Provider not found"));
        ConnectorInstance instance = ConnectorInstance.builder()
            .tenantId(tenantId)
            .provider(provider)
            .connectorName(request.name() != null && !request.name().isBlank() ? request.name() : provider.getProviderName())
            .environment(request.environment())
            .baseUrl(request.baseUrl())
            .config(request.config() != null ? new HashMap<>(request.config()) : Map.of())
            .isActive(Boolean.TRUE.equals(request.active()))
            .createdBy(userId)
            .updatedBy(userId)
            .build();
        return ResponseEntity.ok(toConnectorInstanceResponse(connectorInstanceService.save(instance)));
    }

    @PutMapping("/integrations/connector-instances/{id}")
    public ResponseEntity<ConnectorInstanceResponse> updateConnectorInstance(@PathVariable UUID id, @RequestBody ConnectorInstanceRequest request) {
        UUID tenantId = requireTenantId();
        UUID userId = requireUserId();
        ConnectorInstance existing = connectorInstanceService.findById(tenantId, id)
            .orElseThrow(() -> new BusinessException("NOT_FOUND", "Connector instance not found"));
        ProviderDefinition provider = providerRegistryService.findByProviderKey(request.providerKey())
            .orElseThrow(() -> new BusinessException("NOT_FOUND", "Provider not found"));
        existing.setProvider(provider);
        existing.setConnectorName(request.name() != null && !request.name().isBlank() ? request.name() : provider.getProviderName());
        existing.setEnvironment(request.environment());
        existing.setBaseUrl(request.baseUrl());
        existing.setConfig(request.config() != null ? new HashMap<>(request.config()) : Map.of());
        existing.setIsActive(request.active() != null ? Boolean.TRUE.equals(request.active()) ? Boolean.TRUE : existing.getIsActive() : existing.getIsActive());
        existing.setUpdatedBy(userId);
        return ResponseEntity.ok(toConnectorInstanceResponse(connectorInstanceService.save(existing)));
    }

    @PatchMapping("/integrations/connector-instances/{id}/status")
    public ResponseEntity<ConnectorInstanceResponse> updateConnectorStatus(@PathVariable UUID id, @RequestBody StatusRequest request) {
        UUID tenantId = requireTenantId();
        return ResponseEntity.ok(toConnectorInstanceResponse(connectorInstanceService.activate(tenantId, id, request.active())));
    }

    @GetMapping("/integrations/connector-instances/{id}/credentials")
    public ResponseEntity<CredentialStatusResponse> getCredentialStatus(@PathVariable UUID id) {
        UUID tenantId = requireTenantId();
        Optional<ConnectorCredential> credential = connectorCredentialService.findByTenantId(tenantId).stream()
            .filter(item -> item.getConnectorInstance() != null && item.getConnectorInstance().getId().equals(id))
            .findFirst();
        return ResponseEntity.ok(new CredentialStatusResponse(
            credential.filter(item -> Boolean.TRUE.equals(item.getIsActive())).isPresent(),
            credential.map(item -> item != null ? item.getAuthType() : "PROVIDER_SPECIFIC").orElse("PROVIDER_SPECIFIC")));
    }

    @PutMapping("/integrations/connector-instances/{id}/credentials")
    public ResponseEntity<Map<String, Object>> saveCredentials(@PathVariable UUID id, @RequestBody CredentialsRequest request) {
        UUID tenantId = requireTenantId();
        UUID userId = requireUserId();
        ConnectorInstance instance = connectorInstanceService.findById(tenantId, id)
            .orElseThrow(() -> new BusinessException("NOT_FOUND", "Connector instance not found"));
        ConnectorCredential credential = connectorCredentialService.findByTenantId(tenantId).stream()
            .filter(item -> item.getConnectorInstance() != null && item.getConnectorInstance().getId().equals(id))
            .findFirst()
            .orElseGet(() -> ConnectorCredential.builder().tenantId(tenantId).connectorInstance(instance).build());
        credential.setTenantId(tenantId);
        credential.setConnectorInstance(instance);
        credential.setCredentialName("primary");
        credential.setAuthType(request.authType() != null && !request.authType().isBlank() ? request.authType() : "PROVIDER_SPECIFIC");
        credential.setEncryptedValue(serializeCredentialValues(request.values()));
        credential.setMetadata(Map.of("providerNeutral", true));
        credential.setIsActive(true);
        credential.setCreatedBy(userId);
        credential.setUpdatedBy(userId);
        connectorCredentialService.save(credential);
        return ResponseEntity.ok(Map.of("configured", true));
    }

    @GetMapping("/integrations/connector-instances/{id}/webhook-config")
    public ResponseEntity<WebhookConfigResponse> getWebhookConfig(@PathVariable UUID id) {
        UUID tenantId = requireTenantId();
        ConnectorInstance instance = connectorInstanceService.findById(tenantId, id)
            .orElseThrow(() -> new BusinessException("NOT_FOUND", "Connector instance not found"));
        Optional<ConnectorWebhookConfig> config = connectorWebhookConfigService.findByTenantAndConnector(tenantId, id);
        if (config.isEmpty()) {
            return ResponseEntity.ok(new WebhookConfigResponse(id, buildWebhookUrl(instance, "call-connect"), buildWebhookUrl(instance, "cdr"), null, false, null, false, null));
        }
        ConnectorWebhookConfig current = config.get();
        return ResponseEntity.ok(new WebhookConfigResponse(current.getId(), buildWebhookUrl(instance, "call-connect"), buildWebhookUrl(instance, "cdr"), current.getTargetUrl(), Boolean.TRUE.equals(current.getIsActive()), current.getVerificationMode(), current.getVerificationSecret() != null && !current.getVerificationSecret().isBlank(), current.getWebhookName()));
    }

    @PutMapping("/integrations/connector-instances/{id}/webhook-config")
    public ResponseEntity<WebhookConfigResponse> saveWebhookConfig(@PathVariable UUID id, @RequestBody WebhookConfigRequest request) {
        UUID tenantId = requireTenantId();
        UUID userId = requireUserId();
        ConnectorInstance instance = connectorInstanceService.findById(tenantId, id)
            .orElseThrow(() -> new BusinessException("NOT_FOUND", "Connector instance not found"));
        ConnectorWebhookConfig config = connectorWebhookConfigService.findByTenantAndConnector(tenantId, id)
            .orElseGet(() -> ConnectorWebhookConfig.builder().tenantId(tenantId).connectorInstance(instance).build());
        config.setTenantId(tenantId);
        config.setConnectorInstance(instance);
        config.setWebhookName(request.webhookName() != null && !request.webhookName().isBlank() ? request.webhookName() : "Calling webhook");
        config.setTargetUrl(request.targetUrl() != null && !request.targetUrl().isBlank() ? request.targetUrl() : buildWebhookUrl(instance, "call-connect"));
        config.setVerificationMode(request.verificationMode() != null && !request.verificationMode().isBlank() ? request.verificationMode() : "HMAC");
        config.setEventTypes(request.eventTypes() != null ? new HashMap<>(request.eventTypes()) : Map.of());
        config.setIsActive(request.active() != null ? Boolean.TRUE.equals(request.active()) ? Boolean.TRUE : Boolean.FALSE : Boolean.FALSE);
        config.setCreatedBy(config.getCreatedBy() != null ? config.getCreatedBy() : userId);
        config.setUpdatedBy(userId);
        if (config.getVerificationSecret() == null || config.getVerificationSecret().isBlank()) {
            config.setVerificationSecret(UUID.randomUUID().toString());
        }
        ConnectorWebhookConfig saved = connectorWebhookConfigService.save(config);
        log.info("Webhook config updated tenant={} connector={} active={} verificationMode={}", tenantId, id, Boolean.TRUE.equals(saved.getIsActive()), saved.getVerificationMode());
        return ResponseEntity.ok(new WebhookConfigResponse(saved.getId(), buildWebhookUrl(instance, "call-connect"), buildWebhookUrl(instance, "cdr"), saved.getTargetUrl(), Boolean.TRUE.equals(saved.getIsActive()), saved.getVerificationMode(), saved.getVerificationSecret() != null && !saved.getVerificationSecret().isBlank(), saved.getWebhookName()));
    }

    @PostMapping("/integrations/connector-instances/{id}/webhook-config/regenerate-secret")
    public ResponseEntity<Map<String, Object>> regenerateSecret(@PathVariable UUID id) {
        UUID tenantId = requireTenantId();
        String secret = connectorWebhookConfigService.regenerateSecret(tenantId, id);
        log.info("Webhook secret regenerated tenant={} connector={} configured=true", tenantId, id);
        return ResponseEntity.ok(Map.of("secret", secret, "configured", true));
    }

    @GetMapping("/call-settings/connect-triggers")
    public ResponseEntity<List<CallConnectTriggerResponse>> listConnectTriggers() {
        UUID tenantId = requireTenantId();
        return ResponseEntity.ok(callConnectTriggerService.findByTenantId(tenantId).stream().map(this::toTriggerResponse).toList());
    }

    @PostMapping("/call-settings/connect-triggers")
    public ResponseEntity<CallConnectTriggerResponse> createConnectTrigger(@RequestBody ConnectTriggerRequest request) {
        UUID tenantId = requireTenantId();
        UUID userId = requireUserId();
        CallConnectTrigger trigger = CallConnectTrigger.builder()
            .tenantId(tenantId)
            .triggerKey(request.triggerKey() != null && !request.triggerKey().isBlank() ? request.triggerKey() : "call-connect")
            .callDirection(request.direction() != null && !request.direction().isBlank() ? request.direction() : "BOTH")
            .openActionType(request.openAction() != null && !request.openAction().isBlank() ? request.openAction() : "NO_ACTION")
            .entityType(request.entityType())
            .entityResolveBy(request.resolveBy())
            .targetRoute(request.route())
            .isActive(Boolean.TRUE.equals(request.active()))
            .priority(request.priority())
            .config(buildTriggerConfig(request))
            .createdBy(userId)
            .updatedBy(userId)
            .build();
        return ResponseEntity.ok(toTriggerResponse(callConnectTriggerService.save(trigger)));
    }

    @PutMapping("/call-settings/connect-triggers/{id}")
    public ResponseEntity<CallConnectTriggerResponse> updateConnectTrigger(@PathVariable UUID id, @RequestBody ConnectTriggerRequest request) {
        UUID tenantId = requireTenantId();
        UUID userId = requireUserId();
        CallConnectTrigger trigger = callConnectTriggerService.findById(id)
            .orElseThrow(() -> new BusinessException("NOT_FOUND", "Trigger not found"));
        if (!tenantId.equals(trigger.getTenantId())) {
            throw new BusinessException("FORBIDDEN", "Cannot access trigger from another tenant");
        }
        trigger.setTriggerKey(request.triggerKey() != null && !request.triggerKey().isBlank() ? request.triggerKey() : trigger.getTriggerKey());
        trigger.setCallDirection(request.direction() != null && !request.direction().isBlank() ? request.direction() : trigger.getCallDirection());
        trigger.setOpenActionType(request.openAction() != null && !request.openAction().isBlank() ? request.openAction() : trigger.getOpenActionType());
        trigger.setEntityType(request.entityType());
        trigger.setEntityResolveBy(request.resolveBy());
        trigger.setTargetRoute(request.route());
        trigger.setIsActive(request.active() != null ? Boolean.TRUE.equals(request.active()) ? Boolean.TRUE : trigger.getIsActive() : trigger.getIsActive());
        trigger.setPriority(request.priority());
        trigger.setConfig(buildTriggerConfig(request));
        trigger.setUpdatedBy(userId);
        return ResponseEntity.ok(toTriggerResponse(callConnectTriggerService.save(trigger)));
    }

    @DeleteMapping("/call-settings/connect-triggers/{id}")
    public ResponseEntity<Void> deleteConnectTrigger(@PathVariable UUID id) {
        UUID tenantId = requireTenantId();
        CallConnectTrigger trigger = callConnectTriggerService.findById(id)
            .orElseThrow(() -> new BusinessException("NOT_FOUND", "Trigger not found"));
        if (!tenantId.equals(trigger.getTenantId())) {
            throw new BusinessException("FORBIDDEN", "Cannot access trigger from another tenant");
        }
        callConnectTriggerRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/call-settings/layout-config")
    public ResponseEntity<LayoutConfigResponse> getLayoutConfig() {
        UUID tenantId = requireTenantId();
        return callLayoutConfigService.findByTenantId(tenantId).stream().findFirst()
            .map(this::toLayoutConfigResponse)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.ok(new LayoutConfigResponse(null, "PAGE", true, true, true, true, true, true, true)));
    }

    @PutMapping("/call-settings/layout-config")
    public ResponseEntity<LayoutConfigResponse> saveLayoutConfig(@RequestBody LayoutConfigRequest request) {
        UUID tenantId = requireTenantId();
        UUID userId = requireUserId();
        CallLayoutConfig config = callLayoutConfigService.findByTenantId(tenantId).stream().findFirst()
            .orElseGet(CallLayoutConfig::new);
        config.setTenantId(tenantId);
        config.setLayoutName(request.layoutName() != null && !request.layoutName().isBlank() ? request.layoutName() : "Default call layout");
        config.setDisplayMode(request.displayMode() != null && !request.displayMode().isBlank() ? request.displayMode() : "PAGE");
        Map<String, Object> layoutConfig = new HashMap<>();
        layoutConfig.put("showEntityDetails", Boolean.TRUE.equals(request.showEntityDetails()));
        layoutConfig.put("showCallHistory", Boolean.TRUE.equals(request.showCallHistory()));
        layoutConfig.put("showNotes", Boolean.TRUE.equals(request.showNotes()));
        layoutConfig.put("showDisposition", Boolean.TRUE.equals(request.showDisposition()));
        config.setLayoutConfig(layoutConfig);
        config.setIsActive(request.active() != null ? Boolean.TRUE.equals(request.active()) ? Boolean.TRUE : Boolean.FALSE : Boolean.FALSE);
        config.setIsDefault(true);
        config.setCreatedBy(config.getCreatedBy() != null ? config.getCreatedBy() : userId);
        config.setUpdatedBy(userId);
        return ResponseEntity.ok(toLayoutConfigResponse(callLayoutConfigService.save(config)));
    }

    private ProviderResponse toProviderResponse(ProviderDefinition provider) {
        List<String> supportedActions = providerActionDefinitionRepository.findAll().stream()
            .filter(action -> provider.getId() != null && provider.getId().equals(action.getProvider() != null ? action.getProvider().getId() : null))
            .filter(action -> action != null && Boolean.TRUE.equals(action.getIsActive()))
            .map(action -> action != null ? action.getActionKey() : null)
            .filter(action -> action != null)
            .toList();
        List<String> supportedTriggers = providerTriggerDefinitionRepository.findAll().stream()
            .filter(trigger -> provider.getId() != null && provider.getId().equals(trigger.getProvider() != null ? trigger.getProvider().getId() : null))
            .filter(trigger -> trigger != null && Boolean.TRUE.equals(trigger.getIsActive()))
            .map(trigger -> trigger != null ? trigger.getTriggerKey() : null)
            .filter(trigger -> trigger != null)
            .toList();
        return new ProviderResponse(provider.getId(), provider.getProviderKey(), provider.getProviderName(), provider.getCategory(), supportedActions, supportedTriggers, List.of("API_KEY", "BASIC"), supportedTriggers.stream().filter(trigger -> trigger != null).toList(), Boolean.TRUE.equals(provider.getIsActive()));
    }

    private ConnectorInstanceResponse toConnectorInstanceResponse(ConnectorInstance instance) {
        return new ConnectorInstanceResponse(instance.getId(), instance.getProvider() != null ? instance.getProvider().getProviderKey() : null,
            instance.getProvider() != null ? instance.getProvider().getProviderName() : null,
            instance.getConnectorName(), instance.getEnvironment(), instance.getBaseUrl(), Boolean.TRUE.equals(instance.getIsActive()), instance.getConfig());
    }

    private CallConnectTriggerResponse toTriggerResponse(CallConnectTrigger trigger) {
        Map<String, Object> config = trigger.getConfig() != null ? trigger.getConfig() : Map.of();
        return new CallConnectTriggerResponse(trigger.getId(), trigger.getTriggerKey(),
            config.containsKey("name") ? String.valueOf(config.get("name")) : trigger.getTriggerKey(),
            Boolean.TRUE.equals(trigger.getIsActive()), trigger.getCallDirection(), trigger.getEntityResolveBy(), trigger.getEntityType(),
            trigger.getOpenActionType(), config.containsKey("displayMode") ? String.valueOf(config.get("displayMode")) : "PAGE",
            trigger.getPriority() != null ? trigger.getPriority() : 0);
    }

    private LayoutConfigResponse toLayoutConfigResponse(CallLayoutConfig config) {
        Map<String, Object> layoutConfig = config.getLayoutConfig() != null ? config.getLayoutConfig() : Map.of();
        return new LayoutConfigResponse(config.getId(), config.getDisplayMode() != null ? config.getDisplayMode() : "PAGE",
            Boolean.TRUE.equals(config.getIsActive()),
            Boolean.TRUE.equals(layoutConfig.getOrDefault("showEntityDetails", true)),
            Boolean.TRUE.equals(layoutConfig.getOrDefault("showCallHistory", true)),
            Boolean.TRUE.equals(layoutConfig.getOrDefault("showNotes", true)),
            Boolean.TRUE.equals(layoutConfig.getOrDefault("showDisposition", true)),
            Boolean.TRUE.equals(layoutConfig.getOrDefault("showEntityDetails", true)),
            Boolean.TRUE.equals(layoutConfig.getOrDefault("showNotes", true)));
    }

    private Map<String, Object> buildTriggerConfig(ConnectTriggerRequest request) {
        Map<String, Object> config = new HashMap<>();
        config.put("name", request.name() != null && !request.name().isBlank() ? request.name() : "Call trigger");
        config.put("displayMode", request.displayMode() != null && !request.displayMode().isBlank() ? request.displayMode() : "PAGE");
        config.put("resolveStrategy", request.resolveBy() != null && !request.resolveBy().isBlank() ? request.resolveBy() : "ENTITY");
        config.put("openAction", request.openAction() != null && !request.openAction().isBlank() ? request.openAction() : "NO_ACTION");
        return config;
    }

    private String serializeCredentialValues(Map<String, Object> values) {
        try {
            return objectMapper.writeValueAsString(values != null ? values : Map.of());
        } catch (JsonProcessingException e) {
            throw new BusinessException("INVALID_REQUEST", "Unable to serialize credential payload");
        }
    }

    private String buildWebhookUrl(ConnectorInstance instance, String triggerKey) {
        String tenantSlug = instance.getTenantId() != null ? instance.getTenantId().toString() : "tenant";
        String providerKey = instance.getProvider() != null ? instance.getProvider().getProviderKey() : "provider";
        return "/api/v1/webhooks/connectors/" + tenantSlug + "/" + providerKey + "/" + triggerKey;
    }

    private UUID requireTenantId() {
        UUID tenantId = tenantContext.getTenantId();
        if (tenantId == null) {
            throw new BusinessException("INVALID_REQUEST", "Tenant context is required");
        }
        return tenantId;
    }

    private UUID requireUserId() {
        UUID userId = tenantContext.getUserId();
        if (userId == null) {
            throw new BusinessException("INVALID_REQUEST", "User context is required");
        }
        return userId;
    }

    private boolean matchesCategory(ProviderDefinition provider, String category) {
        if (provider == null || provider.getCategory() == null) {
            return true;
        }
        return provider.getCategory().equalsIgnoreCase(category);
    }

    public record ProviderResponse(UUID id, String providerKey, String displayName, String category, List<String> supportedActions,
                                   List<String> supportedTriggers, List<String> supportedAuthTypes, List<String> defaultWebhookTriggerKeys,
                                   boolean active) {}

    public record ConnectorInstanceRequest(String providerKey, String name, String environment, String baseUrl, Map<String, Object> config, Boolean active) {}

    public record ConnectorInstanceResponse(UUID id, String providerKey, String providerName, String connectorName, String environment,
                                            String baseUrl, boolean active, Map<String, Object> config) {}

    public record StatusRequest(boolean active) {}

    public record CredentialsRequest(String authType, Map<String, Object> values) {}

    public record WebhookConfigRequest(String webhookName, String targetUrl, String verificationMode, Map<String, Object> eventTypes, Boolean active) {}

    public record WebhookConfigResponse(UUID id, String callConnectUrl, String cdrUrl, String targetUrl, boolean active, String verificationMode, boolean configured, String webhookName) {}

    public record CredentialStatusResponse(boolean configured, String authType) {}

    public record ConnectTriggerRequest(String name, Boolean active, String direction, String resolveBy, String entityType, String openAction,
                                        String displayMode, String route, Integer priority, String triggerKey) {}

    public record CallConnectTriggerResponse(UUID id, String triggerKey, String name, boolean active, String direction, String resolveBy,
                                             String entityType, String openAction, String displayMode, int priority) {}

    public record LayoutConfigRequest(String layoutName, String displayMode, Boolean showEntityDetails, Boolean showCallHistory,
                                      Boolean showNotes, Boolean showDisposition, Boolean active) {}

    public record LayoutConfigResponse(UUID id, String displayMode, boolean active, boolean showEntityDetails, boolean showCallHistory,
                                       boolean showNotes, boolean showDisposition, boolean showLeadContext, boolean showCallHistorySection) {}
}
