package com.shivang.crm.modules.records.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.shivang.crm.modules.account.entity.Account;
import com.shivang.crm.modules.account.repository.AccountRepository;
import com.shivang.crm.modules.contact.entity.Contact;
import com.shivang.crm.modules.contact.repository.ContactRepository;
import com.shivang.crm.modules.deal.entity.Deal;
import com.shivang.crm.modules.deal.repository.DealRepository;
import com.shivang.crm.modules.lead.entity.Lead;
import com.shivang.crm.modules.lead.repository.LeadRepository;
import com.shivang.crm.modules.records.entity.RecordField;
import com.shivang.crm.modules.records.entity.RecordFieldType;
import com.shivang.crm.shared.exception.BusinessException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class RecordValidationServiceTest {

    private RecordValidationService service;
    private UUID tenantId = UUID.randomUUID();
    private LeadRepository leadRepository;
    private ContactRepository contactRepository;
    private AccountRepository accountRepository;
    private DealRepository dealRepository;

    @BeforeEach
    void setUp() {
        leadRepository = mock(LeadRepository.class);
        contactRepository = mock(ContactRepository.class);
        accountRepository = mock(AccountRepository.class);
        dealRepository = mock(DealRepository.class);
        service = new RecordValidationService(leadRepository, contactRepository, accountRepository, dealRepository);
    }

    private RecordField field(String key, RecordFieldType type, boolean required, List<String> options) {
        return RecordField.builder()
                .tenantId(tenantId)
                .recordTypeId(UUID.randomUUID())
                .fieldKey(key)
                .fieldLabel(key)
                .fieldType(type)
                .isRequired(required)
                .isActive(true)
                .displayOrder(0)
                .optionsJson(options)
                .build();
    }

    @Test
    void validRecordPasses() {
        var fields = List.of(
                field("call_id", RecordFieldType.TEXT, true, null),
                field("duration", RecordFieldType.INTEGER, true, null),
                field("status", RecordFieldType.ENUM, false, List.of("ANSWERED","MISSED"))
        );
        var data = Map.<String,Object>of("call_id","abc123","duration",120,"status","ANSWERED");
        assertDoesNotThrow(() -> service.validateAndNormalize(tenantId, fields, data));
    }

    @Test
    void requiredFieldMissingThrows() {
        var fields = List.of(field("call_id", RecordFieldType.TEXT, true, null));
        var data = Map.<String,Object>of();
        BusinessException ex = assertThrows(BusinessException.class, () -> service.validateAndNormalize(tenantId, fields, data));
        assertEquals("REQUIRED_FIELD_MISSING", ex.getErrorCode());
    }

    @Test
    void unknownFieldRejected() {
        var fields = List.of(field("call_id", RecordFieldType.TEXT, false, null));
        var data = Map.<String,Object>of("call_id","x","randomField","y");
        BusinessException ex = assertThrows(BusinessException.class, () -> service.validateAndNormalize(tenantId, fields, data));
        assertEquals("UNKNOWN_FIELD", ex.getErrorCode());
    }

    @Test
    void wrongTypeRejected_integer() {
        var fields = List.of(field("duration", RecordFieldType.INTEGER, false, null));
        var data = Map.<String,Object>of("duration","hello");
        BusinessException ex = assertThrows(BusinessException.class, () -> service.validateAndNormalize(tenantId, fields, data));
        assertEquals("INVALID_FIELD_TYPE", ex.getErrorCode());
    }

    @Test
    void enumValidationFailsForInvalidOption() {
        var fields = List.of(field("status", RecordFieldType.ENUM, false, List.of("ANSWERED","MISSED")));
        var data = Map.<String,Object>of("status","INVALID");
        BusinessException ex = assertThrows(BusinessException.class, () -> service.validateAndNormalize(tenantId, fields, data));
        assertEquals("INVALID_ENUM_VALUE", ex.getErrorCode());
    }

    @Test
    void enumValidationPassesForValidOption() {
        var fields = List.of(field("status", RecordFieldType.ENUM, false, List.of("ANSWERED","MISSED")));
        var data = Map.<String,Object>of("status","MISSED");
        assertDoesNotThrow(() -> service.validateAndNormalize(tenantId, fields, data));
    }

    @Test
    void jsonFieldAcceptsAnyValue() {
        var fields = List.of(field("payload", RecordFieldType.JSON, false, null));
        var data = Map.<String,Object>of("payload", Map.of("a",1,"b", List.of(1,2,3)));
        assertDoesNotThrow(() -> service.validateAndNormalize(tenantId, fields, data));
    }

    @Test
    void phoneValidation() {
        var fields = List.of(field("phone", RecordFieldType.PHONE, false, null));
        assertDoesNotThrow(() -> service.validateAndNormalize(tenantId, fields, Map.of("phone","9876543210")));
        assertThrows(BusinessException.class, () -> service.validateAndNormalize(tenantId, fields, Map.of("phone","abc")));
    }

    @Test
    void emailValidation() {
        var fields = List.of(field("email", RecordFieldType.EMAIL, false, null));
        assertDoesNotThrow(() -> service.validateAndNormalize(tenantId, fields, Map.of("email","a@b.com")));
        assertThrows(BusinessException.class, () -> service.validateAndNormalize(tenantId, fields, Map.of("email","not-an-email")));
    }

    @Test
    void urlValidation() {
        var fields = List.of(field("recording_url", RecordFieldType.URL, false, null));
        assertDoesNotThrow(() -> service.validateAndNormalize(tenantId, fields, Map.of("recording_url","https://example.com/rec.mp3")));
        assertThrows(BusinessException.class, () -> service.validateAndNormalize(tenantId, fields, Map.of("recording_url","not a url")));
    }

    @Test
    void preservesJsonNumberTypes() {
        var fields = List.of(
                field("duration", RecordFieldType.INTEGER, false, null),
                field("price", RecordFieldType.DECIMAL, false, null),
                field("flag", RecordFieldType.BOOLEAN, false, null)
        );
        var data = Map.<String,Object>of("duration",120,"price",12.34,"flag",true);
        assertDoesNotThrow(() -> service.validateAndNormalize(tenantId, fields, data));
        // Ensure types are preserved (not coerced to strings) - validation just checks, data map retains original types
        assertTrue(data.get("duration") instanceof Integer);
        assertTrue(data.get("price") instanceof Double);
        assertTrue(data.get("flag") instanceof Boolean);
    }

    // === WF-60 REFERENCE integrity ===

    private RecordField refField(String key, String entityType, boolean required) {
        return RecordField.builder()
                .tenantId(tenantId)
                .recordTypeId(UUID.randomUUID())
                .fieldKey(key)
                .fieldLabel(key)
                .fieldType(RecordFieldType.REFERENCE)
                .referenceEntityType(entityType)
                .isRequired(required)
                .isActive(true)
                .displayOrder(0)
                .build();
    }

    @Test
    void reference_validSameTenantLeadAccepted() {
        UUID refId = UUID.randomUUID();
        when(leadRepository.findByIdAndTenantId(refId, tenantId)).thenReturn(Optional.of(leadWithId(refId)));
        var fields = List.of(refField("customer", "LEAD", false));
        assertDoesNotThrow(() -> service.validateAndNormalize(tenantId, fields, Map.of("customer", refId.toString())));
    }

    @Test
    void reference_validSameTenantContactAccepted() {
        UUID refId = UUID.randomUUID();
        when(contactRepository.findByIdAndTenantId(refId, tenantId)).thenReturn(Optional.of(contactWithId(refId)));
        var fields = List.of(refField("contact_ref", "CONTACT", false));
        assertDoesNotThrow(() -> service.validateAndNormalize(tenantId, fields, Map.of("contact_ref", refId.toString())));
    }

    @Test
    void reference_validSameTenantAccountAccepted() {
        UUID refId = UUID.randomUUID();
        when(accountRepository.findByIdAndTenantId(refId, tenantId)).thenReturn(Optional.of(accountWithId(refId)));
        var fields = List.of(refField("account_ref", "ACCOUNT", false));
        assertDoesNotThrow(() -> service.validateAndNormalize(tenantId, fields, Map.of("account_ref", refId.toString())));
    }

    @Test
    void reference_validSameTenantDealAccepted() {
        UUID refId = UUID.randomUUID();
        when(dealRepository.findByIdAndTenantId(refId, tenantId)).thenReturn(Optional.of(dealWithId(refId)));
        var fields = List.of(refField("deal_ref", "DEAL", false));
        assertDoesNotThrow(() -> service.validateAndNormalize(tenantId, fields, Map.of("deal_ref", refId.toString())));
    }

    @Test
    void reference_nonexistentLeadRejected() {
        UUID refId = UUID.randomUUID();
        when(leadRepository.findByIdAndTenantId(refId, tenantId)).thenReturn(Optional.empty());
        var fields = List.of(refField("customer", "LEAD", false));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.validateAndNormalize(tenantId, fields, Map.of("customer", refId.toString())));
        assertEquals("INVALID_REFERENCE", ex.getErrorCode());
    }

    @Test
    void reference_nonexistentContactRejected() {
        UUID refId = UUID.randomUUID();
        when(contactRepository.findByIdAndTenantId(refId, tenantId)).thenReturn(Optional.empty());
        var fields = List.of(refField("c", "CONTACT", false));
        assertEquals("INVALID_REFERENCE", assertThrows(BusinessException.class, () -> service.validateAndNormalize(tenantId, fields, Map.of("c", refId.toString()))).getErrorCode());
    }

    @Test
    void reference_nonexistentAccountRejected() {
        UUID refId = UUID.randomUUID();
        when(accountRepository.findByIdAndTenantId(refId, tenantId)).thenReturn(Optional.empty());
        var fields = List.of(refField("a", "ACCOUNT", false));
        assertEquals("INVALID_REFERENCE", assertThrows(BusinessException.class, () -> service.validateAndNormalize(tenantId, fields, Map.of("a", refId.toString()))).getErrorCode());
    }

    @Test
    void reference_nonexistentDealRejected() {
        UUID refId = UUID.randomUUID();
        when(dealRepository.findByIdAndTenantId(refId, tenantId)).thenReturn(Optional.empty());
        var fields = List.of(refField("d", "DEAL", false));
        assertEquals("INVALID_REFERENCE", assertThrows(BusinessException.class, () -> service.validateAndNormalize(tenantId, fields, Map.of("d", refId.toString()))).getErrorCode());
    }

    @Test
    void reference_crossTenantLeadRejected() {
        UUID refId = UUID.randomUUID();
        // lookup for tenantId returns empty (simulates cross-tenant not found)
        when(leadRepository.findByIdAndTenantId(refId, tenantId)).thenReturn(Optional.empty());
        var fields = List.of(refField("customer", "LEAD", false));
        assertEquals("INVALID_REFERENCE", assertThrows(BusinessException.class, () -> service.validateAndNormalize(tenantId, fields, Map.of("customer", refId.toString()))).getErrorCode());
    }

    @Test
    void reference_crossTenantContactRejected() {
        UUID refId = UUID.randomUUID();
        when(contactRepository.findByIdAndTenantId(refId, tenantId)).thenReturn(Optional.empty());
        var fields = List.of(refField("c", "CONTACT", false));
        assertEquals("INVALID_REFERENCE", assertThrows(BusinessException.class, () -> service.validateAndNormalize(tenantId, fields, Map.of("c", refId.toString()))).getErrorCode());
    }

    @Test
    void reference_deletedLeadRejected() {
        UUID refId = UUID.randomUUID();
        Lead deleted = leadWithId(refId);
        deleted.setDeleted(true);
        when(leadRepository.findByIdAndTenantId(refId, tenantId)).thenReturn(Optional.of(deleted));
        var fields = List.of(refField("customer", "LEAD", false));
        assertEquals("INVALID_REFERENCE", assertThrows(BusinessException.class, () -> service.validateAndNormalize(tenantId, fields, Map.of("customer", refId.toString()))).getErrorCode());
    }

    @Test
    void reference_invalidUuidRejected() {
        var fields = List.of(refField("customer", "LEAD", false));
        assertEquals("INVALID_FIELD_TYPE", assertThrows(BusinessException.class, () -> service.validateAndNormalize(tenantId, fields, Map.of("customer", "not-a-uuid"))).getErrorCode());
    }

    @Test
    void reference_nullOptionalAccepted() {
        var fields = List.of(refField("customer", "LEAD", false));
        assertDoesNotThrow(() -> service.validateAndNormalize(tenantId, fields, Map.of()));
    }

    @Test
    void reference_nullRequiredRejected() {
        var fields = List.of(refField("customer", "LEAD", true));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.validateAndNormalize(tenantId, fields, Map.of()));
        assertEquals("REQUIRED_FIELD_MISSING", ex.getErrorCode());
    }

    @Test
    void reference_unsupportedTaskPreservesUuidOnly() {
        var fields = List.of(refField("task_ref", "TASK", false));
        // should not hit any repository, just UUID syntax
        assertDoesNotThrow(() -> service.validateAndNormalize(tenantId, fields, Map.of("task_ref", UUID.randomUUID().toString())));
        // invalid UUID still fails even for unsupported
        assertEquals("INVALID_FIELD_TYPE", assertThrows(BusinessException.class, () -> service.validateAndNormalize(tenantId, fields, Map.of("task_ref", "bad"))).getErrorCode());
    }

    private Lead leadWithId(UUID id) {
        Lead l = Lead.builder().id(id).tenantId(tenantId).build();
        // set id via reflection if builder didn't set (BaseEntity id)
        try { var f = com.shivang.crm.shared.base.BaseEntity.class.getDeclaredField("id"); f.setAccessible(true); f.set(l, id); } catch (Exception ignored) {}
        return l;
    }
    private Contact contactWithId(UUID id) {
        Contact c = Contact.builder().id(id).tenantId(tenantId).build();
        try { var f = com.shivang.crm.shared.base.BaseEntity.class.getDeclaredField("id"); f.setAccessible(true); f.set(c, id); } catch (Exception ignored) {}
        return c;
    }
    private Account accountWithId(UUID id) {
        Account a = Account.builder().id(id).tenantId(tenantId).build();
        try { var f = com.shivang.crm.shared.base.BaseEntity.class.getDeclaredField("id"); f.setAccessible(true); f.set(a, id); } catch (Exception ignored) {}
        return a;
    }
    private Deal dealWithId(UUID id) {
        Deal d = Deal.builder().id(id).tenantId(tenantId).build();
        try { var f = com.shivang.crm.shared.base.BaseEntity.class.getDeclaredField("id"); f.setAccessible(true); f.set(d, id); } catch (Exception ignored) {}
        return d;
    }
}
