package com.shivang.crm.modules.reminder.controller;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shivang.crm.modules.reminder.dto.NotificationResponse;
import com.shivang.crm.modules.reminder.dto.UnreadNotificationCountResponse;
import com.shivang.crm.modules.reminder.service.NotificationService;
import com.shivang.crm.shared.dto.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<NotificationResponse>>> listNotifications(
        @RequestParam(required = false) Boolean read,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<NotificationResponse> page = notificationService.listNotifications(read, pageable);
        return ResponseEntity.ok(ApiResponse.success(page));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<UnreadNotificationCountResponse>> getUnreadCount() {
        UnreadNotificationCountResponse response = notificationService.getUnreadCount();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<ApiResponse<NotificationResponse>> markAsRead(@PathVariable UUID id) {
        NotificationResponse response = notificationService.markAsRead(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse<Long>> markAllAsRead() {
        long updated = notificationService.markAllAsRead();
        return ResponseEntity.ok(ApiResponse.success(updated));
    }
}
