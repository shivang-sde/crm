package com.shivang.crm.modules.user.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shivang.crm.modules.user.dto.request.CreateUserRequest;
import com.shivang.crm.modules.user.dto.request.UpdateUserRequest;
import com.shivang.crm.modules.user.dto.response.UserResponse;
import com.shivang.crm.modules.user.service.UserManagementService;
import com.shivang.crm.shared.dto.ApiResponse;
import com.shivang.crm.util.UserUtil;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserManagementController {

    private final UserManagementService userManagementService;

    @GetMapping
    @PreAuthorize("@rbac.has(authentication, 'admin', 'user_manage') or @rbac.has(authentication, 'user', 'read')")
    public ResponseEntity<ApiResponse<Page<UserResponse>>> getUsers(
            Authentication authentication,
            @RequestParam(required = false) String search,
            Pageable pageable) {

        String userId = (String) authentication.getPrincipal();
        String userRole = UserUtil.getUserRole(authentication); // You'll need to extract this
        log.info("Authorities={}", authentication.getAuthorities());
        return ResponseEntity.ok(ApiResponse.success(
                userManagementService.getUsers(UUID.fromString(userId), userRole, search, pageable)));
    }


    @GetMapping("/managers")
    @PreAuthorize("@rbac.has(authentication, 'admin', 'user_manage') or @rbac.has(authentication, 'user', 'read')")
    public ResponseEntity<ApiResponse<List<UserResponse>>> getManagers() {
        return ResponseEntity.ok(ApiResponse.success(userManagementService.getManagers()));
    }

    @GetMapping("/{userId}")
    @PreAuthorize("@rbac.has(authentication, 'admin', 'user_manage') or @rbac.has(authentication, 'user', 'read')")
    public ResponseEntity<ApiResponse<UserResponse>> getUser(@PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse.success(
                userManagementService.getUser(userId)));
    }

    @PostMapping
    @PreAuthorize("@rbac.has(authentication, 'admin', 'user_manage') or @rbac.has(authentication, 'user', 'write')")
    public ResponseEntity<ApiResponse<UserResponse>> createUser(
            @Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                userManagementService.createUser(request)));
    }

    @PutMapping("/{userId}")
    @PreAuthorize("@rbac.has(authentication, 'admin', 'user_manage') or @rbac.has(authentication, 'user', 'write')")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                userManagementService.updateUser(userId, request)));
    }

    @DeleteMapping("/{userId}")
    @PreAuthorize("@rbac.has(authentication, 'admin', 'user_manage') or @rbac.has(authentication, 'user', 'delete')")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable UUID
    userId) {
    userManagementService.deleteUser(userId);
    return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/{userId}/activate")
    @PreAuthorize("@rbac.has(authentication, 'admin', 'user_manage') or @rbac.has(authentication, 'user', 'write')")
    public ResponseEntity<ApiResponse<Void>> activateUser(@PathVariable UUID userId) {
        userManagementService.activateUser(userId, true);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/{userId}/deactivate")
    @PreAuthorize("@rbac.has(authentication, 'admin', 'user_manage') or @rbac.has(authentication, 'user', 'write')")
    public ResponseEntity<ApiResponse<Void>> deactivateUser(@PathVariable UUID userId) {
        userManagementService.activateUser(userId, false);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
