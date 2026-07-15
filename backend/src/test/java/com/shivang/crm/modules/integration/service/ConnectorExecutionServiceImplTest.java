package com.shivang.crm.modules.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.shivang.crm.modules.integration.dto.ConnectorExecutionRequest;
import com.shivang.crm.modules.integration.dto.ConnectorExecutionResult;
import com.shivang.crm.modules.integration.entity.ConnectorExecution;
import com.shivang.crm.modules.integration.entity.ConnectorInstance;
import com.shivang.crm.modules.integration.entity.ProviderActionDefinition;
import com.shivang.crm.modules.integration.entity.ProviderDefinition;
import com.shivang.crm.modules.integration.repository.ConnectorExecutionRepository;
import com.shivang.crm.modules.integration.service.impl.ConnectorExecutionServiceImpl;
import com.shivang.crm.modules.integration.service.impl.HttpProviderActionExecutor;
import com.shivang.crm.modules.integration.service.impl.SimpleTemplateResolver;
import com.shivang.crm.shared.exception.BusinessException;

class ConnectorExecutionServiceImplTest {

    @Test
    void executesSuccessfulFlowAndSavesExecutionAudit() {
        ConnectorExecutionRepository executionRepository = mock(ConnectorExecutionRepository.class);
        ProviderRegistryService providerRegistryService = mock(ProviderRegistryService.class);
        ConnectorInstanceService connectorInstanceService = mock(ConnectorInstanceService.class);
        ConnectorCredentialService credentialService = mock(ConnectorCredentialService.class);
        TemplateResolver templateResolver = new SimpleTemplateResolver();
        HttpProviderActionExecutor providerActionExecutor = mock(HttpProviderActionExecutor.class);
        ConnectorAuditSanitizer sanitizer = new ConnectorAuditSanitizer();

        ConnectorExecutionServiceImpl service = new ConnectorExecutionServiceImpl(
            executionRepository,
            providerRegistryService,
            connectorInstanceService,
            credentialService,
            templateResolver,
            providerActionExecutor,
            sanitizer
        );

        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID connectorId = UUID.randomUUID();

        ProviderDefinition provider = new ProviderDefinition();
        provider.setId(UUID.randomUUID());
        provider.setProviderKey("sellspark_voice");
        provider.setIsActive(true);

        ProviderActionDefinition action = new ProviderActionDefinition();
        action.setId(UUID.randomUUID());
        action.setActionKey("CLICK_TO_CALL");
        action.setIsActive(true);
        action.setEndpointTemplate("/dialer/clicktocall");
        action.setHttpMethod("POST");
        action.setRequestTemplate(Map.of("number", "{{input.phoneNumber}}"));

        ConnectorInstance connectorInstance = new ConnectorInstance();
        connectorInstance.setId(connectorId);
        connectorInstance.setTenantId(tenantId);
        connectorInstance.setBaseUrl("https://example.test");
        connectorInstance.setIsActive(true);
        connectorInstance.setProvider(provider);

        when(providerRegistryService.findByProviderKey("sellspark_voice")).thenReturn(Optional.of(provider));
        when(providerRegistryService.findActionByProviderKeyAndActionKey("sellspark_voice", "CLICK_TO_CALL")).thenReturn(Optional.of(action));
        when(connectorInstanceService.findActiveByTenantAndProvider(tenantId, "sellspark_voice")).thenReturn(Optional.of(connectorInstance));
        when(credentialService.resolveCredentialValue(tenantId, connectorId, userId, "username")).thenReturn(Optional.of("agent"));
        when(credentialService.resolveCredentialValue(tenantId, connectorId, userId, "password")).thenReturn(Optional.of("secret"));
        when(executionRepository.save(any(ConnectorExecution.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(providerActionExecutor.execute(any(), any(), any(), any())).thenReturn(new ConnectorExecutionResult(true, 200, Map.of("ok", true), Map.of(), Map.of(), null, 12L));

        ConnectorExecutionRequest request = new ConnectorExecutionRequest();
        request.setTenantId(tenantId);
        request.setUserId(userId);
        request.setProviderKey("sellspark_voice");
        request.setActionKey("CLICK_TO_CALL");
        request.setEntityType("lead");
        request.setEntityId(UUID.randomUUID());
        request.setEntityData(Map.of("id", UUID.randomUUID(), "phone", "1234567890"));
        request.setInputData(Map.of("phoneNumber", "9876543210"));

        ConnectorExecutionResult result = service.execute(request);

        assertThat(result.isSuccess()).isTrue();
        verify(executionRepository).save(any(ConnectorExecution.class));
    }

    @Test
    void failsClearlyWhenProviderIsMissing() {
        ConnectorExecutionRepository executionRepository = mock(ConnectorExecutionRepository.class);
        ProviderRegistryService providerRegistryService = mock(ProviderRegistryService.class);
        ConnectorInstanceService connectorInstanceService = mock(ConnectorInstanceService.class);
        ConnectorCredentialService credentialService = mock(ConnectorCredentialService.class);
        TemplateResolver templateResolver = new SimpleTemplateResolver();
        HttpProviderActionExecutor providerActionExecutor = mock(HttpProviderActionExecutor.class);
        ConnectorAuditSanitizer sanitizer = new ConnectorAuditSanitizer();

        ConnectorExecutionServiceImpl service = new ConnectorExecutionServiceImpl(
            executionRepository,
            providerRegistryService,
            connectorInstanceService,
            credentialService,
            templateResolver,
            providerActionExecutor,
            sanitizer
        );

        ConnectorExecutionRequest request = new ConnectorExecutionRequest();
        request.setTenantId(UUID.randomUUID());
        request.setUserId(UUID.randomUUID());
        request.setProviderKey("missing_provider");
        request.setActionKey("CLICK_TO_CALL");

        when(providerRegistryService.findByProviderKey("missing_provider")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.execute(request))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Provider");
    }
}
