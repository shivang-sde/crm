package com.shivang.crm.modules.records.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.shivang.crm.modules.account.repository.AccountRepository;
import com.shivang.crm.modules.contact.repository.ContactRepository;
import com.shivang.crm.modules.deal.repository.DealRepository;
import com.shivang.crm.modules.lead.repository.LeadRepository;
import com.shivang.crm.modules.records.dto.CrmRecordCreateRequest;
import com.shivang.crm.modules.records.entity.CrmRecord;
import com.shivang.crm.modules.records.entity.RecordField;
import com.shivang.crm.modules.records.entity.RecordFieldType;
import com.shivang.crm.modules.records.entity.RecordType;
import com.shivang.crm.modules.records.mapper.CrmRecordMapper;
import com.shivang.crm.modules.records.repository.CrmRecordRepository;
import com.shivang.crm.modules.records.repository.RecordFieldRepository;
import com.shivang.crm.modules.records.repository.RecordTypeRepository;
import com.shivang.crm.shared.exception.BusinessException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class CrmRecordServiceTest {

    private CrmRecordRepository recordRepo;
    private RecordTypeRepository typeRepo;
    private RecordFieldRepository fieldRepo;
    private RecordValidationService validationService;
    private CrmRecordMapper mapper;
    private CrmRecordService service;

    private UUID tenantA = UUID.randomUUID();
    private UUID tenantB = UUID.randomUUID();
    private UUID userId = UUID.randomUUID();
    private UUID typeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        recordRepo = mock(CrmRecordRepository.class);
        typeRepo = mock(RecordTypeRepository.class);
        fieldRepo = mock(RecordFieldRepository.class);
        LeadRepository leadRepo = mock(LeadRepository.class);
        ContactRepository contactRepo = mock(ContactRepository.class);
        AccountRepository accountRepo = mock(AccountRepository.class);
        DealRepository dealRepo = mock(DealRepository.class);
        validationService = new RecordValidationService(leadRepo, contactRepo, accountRepo, dealRepo);
        mapper = mock(CrmRecordMapper.class);
        service = new CrmRecordService(recordRepo, typeRepo, fieldRepo, validationService, mapper);

        when(typeRepo.findByIdAndTenantIdAndDeletedFalse(typeId, tenantA))
                .thenReturn(Optional.of(RecordType.builder().id(typeId).tenantId(tenantA).key("cdr").name("CDR").isActive(true).build()));
        when(typeRepo.findByIdAndTenantIdAndDeletedFalse(typeId, tenantB)).thenReturn(Optional.empty());

        // default active fields: call_id TEXT required, duration INTEGER, status ENUM
        List<RecordField> activeFields = List.of(
                RecordField.builder().tenantId(tenantA).recordTypeId(typeId).fieldKey("call_id").fieldLabel("Call ID").fieldType(RecordFieldType.TEXT).isRequired(true).isActive(true).build(),
                RecordField.builder().tenantId(tenantA).recordTypeId(typeId).fieldKey("duration").fieldLabel("Duration").fieldType(RecordFieldType.INTEGER).isRequired(false).isActive(true).build(),
                RecordField.builder().tenantId(tenantA).recordTypeId(typeId).fieldKey("status").fieldLabel("Status").fieldType(RecordFieldType.ENUM).isRequired(false).isActive(true).optionsJson(List.of("ANSWERED","MISSED")).build()
        );
        when(fieldRepo.findActiveByRecordTypeIdAndTenantId(typeId, tenantA)).thenReturn(activeFields);
    }

    @Test
    void createWithValidFields() {
        when(recordRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any())).thenAnswer(inv -> {
            CrmRecord r = inv.getArgument(0);
            return com.shivang.crm.modules.records.dto.CrmRecordResponse.builder().id(r.getId()).tenantId(r.getTenantId()).recordTypeId(r.getRecordTypeId()).data(r.getData()).build();
        });

        var req = CrmRecordCreateRequest.builder().recordTypeId(typeId).data(Map.of("call_id","abc123","duration",120,"status","ANSWERED")).build();
        var resp = service.create(tenantA, userId, req);
        assertNotNull(resp);
        assertEquals("abc123", resp.getData().get("call_id"));
        assertEquals(120, resp.getData().get("duration"));
        verify(recordRepo).save(argThat(r -> r.getTenantId().equals(tenantA) && r.getRecordTypeId().equals(typeId)));
    }

    @Test
    void requiredFieldValidationFails() {
        var req = CrmRecordCreateRequest.builder().recordTypeId(typeId).data(Map.of("duration",120)).build(); // missing call_id
        assertThrows(BusinessException.class, () -> service.create(tenantA, userId, req));
    }

    @Test
    void unknownFieldRejection() {
        var req = CrmRecordCreateRequest.builder().recordTypeId(typeId).data(Map.of("call_id","x","randomField","y")).build();
        assertThrows(BusinessException.class, () -> service.create(tenantA, userId, req));
    }

    @Test
    void wrongTypeRejection() {
        var req = CrmRecordCreateRequest.builder().recordTypeId(typeId).data(Map.of("call_id","x","duration","not-an-int")).build();
        assertThrows(BusinessException.class, () -> service.create(tenantA, userId, req));
    }

    @Test
    void enumValidationFails() {
        var req = CrmRecordCreateRequest.builder().recordTypeId(typeId).data(Map.of("call_id","x","status","INVALID")).build();
        assertThrows(BusinessException.class, () -> service.create(tenantA, userId, req));
    }

    @Test
    void tenantIsolation_mismatchRejected() {
        var req = CrmRecordCreateRequest.builder().recordTypeId(typeId).data(Map.of("call_id","x")).build();
        // typeId belongs to tenantA, try create with tenantB
        assertThrows(Exception.class, () -> service.create(tenantB, userId, req));
    }

    @Test
    void recordTypeTenantMismatchRejected_onGet() {
        UUID recordId = UUID.randomUUID();
        CrmRecord rec = CrmRecord.builder().id(recordId).tenantId(tenantA).recordTypeId(typeId).data(Map.of("call_id","x")).build();
        when(recordRepo.findByIdAndTenantIdAndDeletedFalse(recordId, tenantA)).thenReturn(Optional.of(rec));
        when(recordRepo.findByIdAndTenantIdAndDeletedFalse(recordId, tenantB)).thenReturn(Optional.empty());
        when(typeRepo.findByIdAndTenantIdAndDeletedFalse(typeId, tenantA)).thenReturn(Optional.of(RecordType.builder().id(typeId).tenantId(tenantA).key("cdr").name("CDR").build()));
        when(mapper.toResponse(any())).thenReturn(com.shivang.crm.modules.records.dto.CrmRecordResponse.builder().id(recordId).build());
        assertDoesNotThrow(() -> service.getById(tenantA, recordId));
        assertThrows(Exception.class, () -> service.getById(tenantB, recordId));
    }

    @Test
    void jsonTypesPreserved() {
        when(recordRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any())).thenAnswer(inv -> {
            CrmRecord r = inv.getArgument(0);
            return com.shivang.crm.modules.records.dto.CrmRecordResponse.builder().data(r.getData()).build();
        });
        var data = Map.<String,Object>of("call_id","123","duration",120,"status","ANSWERED");
        var req = CrmRecordCreateRequest.builder().recordTypeId(typeId).data(data).build();
        var resp = service.create(tenantA, userId, req);
        assertTrue(resp.getData().get("duration") instanceof Integer);
        assertTrue(resp.getData().get("call_id") instanceof String);
    }
}
