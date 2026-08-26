package com.shivang.crm.modules.contact.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.shivang.crm.modules.contact.entity.Contact;

public interface ContactRepository extends JpaRepository<Contact, UUID>, JpaSpecificationExecutor<Contact> {

    Optional<Contact> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByIdAndTenantId(UUID entityId, UUID tenantId);

    Optional<Contact> findByTenantIdAndEmailIgnoreCaseAndDeletedFalse(UUID tenantId, String email);

    Optional<Contact> findByTenantIdAndEmailIgnoreCaseAndDeletedFalseAndIdNot(UUID tenantId, String email, UUID id);

    Optional<Contact> findByTenantIdAndPhoneAndDeletedFalse(UUID tenantId, String phone);

    Optional<Contact> findByTenantIdAndPhoneAndDeletedFalseAndIdNot(UUID tenantId, String phone, UUID id);

    Page<Contact> findByAccountIdAndTenantId(UUID accountId, UUID tenantId, Pageable pageable);
    boolean existsByTenantIdAndAccountIdAndDeletedFalse(UUID tenantId, UUID accountId);
}
