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

    Optional<Contact> findByTenantIdAndEmailIgnoreCaseAndDeletedFalse(UUID tenantId, String email);

    Optional<Contact> findByTenantIdAndPhoneAndDeletedFalse(UUID tenantId, String phone);

    Page<Contact> findByAccountIdAndTenantId(UUID accountId, UUID tenantId, Pageable pageable);
}
