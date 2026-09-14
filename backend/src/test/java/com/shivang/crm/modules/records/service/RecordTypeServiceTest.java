package com.shivang.crm.modules.records.service;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.shivang.crm.modules.records.dto.RecordTypeCreateRequest;
import com.shivang.crm.modules.records.dto.RecordTypeResponse;
import com.shivang.crm.modules.records.entity.RecordType;
import com.shivang.crm.modules.records.mapper.RecordTypeMapper;
import com.shivang.crm.modules.records.repository.RecordTypeRepository;
import com.shivang.crm.shared.exception.BusinessException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class RecordTypeServiceTest {

    private RecordTypeRepository repo;
    private RecordTypeMapper mapper;
    private RecordTypeService service;
    private UUID tenantA = UUID.randomUUID();
    private UUID tenantB = UUID.randomUUID();
    private UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        repo = mock(RecordTypeRepository.class);
        mapper = mock(RecordTypeMapper.class);
        service = new RecordTypeService(repo, mapper);
    }

    @Test
    void tenantScopedCreation() {
        when(repo.existsByTenantIdAndKeyAndDeletedFalse(any(), any())).thenReturn(false);
        when(repo.existsByTenantIdAndNameAndDeletedFalse(any(), any())).thenReturn(false);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any())).thenAnswer(inv -> {
            RecordType e = inv.getArgument(0);
            return RecordTypeResponse.builder().id(e.getId()).tenantId(e.getTenantId()).key(e.getKey()).name(e.getName()).build();
        });

        RecordTypeCreateRequest req = RecordTypeCreateRequest.builder().key("cdr").name("CDR").description("test").build();
        RecordTypeResponse resp = service.create(tenantA, userId, req);
        assertEquals("cdr", resp.getKey());
        assertEquals(tenantA, resp.getTenantId());
        verify(repo).save(argThat(e -> e.getTenantId().equals(tenantA) && e.getKey().equals("cdr")));
    }

    @Test
    void tenantIsolation_duplicateKeyOnlyWithinTenant() {
        // tenant A has cdr, tenant B can create same key
        when(repo.existsByTenantIdAndKeyAndDeletedFalse(eq(tenantA), eq("cdr"))).thenReturn(true);
        when(repo.existsByTenantIdAndKeyAndDeletedFalse(eq(tenantB), eq("cdr"))).thenReturn(false);
        when(repo.existsByTenantIdAndNameAndDeletedFalse(any(), any())).thenReturn(false);
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any())).thenAnswer(inv -> {
            RecordType e = inv.getArgument(0);
            return RecordTypeResponse.builder().key(e.getKey()).tenantId(e.getTenantId()).build();
        });

        RecordTypeCreateRequest req = RecordTypeCreateRequest.builder().key("cdr").name("CDR").build();
        // tenantA duplicate -> throws
        assertThrows(BusinessException.class, () -> service.create(tenantA, userId, req));
        // tenantB same key -> allowed
        assertDoesNotThrow(() -> service.create(tenantB, userId, req));
    }

    @Test
    void duplicateNameRejected() {
        when(repo.existsByTenantIdAndKeyAndDeletedFalse(any(), any())).thenReturn(false);
        when(repo.existsByTenantIdAndNameAndDeletedFalse(eq(tenantA), eq("CDR"))).thenReturn(true);
        RecordTypeCreateRequest req = RecordTypeCreateRequest.builder().key("cdr2").name("CDR").build();
        assertThrows(BusinessException.class, () -> service.create(tenantA, userId, req));
    }

    @Test
    void crossTenantReadProtection() {
        UUID id = UUID.randomUUID();
        when(repo.findByIdAndTenantIdAndDeletedFalse(id, tenantA)).thenReturn(Optional.of(RecordType.builder().id(id).tenantId(tenantA).key("cdr").name("CDR").build()));
        when(repo.findByIdAndTenantIdAndDeletedFalse(id, tenantB)).thenReturn(Optional.empty());
        when(mapper.toResponse(any())).thenReturn(RecordTypeResponse.builder().id(id).build());

        assertDoesNotThrow(() -> service.getById(tenantA, id));
        assertThrows(Exception.class, () -> service.getById(tenantB, id));
    }

    @Test
    void softDeleteSetsDeleted() {
        UUID id = UUID.randomUUID();
        RecordType entity = RecordType.builder().id(id).tenantId(tenantA).key("cdr").name("CDR").build();
        when(repo.findByIdAndTenantIdAndDeletedFalse(id, tenantA)).thenReturn(Optional.of(entity));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service.delete(tenantA, id, userId);
        assertTrue(entity.isDeleted());
        verify(repo).save(entity);
    }
}
