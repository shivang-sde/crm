package com.shivang.crm.modules.meeting.controller;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shivang.crm.modules.auth.security.TenantContext;
import com.shivang.crm.modules.meeting.dto.MeetingCreateRequest;
import com.shivang.crm.modules.meeting.dto.MeetingResponse;
import com.shivang.crm.modules.meeting.dto.MeetingUpdateRequest;
import com.shivang.crm.modules.meeting.entity.Meeting;
import com.shivang.crm.modules.meeting.service.MeetingService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/meetings")
@RequiredArgsConstructor
public class MeetingController {

    private final MeetingService meetingService;
    private final TenantContext tenantContext;

    @PostMapping
    public ResponseEntity<MeetingResponse> createMeeting( @Valid @RequestBody MeetingCreateRequest request) {
        UUID tenantId = tenantContext.getTenantId();
        UUID userId = tenantContext.getUserId();
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
        UUID tenantId = tenantContext.getTenantId();
        Page<MeetingResponse> response = meetingService.listMeetings(tenantId, entityType, entityId, status, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<MeetingResponse> getMeeting(@PathVariable UUID id) {
        UUID tenantId = tenantContext.getTenantId();
        MeetingResponse response = meetingService.getMeeting(id, tenantId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<MeetingResponse> updateMeeting(
        @PathVariable UUID id,
        @RequestBody MeetingUpdateRequest request
    ) {
        UUID tenantId = tenantContext.getTenantId();
        UUID userId = tenantContext.getUserId();
        MeetingResponse response = meetingService.updateMeeting(id, tenantId, userId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}") 
    public ResponseEntity<Void> deleteMeeting(@PathVariable UUID id) {
        UUID tenantId = tenantContext.getTenantId();
        UUID userId = tenantContext.getUserId();
        meetingService.deleteMeeting(id, tenantId, userId);
        return ResponseEntity.noContent().build();
    }
}
