package com.shivang.crm.modules.integration.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import tools.jackson.databind.ObjectMapper;

import com.shivang.crm.modules.auth.security.TenantContext;
import com.shivang.crm.modules.dialer.repository.CallConnectTriggerRepository;
import com.shivang.crm.modules.dialer.service.CallConnectTriggerService;
import com.shivang.crm.modules.dialer.service.CallLayoutConfigService;
import com.shivang.crm.modules.integration.entity.ConnectorCredential;
import com.shivang.crm.modules.integration.entity.ConnectorInstance;
import com.shivang.crm.modules.integration.entity.ProviderDefinition;
import com.shivang.crm.modules.integration.repository.ProviderActionDefinitionRepository;
import com.shivang.crm.modules.integration.repository.ProviderDefinitionRepository;
import com.shivang.crm.modules.integration.repository.ProviderTriggerDefinitionRepository;
import com.shivang.crm.modules.integration.service.ConnectorCredentialService;
import com.shivang.crm.modules.integration.service.ConnectorInstanceService;
import com.shivang.crm.modules.integration.service.ConnectorWebhookConfigService;
import com.shivang.crm.modules.integration.service.ProviderRegistryService;
import com.shivang.crm.shared.exception.BusinessException;

class VoiceGenericSipAdminTest {

    private TenantContext tenantContext;
    private ProviderRegistryService providerRegistryService;
    private ConnectorInstanceService connectorInstanceService;
    private ConnectorCredentialService connectorCredentialService;
    private CallingAdminController controller;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID connectorId = UUID.randomUUID();

    private ProviderDefinition voiceProvider;
    private ConnectorInstance voiceInstance;

    @BeforeEach
    void setUp() {
        tenantContext = mock(TenantContext.class);
        providerRegistryService = mock(ProviderRegistryService.class);
        connectorInstanceService = mock(ConnectorInstanceService.class);
        connectorCredentialService = mock(ConnectorCredentialService.class);
        controller = new CallingAdminController(
                tenantContext,
                providerRegistryService,
                connectorInstanceService,
                connectorCredentialService,
                mock(ConnectorWebhookConfigService.class),
                mock(CallConnectTriggerService.class),
                mock(CallLayoutConfigService.class),
                mock(ProviderDefinitionRepository.class),
                mock(ProviderActionDefinitionRepository.class),
                mock(ProviderTriggerDefinitionRepository.class),
                mock(CallConnectTriggerRepository.class));

        when(tenantContext.getTenantId()).thenReturn(tenantId);
        when(tenantContext.getUserId()).thenReturn(userId);

        voiceProvider = ProviderDefinition.builder()
                .providerKey("sellspark_voice")
                .providerName("SellSpark Voice")
                .category("CALLING")
                .isActive(true)
                .build();

        Map<String, Object> config = new HashMap<>();
        config.put("endpoint", "wss://pbx.example.com:8089/ws");
        config.put("sipDomain", "pbx.example.com");
        config.put("username", "1001");
        config.put("iceServers", List.of(Map.of(
                "urls", "turn:turn.example.com:3478",
                "username", "turnuser",
                "credential", "turn-secret-value")));

        voiceInstance = ConnectorInstance.builder()
                .tenantId(tenantId)
                .provider(voiceProvider)
                .connectorName("Generic SIP")
                .environment("PRODUCTION")
                .config(config)
                .isActive(true)
                .build();
        voiceInstance.setId(connectorId);

        when(providerRegistryService.findByProviderKey("sellspark_voice"))
                .thenReturn(Optional.of(voiceProvider));
        when(connectorInstanceService.findActiveByTenantAndProvider(tenantId, "sellspark_voice"))
                .thenReturn(Optional.of(voiceInstance));
        when(connectorInstanceService.save(any(ConnectorInstance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void getReturnsNotConfiguredWhenAbsent() {
        when(connectorInstanceService.findActiveByTenantAndProvider(tenantId, "sellspark_voice"))
                .thenReturn(Optional.empty());
        when(connectorInstanceService.findByTenantId(tenantId)).thenReturn(List.of());

        CallingAdminController.GenericSipConnectionResponse response =
                controller.getGenericSipConnection().getBody();

        assertThat(response).isNotNull();
        assertThat(response.configured()).isFalse();
        assertThat(response.secretConfigured()).isFalse();
        assertThat(response.connectorInstanceId()).isNull();
    }

    @Test
    void getNeverExposesSecrets() throws Exception {
        ConnectorCredential credential = tenantCredential("{\"userId\":\"1001\",\"password\":\"s3cret\"}");
        when(connectorCredentialService.findByTenantIdAndConnectorInstanceIdAndCreatedByIsNullAndIsActiveTrue(tenantId, connectorId))
                .thenReturn(List.of(credential));

        CallingAdminController.GenericSipConnectionResponse response =
                controller.getGenericSipConnection().getBody();

        assertThat(response.secretConfigured()).isTrue();
        assertThat(response.secretScope()).isEqualTo("TENANT");
        String json = new ObjectMapper().writeValueAsString(response);
        assertThat(json).doesNotContain("s3cret");
        assertThat(json).doesNotContain("turn-secret-value");
        assertThat(json).doesNotContain("password");
        assertThat(json).doesNotContain("encryptedValue");
        assertThat(response.config().iceServers()).hasSize(1);
        assertThat(response.config().iceServers().get(0).credentialConfigured()).isTrue();
        assertThat(response.config().iceServers().get(0).username()).isEqualTo("turnuser");
    }

    @Test
    void putRejectsNonWssEndpoints() {
        assertThatThrownBy(() -> controller.saveGenericSipConnection(
                new CallingAdminController.GenericSipConnectionRequest(
                        "http://pbx.example.com/ws", "pbx.example.com", "1001", List.of(), null, null)))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> controller.saveGenericSipConnection(
                new CallingAdminController.GenericSipConnectionRequest(
                        "ws://pbx.example.com/ws", "pbx.example.com", "1001", List.of(), null, null)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void putRejectsMissingFieldsAndBadIce() {
        assertThatThrownBy(() -> controller.saveGenericSipConnection(
                new CallingAdminController.GenericSipConnectionRequest(
                        "wss://pbx.example.com/ws", "", "1001", List.of(), null, null)))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> controller.saveGenericSipConnection(
                new CallingAdminController.GenericSipConnectionRequest(
                        "wss://pbx.example.com/ws", "pbx.example.com", "", List.of(), null, null)))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> controller.saveGenericSipConnection(
                new CallingAdminController.GenericSipConnectionRequest(
                        "wss://pbx.example.com/ws", "pbx.example.com", "1001",
                        List.of(new CallingAdminController.GenericSipIceServerRequest("https://x.example.com", null, null)),
                        null, null)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void putCreatesInstanceAndStoresTenantSecret() {
        when(connectorInstanceService.findActiveByTenantAndProvider(tenantId, "sellspark_voice"))
                .thenReturn(Optional.empty());
        when(connectorInstanceService.findByTenantId(tenantId)).thenReturn(List.of());
        when(connectorCredentialService.findByTenantIdAndConnectorInstanceIdAndCreatedByIsNullAndIsActiveTrue(
                any(UUID.class), any(UUID.class))).thenReturn(List.of());
        when(connectorCredentialService.save(any(ConnectorCredential.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CallingAdminController.GenericSipConnectionResponse response = controller.saveGenericSipConnection(
                new CallingAdminController.GenericSipConnectionRequest(
                        "wss://pbx.example.com:8089/ws", "pbx.example.com", "1001",
                        List.of(new CallingAdminController.GenericSipIceServerRequest("stun:stun.example.com:3478", null, null)),
                        "new-secret", null)).getBody();

        assertThat(response.configured()).isTrue();
        assertThat(response.config().endpoint()).isEqualTo("wss://pbx.example.com:8089/ws");

        ArgumentCaptor<ConnectorCredential> captor = ArgumentCaptor.forClass(ConnectorCredential.class);
        verify(connectorCredentialService).save(captor.capture());
        ConnectorCredential saved = captor.getValue();
        assertThat(saved.getCredentialName()).isEqualTo("primary");
        assertThat(saved.getCredentialScope()).isEqualTo("TENANT");
        assertThat(saved.getCreatedBy()).isNull();
        assertThat(saved.getTenantId()).isEqualTo(tenantId);
        assertThat(saved.getEncryptedValue()).contains("new-secret").contains("1001");

        ArgumentCaptor<ConnectorInstance> instanceCaptor = ArgumentCaptor.forClass(ConnectorInstance.class);
        verify(connectorInstanceService).save(instanceCaptor.capture());
        assertThat(instanceCaptor.getValue().getTenantId()).isEqualTo(tenantId);
    }

    @Test
    void putWithBlankSecretPreservesExistingSecret() {
        ConnectorCredential credential = tenantCredential("{\"userId\":\"1001\",\"password\":\"keep-me\"}");
        when(connectorCredentialService.findByTenantIdAndConnectorInstanceIdAndCreatedByIsNullAndIsActiveTrue(tenantId, connectorId))
                .thenReturn(List.of(credential));
        when(connectorCredentialService.decryptValue(credential)).thenReturn("{\"userId\":\"1001\",\"password\":\"keep-me\"}");
        when(connectorCredentialService.save(any(ConnectorCredential.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        controller.saveGenericSipConnection(
                new CallingAdminController.GenericSipConnectionRequest(
                        "wss://pbx.example.com:8089/ws", "pbx.example.com", "1002", List.of(), null, null));

        ArgumentCaptor<ConnectorCredential> captor = ArgumentCaptor.forClass(ConnectorCredential.class);
        verify(connectorCredentialService).save(captor.capture());
        assertThat(captor.getValue().getEncryptedValue()).contains("keep-me").contains("1002");
        verify(connectorCredentialService, never()).deactivateTenantCredentials(any(), any(), any(), any());
    }

    @Test
    void putRemoveSecretDeactivatesSharedCredential() {
        ConnectorCredential credential = tenantCredential("{\"userId\":\"1001\",\"password\":\"s3cret\"}");
        when(connectorCredentialService.findByTenantIdAndConnectorInstanceIdAndCreatedByIsNullAndIsActiveTrue(tenantId, connectorId))
                .thenReturn(List.of(credential));

        CallingAdminController.GenericSipConnectionResponse response = controller.saveGenericSipConnection(
                new CallingAdminController.GenericSipConnectionRequest(
                        "wss://pbx.example.com:8089/ws", "pbx.example.com", "1001", List.of(), null, true)).getBody();

        verify(connectorCredentialService).deactivateTenantCredentials(tenantId, connectorId, "primary", userId);
        verify(connectorCredentialService, never()).save(any(ConnectorCredential.class));
        // Response reflects the mocked pre-deactivation read; the behavioral
        // assertions above (deactivate called, no credential saved) are authoritative.
    }

    @Test
    void nonCallingProviderIsRejected() {
        voiceProvider.setCategory("SMS");
        assertThatThrownBy(() -> controller.saveGenericSipConnection(
                new CallingAdminController.GenericSipConnectionRequest(
                        "wss://pbx.example.com/ws", "pbx.example.com", "1001", List.of(), null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("calling provider");
    }

    private ConnectorCredential tenantCredential(String encryptedValue) {
        ConnectorCredential credential = ConnectorCredential.builder()
                .tenantId(tenantId)
                .connectorInstance(voiceInstance)
                .credentialName("primary")
                .authType("PROVIDER_SPECIFIC")
                .credentialScope("TENANT")
                .encryptedValue(encryptedValue)
                .isActive(true)
                .build();
        credential.setId(UUID.randomUUID());
        return credential;
    }
}
