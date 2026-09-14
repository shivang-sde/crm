package com.shivang.crm.modules.records.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.shivang.crm.modules.records.dto.RecordFieldCreateRequest;
import com.shivang.crm.modules.records.entity.RecordField;
import com.shivang.crm.modules.records.entity.RecordType;
import com.shivang.crm.modules.records.mapper.RecordFieldMapper;
import com.shivang.crm.modules.records.repository.RecordFieldRepository;
import com.shivang.crm.modules.records.repository.RecordTypeRepository;
import com.shivang.crm.shared.exception.BusinessException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class RecordFieldServiceTest {

    private RecordFieldRepository fieldRepo;
    private RecordTypeRepository typeRepo;
    private RecordFieldMapper mapper;
    private RecordFieldService service;
    private UUID tenantA = UUID.randomUUID();
    private UUID tenantB = UUID.randomUUID();
    private UUID typeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        fieldRepo = mock(RecordFieldRepository.class);
        typeRepo = mock(RecordTypeRepository.class);
        mapper = mock(RecordFieldMapper.class);
        service = new RecordFieldService(fieldRepo, typeRepo, mapper);
        when(typeRepo.findByIdAndTenantIdAndDeletedFalse(typeId, tenantA))
                .thenReturn(Optional.of(RecordType.builder().id(typeId).tenantId(tenantA).key("cdr").name("CDR").build()));
        when(typeRepo.findByIdAndTenantIdAndDeletedFalse(typeId, tenantB)).thenReturn(Optional.empty());
    }

    @Test
    void validFieldCreation() {
        when(fieldRepo.existsByRecordTypeIdAndFieldKeyAndDeletedFalse(any(), any())).thenReturn(false);
        when(fieldRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any())).thenAnswer(inv -> {
            RecordField f = inv.getArgument(0);
            return com.shivang.crm.modules.records.dto.RecordFieldResponse.builder().fieldKey(f.getFieldKey()).fieldType(f.getFieldType().name()).build();
        });

        var req = RecordFieldCreateRequest.builder().fieldKey("call_id").fieldLabel("Call ID").fieldType("TEXT").build();
        var resp = service.create(tenantA, typeId, req);
        assertEquals("call_id", resp.getFieldKey());
    }

    @Test
    void duplicateFieldKeyRejected() {
        when(fieldRepo.existsByRecordTypeIdAndFieldKeyAndDeletedFalse(typeId, "call_id")).thenReturn(true);
        var req = RecordFieldCreateRequest.builder().fieldKey("call_id").fieldLabel("Call ID").fieldType("TEXT").build();
        assertThrows(BusinessException.class, () -> service.create(tenantA, typeId, req));
    }

    @Test
    void validEnumConfiguration() {
        when(fieldRepo.existsByRecordTypeIdAndFieldKeyAndDeletedFalse(any(), any())).thenReturn(false);
        when(fieldRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any())).thenAnswer(inv -> {
            RecordField f = inv.getArgument(0);
            return com.shivang.crm.modules.records.dto.RecordFieldResponse.builder().fieldKey(f.getFieldKey()).build();
        });
        var req = RecordFieldCreateRequest.builder().fieldKey("status").fieldLabel("Status").fieldType("ENUM").options(List.of("ANSWERED","MISSED")).build();
        assertDoesNotThrow(() -> service.create(tenantA, typeId, req));
    }

    @Test
    void invalidEnumConfiguration_emptyOptions() {
        when(fieldRepo.existsByRecordTypeIdAndFieldKeyAndDeletedFalse(any(), any())).thenReturn(false);
        var req = RecordFieldCreateRequest.builder().fieldKey("status").fieldLabel("Status").fieldType("ENUM").options(List.of()).build();
        assertThrows(BusinessException.class, () -> service.create(tenantA, typeId, req));
    }

    @Test
    void nonEnumWithOptionsRejected() {
        when(fieldRepo.existsByRecordTypeIdAndFieldKeyAndDeletedFalse(any(), any())).thenReturn(false);
        var req = RecordFieldCreateRequest.builder().fieldKey("call_id").fieldLabel("Call ID").fieldType("TEXT").options(List.of("A")).build();
        assertThrows(BusinessException.class, () -> service.create(tenantA, typeId, req));
    }

    @Test
    void crossTenantProtection() {
        var req = RecordFieldCreateRequest.builder().fieldKey("call_id").fieldLabel("Call ID").fieldType("TEXT").build();
        assertThrows(Exception.class, () -> service.create(tenantB, typeId, req));
    }

    @Test
    void fieldDeletionIsSoftDelete() {
        UUID fieldId = UUID.randomUUID();
        RecordField field = RecordField.builder().id(fieldId).tenantId(tenantA).recordTypeId(typeId).fieldKey("call_id").fieldLabel("Call ID").fieldType(com.shivang.crm.modules.records.entity.RecordFieldType.TEXT).isActive(true).build();
        when(fieldRepo.findByIdAndRecordTypeIdAndTenantIdAndDeletedFalse(fieldId, typeId, tenantA)).thenReturn(Optional.of(field));
        when(fieldRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service.delete(tenantA, typeId, fieldId, UUID.randomUUID());
        assertTrue(field.isDeleted());
        assertFalse(field.getIsActive());
    }
}
