package com.shivang.crm.modules.form.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.shivang.crm.modules.form.entity.Form;

@Repository
public interface FormRepository extends JpaRepository<Form, UUID> {

    Optional<Form> findByIdAndTenantIdAndDeletedFalse(UUID id, UUID tenantId);

    Optional<Form> findByPublicKeyAndDeletedFalse(String publicKey);

    List<Form> findByTenantIdAndDeletedFalseOrderByUpdatedAtDesc(UUID tenantId);

    List<Form> findByTenantIdAndStatusAndDeletedFalseOrderByUpdatedAtDesc(UUID tenantId, com.shivang.crm.modules.form.entity.FormStatus status);

    boolean existsByPublicKey(String publicKey);

    Optional<Form> findByAcquisitionConfigIdAndDeletedFalse(UUID acquisitionConfigId);
}
