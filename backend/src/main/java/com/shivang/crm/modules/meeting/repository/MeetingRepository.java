package com.shivang.crm.modules.meeting.repository;

import com.shivang.crm.modules.meeting.entity.Meeting;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MeetingRepository extends JpaRepository<Meeting, UUID>, JpaSpecificationExecutor<Meeting> {

    Optional<Meeting> findByIdAndTenantIdAndDeletedFalse(UUID id, UUID tenantId);

    @Query("SELECT m.ownerId FROM Meeting m WHERE m.id = :sourceId AND m.tenantId = :tenantId AND m.deleted = false")
    Optional<UUID> findOwnerIdForReminder(UUID sourceId, UUID tenantId);

    Page<Meeting> findByTenantIdAndDeletedFalse(UUID tenantId, Pageable pageable);

    Page<Meeting> findByTenantIdAndEntityTypeAndEntityIdAndDeletedFalse(
        UUID tenantId, 
        String entityType, 
        UUID entityId, 
        Pageable pageable
    );

    Page<Meeting> findByTenantIdAndStatusAndDeletedFalse(
        UUID tenantId, 
        Meeting.MeetingStatus status, 
        Pageable pageable
    );
}
