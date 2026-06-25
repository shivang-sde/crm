package com.shivang.crm.modules.lead.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.shivang.crm.modules.lead.entity.EntityNote;

@Repository
public interface EntityNoteRepository extends JpaRepository<EntityNote, UUID> {

    @Query("SELECT ln FROM EntityNote ln WHERE ln.tenantId = :tenantId AND ln.entityId = :entityId ORDER BY ln.createdAt DESC")
    Page<EntityNote> findByEntityIdAndTenantId(@Param("entityId") UUID entityId, @Param("tenantId") UUID tenantId, Pageable pageable);

    @Query("SELECT COUNT(ln) FROM EntityNote ln WHERE ln.tenantId = :tenantId AND ln.entityId = :entityId")
    Integer countByEntityIdAndTenantId(@Param("entityId") UUID entityId, @Param("tenantId") UUID tenantId);


    Page<EntityNote> findByEntityIdAndEntityTypeAndTenantId(UUID entityId, String entityType, UUID tenantId, Pageable pageable);
}
