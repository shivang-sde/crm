package com.shivang.crm.modules.catalog.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import com.shivang.crm.modules.catalog.entity.Offering;

@Repository
public interface OfferingRepository extends JpaRepository<Offering, UUID>, JpaSpecificationExecutor<Offering> {

    Optional<Offering> findByIdAndTenantIdAndDeletedFalse(UUID id, UUID tenantId);

    boolean existsByTenantIdAndCodeIgnoreCaseAndDeletedFalse(UUID tenantId, String code);

    boolean existsByTenantIdAndCodeIgnoreCaseAndIdNotAndDeletedFalse(UUID tenantId, String code, UUID id);

    @Override
    Page<Offering> findAll(Specification<Offering> spec, Pageable pageable);
}
