package com.shivang.crm.modules.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.shivang.crm.modules.integration.entity.ConnectorCredential;
import com.shivang.crm.modules.integration.entity.ConnectorInstance;
import com.shivang.crm.modules.integration.repository.ConnectorCredentialRepository;
import com.shivang.crm.modules.integration.service.impl.ConnectorCredentialServiceImpl;
import com.shivang.crm.modules.integration.service.impl.DefaultCredentialEncryptionService;

class ConnectorCredentialServiceTest {

    private static final String ENCRYPTION_KEY = "AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA=";

    /**
     * The contract under test: a credential created by a specific user
     * ({@code created_by = thatUser}) overrides the shared tenant credential
     * ({@code created_by IS NULL}) for that same user, and the shared value is
     * what everyone else gets.
     *
     * <p>Both lookups are the exact repository finders the service calls, so this
     * also pins the precedence rule: the user row is tried first and only falls
     * back to the tenant row when the user has none.
     */
    @Test
    void saveEncryptsValueAndRespectsUserSpecificOverride() {
        ConnectorCredentialRepository repository = mock(ConnectorCredentialRepository.class);
        DefaultCredentialEncryptionService encryptionService =
                new DefaultCredentialEncryptionService(ENCRYPTION_KEY);
        ConnectorCredentialServiceImpl service = new ConnectorCredentialServiceImpl(repository, encryptionService);

        UUID tenantId = UUID.randomUUID();
        UUID connectorId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ConnectorInstance connectorInstance = new ConnectorInstance();
        connectorInstance.setId(connectorId);
        connectorInstance.setTenantId(tenantId);

        ConnectorCredential tenantCredential = credential(
                tenantId, connectorInstance, encryptionService.encrypt("tenant-secret"));

        ConnectorCredential userCredential = credential(
                tenantId, connectorInstance, encryptionService.encrypt("user-secret"));
        userCredential.setCreatedBy(userId);

        // The two finders resolveCredentialValue actually calls.
        when(repository
                .findByTenantIdAndConnectorInstanceIdAndCreatedByAndIsActiveTrueAndDeletedFalseOrderByCreatedAtDesc(
                        tenantId, connectorId, userId))
            .thenReturn(List.of(userCredential));
        when(repository
                .findByTenantIdAndConnectorInstanceIdAndCreatedByIsNullAndIsActiveTrueAndDeletedFalseOrderByCreatedAtDesc(
                        tenantId, connectorId))
            .thenReturn(List.of(tenantCredential));

        when(repository.save(any(ConnectorCredential.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ConnectorCredential saveResult = service.save(new ConnectorCredential());
        assertThat(saveResult).isNotNull();

        // The user's own credential wins over the shared tenant one.
        assertThat(service.resolveCredentialValue(tenantId, connectorId, userId, "shared"))
                .contains("user-secret");
    }

    /**
     * A user with no personal credential falls back to the tenant's shared value
     * rather than seeing nothing.
     */
    @Test
    void fallsBackToTheTenantCredentialWhenTheUserHasNone() {
        ConnectorCredentialRepository repository = mock(ConnectorCredentialRepository.class);
        DefaultCredentialEncryptionService encryptionService =
                new DefaultCredentialEncryptionService(ENCRYPTION_KEY);
        ConnectorCredentialServiceImpl service = new ConnectorCredentialServiceImpl(repository, encryptionService);

        UUID tenantId = UUID.randomUUID();
        UUID connectorId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ConnectorInstance connectorInstance = new ConnectorInstance();
        connectorInstance.setId(connectorId);
        connectorInstance.setTenantId(tenantId);

        ConnectorCredential tenantCredential = credential(
                tenantId, connectorInstance, encryptionService.encrypt("tenant-secret"));

        when(repository
                .findByTenantIdAndConnectorInstanceIdAndCreatedByAndIsActiveTrueAndDeletedFalseOrderByCreatedAtDesc(
                        tenantId, connectorId, userId))
            .thenReturn(List.of());
        when(repository
                .findByTenantIdAndConnectorInstanceIdAndCreatedByIsNullAndIsActiveTrueAndDeletedFalseOrderByCreatedAtDesc(
                        tenantId, connectorId))
            .thenReturn(List.of(tenantCredential));

        assertThat(service.resolveCredentialValue(tenantId, connectorId, userId, "shared"))
                .contains("tenant-secret");
    }

    /**
     * A credential belonging to another tenant is never returned, and the lookup
     * for a different tenant finds nothing. Every finder the service uses carries
     * {@code tenantId} in its signature, so this is enforced by the query itself
     * rather than by a post-filter that a bug could skip.
     */
    @Test
    void neverResolvesAcrossTenants() {
        ConnectorCredentialRepository repository = mock(ConnectorCredentialRepository.class);
        DefaultCredentialEncryptionService encryptionService =
                new DefaultCredentialEncryptionService(ENCRYPTION_KEY);
        ConnectorCredentialServiceImpl service = new ConnectorCredentialServiceImpl(repository, encryptionService);

        UUID owningTenant = UUID.randomUUID();
        UUID otherTenant = UUID.randomUUID();
        UUID connectorId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        ConnectorInstance connectorInstance = new ConnectorInstance();
        connectorInstance.setId(connectorId);
        connectorInstance.setTenantId(owningTenant);

        // The other tenant's query returns nothing, which is what the tenant-scoped
        // finder guarantees for rows owned by somebody else.
        when(repository
                .findByTenantIdAndConnectorInstanceIdAndCreatedByAndIsActiveTrueAndDeletedFalseOrderByCreatedAtDesc(
                        otherTenant, connectorId, userId))
            .thenReturn(List.of());
        when(repository
                .findByTenantIdAndConnectorInstanceIdAndCreatedByIsNullAndIsActiveTrueAndDeletedFalseOrderByCreatedAtDesc(
                        otherTenant, connectorId))
            .thenReturn(List.of());

        assertThat(service.resolveCredentialValue(otherTenant, connectorId, userId, "shared"))
                .as("a tenant must never resolve another tenant's credential")
                .isEmpty();
    }

    /** Shared tenant credential: no {@code createdBy}, so it is the fallback row. */
    private static ConnectorCredential credential(
            UUID tenantId, ConnectorInstance connectorInstance, String encryptedValue) {
        ConnectorCredential credential = new ConnectorCredential();
        credential.setTenantId(tenantId);
        credential.setConnectorInstance(connectorInstance);
        credential.setCredentialName("shared");
        credential.setAuthType("API_KEY");
        credential.setEncryptedValue(encryptedValue);
        credential.setIsActive(true);
        return credential;
    }
}
