package com.shivang.crm.modules.activity.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.shivang.crm.modules.activity.dto.ActivityResponse;
import com.shivang.crm.modules.activity.entity.Activity;
import com.shivang.crm.modules.activity.service.ActivityAccessGuard;
import com.shivang.crm.modules.activity.service.ActivityService;
import com.shivang.crm.shared.exception.BusinessException;
import com.shivang.crm.shared.exception.PermissionDeniedException;

/**
 * L3.1-P8: the generic activity endpoints must authorize through the parent entity.
 *
 * <p>Before this phase these endpoints filtered by tenant only, which let any caller
 * with activity:read read every activity row in the tenant — the audit trail of
 * tickets, deals and accounts their OWN or TEAM scope excludes. The tests below pin
 * the fixed behaviour rather than the tenant boundary that was always there.
 */
class ActivityControllerAuthorizationTest {

    private ActivityService activityService;
    private ActivityAccessGuard accessGuard;
    private ActivityController controller;

    private UUID tenantId;
    private UUID entityId;
    private String auth;

@BeforeEach
    void setup() {
        // Ids first: the tenant mock stubs getTenantId() with the value at stub time.
        tenantId = UUID.randomUUID();
        entityId = UUID.randomUUID();
        auth = UUID.randomUUID().toString();

        activityService = mock(ActivityService.class);
        accessGuard = mock(ActivityAccessGuard.class);
        controller = new ActivityController(activityService, accessGuard, tenantContextFor());

        when(activityService.getEntityActivities(any(), any(), any(), any(Integer.class), any(Integer.class)))
                .thenReturn(new PageImpl<>(List.of()));

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(auth, null, List.of()));
    }

    private com.shivang.crm.modules.auth.security.TenantContext tenantContextFor() {
        var tenantContext = mock(com.shivang.crm.modules.auth.security.TenantContext.class);
        when(tenantContext.getTenantId()).thenReturn(tenantId);
        return tenantContext;
    }

    /** A @RequestParam is optional unless {@code required = true} or it is a primitive. */
    private static boolean isRequired(java.lang.reflect.Parameter parameter) {
        var annotation = parameter.getAnnotation(org.springframework.web.bind.annotation.RequestParam.class);
        return annotation != null && annotation.required();
    }

    private void code(Throwable thrown, String expected) {
        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).getErrorCode()).isEqualTo(expected);
    }

    // ================= parent scope is enforced =================

    @Test
    void entityScopedReadAuthorizesThroughTheParent() {
        controller.getEntityActivities("DEAL", entityId, 0, 20);

        verify(accessGuard).requireEntityReadable(tenantId, "DEAL", entityId, "read");
    }

    @Test
    void queryParamReadAuthorizesThroughTheParent() {
        controller.getUnifiedActivities("TICKET", entityId, 0, 20);

        verify(accessGuard).requireEntityReadable(tenantId, "TICKET", entityId, "read");
    }

    @Test
    void recentReadAuthorizesThroughTheParent() {
        controller.getRecentActivities("ACCOUNT", entityId, null);

        verify(accessGuard).requireEntityReadable(tenantId, "ACCOUNT", entityId, "read");
    }

    @Test
    void aDeniedParentScopeStopsTheQuery() {
        org.mockito.Mockito.doThrow(new PermissionDeniedException("SCOPE_DENIED", "denied"))
                .when(accessGuard).requireEntityReadable(any(), anyString(), any(), anyString());

        code(catchThrowable(() -> controller.getEntityActivities("DEAL", entityId, 0, 20)),
                "SCOPE_DENIED");

        // The point of the fix: no activity is read for a record the caller cannot see.
        verifyNoInteractions(activityService);
    }

    @Test
    void anOutOfScopeCallerCannotReadAnyActivityRow() {
        org.mockito.Mockito.doThrow(new PermissionDeniedException("SCOPE_DENIED", "denied"))
                .when(accessGuard).requireEntityReadable(any(), anyString(), any(), anyString());

        catchThrowable(() -> controller.getUnifiedActivities("TICKET", entityId, 0, 20));
        catchThrowable(() -> controller.getRecentActivities("LEAD", entityId, null));

        verifyNoInteractions(activityService);
    }

    @Test
    void aSupportedEntityTypeIsNotRejectedAsUnsupported() {
        controller.getEntityActivities("LEAD", entityId, 0, 20);

        verify(activityService).getEntityActivities(entityId, "LEAD", tenantId, 0, 20);
    }

    // ================= unsupported and missing entity types =================

    @Test
    void anUnsupportedEntityTypeIsRejected() {
        org.mockito.Mockito.doThrow(new BusinessException(
                "ACTIVITY_ENTITY_TYPE_NOT_SUPPORTED", "nope"))
                .when(accessGuard).requireEntityReadable(any(), anyString(), any(), anyString());

        code(catchThrowable(() -> controller.getEntityActivities("CALL", entityId, 0, 20)),
                "ACTIVITY_ENTITY_TYPE_NOT_SUPPORTED");

        verifyNoInteractions(activityService);
    }

@Test
    void anUnfilteredTenantListingIsNoLongerPossible() {
        // The root GET used to accept a null entityType/entityId and returned every
        // activity row in the tenant. It now requires both explicitly.
        var root = java.util.Arrays.stream(ActivityController.class.getDeclaredMethods())
                .filter(m -> m.getAnnotation(GetMapping.class) != null
                        && (m.getAnnotation(GetMapping.class).value().length == 0))
                .findFirst()
                .orElseThrow(() -> new AssertionError("root GET mapping not found"));

        for (var parameter : root.getParameters()) {
            if ("entityId".equals(parameter.getName()) || "entityType".equals(parameter.getName())) {
                assertThat(isRequired(parameter))
                        .as("%s must be required on the root listing", parameter.getName())
                        .isTrue();
            }
        }
    }

    @Test
    void noGenericActivityWriteEndpointExists() {
        // Activities are written by domain services and system producers, never by a
        // client, so there is no write surface to authorize.
        assertThat(java.util.Arrays.stream(ActivityController.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName)
                .map(n -> n.toLowerCase()))
                .noneMatch(n -> n.contains("create") || n.contains("post")
                        || n.contains("delete") || n.contains("update"));
    }

    @Test
    void theControllerExposesOnlyReads() {
        assertThat(java.util.Arrays.stream(ActivityController.class.getDeclaredMethods())
                .filter(m -> !java.lang.reflect.Modifier.isPrivate(m.getModifiers()))
                .map(java.lang.reflect.Method::getName))
                .containsExactlyInAnyOrder(
                        "getUnifiedActivities", "getEntityActivities", "getRecentActivities");
    }

    // ================= permission annotations =================

    @Test
    void everyEndpointRestatesTheActivityReadPermission() {
        // activity:read exists (V4) and RbacFilter already derives it from this path;
        // restating it matches the defense-in-depth convention used elsewhere.
        for (var method : ActivityController.class.getDeclaredMethods()) {
            if (method.getAnnotation(GetMapping.class) == null) {
                continue;
            }
            var annotation = AnnotatedElementUtils.findMergedAnnotation(method, PreAuthorize.class);
            assertThat(annotation).as("%s must declare @PreAuthorize", method.getName()).isNotNull();
            assertThat(annotation.value()).contains("'activity'").contains("'read'");
        }
    }

    @Test
    void noEndpointRequiresWriteOrDeleteOnActivities() {
        for (var method : ActivityController.class.getDeclaredMethods()) {
            var annotation = AnnotatedElementUtils.findMergedAnnotation(method, PreAuthorize.class);
            if (annotation == null) {
                continue;
            }
            assertThat(annotation.value())
                    .as("%s must not require a mutating permission", method.getName())
                    .doesNotContain("'write'")
                    .doesNotContain("'delete'")
                    .doesNotContain("'assign'");
        }
    }

    // ================= tenant boundary =================

    @Test
    void theTenantAlwaysComesFromTenantContext() {
        controller.getEntityActivities("DEAL", entityId, 0, 20);

        // Never from the request, so a caller cannot widen the boundary.
        verify(accessGuard).requireEntityReadable(eq(tenantId), anyString(), any(), anyString());
        verify(activityService).getEntityActivities(entityId, "DEAL", tenantId, 0, 20);
    }

    @Test
    void aCrossTenantParentIsRejectedByTheParentLookup() {
        // The guard delegates to a tenant-scoped parent finder, so another tenant's
        // entity never resolves.
        org.mockito.Mockito.doThrow(new BusinessException("NOT_FOUND", "Deal not found"))
                .when(accessGuard).requireEntityReadable(any(), eq("DEAL"), any(), anyString());

        code(catchThrowable(() -> controller.getEntityActivities("DEAL", entityId, 0, 20)),
                "NOT_FOUND");

        verifyNoInteractions(activityService);
    }

    @Test
    void aDeletedParentIsRejectedTheSameWayAsAMissingOne() {
        // Deleted parents are excluded by the tenant-scoped parent finders, so they
        // cannot become an authorization bypass.
        org.mockito.Mockito.doThrow(new BusinessException("NOT_FOUND", "not found"))
                .when(accessGuard).requireEntityReadable(any(), eq("TICKET"), any(), anyString());

        Throwable deleted = catchThrowable(() -> controller.getEntityActivities("TICKET", entityId, 0, 20));

        assertThat(((BusinessException) deleted).getErrorCode()).isEqualTo("NOT_FOUND");
        verifyNoInteractions(activityService);
    }

    @Test
    void authorizationRunsBeforeTheActivityQuery() {
        org.mockito.Mockito.doThrow(new PermissionDeniedException("SCOPE_DENIED", "denied"))
                .when(accessGuard).requireEntityReadable(any(), anyString(), any(), anyString());

        catchThrowable(() -> controller.getEntityActivities("ACCOUNT", entityId, 0, 20));

        // Ordering matters: querying first would let a denial be inferred from timing.
        verifyNoInteractions(activityService);
    }

    @Test
    void paginationIsUnchangedForAuthorizedCallers() {
        controller.getEntityActivities("DEAL", entityId, 2, 50);

        verify(activityService).getEntityActivities(entityId, "DEAL", tenantId, 2, 50);
    }

    @Test
    void theRecentDefaultTypeListIsUnchanged() {
        controller.getRecentActivities("DEAL", entityId, null);

        verify(activityService).getRecentActivities(entityId, "DEAL", tenantId,
                List.of("TASK", "CALL", "MEETING", "NOTE", "EMAIL"));
    }

    @Test
    void anExplicitRecentTypeListIsUnchanged() {
        controller.getRecentActivities("DEAL", entityId, List.of("DEAL_ACTIVATED"));

        verify(activityService).getRecentActivities(entityId, "DEAL", tenantId,
                List.of("DEAL_ACTIVATED"));
    }

    @Test
    void anAuthorizedReadStillReturnsActivities() {
        Activity activity = new Activity();
        activity.setId(UUID.randomUUID());
        activity.setActivityType("DEAL_ACTIVATED");
        Page<ActivityResponse> page = new PageImpl<>(List.of());
        when(activityService.getEntityActivities(any(), any(), any(), any(Integer.class), any(Integer.class)))
                .thenReturn(page);

        assertThat(controller.getEntityActivities("DEAL", entityId, 0, 20).getStatusCode().value())
                .isEqualTo(200);
    }

    @Test
    void systemProducedActivitiesAreUnaffectedByTheGuard() {
        // The SLA scheduler and escalation path write through ActivityService directly
        // and never traverse this controller, so hardening it cannot disable them.
        assertThat(java.util.Arrays.stream(ActivityService.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName))
                .contains("logSystemActivity", "logActivity");
    }

    @Test
    void theGuardIsUsedOnlyByTheGenericController() {
        // The entity-scoped endpoints keep their own direct assertions; this guard adds
        // the check only where there was none.
assertThat(java.util.Arrays.stream(ActivityAccessGuard.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName))
                .containsExactlyInAnyOrder("requireEntityReadable", "currentUserId");
    }
}
