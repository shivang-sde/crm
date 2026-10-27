package com.shivang.crm.modules.account.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.shivang.crm.modules.account.entity.Account;

public interface AccountRepository extends JpaRepository<Account, UUID>, JpaSpecificationExecutor<Account> {

    Optional<Account> findByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * L3.0-P2: live lookup. Customer relationship validation must never accept a
     * soft-deleted Account as a live parent.
     */
    Optional<Account> findByIdAndTenantIdAndDeletedFalse(UUID id, UUID tenantId);

    boolean existsByIdAndTenantId(UUID entityId, UUID tenantId);

    boolean existsByIdAndTenantIdAndDeletedFalse(UUID entityId, UUID tenantId);

    Optional<Account> findByTenantIdAndNameIgnoreCaseAndDeletedFalse(UUID tenantId, String name);

    Optional<Account> findByTenantIdAndNameIgnoreCaseAndDeletedFalseAndIdNot(UUID tenantId, String name, UUID id);

    Optional<Account> findByTenantIdAndPhoneAndDeletedFalse(UUID tenantId, String phone);

    /**
     * L3.3-P1: resolve an Account from a customer-supplied email address.
     *
     * <p>Returns a {@link List}, not an {@code Optional}, and that is the whole
     * point. {@code accounts.email} carries no unique constraint and no index
     * (unlike {@code contacts}, which has
     * {@code uq_contact_email ON contacts(tenant_id, lower(email)) WHERE deleted = false}),
     * so a tenant may legitimately hold several accounts sharing one email. This
     * method therefore has to be able to report "more than one" and let the caller
     * reject the ambiguity rather than silently picking one.
     *
     * <p>Matches the existing case-insensitive, tenant-scoped, deleted-aware
     * finder convention.
     */
    List<Account> findByTenantIdAndEmailIgnoreCaseAndDeletedFalse(UUID tenantId, String email);

}
