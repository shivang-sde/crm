package com.shivang.crm.modules.workflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * L3.0-P1 Change D: registry collisions must fail deterministically instead of
 * silently keeping the first registration.
 */
class WorkflowRegistryCollisionTest {

    // --- context provider registry ---

    @Test
    void contextProviderRegistryAcceptsUniqueRegistrations() {
        assertThatCode(() -> new WorkflowEntityContextProviderRegistry(
                        List.of(provider("LEAD"), provider("DEAL"), provider("ACCOUNT"))))
                .doesNotThrowAnyException();
    }

    @Test
    void contextProviderRegistryExposesSupportedEntityTypes() {
        WorkflowEntityContextProviderRegistry registry =
                new WorkflowEntityContextProviderRegistry(List.of(provider("LEAD"), provider("DEAL")));

        assertThat(registry.supportedEntityTypes()).containsExactlyInAnyOrder("LEAD", "DEAL");
        assertThat(registry.supports("lead")).isTrue();
        assertThat(registry.supports("TICKET")).isFalse();
        assertThat(registry.supports(null)).isFalse();
    }

    @Test
    void contextProviderRegistryRejectsDuplicateEntityType() {
        assertThatThrownBy(() -> new WorkflowEntityContextProviderRegistry(
                        List.of(provider("DEAL"), provider("DEAL"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("WorkflowEntityContextProviderRegistry")
                .hasMessageContaining("DEAL");
    }

    @Test
    void contextProviderRegistryRejectsDuplicateRegardlessOfCase() {
        assertThatThrownBy(() -> new WorkflowEntityContextProviderRegistry(
                        List.of(provider("DEAL"), provider("deal"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DEAL");
    }

    @Test
    void contextProviderRegistryIdentifiesBothConflictingBeans() {
        assertThatThrownBy(() -> new WorkflowEntityContextProviderRegistry(
                        List.of(provider("DEAL"), provider("DEAL"))))
                .hasMessageContaining("WorkflowEntityContextProvider");
    }

    // --- update adapter registry ---

    @Test
    void updateAdapterRegistryAcceptsUniqueRegistrations() {
        assertThatCode(() -> new WorkflowEntityUpdateAdapterRegistry(
                        List.of(updateAdapter("DEAL"), updateAdapter("LEAD"))))
                .doesNotThrowAnyException();
    }

    @Test
    void updateAdapterRegistryRejectsDuplicateEntityType() {
        assertThatThrownBy(() -> new WorkflowEntityUpdateAdapterRegistry(
                        List.of(updateAdapter("DEAL"), updateAdapter("DEAL"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("WorkflowEntityUpdateAdapterRegistry")
                .hasMessageContaining("DEAL");
    }

    // --- owner assignment adapter registry ---

    @Test
    void ownerAssignmentAdapterRegistryAcceptsUniqueRegistrations() {
        assertThatCode(() -> new WorkflowOwnerAssignmentAdapterRegistry(
                        List.of(ownerAdapter("DEAL"), ownerAdapter("LEAD"))))
                .doesNotThrowAnyException();
    }

    @Test
    void ownerAssignmentAdapterRegistryRejectsDuplicateEntityType() {
        assertThatThrownBy(() -> new WorkflowOwnerAssignmentAdapterRegistry(
                        List.of(ownerAdapter("DEAL"), ownerAdapter("DEAL"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("WorkflowOwnerAssignmentAdapterRegistry")
                .hasMessageContaining("DEAL");
    }

    // --- helpers ---

    private WorkflowEntityContextProvider provider(String entityType) {
        WorkflowEntityContextProvider provider = mock(WorkflowEntityContextProvider.class);
        when(provider.entityType()).thenReturn(entityType);
        when(provider.load(any(), any(UUID.class))).thenReturn(Optional.empty());
        return provider;
    }

    private WorkflowEntityUpdateAdapter updateAdapter(String entityType) {
        WorkflowEntityUpdateAdapter adapter = mock(WorkflowEntityUpdateAdapter.class);
        when(adapter.entityType()).thenReturn(entityType);
        return adapter;
    }

    private WorkflowOwnerAssignmentAdapter ownerAdapter(String entityType) {
        WorkflowOwnerAssignmentAdapter adapter = mock(WorkflowOwnerAssignmentAdapter.class);
        when(adapter.entityType()).thenReturn(entityType);
        return adapter;
    }
}