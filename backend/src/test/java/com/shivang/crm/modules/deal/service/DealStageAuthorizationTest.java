package com.shivang.crm.modules.deal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.shivang.crm.modules.deal.dto.DealStageCreateRequest;
import com.shivang.crm.modules.deal.dto.DealStageUpdateRequest;
import com.shivang.crm.modules.deal.entity.DealStage;
import com.shivang.crm.modules.deal.entity.RecordCategory;
import com.shivang.crm.modules.deal.mapper.DealStageMapper;
import com.shivang.crm.modules.deal.repository.DealRepository;
import com.shivang.crm.modules.deal.repository.DealStageRepository;
import com.shivang.crm.modules.rbac.service.PermissionEvaluatorService;
import com.shivang.crm.modules.rbac.service.RecordScopeGuard;
import com.shivang.crm.shared.exception.BusinessException;
import com.shivang.crm.shared.exception.PermissionDeniedException;

/**
 * L3.0-P3: DealStage is tenant-wide pipeline configuration, so its mutations
 * require an effective ALL scope on the corresponding deal action.
 *
 * The real RecordScopeGuard is wired with a mocked PermissionEvaluatorService,
 * so these tests exercise the actual scope-resolution and fail-closed path rather
 * than a stubbed guard.
 */
class DealStageAuthorizationTest {

    private DealStageRepository dealStageRepository;
    private DealRepository dealRepository;
    private DealStageMapper dealStageMapper;
    private PermissionEvaluatorService permissionEvaluatorService;
    private DealStageService service;

    private UUID tenantId;
    private UUID userId;
    private UUID stageId;

    @BeforeEach
    void setup() {
        dealStageRepository = mock(DealStageRepository.class);
        dealRepository = mock(DealRepository.class);
        dealStageMapper = mock(DealStageMapper.class);
        permissionEvaluatorService = mock(PermissionEvaluatorService.class);

        service = new DealStageService(
                dealStageRepository, dealRepository, dealStageMapper,
                new RecordScopeGuard(permissionEvaluatorService, mock(com.shivang.crm.modules.auth.repository.UserRepository.class)));

        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        stageId = UUID.randomUUID();

        when(dealStageMapper.toResponse(any(DealStage.class)))
                .thenReturn(com.shivang.crm.modules.deal.dto.DealStageResponse.builder().build());
        when(dealStageRepository.save(any(DealStage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    /** Grant exactly one effective scope for the given deal action. */
    private void grantScope(String action, String scope) {
        when(permissionEvaluatorService.getAccessScope(userId, tenantId, "deal", action))
                .thenReturn(scope);
    }

    private DealStage persistedStage() {
        return persistedStage(false);
    }

    private DealStage persistedStage(boolean isDefault) {
        DealStage stage = new DealStage();
        stage.setId(stageId);
        stage.setTenantId(tenantId);
        stage.setName("Qualification");
        stage.setDisplayOrder(1);
        stage.setIsDefault(isDefault);
        stage.setRecordCategory(RecordCategory.OPEN);
        when(dealStageRepository.findByIdAndTenantId(stageId, tenantId)).thenReturn(Optional.of(stage));
        return stage;
    }

    private DealStageCreateRequest createRequest() {
        return DealStageCreateRequest.builder()
                .name("New Stage")
                .displayOrder(5)
                .recordCategory(RecordCategory.OPEN)
                .build();
    }

    // --- ALL scope is permitted ---

    @Test
    void allScopeCanCreateStage() {
        grantScope("write", "ALL");
        DealStage mapped = new DealStage();
        mapped.setName("New Stage");
        when(dealStageMapper.toEntity(any(DealStageCreateRequest.class))).thenReturn(mapped);
        when(dealStageRepository.findByTenantIdAndName(tenantId, "New Stage")).thenReturn(Optional.empty());

        assertThatNoException().isThrownBy(() -> service.createDealStage(tenantId, userId, createRequest()));
    }

    @Test
    void allScopeCanUpdateStage() {
        grantScope("write", "ALL");
        persistedStage();
        when(dealStageRepository.findByTenantIdAndName(tenantId, "Renamed")).thenReturn(Optional.empty());

        DealStageUpdateRequest request = DealStageUpdateRequest.builder().name("Renamed").build();
        assertThatNoException().isThrownBy(() -> service.updateDealStage(stageId, tenantId, userId, request));
    }

    @Test
    void allScopeCanDeleteStage() {
        grantScope("delete", "ALL");
        persistedStage(false);
        when(dealRepository.countByStageId(tenantId, stageId)).thenReturn(0);

        assertThatNoException().isThrownBy(() -> service.deleteDealStage(stageId, tenantId, userId));
    }

    // --- OWN / TEAM must not mutate tenant-wide configuration ---

    @Test
    void ownScopeCannotCreateStage() {
        grantScope("write", "OWN");

        assertThat(catchThrowable(() -> service.createDealStage(tenantId, userId, createRequest())))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("tenant-wide");
        verify(dealStageRepository, never()).save(any(DealStage.class));
    }

    @Test
    void ownScopeCannotUpdateStage() {
        grantScope("write", "OWN");

        assertThat(catchThrowable(() -> service.updateDealStage(
                stageId, tenantId, userId, DealStageUpdateRequest.builder().name("Renamed").build())))
                .isInstanceOf(PermissionDeniedException.class);
        verify(dealStageRepository, never()).save(any(DealStage.class));
    }

    @Test
    void ownScopeCannotDeleteStage() {
        grantScope("delete", "OWN");

        assertThat(catchThrowable(() -> service.deleteDealStage(stageId, tenantId, userId)))
                .isInstanceOf(PermissionDeniedException.class);
        verify(dealStageRepository, never()).delete(any(DealStage.class));
    }

    @Test
    void teamScopeCannotCreateStage() {
        grantScope("write", "TEAM");

        assertThat(catchThrowable(() -> service.createDealStage(tenantId, userId, createRequest())))
                .isInstanceOf(PermissionDeniedException.class);
        verify(dealStageRepository, never()).save(any(DealStage.class));
    }

    @Test
    void teamScopeCannotUpdateStage() {
        grantScope("write", "TEAM");

        assertThat(catchThrowable(() -> service.updateDealStage(
                stageId, tenantId, userId, DealStageUpdateRequest.builder().name("Renamed").build())))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void teamScopeCannotDeleteStage() {
        grantScope("delete", "TEAM");

        assertThat(catchThrowable(() -> service.deleteDealStage(stageId, tenantId, userId)))
                .isInstanceOf(PermissionDeniedException.class);
    }

    // --- missing permission remains denied (fail-closed) ---

    @Test
    void missingDealWritePermissionIsDenied() {
        grantScope("write", "NONE");

        assertThat(catchThrowable(() -> service.createDealStage(tenantId, userId, createRequest())))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("record-level access");
    }

    @Test
    void missingDealDeletePermissionIsDenied() {
        grantScope("delete", "NONE");

        assertThat(catchThrowable(() -> service.deleteDealStage(stageId, tenantId, userId)))
                .isInstanceOf(PermissionDeniedException.class);
    }

    // --- authorization runs before business validation ---

    @Test
    void authorizationIsCheckedBeforeDuplicateNameValidation() {
        grantScope("write", "OWN");
        when(dealStageRepository.findByTenantIdAndName(any(), anyString()))
                .thenThrow(new AssertionError("business validation must not run for an unauthorized caller"));

        assertThat(catchThrowable(() -> service.createDealStage(tenantId, userId, createRequest())))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void authorizationIsCheckedBeforeStageExistenceLookup() {
        grantScope("write", "OWN");
        when(dealStageRepository.findByIdAndTenantId(any(), any()))
                .thenThrow(new AssertionError("record lookup must not run for an unauthorized caller"));

        assertThat(catchThrowable(() -> service.updateDealStage(
                stageId, tenantId, userId, DealStageUpdateRequest.builder().name("Renamed").build())))
                .isInstanceOf(PermissionDeniedException.class);
    }

    // --- existing business guards remain intact ---

    @Test
    void stageWithActiveDealsCannotBeDeletedByAuthorizedAdmin() {
        grantScope("delete", "ALL");
        persistedStage(false);
        when(dealRepository.countByStageId(tenantId, stageId)).thenReturn(3);

        assertThat(catchThrowable(() -> service.deleteDealStage(stageId, tenantId, userId)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Cannot delete stage that has deals associated with it");
        verify(dealStageRepository, never()).delete(any(DealStage.class));
    }

    @Test
    void defaultStageCannotBeDeletedByAuthorizedAdmin() {
        grantScope("delete", "ALL");
        persistedStage(true);
        when(dealRepository.countByStageId(tenantId, stageId)).thenReturn(0);

        assertThat(catchThrowable(() -> service.deleteDealStage(stageId, tenantId, userId)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Default stage cannot be deleted");
    }

    @Test
    void duplicateStageNameStillRejectedForAuthorizedAdmin() {
        grantScope("write", "ALL");
        DealStageMapper mapper = dealStageMapper;
        when(mapper.toEntity(any(DealStageCreateRequest.class))).thenReturn(new DealStage());
        when(dealStageRepository.findByTenantIdAndName(tenantId, "New Stage"))
                .thenReturn(Optional.of(new DealStage()));

        assertThat(catchThrowable(() -> service.createDealStage(tenantId, userId, createRequest())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("A stage with this name already exists");
    }

    // --- reads stay unguarded by configuration scope ---

    @Test
    void listingStagesDoesNotRequireAllScope() {
        grantScope("read", "OWN");
        when(dealStageRepository.findByTenantIdOrderByDisplayOrder(tenantId))
                .thenReturn(java.util.List.of());

        assertThatNoException().isThrownBy(() -> service.listDealStages(tenantId));
    }
}