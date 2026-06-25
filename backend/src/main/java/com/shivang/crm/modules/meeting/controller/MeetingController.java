package com.shivang.crm.modules.meeting.controller;

import com.shivang.crm.modules.meeting.dto.MeetingCreateRequest;
import com.shivang.crm.modules.meeting.dto.MeetingResponse;
import com.shivang.crm.modules.meeting.dto.MeetingUpdateRequest;
import com.shivang.crm.modules.meeting.entity.Meeting;
import com.shivang.crm.modules.meeting.service.MeetingService;
import com.shivang.crm.shared.security.TenantContext;
import com.shivang.crm.shared.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/meetings")
@RequiredArgsConstructor
public class MeetingController {

    private final MeetingService meetingService;

    @PostMapping
    public ResponseEntity<MeetingResponse> createMeeting(@RequestBody MeetingCreateRequest request) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        UUID userId = UserContext.getCurrentUserId();
        MeetingResponse response = meetingService.createMeeting(tenantId, userId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<Page<MeetingResponse>> listMeetings(
        @RequestParam(required = false) String entityType,
        @RequestParam(required = false) UUID entityId,
        @RequestParam(required = false) Meeting.MeetingStatus status,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        Page<MeetingResponse> response = meetingService.listMeetings(tenantId, entityType, entityId, status, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<MeetingResponse> getMeeting(@PathVariable UUID id) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        MeetingResponse response = meetingService.getMeeting(id, tenantId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<MeetingResponse> updateMeeting(
        @PathVariable UUID id,
        @RequestBody MeetingUpdateRequest request
    ) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        UUID userId = UserContext.getCurrentUserId();
        MeetingResponse response = meetingService.updateMeeting(id, tenantId, userId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMeeting(@PathVariable UUID id) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        UUID userId = UserContext.getCurrentUserId();
        meetingService.deleteMeeting(id, tenantId, userId);
        return ResponseEntity.noContent().build();
    }
}
