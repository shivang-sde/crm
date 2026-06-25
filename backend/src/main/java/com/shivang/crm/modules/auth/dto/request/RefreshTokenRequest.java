package com.shivang.crm.modules.auth.dto.request;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Placeholder DTO for refresh token requests.
 * The actual refresh token is read from the HTTP-only cookie,
 * not from the request body.
 */
@Data
@NoArgsConstructor
public class RefreshTokenRequest {
    // The refresh token is extracted from the HTTP-only cookie.
    // This DTO exists for potential future fields (e.g., device fingerprint).
}
