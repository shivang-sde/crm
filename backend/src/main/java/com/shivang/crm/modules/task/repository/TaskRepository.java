package com.shivang.crm.modules.task.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import com.shivang.crm.modules.task.entity.Task;

@Repository
public interface TaskRepository extends JpaRepository<Task, UUID>, JpaSpecificationExecutor<Task> {

    Optional<Task> findByIdAndTenantIdAndDeletedFalse(UUID id, UUID tenantId);

    List<Task> findByTenantIdAndEntityTypeAndEntityId(UUID tenantId, String entityType, UUID entityId);

    List<Task> findByTenantIdAndOwnerUserId(UUID tenantId, UUID ownerUserId);

    List<Task> findByTenantIdAndStatus(UUID tenantId, String status);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);
}
