package com.shivang.crm.modules.workflow.service;

import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.shivang.crm.modules.account.service.AccountService;

@Component
public class AccountWorkflowOwnerAssignmentAdapter implements WorkflowOwnerAssignmentAdapter {

    private final AccountService accountService;

    public AccountWorkflowOwnerAssignmentAdapter(AccountService accountService) { this.accountService = accountService; }

    @Override public String entityType() { return "ACCOUNT"; }

    @Override
    public WorkflowOwnerAssignmentResult assign(UUID tenantId, UUID actorId, UUID entityId, UUID ownerId) {
        accountService.assignOwner(entityId, tenantId, ownerId, actorId);
        return new WorkflowOwnerAssignmentResult("ACCOUNT", entityId, ownerId, Map.of("assigned", true));
    }
}