package com.shivang.crm.modules.records.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.shivang.crm.modules.records.entity.MappingProfileMode;
import com.shivang.crm.modules.records.entity.RecordField;
import com.shivang.crm.modules.records.entity.RecordFieldType;
import com.shivang.crm.modules.records.entity.RecordMappingProfile;
import com.shivang.crm.shared.exception.BusinessException;

import static org.junit.jupiter.api.Assertions.*;

public class RecordMappingTransformationTest {

    private RecordMappingService service;
    private UUID fieldInt = UUID.randomUUID();
    private UUID fieldDec = UUID.randomUUID();
    private UUID fieldBool = UUID.randomUUID();
    private UUID fieldDate = UUID.randomUUID();
    private UUID fieldDateTime = UUID.randomUUID();
    private UUID fieldText = UUID.randomUUID();
    private UUID fieldJson = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new RecordMappingService();
    }

    private RecordField field(UUID id, String key, RecordFieldType type) {
        return RecordField.builder().id(id).fieldKey(key).fieldLabel(key).fieldType(type).isActive(true).build();
    }

    @Test
    void integerTransformStringToLong() {
        var f = field(fieldInt, "duration", RecordFieldType.INTEGER);
        var profile = RecordMappingProfile.builder()
                .mode(MappingProfileMode.CUSTOM)
                .configuration(Map.of("mappings", List.of(Map.of("source","durationSec","targetFieldId", fieldInt.toString(), "transform","INTEGER"))))
                .build();
        var payload = Map.<String,Object>of("durationSec","120");
        var result = service.map(profile, payload, Map.of(fieldInt, f));
        assertEquals(120L, result.get("duration"));
    }

    @Test
    void integerTransformRejectsDecimalString() {
        var f = field(fieldInt, "duration", RecordFieldType.INTEGER);
        var profile = RecordMappingProfile.builder()
                .mode(MappingProfileMode.CUSTOM)
                .configuration(Map.of("mappings", List.of(Map.of("source","durationSec","targetFieldId", fieldInt.toString(), "transform","INTEGER"))))
                .build();
        var payload = Map.<String,Object>of("durationSec","12.5");
        assertThrows(BusinessException.class, () -> service.map(profile, payload, Map.of(fieldInt, f)));
    }

    @Test
    void decimalTransform() {
        var f = field(fieldDec, "price", RecordFieldType.DECIMAL);
        var profile = RecordMappingProfile.builder()
                .mode(MappingProfileMode.CUSTOM)
                .configuration(Map.of("mappings", List.of(Map.of("source","amount","targetFieldId", fieldDec.toString(), "transform","DECIMAL"))))
                .build();
        var payload = Map.<String,Object>of("amount","12.34");
        var result = service.map(profile, payload, Map.of(fieldDec, f));
        assertNotNull(result.get("price"));
    }

    @Test
    void booleanTransform() {
        var f = field(fieldBool, "flag", RecordFieldType.BOOLEAN);
        var profile = RecordMappingProfile.builder()
                .mode(MappingProfileMode.CUSTOM)
                .configuration(Map.of("mappings", List.of(Map.of("source","enabled","targetFieldId", fieldBool.toString(), "transform","BOOLEAN"))))
                .build();
        var payload = Map.<String,Object>of("enabled","true");
        var result = service.map(profile, payload, Map.of(fieldBool, f));
        assertEquals(true, result.get("flag"));
    }

    @Test
    void dateTransform() {
        var f = field(fieldDate, "birthday", RecordFieldType.DATE);
        var profile = RecordMappingProfile.builder()
                .mode(MappingProfileMode.CUSTOM)
                .configuration(Map.of("mappings", List.of(Map.of("source","bday","targetFieldId", fieldDate.toString(), "transform","DATE"))))
                .build();
        var payload = Map.<String,Object>of("bday","2023-01-15");
        var result = service.map(profile, payload, Map.of(fieldDate, f));
        assertEquals("2023-01-15", result.get("birthday"));
    }

    @Test
    void datetimeTransform() {
        var f = field(fieldDateTime, "created", RecordFieldType.DATETIME);
        var profile = RecordMappingProfile.builder()
                .mode(MappingProfileMode.CUSTOM)
                .configuration(Map.of("mappings", List.of(Map.of("source","ts","targetFieldId", fieldDateTime.toString(), "transform","DATETIME"))))
                .build();
        var payload = Map.<String,Object>of("ts","2023-01-15T10:15:30Z");
        var result = service.map(profile, payload, Map.of(fieldDateTime, f));
        assertEquals("2023-01-15T10:15:30Z", result.get("created"));
    }

    @Test
    void stringTransform() {
        var f = field(fieldText, "name", RecordFieldType.TEXT);
        var profile = RecordMappingProfile.builder()
                .mode(MappingProfileMode.CUSTOM)
                .configuration(Map.of("mappings", List.of(Map.of("source","count","targetFieldId", fieldText.toString(), "transform","STRING"))))
                .build();
        var payload = Map.<String,Object>of("count", 123);
        var result = service.map(profile, payload, Map.of(fieldText, f));
        assertEquals("123", result.get("name"));
    }

    @Test
    void stringTransformRejectsObject() {
        var f = field(fieldText, "name", RecordFieldType.TEXT);
        var profile = RecordMappingProfile.builder()
                .mode(MappingProfileMode.CUSTOM)
                .configuration(Map.of("mappings", List.of(Map.of("source","obj","targetFieldId", fieldText.toString(), "transform","STRING"))))
                .build();
        var payload = Map.<String,Object>of("obj", Map.of("a",1));
        assertThrows(BusinessException.class, () -> service.map(profile, payload, Map.of(fieldText, f)));
    }

    @Test
    void explicitNullOmitted() {
        var f = field(fieldText, "phone", RecordFieldType.TEXT);
        var profile = RecordMappingProfile.builder()
                .mode(MappingProfileMode.CUSTOM)
                .configuration(Map.of("mappings", List.of(Map.of("source","phone","targetFieldId", fieldText.toString()))))
                .build();
        Map<String,Object> payload = new java.util.HashMap<>();
        payload.put("phone", null);
        var result = service.map(profile, payload, Map.of(fieldText, f));
        assertFalse(result.containsKey("phone"));
    }

    @Test
    void arrayTraversalOmitted() {
        var f = field(fieldText, "phone", RecordFieldType.TEXT);
        var profile = RecordMappingProfile.builder()
                .mode(MappingProfileMode.CUSTOM)
                .configuration(Map.of("mappings", List.of(Map.of("source","customer.phone","targetFieldId", fieldText.toString()))))
                .build();
        Map<String,Object> payload = Map.of("customer", List.of(Map.of("phone","123")));
        var result = service.map(profile, payload, Map.of(fieldText, f));
        assertFalse(result.containsKey("phone"));
    }

    @Test
    void decimalPreservationNativeTypes() {
        var f = field(fieldDec, "price", RecordFieldType.DECIMAL);
        var profile = RecordMappingProfile.builder()
                .mode(MappingProfileMode.CUSTOM)
                .configuration(Map.of("mappings", List.of(Map.of("source","price","targetFieldId", fieldDec.toString()))))
                .build();
        var payload = Map.<String,Object>of("price", 12.34);
        var result = service.map(profile, payload, Map.of(fieldDec, f));
        assertTrue(result.get("price") instanceof Number);
    }
}
