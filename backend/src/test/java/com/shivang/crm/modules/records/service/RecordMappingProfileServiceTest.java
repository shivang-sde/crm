package com.shivang.crm.modules.records.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;
import com.shivang.crm.modules.records.dto.RecordMappingProfileCreateRequest;
import com.shivang.crm.modules.records.entity.MappingProfileMode;
import com.shivang.crm.modules.records.entity.RecordField;
import com.shivang.crm.modules.records.entity.RecordFieldType;
import com.shivang.crm.modules.records.entity.RecordType;
import com.shivang.crm.modules.records.mapper.RecordMappingProfileMapper;
import com.shivang.crm.modules.records.repository.RecordFieldRepository;
import com.shivang.crm.modules.records.repository.RecordMappingProfileRepository;
import com.shivang.crm.modules.records.repository.RecordTypeRepository;
import com.shivang.crm.shared.exception.BusinessException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class RecordMappingProfileServiceTest {

    private RecordMappingProfileRepository mappingRepo;
    private RecordTypeRepository typeRepo;
    private RecordFieldRepository fieldRepo;
    private RecordMappingProfileMapper mapper;
    private RecordMappingProfileService service;
    private UUID tenantA = UUID.randomUUID();
    private UUID tenantB = UUID.randomUUID();
    private UUID userId = UUID.randomUUID();
    private UUID typeId = UUID.randomUUID();
    private UUID field1 = UUID.randomUUID();
    private UUID field2 = UUID.randomUUID();
    private RecordField activeField1, activeField2;

    @BeforeEach
    void setUp() {
        mappingRepo = mock(RecordMappingProfileRepository.class);
        typeRepo = mock(RecordTypeRepository.class);
        fieldRepo = mock(RecordFieldRepository.class);
        mapper = mock(RecordMappingProfileMapper.class);
        service = new RecordMappingProfileService(mappingRepo, typeRepo, fieldRepo, mapper);

        when(typeRepo.findByIdAndTenantIdAndDeletedFalse(typeId, tenantA))
                .thenReturn(Optional.of(RecordType.builder().id(typeId).tenantId(tenantA).key("cdr").name("CDR").build()));
        when(typeRepo.findByIdAndTenantIdAndDeletedFalse(typeId, tenantB)).thenReturn(Optional.empty());

        activeField1 = RecordField.builder().id(field1).tenantId(tenantA).recordTypeId(typeId).fieldKey("call_id").fieldLabel("Call ID").fieldType(RecordFieldType.TEXT).isActive(true).build();
        activeField2 = RecordField.builder().id(field2).tenantId(tenantA).recordTypeId(typeId).fieldKey("phone").fieldLabel("Phone").fieldType(RecordFieldType.PHONE).isActive(true).build();
        when(fieldRepo.findByRecordTypeIdAndTenantIdAndDeletedFalseOrderByDisplayOrder(typeId, tenantA)).thenReturn(List.of(activeField1, activeField2));

        when(mappingRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any())).thenAnswer(inv -> {
            var e = (com.shivang.crm.modules.records.entity.RecordMappingProfile) inv.getArgument(0);
            return com.shivang.crm.modules.records.dto.RecordMappingProfileResponse.builder()
                    .id(e.getId()).tenantId(e.getTenantId()).recordTypeId(e.getRecordTypeId()).mappingKey(e.getMappingKey()).name(e.getName()).mode(e.getMode().name()).build();
        });
        when(typeRepo.findByIdAndTenantIdAndDeletedFalse(any(), any())).thenAnswer(inv -> {
            UUID tid = inv.getArgument(1);
            if (tid.equals(tenantA)) return Optional.of(RecordType.builder().id(typeId).tenantId(tenantA).key("cdr").name("CDR").build());
            return Optional.empty();
        });
    }

    @Test
    void createDirectMappingSuccess() {
        var req = RecordMappingProfileCreateRequest.builder()
                .recordTypeId(typeId).mappingKey("default_cdr").name("Default CDR").mode("DIRECT").configuration(Map.of()).build();
        when(mappingRepo.existsByTenantIdAndMappingKeyAndDeletedFalse(tenantA, "default_cdr")).thenReturn(false);
        var resp = service.create(tenantA, userId, req);
        assertEquals("default_cdr", resp.getMappingKey());
    }

    @Test
    void createCustomMappingSuccess() {
        var req = RecordMappingProfileCreateRequest.builder()
                .recordTypeId(typeId).mappingKey("sellspark_cdr").name("Sellspark CDR").mode("CUSTOM")
                .configuration(Map.of("mappings", List.of(
                        Map.of("source","callId","targetFieldId", field1.toString()),
                        Map.of("source","mobile","targetFieldId", field2.toString())
                ))).build();
        when(mappingRepo.existsByTenantIdAndMappingKeyAndDeletedFalse(tenantA, "sellspark_cdr")).thenReturn(false);
        var resp = service.create(tenantA, userId, req);
        assertEquals("sellspark_cdr", resp.getMappingKey());
    }

    @Test
    void duplicateTargetRejected() {
        var req = RecordMappingProfileCreateRequest.builder()
                .recordTypeId(typeId).mappingKey("dup_target").name("Dup").mode("CUSTOM")
                .configuration(Map.of("mappings", List.of(
                        Map.of("source","a","targetFieldId", field1.toString()),
                        Map.of("source","b","targetFieldId", field1.toString())
                ))).build();
        when(mappingRepo.existsByTenantIdAndMappingKeyAndDeletedFalse(tenantA, "dup_target")).thenReturn(false);
        assertThrows(BusinessException.class, () -> service.create(tenantA, userId, req));
    }

    @Test
    void duplicateSourceRejected() {
        var req = RecordMappingProfileCreateRequest.builder()
                .recordTypeId(typeId).mappingKey("dup_source").name("Dup").mode("CUSTOM")
                .configuration(Map.of("mappings", List.of(
                        Map.of("source","same","targetFieldId", field1.toString()),
                        Map.of("source","same","targetFieldId", field2.toString())
                ))).build();
        when(mappingRepo.existsByTenantIdAndMappingKeyAndDeletedFalse(tenantA, "dup_source")).thenReturn(false);
        assertThrows(BusinessException.class, () -> service.create(tenantA, userId, req));
    }

    @Test
    void invalidTargetFieldRejected() {
        UUID unknown = UUID.randomUUID();
        var req = RecordMappingProfileCreateRequest.builder()
                .recordTypeId(typeId).mappingKey("bad_target").name("Bad").mode("CUSTOM")
                .configuration(Map.of("mappings", List.of(Map.of("source","x","targetFieldId", unknown.toString())))).build();
        when(mappingRepo.existsByTenantIdAndMappingKeyAndDeletedFalse(tenantA, "bad_target")).thenReturn(false);
        assertThrows(BusinessException.class, () -> service.create(tenantA, userId, req));
    }

    @Test
    void fieldFromAnotherTypeRejected() {
        // Simulate field from another type by having fieldRepo return only field1/field2 for typeId, so otherField not in list
        UUID otherField = UUID.randomUUID();
        var req = RecordMappingProfileCreateRequest.builder()
                .recordTypeId(typeId).mappingKey("cross_type").name("Cross").mode("CUSTOM")
                .configuration(Map.of("mappings", List.of(Map.of("source","x","targetFieldId", otherField.toString())))).build();
        when(mappingRepo.existsByTenantIdAndMappingKeyAndDeletedFalse(tenantA, "cross_type")).thenReturn(false);
        assertThrows(BusinessException.class, () -> service.create(tenantA, userId, req));
    }

    @Test
    void deletedInactiveFieldRejected() {
        RecordField inactive = RecordField.builder().id(UUID.randomUUID()).tenantId(tenantA).recordTypeId(typeId).fieldKey("old").fieldLabel("Old").fieldType(RecordFieldType.TEXT).isActive(false).build();
        when(fieldRepo.findByRecordTypeIdAndTenantIdAndDeletedFalseOrderByDisplayOrder(typeId, tenantA)).thenReturn(List.of(activeField1, inactive));
        var req = RecordMappingProfileCreateRequest.builder()
                .recordTypeId(typeId).mappingKey("inactive_target").name("Inactive").mode("CUSTOM")
                .configuration(Map.of("mappings", List.of(Map.of("source","x","targetFieldId", inactive.getId().toString())))).build();
        when(mappingRepo.existsByTenantIdAndMappingKeyAndDeletedFalse(tenantA, "inactive_target")).thenReturn(false);
        assertThrows(BusinessException.class, () -> service.create(tenantA, userId, req));
    }

    @Test
    void crossTenantRecordTypeRejected() {
        var req = RecordMappingProfileCreateRequest.builder()
                .recordTypeId(typeId).mappingKey("cross_tenant").name("Cross").mode("DIRECT").build();
        // tenantB trying to create mapping for typeId owned by tenantA
        assertThrows(Exception.class, () -> service.create(tenantB, userId, req));
    }

    @Test
    void mappingKeyUniquenessEnforced() {
        when(mappingRepo.existsByTenantIdAndMappingKeyAndDeletedFalse(tenantA, "dup_key")).thenReturn(true);
        var req = RecordMappingProfileCreateRequest.builder()
                .recordTypeId(typeId).mappingKey("dup_key").name("Dup").mode("DIRECT").build();
        assertThrows(BusinessException.class, () -> service.create(tenantA, userId, req));
    }

    @Test
    void invalidModeRejected() {
        var req = RecordMappingProfileCreateRequest.builder()
                .recordTypeId(typeId).mappingKey("bad_mode").name("Bad").mode("INVALID").build();
        when(mappingRepo.existsByTenantIdAndMappingKeyAndDeletedFalse(tenantA, "bad_mode")).thenReturn(false);
        assertThrows(BusinessException.class, () -> service.create(tenantA, userId, req));
    }

    @Test
    void invalidConfigurationRejected_emptyCustom() {
        var req = RecordMappingProfileCreateRequest.builder()
                .recordTypeId(typeId).mappingKey("empty_custom").name("Empty").mode("CUSTOM")
                .configuration(Map.of("mappings", List.of())).build();
        when(mappingRepo.existsByTenantIdAndMappingKeyAndDeletedFalse(tenantA, "empty_custom")).thenReturn(false);
        assertThrows(BusinessException.class, () -> service.create(tenantA, userId, req));
    }

    @Test
    void directModeWithMappingsRejected() {
        var req = RecordMappingProfileCreateRequest.builder()
                .recordTypeId(typeId).mappingKey("direct_with_map").name("Direct").mode("DIRECT")
                .configuration(Map.of("mappings", List.of(Map.of("source","x","targetFieldId", field1.toString())))).build();
        when(mappingRepo.existsByTenantIdAndMappingKeyAndDeletedFalse(tenantA, "direct_with_map")).thenReturn(false);
        assertThrows(BusinessException.class, () -> service.create(tenantA, userId, req));
    }

    @Test
    void malformedPathRejected() {
        var req = RecordMappingProfileCreateRequest.builder()
                .recordTypeId(typeId).mappingKey("bad_path").name("Bad").mode("CUSTOM")
                .configuration(Map.of("mappings", List.of(Map.of("source",".bad","targetFieldId", field1.toString())))).build();
        when(mappingRepo.existsByTenantIdAndMappingKeyAndDeletedFalse(tenantA, "bad_path")).thenReturn(false);
        assertThrows(BusinessException.class, () -> service.create(tenantA, userId, req));
    }

    @Test
    void deepPathRejected() {
        String deep = "a.b.c.d.e.f.g.h.i.j.k"; // 11 depth >10
        var req = RecordMappingProfileCreateRequest.builder()
                .recordTypeId(typeId).mappingKey("deep_path").name("Deep").mode("CUSTOM")
                .configuration(Map.of("mappings", List.of(Map.of("source",deep,"targetFieldId", field1.toString())))).build();
        when(mappingRepo.existsByTenantIdAndMappingKeyAndDeletedFalse(tenantA, "deep_path")).thenReturn(false);
        assertThrows(BusinessException.class, () -> service.create(tenantA, userId, req));
    }

    @Test
    void incompatibleTransformRejected() {
        var req = RecordMappingProfileCreateRequest.builder()
                .recordTypeId(typeId).mappingKey("bad_transform").name("Bad").mode("CUSTOM")
                .configuration(Map.of("mappings", List.of(Map.of("source","x","targetFieldId", field1.toString(), "transform","INTEGER")))).build();
        // field1 is TEXT, INTEGER transform incompatible
        when(mappingRepo.existsByTenantIdAndMappingKeyAndDeletedFalse(tenantA, "bad_transform")).thenReturn(false);
        assertThrows(BusinessException.class, () -> service.create(tenantA, userId, req));
    }

    @Test
    void validTransformAccepted() {
        // field2 is PHONE (text-like) with STRING transform should be allowed
        var req = RecordMappingProfileCreateRequest.builder()
                .recordTypeId(typeId).mappingKey("good_transform").name("Good").mode("CUSTOM")
                .configuration(Map.of("mappings", List.of(Map.of("source","x","targetFieldId", field1.toString(), "transform","STRING")))).build();
        when(mappingRepo.existsByTenantIdAndMappingKeyAndDeletedFalse(tenantA, "good_transform")).thenReturn(false);
        var resp = service.create(tenantA, userId, req);
        assertEquals("good_transform", resp.getMappingKey());
    }
}
