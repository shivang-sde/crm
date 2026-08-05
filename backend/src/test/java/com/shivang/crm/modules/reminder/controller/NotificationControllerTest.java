package com.shivang.crm.modules.reminder.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.springframework.data.domain.Page;
import java.io.IOException;

import com.shivang.crm.modules.reminder.dto.NotificationResponse;
import com.shivang.crm.modules.reminder.dto.UnreadNotificationCountResponse;
import com.shivang.crm.modules.reminder.service.NotificationService;

class NotificationControllerTest {

    private MockMvc mockMvc;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = org.mockito.Mockito.mock(NotificationService.class);
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        // Test-only Page serializer so standalone MockMvc can write Page<T>
        SimpleModule pageModule = new SimpleModule();
        @SuppressWarnings({"rawtypes", "unchecked"})
        Class pageClass = Page.class;
        pageModule.addSerializer((Class) pageClass, new JsonSerializer<Page<?>>() {
            @Override
            public void serialize(Page<?> value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                gen.writeStartObject();
                gen.writeFieldName("content");
                serializers.defaultSerializeValue(value.getContent(), gen);
                gen.writeObjectFieldStart("pageable");
                gen.writeNumberField("pageNumber", value.getNumber());
                gen.writeNumberField("pageSize", value.getSize());
                gen.writeNumberField("totalElements", value.getTotalElements());
                gen.writeNumberField("totalPages", value.getTotalPages());
                gen.writeEndObject();
                gen.writeEndObject();
            }
        });
        mapper.registerModule(pageModule);

        mockMvc = MockMvcBuilders.standaloneSetup(new NotificationController(notificationService))
            .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
            .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
            .build();
    }

    @Test
    void listNotificationsReturnsPageWrappedInApiResponse() throws Exception {
        NotificationResponse response = NotificationResponse.builder()
            .id(UUID.randomUUID())
            .notificationType(null)
            .title("Title")
            .message("Message")
            .read(false)
            .createdAt(Instant.now())
            .build();

        when(notificationService.listNotifications(eq(false), any())).thenReturn(new PageImpl<>(java.util.List.of(response)));

        mockMvc.perform(get("/api/v1/notifications?page=0&size=20&read=false"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.content[0].title").value("Title"));
    }

    @Test
    void getUnreadCountReturnsApiResponse() throws Exception {
        when(notificationService.getUnreadCount()).thenReturn(new UnreadNotificationCountResponse(4));

        mockMvc.perform(get("/api/v1/notifications/unread-count"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.count").value(4));
    }

    @Test
    void markAsReadReturnsUpdatedNotification() throws Exception {
        UUID id = UUID.randomUUID();
        NotificationResponse response = NotificationResponse.builder()
            .id(id)
            .read(true)
            .readAt(Instant.now())
            .build();

        when(notificationService.markAsRead(id)).thenReturn(response);

        mockMvc.perform(patch("/api/v1/notifications/" + id + "/read"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(id.toString()));
    }

    @Test
    void markAllAsReadReturnsUpdatedCount() throws Exception {
        when(notificationService.markAllAsRead()).thenReturn(5L);

        mockMvc.perform(patch("/api/v1/notifications/read-all"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").value(5));
    }
}
