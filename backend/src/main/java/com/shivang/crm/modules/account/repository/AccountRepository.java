package com.shivang.crm.modules.account.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.shivang.crm.modules.account.entity.Account;

public interface AccountRepository extends JpaRepository<Account, UUID>, JpaSpecificationExecutor<Account> {

    Optional<Account> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<Account> findByTenantIdAndNameIgnoreCaseAndDeletedFalse(UUID tenantId, String name);

}
