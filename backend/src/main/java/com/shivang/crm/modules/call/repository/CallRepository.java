package com.shivang.crm.modules.call.repository;

import com.shivang.crm.modules.call.entity.Call;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CallRepository extends JpaRepository<Call, UUID>, JpaSpecificationExecutor<Call> {

    Optional<Call> findByIdAndTenantIdAndDeletedFalse(UUID id, UUID tenantId);

    Page<Call> findByTenantIdAndDeletedFalse(UUID tenantId, Pageable pageable);

    Page<Call> findByTenantIdAndEntityTypeAndEntityIdAndDeletedFalse(
        UUID tenantId, 
        String entityType, 
        UUID entityId, 
        Pageable pageable
    );

    Page<Call> findByTenantIdAndStatusAndDeletedFalse(
        UUID tenantId, 
        Call.CallStatus status, 
        Pageable pageable
    );
}
