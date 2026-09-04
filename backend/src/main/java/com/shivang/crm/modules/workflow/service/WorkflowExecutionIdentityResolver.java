package com.shivang.crm.modules.workflow.service;

import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.shivang.crm.modules.auth.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Centralized resolver for outbound execution identity.
 *
 * <p>Reusable by both {@code HTTP_API} and {@code CLICK_TO_CALL} executors.
 * Resolves the effective CRM user for an outbound operation based on
 * {@code executeAs} configuration.</p>
 *
 * <ul>
 *   <li>{@code WORKFLOW_USER} (default) → {@code WorkflowExecutionContext.identity.actorId}</li>
 *   <li>{@code RECORD_OWNER} → {@code entity.ownerId} from {@code WorkflowExecutionContext.getEntity()}</li>
 *   <li>{@code SPECIFIC_USER} → {@code executeAsUserId} (UUID or resolved template) from node config</li>
 *   <li>{@code TENANT} → shared workspace credential (no user, returns null)</li>
 * </ul>
 *
 * <p>Validates tenant isolation, existence, and active status. Never falls back
 * silently if an explicitly selected user is invalid.</p>
 */
@Component
@RequiredArgsConstructor
public class WorkflowExecutionIdentityResolver {

    private final UserRepository userRepository;
    private final WorkflowValueResolver valueResolver;

    public UUID resolveExecutionUser(WorkflowExecutionContext context, Map<String, Object> configuration) {
        String executeAsRaw = configuration == null ? null : String.valueOf(configuration.get("executeAs"));
        String executeAs = executeAsRaw == null ? "WORKFLOW_USER" : executeAsRaw.trim().toUpperCase();
        if (executeAs.isBlank()) executeAs = "WORKFLOW_USER";

        UUID tenantId = context.getIdentity().tenantId();

        switch (executeAs) {
            case "TENANT":
                return null;
            case "WORKFLOW_USER":
                return requireValidUser(tenantId, context.getIdentity().actorId(), "WORKFLOW_USER");

            case "RECORD_OWNER": {
                Object ownerIdObj = context.getEntity().get("ownerId");
                if (ownerIdObj == null || String.valueOf(ownerIdObj).isBlank()) {
                    throw new WorkflowRuntimeException("EXECUTION_USER_NOT_FOUND", "Record owner not found for this workflow execution");
                }
                UUID ownerId;
                try {
                    ownerId = UUID.fromString(String.valueOf(ownerIdObj));
                } catch (IllegalArgumentException ex) {
                    throw new WorkflowRuntimeException("EXECUTION_USER_NOT_FOUND", "Record owner ID is invalid");
                }
                return requireValidUser(tenantId, ownerId, "RECORD_OWNER");
            }

            case "SPECIFIC_USER": {
                Object rawUserId = configuration == null ? null : configuration.get("executeAsUserId");
                if (rawUserId == null || String.valueOf(rawUserId).isBlank()) {
                    throw new WorkflowRuntimeException("EXECUTION_USER_NOT_FOUND", "Specific user not configured for this workflow action");
                }
                // Support {{}} token that may have been resolved already by ActionNodeExecutor.resolveMap,
                // but also handle raw token here for safety.
                String userIdStr = String.valueOf(rawUserId).trim();
                if (userIdStr.startsWith("{{") && userIdStr.endsWith("}}")) {
                    String path = userIdStr.substring(2, userIdStr.length() - 2).trim();
                    WorkflowResolvedValue resolved = valueResolver.resolve(context, path);
                    if (!resolved.found() || resolved.value() == null) {
                        throw new WorkflowRuntimeException("EXECUTION_USER_NOT_FOUND", "Specific user could not be resolved: " + path);
                    }
                    userIdStr = String.valueOf(resolved.value());
                }
                UUID specificUserId;
                try {
                    specificUserId = UUID.fromString(userIdStr);
                } catch (IllegalArgumentException ex) {
                    throw new WorkflowRuntimeException("EXECUTION_USER_NOT_FOUND", "Specific user ID is not a valid UUID");
                }
                return requireValidUser(tenantId, specificUserId, "SPECIFIC_USER");
            }

            default:
                throw new WorkflowRuntimeException("EXECUTION_USER_NOT_FOUND", "Unknown execution identity: " + executeAs);
        }
    }

    private UUID requireValidUser(UUID tenantId, UUID userId, String identityType) {
        if (userId == null) {
            throw new WorkflowRuntimeException("EXECUTION_USER_NOT_FOUND", identityType + " user not found");
        }
        var userOpt = userRepository.findByIdAndTenantIdAndDeletedFalse(userId, tenantId);
        if (userOpt.isEmpty()) {
            throw new WorkflowRuntimeException("EXECUTION_USER_TENANT_MISMATCH", "User does not belong to this tenant or does not exist");
        }
        var user = userOpt.get();
        if (Boolean.FALSE.equals(user.getIsActive()) || user.isDeleted()) {
            throw new WorkflowRuntimeException("EXECUTION_USER_INACTIVE", "User is inactive or deleted: " + userId);
        }
        return userId;
    }
}
