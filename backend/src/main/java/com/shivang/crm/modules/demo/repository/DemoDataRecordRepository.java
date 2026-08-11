package com.shivang.crm.modules.demo.repository;

import com.shivang.crm.modules.demo.entity.DemoDataRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface DemoDataRecordRepository extends JpaRepository<DemoDataRecord, UUID> {
}
