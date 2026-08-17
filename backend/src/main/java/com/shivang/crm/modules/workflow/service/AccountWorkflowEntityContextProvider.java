package com.shivang.crm.modules.workflow.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.shivang.crm.modules.account.entity.Account;
import com.shivang.crm.modules.account.repository.AccountRepository;

@Component
public class AccountWorkflowEntityContextProvider implements WorkflowEntityContextProvider {

    private final AccountRepository accountRepository;

    public AccountWorkflowEntityContextProvider(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Override
    public String entityType() {
        return "ACCOUNT";
    }

    @Override
    public Optional<Map<String, Object>> load(UUID tenantId, UUID entityId) {
        return accountRepository.findByIdAndTenantId(entityId, tenantId)
            .filter(account -> !Boolean.TRUE.equals(account.getDeleted()))
            .map(this::toContext);
    }

    private Map<String, Object> toContext(Account account) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("id", account.getId());
        context.put("tenantId", account.getTenantId());
        context.put("ownerId", account.getOwnerId());
        context.put("name", account.getName());
        context.put("website", account.getWebsite());
        context.put("industry", account.getIndustry());
        context.put("phone", account.getPhone());
        context.put("email", account.getEmail());
        context.put("annualRevenue", account.getAnnualRevenue());
        context.put("employeeCount", account.getEmployeeCount());
        context.put("description", account.getDescription());
        context.put("country", account.getCountry());
        context.put("state", account.getState());
        context.put("city", account.getCity());
        context.put("addressLine1", account.getAddressLine1());
        context.put("postalCode", account.getPostalCode());
        context.put("leadId", account.getLeadId());
        context.put("isActive", account.getIsActive());
        context.put("createdAt", account.getCreatedAt());
        context.put("updatedAt", account.getUpdatedAt());
        context.put("customFields", account.getCustomData() == null ? Map.of() : account.getCustomData());
        return context;
    }
}