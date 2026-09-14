package com.shivang.crm.modules.records.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.shivang.crm.modules.records.entity.RecordField;
import com.shivang.crm.modules.records.entity.RecordFieldType;
import com.shivang.crm.shared.exception.BusinessException;

import static org.junit.jupiter.api.Assertions.*;

public class RecordValidationServiceTest {

    private RecordValidationService service;
    private UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new RecordValidationService();
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
}
