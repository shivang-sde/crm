package com.shivang.crm.modules.task.controller;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shivang.crm.modules.auth.security.TenantContext;
import com.shivang.crm.modules.task.dto.TaskCreateRequest;
import com.shivang.crm.modules.task.dto.TaskResponse;
import com.shivang.crm.modules.task.dto.TaskUpdateRequest;
import com.shivang.crm.modules.task.entity.TaskStatus;
import com.shivang.crm.modules.task.service.TaskService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
public class TaskController {

private final TaskService taskService;
   private final TenantContext tenantContext;

    @PostMapping
    public ResponseEntity<TaskResponse> createTask(@RequestBody TaskCreateRequest request) {
        UUID tenantId =tenantContext.getTenantId();
        UUID userId = tenantContext.getUserId();
        TaskResponse response = taskService.createTask(tenantId, userId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<Page<TaskResponse>> listTasks(
        @RequestParam(required = false) String entityType,
        @RequestParam(required = false) UUID entityId,
        @RequestParam(required = false) TaskStatus status,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        UUID tenantId = tenantContext.getTenantId();
        Page<TaskResponse> response = taskService.listTasks(tenantId, entityType, entityId, status, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaskResponse> getTask(@PathVariable UUID id) {
        UUID tenantId = tenantContext.getTenantId();
        TaskResponse response = taskService.getTask(id, tenantId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<TaskResponse> updateTask(
        @PathVariable UUID id,
        @RequestBody TaskUpdateRequest request
    ) {
        UUID tenantId = tenantContext.getTenantId();
        UUID userId = tenantContext.getUserId();
        TaskResponse response = taskService.updateTask(id, tenantId, userId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable UUID id) {
       UUID tenantId = tenantContext.getTenantId();
        UUID userId = tenantContext.getUserId();
        taskService.deleteTask(id, tenantId, userId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/complete")
    public ResponseEntity<TaskResponse> completeTask(@PathVariable UUID id) {
        UUID tenantId = tenantContext.getTenantId();
        UUID userId = tenantContext.getUserId();
        TaskResponse response = taskService.completeTask(id, tenantId, userId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/reopen")
    public ResponseEntity<TaskResponse> reopenTask(@PathVariable UUID id) {
       UUID tenantId = tenantContext.getTenantId();
        UUID userId = tenantContext.getUserId();
        TaskResponse response = taskService.reopenTask(id, tenantId, userId);
        return ResponseEntity.ok(response);
    }
}
