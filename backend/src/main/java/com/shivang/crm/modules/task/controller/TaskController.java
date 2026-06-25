package com.shivang.crm.modules.task.controller;

import com.shivang.crm.modules.task.dto.TaskCreateRequest;
import com.shivang.crm.modules.task.dto.TaskResponse;
import com.shivang.crm.modules.task.dto.TaskUpdateRequest;
import com.shivang.crm.modules.task.entity.Task;
import com.shivang.crm.modules.task.service.TaskService;
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
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @PostMapping
    public ResponseEntity<TaskResponse> createTask(@RequestBody TaskCreateRequest request) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        UUID userId = UserContext.getCurrentUserId();
        TaskResponse response = taskService.createTask(tenantId, userId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<Page<TaskResponse>> listTasks(
        @RequestParam(required = false) String entityType,
        @RequestParam(required = false) UUID entityId,
        @RequestParam(required = false) Task.TaskStatus status,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        Page<TaskResponse> response = taskService.listTasks(tenantId, entityType, entityId, status, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaskResponse> getTask(@PathVariable UUID id) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        TaskResponse response = taskService.getTask(id, tenantId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<TaskResponse> updateTask(
        @PathVariable UUID id,
        @RequestBody TaskUpdateRequest request
    ) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        UUID userId = UserContext.getCurrentUserId();
        TaskResponse response = taskService.updateTask(id, tenantId, userId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable UUID id) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        UUID userId = UserContext.getCurrentUserId();
        taskService.deleteTask(id, tenantId, userId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/complete")
    public ResponseEntity<TaskResponse> completeTask(@PathVariable UUID id) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        UUID userId = UserContext.getCurrentUserId();
        TaskResponse response = taskService.completeTask(id, tenantId, userId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/reopen")
    public ResponseEntity<TaskResponse> reopenTask(@PathVariable UUID id) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        UUID userId = UserContext.getCurrentUserId();
        TaskResponse response = taskService.reopenTask(id, tenantId, userId);
        return ResponseEntity.ok(response);
    }
}
