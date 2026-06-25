package com.shivang.crm.modules.auth.service;

import com.shivang.crm.modules.auth.dto.request.ChangePasswordRequest;
import com.shivang.crm.modules.auth.dto.request.LoginRequest;
import com.shivang.crm.modules.auth.dto.request.RegisterRequest;
import com.shivang.crm.modules.auth.dto.response.AuthResponse;
import com.shivang.crm.modules.user.dto.response.UserResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public interface AuthService {

    /**
     * Register a new user with a new tenant.
     */
    AuthResponse register(RegisterRequest request, HttpServletResponse response);

    /**
     * Authenticate user with email/password.
     * Returns access token in response body, sets refresh token in HTTP-only cookie.
     */
    AuthResponse login(LoginRequest request, HttpServletResponse response);

    /**
     * Refresh access token using the refresh token from HTTP-only cookie.
     * Implements token rotation: old refresh token revoked, new one issued.
     */
    AuthResponse refresh(HttpServletRequest request, HttpServletResponse response);

    /**
     * Logout: revoke refresh token, blacklist access token in Redis.
     */
    void logout(HttpServletRequest request, HttpServletResponse response);

    /**
     * Change password for the currently authenticated user.
     */
    void changePassword(ChangePasswordRequest request, String userId);


    /**
     * Initiate password reset process.
     */
    void initiatePasswordReset(String email);

    /**
     * Complete password reset using reset token.
     */
    void resetPassword(String resetToken, String newPassword);

    /**
     * Get current authenticated user info.
     */
    UserResponse getCurrentUser(String userId);
}
