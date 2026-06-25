package com.shivang.crm.modules.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shivang.crm.modules.auth.dto.request.ChangePasswordRequest;
import com.shivang.crm.modules.auth.dto.request.LoginRequest;
import com.shivang.crm.modules.auth.dto.request.RegisterRequest;
import com.shivang.crm.modules.auth.dto.response.AuthResponse;
import com.shivang.crm.modules.auth.service.AuthService;
import com.shivang.crm.modules.user.dto.response.UserResponse;
import com.shivang.crm.shared.dto.ApiResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * POST /api/v1/auth/register
     * Register a new user and create a tenant.
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletResponse response) {
        
        AuthResponse authResponse = authService.register(request, response);
        return ResponseEntity.ok(ApiResponse.success(authResponse));
    }

    /**
     * POST /api/v1/auth/login
     * Authenticate, return access token in body + set refresh cookie.
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response) {

        AuthResponse authResponse = authService.login(request, response);
        return ResponseEntity.ok(ApiResponse.success(authResponse));
    }

    /**
     * POST /api/v1/auth/refresh
     * Get new access token using refresh cookie. Implements token rotation.
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            HttpServletRequest request,
            HttpServletResponse response) {

        AuthResponse authResponse = authService.refresh(request, response);
        return ResponseEntity.ok(ApiResponse.success(authResponse));
    }

    /**
     * POST /api/v1/auth/logout
     * Revoke refresh token, blacklist access token in Redis.
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<String>> logout(
            HttpServletRequest request,
            HttpServletResponse response) {

        authService.logout(request, response);
        return ResponseEntity.ok(ApiResponse.success("Logged out successfully"));
    }

    /**
     * POST /api/v1/auth/change-password
     * Authenticated user changes password. Invalidates all other sessions.
     */
    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<String>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            Authentication authentication) {

        String userId = (String) authentication.getPrincipal();
        authService.changePassword(request, userId);
        return ResponseEntity.ok(ApiResponse.success("Password changed successfully"));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<String>> forgotPassword(@RequestParam String email) {
        authService.initiatePasswordReset(email);
        return ResponseEntity.ok(ApiResponse.success("Password reset email sent if the email exists"));
    }

    // @PostMapping("/reset-password")
    // public ResponseEntity<ApiResponse<String>> resetPassword(
    //         @RequestParam String resetToken,
    //         @RequestBody @Valid PasswordResetRequest request) {
    //     authService.resetPassword(resetToken, request.getNewPassword());
    //     return ResponseEntity.ok(ApiResponse.success("Password reset successfully"));
    // }


    /**
     * GET /api/v1/auth/me
     * Get current authenticated user info.
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> me(Authentication authentication) {
        String userId = (String) authentication.getPrincipal();
        UserResponse userResponse = authService.getCurrentUser(userId);
        return ResponseEntity.ok(ApiResponse.success(userResponse));
    }
}
