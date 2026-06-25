package com.shivang.crm.modules.auth.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.auth.dto.request.ChangePasswordRequest;
import com.shivang.crm.modules.auth.dto.request.LoginRequest;
import com.shivang.crm.modules.auth.dto.request.RegisterRequest;
import com.shivang.crm.modules.auth.dto.request.TenantProvisionRequest;
import com.shivang.crm.modules.auth.dto.response.AuthResponse;
import com.shivang.crm.modules.auth.entity.RefreshToken;
import com.shivang.crm.modules.auth.entity.User;
import com.shivang.crm.modules.auth.mapper.TenantMapper;
import com.shivang.crm.modules.auth.repository.RefreshTokenRepository;
import com.shivang.crm.modules.auth.repository.UserRepository;
import com.shivang.crm.modules.auth.security.JwtService;
import com.shivang.crm.modules.auth.security.TokenBlacklistService;
import com.shivang.crm.modules.auth.service.AuthService;
import com.shivang.crm.modules.auth.service.TenantProvisioningService;
import com.shivang.crm.modules.rbac.entity.UserRole;
import com.shivang.crm.modules.rbac.repository.UserRoleRepository;
import com.shivang.crm.modules.tenant.entity.Tenant;
import com.shivang.crm.modules.tenant.repository.TenantRepository;
import com.shivang.crm.modules.user.dto.response.UserResponse;
import com.shivang.crm.modules.user.mapper.UserManagementMapper;
import com.shivang.crm.shared.exception.BusinessException;
import com.shivang.crm.shared.exception.UnauthorizedException;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TenantRepository tenantRepository;
    private final JwtService jwtService;
    private final TokenBlacklistService tokenBlacklistService;
    private final PasswordEncoder passwordEncoder;
    private final UserRoleRepository userRoleRepository;
    private final TenantProvisioningService tenantProvisioningService;
    private final TenantMapper tenantMapper;
    private final UserManagementMapper userManagementMapper;

    @Value("${jwt.refresh-token-expiry-days}")
    private int refreshTokenExpiryDays;

    private static final String REFRESH_COOKIE_NAME = "refresh_token";
    private static final String ACCESS_COOKIE_NAME = "access_token";
    private static final String REFRESH_COOKIE_PATH = "/api/v1/auth";

    // ───────────────────────────── REGISTER ──────────────────────────

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request, HttpServletResponse response) {
        String email = request.getEmail().toLowerCase().trim();
        if (userRepository.findByEmail(email).isPresent()) {
            throw new BusinessException("EMAIL_ALREADY_EXISTS", "User with this email already registered");
        }

        TenantProvisionRequest provisionRequest = TenantProvisionRequest.builder()
                .companyName(request.getCompanyName().trim())
                .admin(TenantProvisionRequest.TenantAdminRequest.builder()
                        .email(email)
                        .password(request.getPassword())
                        .firstName(request.getFirstName())
                        .lastName(request.getLastName())
                        .build())
                .build();

        TenantProvisionResult result = tenantProvisioningService.provisionTenant(provisionRequest, null);

        User user = result.getAdminUser();
        UserRole userRole = result.getAdminUserRole();
        Tenant tenant = result.getTenant();

        String accessToken = jwtService.generateAccessToken(user.getId(), user.getTenantId(), user.getRoleId(),
                userRole.getRole().getName());
        String rawRefreshToken = generateAndStoreRefreshToken(user.getId());

        setRefreshCookie(response, rawRefreshToken);
        setAccessCookie(response, accessToken);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getAccessTokenExpiryMs() / 1000)
                .user(userManagementMapper.toUserResponse(user, userRole))
                .tenant(tenantMapper.toTenantInfo(tenant))
                .build();
    }

    // ───────────────────────────── LOGIN ─────────────────────────────

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request, HttpServletResponse response) {
        // 1. Find user by email
        User user = userRepository.findByEmail(request.getEmail().toLowerCase().trim())
                .orElseThrow(() -> new UnauthorizedException("INVALID_CREDENTIALS", "Invalid email or password"));

        // 2. Verify password
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException("INVALID_CREDENTIALS", "Invalid email or password");
        }

        // 3. Check user is active
        if (!user.getIsActive()) {
            throw new UnauthorizedException("USER_INACTIVE", "Account is disabled");
        }

        // 4. Check tenant is active (only for tenant users, not platform admins)
        Tenant tenant = null;
        if (user.getTenantId() != null) {
            tenant = tenantRepository.findById(user.getTenantId())
                    .orElseThrow(() -> new UnauthorizedException("TENANT_NOT_FOUND", "Tenant organization not found"));
            if (!tenant.getIsActive()) {
                throw new UnauthorizedException("TENANT_INACTIVE", "Tenant organization is disabled");
            }
        }

        // 5. Get user role
        UserRole userRole = findUserRole(user)
                .orElseThrow(
                        () -> new UnauthorizedException("ROLE_NOT_ASSIGNED", "User does not have an assigned role"));
        UUID roleId = userRole.getRoleId();
        String roleName = userRole.getRole() != null ? userRole.getRole().getName() : null;

        // 6. Generate tokens
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getTenantId(), roleId, roleName);
        String rawRefreshToken = generateAndStoreRefreshToken(user.getId());

        // 7. Set refresh token in HTTP-only cookie
        setRefreshCookie(response, rawRefreshToken);
        setAccessCookie(response, accessToken);

        // 8. Update last login
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        // 8. Return access token + user info + tenant
        return AuthResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getAccessTokenExpiryMs() / 1000)
                .user(userManagementMapper.toUserResponse(user, userRole))
                .tenant(tenantMapper.toTenantInfo(tenant))
                .build();
    }

    // ───────────────────────────── REFRESH ─────────────────────────────

    @Override
    @Transactional
    public AuthResponse refresh(HttpServletRequest request, HttpServletResponse response) {
        // 1. Extract refresh token from cookie
        String rawRefreshToken = extractRefreshTokenFromCookie(request);
        if (rawRefreshToken == null) {
            throw new UnauthorizedException("REFRESH_TOKEN_EXPIRED", "Refresh token not found");
        }

        // 2. Hash the token and look up in DB
        String tokenHash = hashToken(rawRefreshToken);
        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new UnauthorizedException("REFRESH_TOKEN_EXPIRED", "Refresh token is invalid"));

        // 3. Validate token state
        if (storedToken.isRevoked()) {
            // Potential token theft — revoke ALL user tokens
            refreshTokenRepository.revokeAllByUserId(storedToken.getUserId(), Instant.now());
            clearRefreshCookie(response);
            throw new UnauthorizedException("REFRESH_TOKEN_REVOKED",
                    "Refresh token was revoked. All sessions invalidated.");
        }

        if (storedToken.isExpired()) {
            clearRefreshCookie(response);
            throw new UnauthorizedException("REFRESH_TOKEN_EXPIRED", "Refresh token has expired");
        }

        // 4. Load user
        User user = userRepository.findById(storedToken.getUserId())
                .orElseThrow(() -> new UnauthorizedException("INVALID_CREDENTIALS", "User not found"));

        if (!user.getIsActive()) {
            throw new UnauthorizedException("USER_INACTIVE", "Account is disabled");
        }

        // 5. Token rotation: revoke old, create new
        refreshTokenRepository.revokeByTokenHash(tokenHash, Instant.now());
        String newRawRefreshToken = generateAndStoreRefreshToken(user.getId());

        // 6. Generate new access token
        UserRole userRole = findUserRole(user)
                .orElseThrow(
                        () -> new UnauthorizedException("ROLE_NOT_ASSIGNED", "User does not have an assigned role"));

        String accessToken = jwtService.generateAccessToken(
                user.getId(),
                user.getTenantId(),
                user.getRoleId(),
                userRole.getRole().getName());

        // 7. Set new refresh cookie
        setRefreshCookie(response, newRawRefreshToken);
        setAccessCookie(response, accessToken);

        Tenant tenant = user.getTenantId() == null
                ? null
                : tenantRepository.findById(user.getTenantId()).orElse(null);

        log.debug("Token refreshed for user: {}", user.getEmail());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getAccessTokenExpiryMs() / 1000)
                .user(userManagementMapper.toUserResponse(user, userRole))
                .tenant(tenantMapper.toTenantInfo(tenant))
                .build();
    }

    // ───────────────────────────── LOGOUT ─────────────────────────────

    @Override
    @Transactional
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        // 1. Blacklist the access token in Redis
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String accessToken = authHeader.substring(7);
            long remainingSeconds = jwtService.getRemainingExpirySeconds(accessToken);
            tokenBlacklistService.blacklist(accessToken, remainingSeconds);
        }

        // 2. Revoke the refresh token in DB
        String rawRefreshToken = extractRefreshTokenFromCookie(request);
        if (rawRefreshToken != null) {
            String tokenHash = hashToken(rawRefreshToken);
            refreshTokenRepository.revokeByTokenHash(tokenHash, Instant.now());
        }

        // 3. Clear the refresh cookie
        clearRefreshCookie(response);
        clearAccessCookie(response);

        log.info("User logged out successfully");
    }

    // ────────────────────────── CHANGE PASSWORD ──────────────────────────

    @Override
    @Transactional
    public void changePassword(ChangePasswordRequest request, String userId) {
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new UnauthorizedException("INVALID_CREDENTIALS", "User not found"));

        // Verify current password
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new BusinessException("INVALID_CREDENTIALS", "Current password is incorrect");
        }

        // Set new password
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // Revoke all refresh tokens (force re-login on other devices)
        refreshTokenRepository.revokeAllByUserId(user.getId(), Instant.now());

        log.info("Password changed for user: {}", user.getEmail());
    }

    // ──────────────────────────── FORGOT PASSWORD ────────────────────────────
    @Override
    public void initiatePasswordReset(String email) {
        // For security, we do not reveal whether the email exists or not
        userRepository.findByEmail(email.toLowerCase().trim()).ifPresent(user -> {
            // Initiate password reset logic (e.g., generate reset token, send email)
            // for this example, we'll just log it. In a real implementation, you'd generate
            // a secure token, save it, and send an email with a reset link.
            log.info("Password reset requested for email: {}", email);
        });
    }

    // ──────────────────────────── FORGOT PASSWORD ────────────────────────────
    @Override
    public void resetPassword(String resetToken, String newPassword) {
        // This method would be called when the user clicks the password reset link in
        // their email.
        // You would verify the reset token, find the associated user, and allow them to
        // set a new password.
        // For this example, we'll just log it. In a real implementation, you'd look up
        // the reset token, verify it, and update the user's password.
        log.info("Resetting password with token: {}", resetToken);
    }

    // ──────────────────────────── GET ME ────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(String userId) {
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new UnauthorizedException("INVALID_CREDENTIALS", "User not found"));
        UserRole userRole = findUserRole(user).orElse(null);
        return userManagementMapper.toUserResponse(user, userRole);
    }

    // ═══════════════════════════ PRIVATE HELPERS ═══════════════════════════

    /**
     * Generate a random refresh token, hash it, and store in PostgreSQL.
     */
    private String generateAndStoreRefreshToken(UUID userId) {
        String rawToken = UUID.randomUUID().toString();
        String tokenHash = hashToken(rawToken);

        RefreshToken refreshToken = RefreshToken.builder()
                .userId(userId)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plus(Duration.ofDays(refreshTokenExpiryDays)))
                .build();

        refreshTokenRepository.save(refreshToken);
        return rawToken;
    }

    /**
     * SHA-256 hash the raw token for storage.
     * We never store raw tokens in the DB.
     */
    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Set refresh token as HTTP-only cookie.
     * Per SKILL-04: HttpOnly=true, Secure=true, SameSite=Strict
     */
    private void setRefreshCookie(HttpServletResponse response, String refreshToken) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, refreshToken)
                .httpOnly(true)
                .secure(false) // set to true in production (HTTPS)
                .path(REFRESH_COOKIE_PATH)
                .maxAge(Duration.ofDays(refreshTokenExpiryDays))
                .sameSite("lax")
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    private void setAccessCookie(HttpServletResponse response, String accessToken) {

        ResponseCookie cookie = ResponseCookie.from(ACCESS_COOKIE_NAME, accessToken)
                .httpOnly(true)
                .secure(false) // true in production HTTPS
                .path("/")
                .maxAge(Duration.ofMinutes(15))
                .sameSite("Lax")
                .build();

        response.addHeader("Set-Cookie", cookie.toString());
    }

    /**
     * Clear the refresh cookie on logout.
     */
    private void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(false)
                .path(REFRESH_COOKIE_PATH)
                .maxAge(0)
                .sameSite("lax")
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    private void clearAccessCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(ACCESS_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(false)
                .path("/")
                .maxAge(0)
                .sameSite("Lax")
                .build();

        response.addHeader("Set-Cookie", cookie.toString());
    }

    /**
     * Extract refresh token from HTTP-only cookie.
     */
    private String extractRefreshTokenFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (REFRESH_COOKIE_NAME.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    private java.util.Optional<UserRole> findUserRole(User user) {
        if (user.getTenantId() == null) {
            return userRoleRepository.findPlatformRole(user.getId());
        }

        return userRoleRepository.findByUserIdAndTenantId(user.getId(), user.getTenantId());
    }
}
