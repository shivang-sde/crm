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

import static org.junit.jupiter.api.Assertions.*;

public class RecordMappingServiceTest {

    private RecordMappingService service;
    private UUID fieldCallId = UUID.randomUUID();
    private UUID fieldPhone = UUID.randomUUID();
    private UUID fieldAgent = UUID.randomUUID();
    private UUID fieldDuration = UUID.randomUUID();
    private UUID fieldStatus = UUID.randomUUID();
    private UUID fieldRecording = UUID.randomUUID();

    private Map<UUID, RecordField> fieldById;

    @BeforeEach
    void setUp() {
        service = new RecordMappingService();
        fieldById = Map.of(
                fieldCallId, field("call_id", fieldCallId),
                fieldPhone, field("phone", fieldPhone),
                fieldAgent, field("agent", fieldAgent),
                fieldDuration, field("duration", fieldDuration),
                fieldStatus, field("status", fieldStatus),
                fieldRecording, field("recording_url", fieldRecording)
        );
    }

    private RecordField field(String key, UUID id) {
        return RecordField.builder().id(id).fieldKey(key).fieldLabel(key).fieldType(RecordFieldType.TEXT).isActive(true).build();
    }
    private RecordField intField(String key, UUID id) {
        return RecordField.builder().id(id).fieldKey(key).fieldLabel(key).fieldType(RecordFieldType.INTEGER).isActive(true).build();
    }

    @Test
    void directMappingPreservesCanonicalFields() {
        // Direct: payload already canonical
        Map<UUID, RecordField> map = Map.of(
                fieldCallId, field("call_id", fieldCallId),
                fieldPhone, field("phone", fieldPhone)
        );
        RecordMappingProfile profile = RecordMappingProfile.builder().mode(MappingProfileMode.DIRECT).configuration(Map.of()).build();
        Map<String,Object> payload = Map.of("call_id","abc","phone","9876543210","unknown","x");
        Map<String,Object> result = service.map(profile, payload, map);
        assertEquals("abc", result.get("call_id"));
        assertEquals("9876543210", result.get("phone"));
        assertFalse(result.containsKey("unknown"));
    }

    @Test
    void customTopLevelMapping() {
        RecordMappingProfile profile = RecordMappingProfile.builder()
                .mode(MappingProfileMode.CUSTOM)
                .configuration(Map.of("mappings", List.of(
                        Map.of("source","callId","targetFieldId", fieldCallId.toString()),
                        Map.of("source","mobile","targetFieldId", fieldPhone.toString())
                ))).build();
        Map<String,Object> payload = Map.of("callId","abc","mobile","9876543210");
        Map<String,Object> result = service.map(profile, payload, fieldById);
        assertEquals("abc", result.get("call_id"));
        assertEquals("9876543210", result.get("phone"));
    }

    @Test
    void customNestedMapping() {
        RecordMappingProfile profile = RecordMappingProfile.builder()
                .mode(MappingProfileMode.CUSTOM)
                .configuration(Map.of("mappings", List.of(
                        Map.of("source","customer.phone","targetFieldId", fieldPhone.toString())
                ))).build();
        Map<String,Object> payload = Map.of("customer", Map.of("phone","9876543210"));
        Map<String,Object> result = service.map(profile, payload, fieldById);
        assertEquals("9876543210", result.get("phone"));
    }

    @Test
    void missingSourceOmitted() {
        RecordMappingProfile profile = RecordMappingProfile.builder()
                .mode(MappingProfileMode.CUSTOM)
                .configuration(Map.of("mappings", List.of(
                        Map.of("source","missing","targetFieldId", fieldPhone.toString())
                ))).build();
        Map<String,Object> payload = Map.of("other","x");
        Map<String,Object> result = service.map(profile, payload, fieldById);
        assertFalse(result.containsKey("phone"));
        assertTrue(result.isEmpty());
    }

    @Test
    void preservesNumericTypes() {
        // Ensure duration integer preserved as number
        Map<UUID, RecordField> intMap = Map.of(fieldDuration, intField("duration", fieldDuration));
        RecordMappingProfile profile = RecordMappingProfile.builder()
                .mode(MappingProfileMode.CUSTOM)
                .configuration(Map.of("mappings", List.of(
                        Map.of("source","durationSec","targetFieldId", fieldDuration.toString())
                ))).build();
        Map<String,Object> payload = Map.of("durationSec", 120);
        Map<String,Object> result = service.map(profile, payload, intMap);
        assertTrue(result.get("duration") instanceof Integer || result.get("duration") instanceof Number);
        assertEquals(120, ((Number)result.get("duration")).intValue());
    }

    @Test
    void realisticCdrMapping() {
        RecordMappingProfile profile = RecordMappingProfile.builder()
                .mode(MappingProfileMode.CUSTOM)
                .configuration(Map.of("mappings", List.of(
                        Map.of("source","callId","targetFieldId", fieldCallId.toString()),
                        Map.of("source","mobile","targetFieldId", fieldPhone.toString()),
                        Map.of("source","user","targetFieldId", fieldAgent.toString()),
                        Map.of("source","durationSec","targetFieldId", fieldDuration.toString()),
                        Map.of("source","callStatus","targetFieldId", fieldStatus.toString()),
                        Map.of("source","recordingUrl","targetFieldId", fieldRecording.toString())
                ))).build();
        Map<String,Object> payload = Map.of(
                "callId","abc123",
                "mobile","9876543210",
                "user","John",
                "durationSec",120,
                "callStatus","ANSWERED",
                "recordingUrl","https://example.com/recording"
        );
        Map<String,Object> result = service.map(profile, payload, fieldById);
        assertEquals("abc123", result.get("call_id"));
        assertEquals("9876543210", result.get("phone"));
        assertEquals("John", result.get("agent"));
        assertEquals(120, ((Number)result.get("duration")).intValue());
        assertEquals("ANSWERED", result.get("status"));
        assertEquals("https://example.com/recording", result.get("recording_url"));
    }
}
