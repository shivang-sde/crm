package com.shivang.crm.modules.feedback.service;

import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shivang.crm.modules.auth.repository.UserRepository;
import com.shivang.crm.modules.auth.security.TenantContext;
import com.shivang.crm.modules.feedback.dto.FeedbackCreateRequest;
import com.shivang.crm.modules.feedback.dto.FeedbackResponse;
import com.shivang.crm.modules.feedback.entity.FeedbackSource;
import com.shivang.crm.shared.exception.BusinessException;

import lombok.RequiredArgsConstructor;

/**
 * L3.5: trusted feedback ingestion over the existing commercial API key.
 *
 * <p>Reuses the email-based submission contract and the integration filter's
 * tenant binding. No new credential exists: the same tenant key that already
 * authenticates commercial sync authenticates feedback. The source defaults to
 * {@code API} so this provenance is truthful even when the caller omits it.
 */
@Service
@RequiredArgsConstructor
public class FeedbackIntegrationService {

    private final FeedbackService feedbackService;
    private final UserRepository userRepository;
    private final TenantContext tenantContext;

    @Transactional
    public FeedbackResponse submit(FeedbackCreateRequest request) {
        UUID tenantId = tenantContext.requireTenantId();
        if (request.getSource() == null) {
            request.setSource(FeedbackSource.API);
        }
        return feedbackService.submit(tenantId, resolveActorUserId(tenantId), request);
    }

    private UUID resolveActorUserId(UUID tenantId) {
        var page = userRepository.findByTenantId(tenantId, PageRequest.of(0, 1));
        if (page.hasContent()) {
            return page.getContent().get(0).getId();
        }
        throw new BusinessException("NO_USER_FOR_TENANT",
                "Tenant has no user to act as integration actor");
    }
}
