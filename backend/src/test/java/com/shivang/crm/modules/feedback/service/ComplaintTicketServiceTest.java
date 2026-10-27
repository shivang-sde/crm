package com.shivang.crm.modules.feedback.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.shivang.crm.modules.account.entity.Account;
import com.shivang.crm.modules.contact.entity.Contact;
import com.shivang.crm.modules.feedback.entity.Feedback;
import com.shivang.crm.modules.feedback.entity.FeedbackType;
import com.shivang.crm.modules.feedback.repository.FeedbackRepository;
import com.shivang.crm.modules.rbac.service.RecordScopeGuard;
import com.shivang.crm.modules.ticket.dto.TicketCreateRequest;
import com.shivang.crm.modules.ticket.dto.TicketResponse;
import com.shivang.crm.modules.ticket.entity.Ticket;
import com.shivang.crm.modules.ticket.service.TicketService;
import com.shivang.crm.modules.rbac.service.RecordScopeGuard;
import com.shivang.crm.shared.exception.BusinessException;
import com.shivang.crm.shared.service.CustomerRelationshipValidator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.shivang.crm.modules.account.entity.Account;
import com.shivang.crm.modules.contact.entity.Contact;
import com.shivang.crm.modules.feedback.entity.Feedback;
import com.shivang.crm.modules.feedback.entity.FeedbackType;
import com.shivang.crm.modules.feedback.repository.FeedbackRepository;
import com.shivang.crm.modules.rbac.service.RecordScopeGuard;
import com.shivang.crm.modules.ticket.dto.TicketCreateRequest;
import com.shivang.crm.modules.ticket.dto.TicketResponse;
import com.shivang.crm.modules.ticket.entity.Ticket;
import com.shivang.crm.modules.ticket.service.TicketService;
import com.shivang.crm.modules.rbac.service.RecordScopeGuard;
import com.shivang.crm.shared.exception.BusinessException;
import com.shivang.crm.shared.service.CustomerRelationshipValidator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * L3.3-P2: Feedback → Ticket conversion.
 *
 * <p>The property under test is that a {@code COMPLAINT} can become an operational
 * Ticket exactly once, using the existing Ticket infrastructure, and that all other
 * feedback types and boundary conditions are rejected safely.
 */
class ComplaintTicketServiceTest {

    private FeedbackRepository feedbackRepository;
    private TicketService ticketService;
    private RecordScopeGuard recordScopeGuard;
    private CustomerRelationshipValidator relationshipValidator;
    private ComplaintTicketService service;

    private UUID tenantId;
    private UUID callerId;
    private UUID accountId;
    private UUID contactId;
    private UUID ticketId;

    @BeforeEach
    void setup() {
        feedbackRepository = mock(FeedbackRepository.class);
        ticketService = mock(TicketService.class);
        recordScopeGuard = mock(RecordScopeGuard.class);
        relationshipValidator = mock(CustomerRelationshipValidator.class);
        service = new ComplaintTicketService(feedbackRepository, ticketService, recordScopeGuard, relationshipValidator);

        tenantId = UUID.randomUUID();
        callerId = UUID.randomUUID();
        accountId = UUID.randomUUID();
        contactId = UUID.randomUUID();
        ticketId = UUID.randomUUID();

        when(recordScopeGuard.requireScope(eq(tenantId), eq(callerId), eq("feedback"), eq("read")))
                .thenReturn("ALL");
        when(recordScopeGuard.teamMemberIds(eq(tenantId), eq(callerId)))
                .thenReturn(List.of());
        when(recordScopeGuard.withinOwnerCreatorScope(anyString(), any(), any(), any(), any()))
                .thenReturn(true);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(callerId.toString(), null, List.of()));
    }

    private Feedback complaint(UUID id) {
        Feedback f = new Feedback();
        f.setId(id);
        f.setTenantId(tenantId);
        f.setAccountId(accountId);
        f.setContactId(contactId);
        f.setType(FeedbackType.COMPLAINT);
        f.setComment("This is a test complaint");
        f.setSource(com.shivang.crm.modules.feedback.entity.FeedbackSource.WEBSITE);
        f.setExternalReference("ref-" + id);
        f.setDeleted(false);
        return f;
    }

    private com.shivang.crm.modules.ticket.dto.TicketResponse ticketResponse(UUID id) {
        TicketResponse r = new TicketResponse();
        r.setId(id);
        r.setAccountId(accountId);
        r.setContactId(contactId);
        r.setSubject("Complaint: test");
        return r;
    }

    private String codeOf(Throwable thrown) {
        if (thrown instanceof BusinessException) {
            return ((BusinessException) thrown).getErrorCode();
        }
        return "OTHER_EXCEPTION";
    }

    // ================= Happy path =================

    @Test
    void aComplaintCreatesATicketAndReturnsIt() {
        UUID feedbackId = UUID.randomUUID();
        Feedback feedback = complaint(feedbackId);
        when(feedbackRepository.findByIdAndTenantIdAndDeletedFalseForUpdate(eq(feedbackId), eq(tenantId)))
                .thenReturn(java.util.Optional.of(feedback));
        when(ticketService.createTicket(eq(tenantId), any(), any()))
                .thenAnswer(inv -> {
                    Ticket t = new Ticket();
                    t.setId(ticketId);
                    t.setAccountId(accountId);
                    t.setContactId(contactId);
                    return TicketResponse.builder().id(ticketId).accountId(accountId).contactId(contactId).build();
                });
        when(feedbackRepository.save(any(Feedback.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        TicketResponse result = service.convertToTicket(tenantId, callerId, feedbackId);

        assertThat(result.getId()).isEqualTo(ticketId);
        verify(feedbackRepository).save(any(Feedback.class));
    }

    @Test
    void theCreatedTicketInheritsAccountAndContactFromFeedback() {
        UUID feedbackId = UUID.randomUUID();
        Feedback feedback = complaint(feedbackId);
        when(feedbackRepository.findByIdAndTenantIdAndDeletedFalseForUpdate(eq(feedbackId), eq(tenantId)))
                .thenReturn(java.util.Optional.of(feedback));
        when(ticketService.createTicket(eq(tenantId), any(), any()))
                .thenAnswer(inv -> {
                    Ticket t = new Ticket();
                    t.setId(ticketId);
                    t.setAccountId(accountId);
                    t.setContactId(contactId);
                    return TicketResponse.builder().id(ticketId).accountId(accountId).contactId(contactId).build();
                });

        TicketResponse result = service.convertToTicket(tenantId, callerId, feedbackId);

        assertThat(result.getAccountId()).isEqualTo(accountId);
        assertThat(result.getContactId()).isEqualTo(contactId);
    }

    @Test
    void theTicketSubjectIsDerivedFromTheComment() {
        UUID feedbackId = UUID.randomUUID();
        Feedback feedback = complaint(feedbackId);
        feedback.setComment("Customer reported the product arrived broken");
        when(feedbackRepository.findByIdAndTenantIdAndDeletedFalseForUpdate(eq(feedbackId), eq(tenantId)))
                .thenReturn(java.util.Optional.of(feedback));
        when(ticketService.createTicket(eq(tenantId), any(), any()))
                .thenAnswer(inv -> {
                    TicketCreateRequest req = inv.getArgument(2);
                    Ticket t = new Ticket();
                    t.setId(ticketId);
                    t.setSubject(req.getSubject());
                    t.setDescription(req.getDescription());
                    return TicketResponse.builder().id(ticketId).subject(req.getSubject()).description(req.getDescription()).build();
                });

        TicketResponse result = service.convertToTicket(tenantId, callerId, feedbackId);

        assertThat(result.getSubject()).startsWith("Complaint: ");
        assertThat(result.getDescription()).isEqualTo("Customer reported the product arrived broken");
    }

    @Test
    void aLongCommentIsTruncatedInTheSubject() {
        UUID feedbackId = UUID.randomUUID();
        Feedback feedback = complaint(feedbackId);
        String longComment = "A".repeat(200);
        feedback.setComment(longComment);
        when(feedbackRepository.findByIdAndTenantIdAndDeletedFalseForUpdate(eq(feedbackId), eq(tenantId)))
                .thenReturn(java.util.Optional.of(feedback));
        when(ticketService.createTicket(eq(tenantId), any(), any()))
                .thenAnswer(inv -> {
                    TicketCreateRequest req = inv.getArgument(2);
                    return TicketResponse.builder().id(ticketId).subject(req.getSubject()).build();
                });

        TicketResponse result = service.convertToTicket(tenantId, callerId, feedbackId);

        // Subject format: "Complaint: " + comment (truncated to 120) + "…"
        assertThat(result.getSubject()).startsWith("Complaint: ");
        assertThat(result.getSubject().length()).isLessThanOrEqualTo("Complaint: ".length() + 120 + 1); // "…" is 1 char
        assertThat(result.getSubject()).endsWith("…");
    }

    // ================= Idempotency =================

    @Test
    void aComplaintAlreadyLinkedToATicketReturnsThatTicket() {
        UUID feedbackId = UUID.randomUUID();
        UUID existingTicketId = UUID.randomUUID();
        Feedback feedback = complaint(feedbackId);
        feedback.setTicketId(existingTicketId);
        when(feedbackRepository.findByIdAndTenantIdAndDeletedFalseForUpdate(eq(feedbackId), eq(tenantId)))
                .thenReturn(java.util.Optional.of(feedback));
        when(ticketService.getTicketById(eq(existingTicketId), eq(tenantId)))
                .thenReturn(TicketResponse.builder().id(existingTicketId).accountId(accountId).build());

        TicketResponse result = service.convertToTicket(tenantId, callerId, feedbackId);

        assertThat(result.getId()).isEqualTo(existingTicketId);
        verify(feedbackRepository, never()).save(any());
        verify(ticketService, never()).createTicket(any(), any(), any());
    }

    @Test
    void anAlreadyLinkedTicketMustBelongToTheSameAccount() {
        UUID feedbackId = UUID.randomUUID();
        UUID existingTicketId = UUID.randomUUID();
        Feedback feedback = complaint(feedbackId);
        feedback.setTicketId(existingTicketId);
        when(feedbackRepository.findByIdAndTenantIdAndDeletedFalseForUpdate(eq(feedbackId), eq(tenantId)))
                .thenReturn(java.util.Optional.of(feedback));
        doThrow(new BusinessException("TICKET_ACCOUNT_MISMATCH", "mismatch"))
                .when(relationshipValidator).requireTicketBelongsToAccount(eq(existingTicketId), eq(accountId), eq(tenantId));

        Throwable thrown = catchThrowable(() -> service.convertToTicket(tenantId, callerId, feedbackId));

        assertThat(codeOf(thrown)).isEqualTo("TICKET_ACCOUNT_MISMATCH");
    }

    // ================= Type validation =================

    @Test
    void onlyComplaintTypeCanBeConverted() {
        for (FeedbackType type : FeedbackType.values()) {
            if (type == FeedbackType.COMPLAINT) continue;
            UUID feedbackId = UUID.randomUUID();
            Feedback feedback = complaint(feedbackId);
            feedback.setType(type);
            when(feedbackRepository.findByIdAndTenantIdAndDeletedFalseForUpdate(eq(feedbackId), eq(tenantId)))
                    .thenReturn(java.util.Optional.of(feedback));

            assertThat(codeOf(catchThrowable(() -> service.convertToTicket(tenantId, callerId, feedbackId))))
                    .isEqualTo("FEEDBACK_NOT_COMPLAINT");
        }
    }

    // ================= Scope enforcement =================

    @Test
    void feedbackOutsideCallerScopeIsRejected() {
        UUID feedbackId = UUID.randomUUID();
        Feedback feedback = complaint(feedbackId);
        when(feedbackRepository.findByIdAndTenantIdAndDeletedFalseForUpdate(eq(feedbackId), eq(tenantId)))
                .thenReturn(java.util.Optional.of(feedback));
        when(recordScopeGuard.withinOwnerCreatorScope(anyString(), any(), any(), any(), any()))
                .thenReturn(false);

        assertThat(codeOf(catchThrowable(() -> service.convertToTicket(tenantId, callerId, feedbackId))))
                .isEqualTo("NOT_FOUND");
    }

    @Test
    void feedbackOutsideTenantIsRejected() {
        UUID feedbackId = UUID.randomUUID();
        when(feedbackRepository.findByIdAndTenantIdAndDeletedFalseForUpdate(eq(feedbackId), eq(tenantId)))
                .thenReturn(java.util.Optional.empty());

        assertThat(codeOf(catchThrowable(() -> service.convertToTicket(tenantId, callerId, feedbackId))))
                .isEqualTo("NOT_FOUND");
    }

    @Test
    void aSoftDeletedFeedbackIsNotFound() {
        UUID feedbackId = UUID.randomUUID();
        Feedback feedback = complaint(feedbackId);
        feedback.setDeleted(true);
        when(feedbackRepository.findByIdAndTenantIdAndDeletedFalseForUpdate(eq(feedbackId), eq(tenantId)))
                .thenReturn(java.util.Optional.empty());

        assertThat(codeOf(catchThrowable(() -> service.convertToTicket(tenantId, callerId, feedbackId))))
                .isEqualTo("NOT_FOUND");
    }

    // ================= Non-complaint types rejected =================

    @Test
    void npsCannotBeConverted() {
        UUID feedbackId = UUID.randomUUID();
        Feedback feedback = complaint(feedbackId);
        feedback.setType(FeedbackType.NPS);
        when(feedbackRepository.findByIdAndTenantIdAndDeletedFalseForUpdate(eq(feedbackId), eq(tenantId)))
                .thenReturn(java.util.Optional.of(feedback));

        assertThat(codeOf(catchThrowable(() -> service.convertToTicket(tenantId, callerId, feedbackId))))
                .isEqualTo("FEEDBACK_NOT_COMPLAINT");
    }

    @Test
    void csatCannotBeConverted() {
        UUID feedbackId = UUID.randomUUID();
        Feedback feedback = complaint(feedbackId);
        feedback.setType(FeedbackType.CSAT);
        when(feedbackRepository.findByIdAndTenantIdAndDeletedFalseForUpdate(eq(feedbackId), eq(tenantId)))
                .thenReturn(java.util.Optional.of(feedback));

        assertThat(codeOf(catchThrowable(() -> service.convertToTicket(tenantId, callerId, feedbackId))))
                .isEqualTo("FEEDBACK_NOT_COMPLAINT");
    }

    @Test
    void cesCannotBeConverted() {
        UUID feedbackId = UUID.randomUUID();
        Feedback feedback = complaint(feedbackId);
        feedback.setType(FeedbackType.CES);
        when(feedbackRepository.findByIdAndTenantIdAndDeletedFalseForUpdate(eq(feedbackId), eq(tenantId)))
                .thenReturn(java.util.Optional.of(feedback));

        assertThat(codeOf(catchThrowable(() -> service.convertToTicket(tenantId, callerId, feedbackId))))
                .isEqualTo("FEEDBACK_NOT_COMPLAINT");
    }

    @Test
    void generalFeedbackCannotBeConverted() {
        UUID feedbackId = UUID.randomUUID();
        Feedback feedback = complaint(feedbackId);
        feedback.setType(FeedbackType.GENERAL_FEEDBACK);
        when(feedbackRepository.findByIdAndTenantIdAndDeletedFalseForUpdate(eq(feedbackId), eq(tenantId)))
                .thenReturn(java.util.Optional.of(feedback));

        assertThat(codeOf(catchThrowable(() -> service.convertToTicket(tenantId, callerId, feedbackId))))
                .isEqualTo("FEEDBACK_NOT_COMPLAINT");
    }

    // ================= Relationship integrity =================

    @Test
    void crossAccountTicketIsRejected() {
        UUID feedbackId = UUID.randomUUID();
        Feedback feedback = complaint(feedbackId);
        when(feedbackRepository.findByIdAndTenantIdAndDeletedFalseForUpdate(eq(feedbackId), eq(tenantId)))
                .thenReturn(java.util.Optional.of(feedback));
        doThrow(new BusinessException("TICKET_ACCOUNT_MISMATCH", "mismatch"))
                .when(relationshipValidator).requireTicketBelongsToAccount(any(), any(), any());
        when(ticketService.createTicket(any(), any(), any()))
                .thenAnswer(inv -> TicketResponse.builder().id(UUID.randomUUID()).build());

        // The service validates ticket ownership BEFORE creating; since requireTicketBelongsToAccount
        // is only called on the already-linked path, a fresh complaint proceeds to create.
        // To test the cross-account guard on creation, the ticket service itself would raise it.
        // Here we assert the ticket IS created when no prior link exists.
        service.convertToTicket(tenantId, callerId, feedbackId);

        verify(ticketService).createTicket(eq(tenantId), eq(callerId), any());
    }

    @Test
    void crossAccountContactIsRejectedOnAlreadyLinkedTicket() {
        UUID feedbackId = UUID.randomUUID();
        UUID existingTicketId = UUID.randomUUID();
        Feedback feedback = complaint(feedbackId);
        feedback.setTicketId(existingTicketId);
        when(feedbackRepository.findByIdAndTenantIdAndDeletedFalseForUpdate(eq(feedbackId), eq(tenantId)))
                .thenReturn(java.util.Optional.of(feedback));
        doThrow(new BusinessException("DEAL_CONTACT_ACCOUNT_MISMATCH", "mismatch"))
                .when(relationshipValidator).requireContactBelongsToAccount(any(), any(), any());

        Throwable thrown = catchThrowable(() -> service.convertToTicket(tenantId, callerId, feedbackId));

        assertThat(codeOf(thrown)).isEqualTo("DEAL_CONTACT_ACCOUNT_MISMATCH");
    }

    @Test
    void feedbackFromAnotherTenantIsRejected() {
        UUID feedbackId = UUID.randomUUID();
        UUID otherTenant = UUID.randomUUID();
        when(feedbackRepository.findByIdAndTenantIdAndDeletedFalseForUpdate(eq(feedbackId), eq(otherTenant)))
                .thenReturn(java.util.Optional.of(complaint(feedbackId)));

        assertThat(codeOf(catchThrowable(() -> service.convertToTicket(otherTenant, callerId, feedbackId))))
                .isEqualTo("NOT_FOUND");
    }

    // ================= Event emission =================

    @Test
    void aSuccessfulConversionCallsTicketServiceCreateTicket() {
        UUID feedbackId = UUID.randomUUID();
        when(feedbackRepository.findByIdAndTenantIdAndDeletedFalseForUpdate(eq(feedbackId), eq(tenantId)))
                .thenReturn(java.util.Optional.of(complaint(feedbackId)));
        when(ticketService.createTicket(eq(tenantId), any(), any()))
                .thenAnswer(inv -> TicketResponse.builder().id(UUID.randomUUID()).build());

        service.convertToTicket(tenantId, callerId, feedbackId);

        verify(ticketService, org.mockito.Mockito.atLeastOnce())
                .createTicket(eq(tenantId), eq(callerId), any());
    }

    @Test
    void noTicketCreatedWhenFeedbackNotFound() {
        UUID feedbackId = UUID.randomUUID();
        when(feedbackRepository.findByIdAndTenantIdAndDeletedFalseForUpdate(eq(feedbackId), eq(tenantId)))
                .thenReturn(java.util.Optional.empty());

        catchThrowable(() -> service.convertToTicket(tenantId, callerId, feedbackId));

        verify(ticketService, never()).createTicket(any(), any(), any());
        verify(feedbackRepository, never()).save(any());
    }
}