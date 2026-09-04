package com.shivang.crm.modules.workflow.service;

/**
 * Execution identity for outbound workflow actions.
 * Determines which CRM user is considered the actor for the outbound operation
 * and thus which credential should be used.
 *
 * <p>Stored as {@code executeAs} in {@code WorkflowNode.configuration.config}
 * (inside the ACTION node's config map). Absent value defaults to
 * {@link #WORKFLOW_USER} for backward compatibility.</p>
 */
public enum WorkflowExecutionIdentityType {
    WORKFLOW_USER,
    RECORD_OWNER,
    SPECIFIC_USER
}
