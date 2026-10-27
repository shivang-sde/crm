package com.shivang.crm.modules.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.shivang.crm.modules.catalog.controller.OfferingController;
import com.shivang.crm.modules.integration.controller.CallingUserSettingsController;
import com.shivang.crm.modules.workflow.controller.WorkflowCredentialController;
import com.shivang.crm.modules.workflow.controller.WorkflowHttpConnectionController;

/**
 * Guards the authorization hardening applied to the sensitive surfaces. These are
 * annotation-level assertions: they prove the declared boundary exists and names an
 * existing {@code module:action} permission, without booting the whole context.
 *
 * <p>They do NOT replace the service-layer tenant and ownership checks, and they do
 * not exercise {@code RbacFilter}. They exist so a later refactor cannot silently
 * drop a guard.
 */
class SensitiveEndpointAuthorizationTest {

    @Test
    void callingUserSettingsRequiresCallReadOrWriteNotBareAuthentication() {
        Map<String, String> expected = Map.of(
                "connectors", "read",
                "credentialStatus", "read",
                "sipConnection", "read",
                "getAgentMapping", "read",
                "deleteCredentials", "write",
                "saveAgentMapping", "write");

        expected.forEach((method, action) -> assertExpression(
                CallingUserSettingsController.class, method,
                "@rbac.has(authentication, 'call', '" + action + "')"));
    }

    @Test
    void callingUserSettingsHasNoClassLevelAuthenticatedOnlyEscapeHatch() {
        assertThat(CallingUserSettingsController.class.getAnnotation(PreAuthorize.class))
                .as("a class-level isAuthenticated() would be inherited by every handler")
                .isNull();
    }

    @Test
    void userScopedWorkflowCredentialsRequireSelfOrAdmin() {
        assertExpression(WorkflowCredentialController.class, "userStatus",
                "#userId.toString().equals(authentication.name)", "admin', 'settings");
        assertExpression(WorkflowCredentialController.class, "putUser",
                "#userId.toString().equals(authentication.name)", "admin', 'settings");
        assertExpression(WorkflowCredentialController.class, "deleteUser",
                "#userId.toString().equals(authentication.name)", "admin', 'settings");
    }

    @Test
    void tenantScopedWorkflowCredentialsAreUnchanged() {
        assertExpression(WorkflowCredentialController.class, "tenantStatus",
                "@rbac.has(authentication, 'workflow', 'read')");
        assertExpression(WorkflowCredentialController.class, "putTenant",
                "@rbac.has(authentication, 'workflow', 'write')");
        assertExpression(WorkflowCredentialController.class, "deleteTenant",
                "@rbac.has(authentication, 'workflow', 'write')");
    }

    @Test
    void connectionTestRequiresWriteBecauseItTransmitsStoredCredentials() {
        assertExpression(WorkflowHttpConnectionController.class, "testConnection",
                "@rbac.has(authentication, 'workflow', 'write')");
    }

    @Test
    void everyOfferingMutationIsAuthorized() {
        Map<String, String> expected = Map.of(
                "createOffering", "write",
                "getOffering", "read",
                "listOfferings", "read",
                "updateOffering", "write",
                "activateOffering", "write",
                "deactivateOffering", "write",
                "deleteOffering", "delete");

        expected.forEach((method, action) -> assertExpression(
                OfferingController.class, method,
                "@rbac.has(authentication, 'offering', '" + action + "')"));
    }

    @Test
    void noAnnotatedHandlerInventsAPermissionStringOutsideTheCatalog() {
        List<String> known = List.of(
                "lead", "contact", "account", "deal", "activity", "report", "workflow",
                "admin", "user", "tenant", "task", "call", "meeting", "offering",
                "entitlement", "acquisition", "records", "commercial-integration");

        for (Class<?> type : List.of(CallingUserSettingsController.class,
                WorkflowCredentialController.class, WorkflowHttpConnectionController.class,
                OfferingController.class)) {
            for (Method method : type.getDeclaredMethods()) {
                PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
                if (preAuthorize == null) {
                    continue;
                }
                String expression = preAuthorize.value();
                int index = expression.indexOf("@rbac.has(authentication, '");
                while (index >= 0) {
                    int start = index + "@rbac.has(authentication, '".length();
                    int end = expression.indexOf('\'', start);
                    String module = expression.substring(start, end);
                    assertThat(module)
                            .as("%s#%s must use a seeded permission module", type.getSimpleName(), method.getName())
                            .isIn(known);
                    index = expression.indexOf("@rbac.has(authentication, '", end);
                }
            }
        }
    }

    @Test
    void allCataloguedHandlersInTheHardenedControllersCarryAGuard() {
        for (Class<?> type : List.of(CallingUserSettingsController.class,
                WorkflowHttpConnectionController.class, OfferingController.class)) {
            for (Method method : type.getDeclaredMethods()) {
                boolean mapped = method.isAnnotationPresent(GetMapping.class)
                        || method.isAnnotationPresent(PostMapping.class)
                        || method.isAnnotationPresent(PutMapping.class)
                        || method.isAnnotationPresent(PatchMapping.class)
                        || method.isAnnotationPresent(DeleteMapping.class)
                        || method.isAnnotationPresent(RequestMapping.class);
                if (!mapped || method.isSynthetic()) {
                    continue;
                }
                assertThat(method.getAnnotation(PreAuthorize.class))
                        .as("%s#%s is a mapped handler with no authorization guard",
                                type.getSimpleName(), method.getName())
                        .isNotNull();
            }
        }
    }

    private static void assertExpression(Class<?> type, String methodName, String... fragments) {
        Method method = Arrays.stream(type.getDeclaredMethods())
                .filter(m -> m.getName().equals(methodName))
                .findFirst()
                .orElseThrow(() -> new AssertionError(type.getSimpleName() + "#" + methodName + " not found"));

        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        assertThat(annotation)
                .as("%s#%s must carry @PreAuthorize", type.getSimpleName(), methodName)
                .isNotNull();
        for (String fragment : fragments) {
            assertThat(annotation.value())
                    .as("%s#%s must contain %s", type.getSimpleName(), methodName, fragment)
                    .contains(fragment);
        }
    }
}
