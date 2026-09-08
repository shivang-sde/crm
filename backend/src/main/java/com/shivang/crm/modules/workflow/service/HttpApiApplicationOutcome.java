package com.shivang.crm.modules.workflow.service;

/**
 * Application-level outcome interpretation for HTTP_API responses.
 * Provider-agnostic, backward compatible.
 *
 * Precedence:
 * 1) Transport failure
 * 2) HTTP failure (non-2xx)
 * 3) Explicit application-level failure signal
 * 4) Explicit application-level success signal
 * 5) Otherwise UNKNOWN (retain transport/HTTP result)
 */
public enum HttpApiApplicationOutcome {
    SUCCESS,
    FAILURE,
    UNKNOWN
}
