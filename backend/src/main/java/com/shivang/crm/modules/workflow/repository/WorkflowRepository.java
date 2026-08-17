package com.shivang.crm.modules.workflow.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.shivang.crm.modules.workflow.entity.Workflow;

@Repository
public interface WorkflowRepository extends JpaRepository<Workflow, UUID> {

    Optional<Workflow> findByIdAndTenantIdAndDeletedFalse(UUID id, UUID tenantId);
}