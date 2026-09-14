package com.shivang.crm.modules.records.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shivang.crm.modules.records.dto.display.DisplayConfigRequest;
import com.shivang.crm.modules.records.dto.display.DisplayConfigResponse;
import com.shivang.crm.modules.records.entity.RecordDisplayConfig;
import com.shivang.crm.modules.records.entity.RecordField;
import com.shivang.crm.modules.records.entity.RecordFieldType;
import com.shivang.crm.modules.records.entity.RecordType;
import com.shivang.crm.modules.records.repository.RecordDisplayConfigRepository;
import com.shivang.crm.modules.records.repository.RecordFieldRepository;
import com.shivang.crm.modules.records.repository.RecordTypeRepository;
import com.shivang.crm.shared.exception.BusinessException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class RecordDisplayConfigServiceTest {

    private RecordDisplayConfigRepository displayRepo;
    private RecordTypeRepository typeRepo;
    private RecordFieldRepository fieldRepo;
    private RecordDisplayConfigService service;
    private UUID tenantA = UUID.randomUUID();
    private UUID tenantB = UUID.randomUUID();
    private UUID typeId = UUID.randomUUID();
    private UUID field1 = UUID.randomUUID();
    private UUID field2 = UUID.randomUUID();
    private UUID field3 = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        displayRepo = mock(RecordDisplayConfigRepository.class);
        typeRepo = mock(RecordTypeRepository.class);
        fieldRepo = mock(RecordFieldRepository.class);
        service = new RecordDisplayConfigService(displayRepo, typeRepo, fieldRepo, new ObjectMapper());

        when(typeRepo.findByIdAndTenantIdAndDeletedFalse(typeId, tenantA))
                .thenReturn(Optional.of(RecordType.builder().id(typeId).tenantId(tenantA).key("cdr").name("CDR").build()));
        when(typeRepo.findByIdAndTenantIdAndDeletedFalse(typeId, tenantB)).thenReturn(Optional.empty());

        List<RecordField> fields = List.of(
                RecordField.builder().id(field1).tenantId(tenantA).recordTypeId(typeId).fieldKey("call_id").fieldLabel("Call ID").fieldType(RecordFieldType.TEXT).isActive(true).displayOrder(0).build(),
                RecordField.builder().id(field2).tenantId(tenantA).recordTypeId(typeId).fieldKey("phone").fieldLabel("Phone").fieldType(RecordFieldType.PHONE).isActive(true).displayOrder(1).build(),
                RecordField.builder().id(field3).tenantId(tenantA).recordTypeId(typeId).fieldKey("duration").fieldLabel("Duration").fieldType(RecordFieldType.INTEGER).isActive(true).displayOrder(2).build()
        );
        when(fieldRepo.findByRecordTypeIdAndTenantIdAndDeletedFalseOrderByDisplayOrder(typeId, tenantA)).thenReturn(fields);
        when(fieldRepo.findActiveByRecordTypeIdAndTenantId(typeId, tenantA)).thenReturn(fields);
    }

    @Test
    void defaultConfigWhenNoCustom() {
        when(displayRepo.findByRecordTypeIdAndTenantIdAndDeletedFalse(typeId, tenantA)).thenReturn(Optional.empty());
        DisplayConfigResponse resp = service.get(tenantA, typeId);
        assertFalse(resp.isCustom());
        assertEquals(3, resp.getList().getColumns().size());
        assertEquals(1, resp.getDetail().getSections().size());
        assertEquals(3, resp.getDetail().getSections().get(0).getFieldIds().size());
    }

    @Test
    void createValidConfig() {
        when(displayRepo.findByRecordTypeIdAndTenantIdAndDeletedFalse(typeId, tenantA)).thenReturn(Optional.empty());
        when(displayRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DisplayConfigRequest req = DisplayConfigRequest.builder()
                .list(DisplayConfigRequest.ListConfig.builder().columns(List.of(
                        DisplayConfigRequest.ListConfig.Column.builder().fieldId(field1).build(),
                        DisplayConfigRequest.ListConfig.Column.builder().fieldId(field2).build()
                )).build())
                .detail(DisplayConfigRequest.DetailConfig.builder().sections(List.of(
                        DisplayConfigRequest.DetailConfig.Section.builder().name("Info").fieldIds(List.of(field1, field2)).build(),
                        DisplayConfigRequest.DetailConfig.Section.builder().name("Metrics").fieldIds(List.of(field3)).build()
                )).build())
                .build();

        DisplayConfigResponse resp = service.put(tenantA, typeId, req);
        assertTrue(resp.isCustom());
        verify(displayRepo).save(any());
    }

    @Test
    void invalidFieldRejected() {
        when(displayRepo.findByRecordTypeIdAndTenantIdAndDeletedFalse(typeId, tenantA)).thenReturn(Optional.empty());
        UUID unknown = UUID.randomUUID();
        DisplayConfigRequest req = DisplayConfigRequest.builder()
                .list(DisplayConfigRequest.ListConfig.builder().columns(List.of(
                        new DisplayConfigRequest.ListConfig.Column(unknown)
                )).build())
                .detail(DisplayConfigRequest.DetailConfig.builder().sections(List.of()).build())
                .build();
        assertThrows(BusinessException.class, () -> service.put(tenantA, typeId, req));
    }

    @Test
    void duplicateListFieldRejected() {
        when(displayRepo.findByRecordTypeIdAndTenantIdAndDeletedFalse(typeId, tenantA)).thenReturn(Optional.empty());
        DisplayConfigRequest req = DisplayConfigRequest.builder()
                .list(DisplayConfigRequest.ListConfig.builder().columns(List.of(
                        new DisplayConfigRequest.ListConfig.Column(field1),
                        new DisplayConfigRequest.ListConfig.Column(field1)
                )).build())
                .detail(DisplayConfigRequest.DetailConfig.builder().sections(List.of()).build())
                .build();
        assertThrows(BusinessException.class, () -> service.put(tenantA, typeId, req));
    }

    @Test
    void duplicateDetailFieldAcrossSectionsRejected() {
        when(displayRepo.findByRecordTypeIdAndTenantIdAndDeletedFalse(typeId, tenantA)).thenReturn(Optional.empty());
        DisplayConfigRequest req = DisplayConfigRequest.builder()
                .list(DisplayConfigRequest.ListConfig.builder().columns(List.of()).build())
                .detail(DisplayConfigRequest.DetailConfig.builder().sections(List.of(
                        new DisplayConfigRequest.DetailConfig.Section("id1", "S1", List.of(field1)),
                        new DisplayConfigRequest.DetailConfig.Section("id2", "S2", List.of(field1))
                )).build())
                .build();
        assertThrows(BusinessException.class, () -> service.put(tenantA, typeId, req));
    }

    @Test
    void fieldFromAnotherTypeRejected() {
        // field from another type not in fieldRepo list will be rejected as invalid
        UUID otherField = UUID.randomUUID();
        when(displayRepo.findByRecordTypeIdAndTenantIdAndDeletedFalse(typeId, tenantA)).thenReturn(Optional.empty());
        DisplayConfigRequest req = DisplayConfigRequest.builder()
                .list(DisplayConfigRequest.ListConfig.builder().columns(List.of(new DisplayConfigRequest.ListConfig.Column(otherField))).build())
                .detail(DisplayConfigRequest.DetailConfig.builder().sections(List.of()).build())
                .build();
        assertThrows(BusinessException.class, () -> service.put(tenantA, typeId, req));
    }

    @Test
    void tenantIsolationRejected() {
        DisplayConfigRequest req = DisplayConfigRequest.builder()
                .list(DisplayConfigRequest.ListConfig.builder().columns(List.of()).build())
                .detail(DisplayConfigRequest.DetailConfig.builder().sections(List.of()).build())
                .build();
        assertThrows(Exception.class, () -> service.get(tenantB, typeId));
        assertThrows(Exception.class, () -> service.put(tenantB, typeId, req));
    }

    @Test
    void invalidSectionNameRejected() {
        when(displayRepo.findByRecordTypeIdAndTenantIdAndDeletedFalse(typeId, tenantA)).thenReturn(Optional.empty());
        DisplayConfigRequest req = DisplayConfigRequest.builder()
                .list(DisplayConfigRequest.ListConfig.builder().columns(List.of()).build())
                .detail(DisplayConfigRequest.DetailConfig.builder().sections(List.of(
                        new DisplayConfigRequest.DetailConfig.Section("id1", "   ", List.of(field1))
                )).build())
                .build();
        assertThrows(BusinessException.class, () -> service.put(tenantA, typeId, req));
    }

    @Test
    void resetDeletesCustom() {
        RecordDisplayConfig existing = RecordDisplayConfig.builder().id(UUID.randomUUID()).tenantId(tenantA).recordTypeId(typeId).config(Map.of("list", Map.of())).build();
        when(displayRepo.findByRecordTypeIdAndTenantIdAndDeletedFalse(typeId, tenantA)).thenReturn(Optional.of(existing));
        service.delete(tenantA, typeId);
        verify(displayRepo).delete(existing);
    }
}
