package com.shivang.crm.modules.call.service;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shivang.crm.modules.activity.service.ActivityService;
import com.shivang.crm.modules.auth.security.TenantContext;
import com.shivang.crm.modules.call.dto.CallDispositionRequest;
import com.shivang.crm.modules.call.dto.CallLinkRequest;
import com.shivang.crm.modules.call.dto.CallResponse;
import com.shivang.crm.modules.call.entity.Call;
import com.shivang.crm.modules.call.repository.CallRepository;
import com.shivang.crm.modules.rbac.service.PermissionEvaluatorService;
import com.shivang.crm.shared.enums.OwnershipScope;
import com.shivang.crm.shared.exception.BusinessException;
import com.shivang.crm.shared.exception.NotFoundException;
import com.shivang.crm.shared.exception.PermissionDeniedException;
import com.shivang.crm.shared.service.EntityResolverService;

class CallServiceTest {

    private CallRepository callRepository;
    private PermissionEvaluatorService permissionEvaluatorService;
    private EntityResolverService entityResolverService;
    private ActivityService activityService;
    private TenantContext tenantContext;
    private CallService callService;

    private UUID tenantId;
    private UUID userId;
    private UUID callId;

    @BeforeEach
    void setUp() {
        callRepository = mock(CallRepository.class);
        permissionEvaluatorService = mock(PermissionEvaluatorService.class);
        entityResolverService = mock(EntityResolverService.class);
        activityService = mock(ActivityService.class);
        tenantContext = mock(TenantContext.class);

        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        callId = UUID.randomUUID();

        when(tenantContext.getTenantId()).thenReturn(tenantId);
        when(tenantContext.getUserId()).thenReturn(userId);
        when(permissionEvaluatorService.hasPermission(tenantId, userId, "call:write")).thenReturn(true);
        when(permissionEvaluatorService.getOwnershipScope(tenantId, userId, "call")).thenReturn(OwnershipScope.ALL);

        callService = new CallService(callRepository, permissionEvaluatorService, entityResolverService, activityService, tenantContext);
    }

    @Test
    void saveDispositionForCompletedCallSucceeds() {
        Call call = Call.builder().id(callId).tenantId(tenantId).createdBy(userId).status(Call.CallStatus.HELD).endTime(java.time.Instant.now()).build();
        when(callRepository.findByIdAndTenantIdAndDeletedFalse(callId, tenantId)).thenReturn(Optional.of(call));
        when(callRepository.save(any(Call.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CallDispositionRequest request = new CallDispositionRequest();
        request.setDisposition("CONNECTED");
        request.setNotes("Customer requested a callback.");
        request.setNextAction("CALLBACK");
        request.setFollowUpAt(java.time.Instant.now().plusSeconds(3600));

        CallResponse response = callService.saveDisposition(callId, tenantId, userId, request);

        assertEquals("CONNECTED", response.getDisposition());
        assertEquals("Customer requested a callback.", response.getNotes());
        assertEquals("CALLBACK", response.getNextAction());
        verify(activityService, times(1)).logActivity(eq(tenantId), eq(callId), eq("CALL"), eq("CALL_DISPOSITION_SAVED"), any(), eq(userId), any());
    }

    @Test
    void saveDispositionForActiveCallFails() {
        Call call = Call.builder().id(callId).tenantId(tenantId).createdBy(userId).status(Call.CallStatus.PLANNED).build();
        when(callRepository.findByIdAndTenantIdAndDeletedFalse(callId, tenantId)).thenReturn(Optional.of(call));

        CallDispositionRequest request = new CallDispositionRequest();
        request.setDisposition("CONNECTED");

        assertThrows(BusinessException.class, () -> callService.saveDisposition(callId, tenantId, userId, request));
    }

    @Test
    void missingCallIsRejectedForDisposition() {
        when(callRepository.findByIdAndTenantIdAndDeletedFalse(callId, tenantId)).thenReturn(Optional.empty());

        CallDispositionRequest request = new CallDispositionRequest();
        request.setDisposition("CONNECTED");

        assertThrows(NotFoundException.class, () -> callService.saveDisposition(callId, tenantId, userId, request));
    }

    @Test
    void linkCallToLeadInSameTenantSucceeds() {
        Call call = Call.builder().id(callId).tenantId(tenantId).createdBy(userId).build();
        when(callRepository.findByIdAndTenantIdAndDeletedFalse(callId, tenantId)).thenReturn(Optional.of(call));
        when(callRepository.save(any(Call.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(permissionEvaluatorService.hasPermission(tenantId, userId, "lead:read")).thenReturn(true);

        CallLinkRequest request = new CallLinkRequest();
        request.setEntityType("LEAD");
        request.setEntityId(UUID.randomUUID());

        CallResponse response = callService.linkCallEntity(callId, tenantId, userId, request);

        assertEquals(request.getEntityType(), response.getEntityType());
        assertEquals(request.getEntityId().toString(), response.getEntityId().toString());
        verify(entityResolverService, times(1)).validateEntityExists(request.getEntityType(), request.getEntityId(), tenantId);
        verify(activityService, times(1)).logActivity(eq(tenantId), eq(callId), eq("CALL"), eq("CALL_LINKED"), any(), eq(userId), any());
    }

    @Test
    void linkCallToContactInSameTenantSucceeds() {
        Call call = Call.builder().id(callId).tenantId(tenantId).createdBy(userId).build();
        when(callRepository.findByIdAndTenantIdAndDeletedFalse(callId, tenantId)).thenReturn(Optional.of(call));
        when(callRepository.save(any(Call.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(permissionEvaluatorService.hasPermission(tenantId, userId, "contact:read")).thenReturn(true);

        CallLinkRequest request = new CallLinkRequest();
        request.setEntityType("CONTACT");
        request.setEntityId(UUID.randomUUID());

        CallResponse response = callService.linkCallEntity(callId, tenantId, userId, request);

        assertEquals(request.getEntityType(), response.getEntityType());
        assertEquals(request.getEntityId().toString(), response.getEntityId().toString());
        verify(entityResolverService, times(1)).validateEntityExists(request.getEntityType(), request.getEntityId(), tenantId);
    }

    @Test
    void missingCallIsRejected() {
        when(callRepository.findByIdAndTenantIdAndDeletedFalse(callId, tenantId)).thenReturn(Optional.empty());

        CallLinkRequest request = new CallLinkRequest();
        request.setEntityType("LEAD");
        request.setEntityId(UUID.randomUUID());

        assertThrows(NotFoundException.class, () -> callService.linkCallEntity(callId, tenantId, userId, request));
    }

    @Test
    void missingEntityIsRejected() {
        Call call = Call.builder().id(callId).tenantId(tenantId).createdBy(userId).build();
        when(callRepository.findByIdAndTenantIdAndDeletedFalse(callId, tenantId)).thenReturn(Optional.of(call));
        doThrow(new BusinessException("ENTITY_NOT_FOUND", "Not found")).when(entityResolverService).validateEntityExists(eq("LEAD"), any(UUID.class), eq(tenantId));

        CallLinkRequest request = new CallLinkRequest();
        request.setEntityType("LEAD");
        request.setEntityId(UUID.randomUUID());

        assertThrows(BusinessException.class, () -> callService.linkCallEntity(callId, tenantId, userId, request));
    }

    @Test
    void linkCallToEntityWithoutReadPermissionIsRejected() {
        Call call = Call.builder().id(callId).tenantId(tenantId).createdBy(userId).build();
        when(callRepository.findByIdAndTenantIdAndDeletedFalse(callId, tenantId)).thenReturn(Optional.of(call));
        when(permissionEvaluatorService.hasPermission(tenantId, userId, "lead:read")).thenReturn(false);

        CallLinkRequest request = new CallLinkRequest();
        request.setEntityType("LEAD");
        request.setEntityId(UUID.randomUUID());

        assertThrows(PermissionDeniedException.class, () -> callService.linkCallEntity(callId, tenantId, userId, request));
    }

    @Test
    void crossTenantEntityLinkIsRejected() {
        Call call = Call.builder().id(callId).tenantId(tenantId).createdBy(userId).build();
        when(callRepository.findByIdAndTenantIdAndDeletedFalse(callId, tenantId)).thenReturn(Optional.of(call));
        doThrow(new BusinessException("ENTITY_NOT_FOUND", "Not found")).when(entityResolverService).validateEntityExists(eq("ACCOUNT"), any(UUID.class), eq(tenantId));

        CallLinkRequest request = new CallLinkRequest();
        request.setEntityType("ACCOUNT");
        request.setEntityId(UUID.randomUUID());

        assertThrows(BusinessException.class, () -> callService.linkCallEntity(callId, tenantId, userId, request));
    }
}
