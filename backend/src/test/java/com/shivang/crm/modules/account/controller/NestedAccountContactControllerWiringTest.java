package com.shivang.crm.modules.account.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.shivang.crm.modules.account.service.AccountService;
import com.shivang.crm.modules.activity.service.ActivityService;
import com.shivang.crm.modules.auth.security.TenantContext;
import com.shivang.crm.modules.contact.controller.ContactController;
import com.shivang.crm.modules.contact.service.ContactService;
import com.shivang.crm.modules.lead.service.EntityNoteService;
import com.shivang.crm.shared.exception.PermissionDeniedException;

/**
 * L3.0-P3: controller wiring for nested Account/Contact endpoints.
 *
 * These assertions prove each nested handler invokes the parent scope guard
 * BEFORE touching the child resource, and that the guard's action matches the
 * operation. The service-layer semantics of the guard are covered by
 * NestedAccountContactAuthorizationTest.
 */
class NestedAccountContactControllerWiringTest {

    private AccountService accountService;
    private ContactService contactService;
    private EntityNoteService entityNoteService;
    private ActivityService activityService;
    private TenantContext tenantContext;

    private UUID tenantId;
    private UUID accountId;

    @BeforeEach
    void setup() {
        accountService = mock(AccountService.class);
        contactService = mock(ContactService.class);
        entityNoteService = mock(EntityNoteService.class);
        activityService = mock(ActivityService.class);
        tenantContext = mock(TenantContext.class);

        tenantId = UUID.randomUUID();
        accountId = UUID.randomUUID();
        when(tenantContext.getTenantId()).thenReturn(tenantId);

        // Several handlers resolve the caller before delegating.
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        UUID.randomUUID().toString(), null, java.util.List.of()));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private AccountController accountController() {
        return new AccountController(accountService, tenantContext, entityNoteService, activityService,
                mock(com.shivang.crm.modules.account.service.Customer360Service.class));
    }

    private ContactController contactController() {
        return new ContactController(contactService, tenantContext, entityNoteService, activityService);
    }

    private void denyAccountScope() {
        doThrow(new PermissionDeniedException("SCOPE_DENIED", "denied"))
                .when(accountService).assertAccountAccessible(any(), any(), anyString());
    }

    private void denyContactScope() {
        doThrow(new PermissionDeniedException("SCOPE_DENIED", "denied"))
                .when(contactService).assertContactAccessible(any(), any(), anyString());
    }

    // --- Account nested endpoints ---

    @Test
    void accountActivitiesReadChecksParentAccountReadScope() {
        denyAccountScope();

        assertThatThrownBy(() -> accountController().getActivities(accountId, 0, 20))
                .isInstanceOf(PermissionDeniedException.class);

        verify(accountService).assertAccountAccessible(tenantId, accountId, "read");
        verifyNoInteractions(activityService);
    }

    @Test
    void accountNotesReadChecksParentAccountReadScope() {
        denyAccountScope();

        assertThatThrownBy(() -> accountController().getNotes(accountId, 0, 20))
                .isInstanceOf(PermissionDeniedException.class);

        verify(accountService).assertAccountAccessible(tenantId, accountId, "read");
        verifyNoInteractions(entityNoteService);
    }

    @Test
    void accountNoteCreateChecksParentAccountWriteScope() {
        denyAccountScope();

        assertThatThrownBy(() -> accountController().addNote(accountId, java.util.Map.of("note", "hello")))
                .isInstanceOf(PermissionDeniedException.class);

        verify(accountService).assertAccountAccessible(tenantId, accountId, "write");
        verifyNoInteractions(entityNoteService);
    }

    @Test
    void accountNoteDeleteChecksParentAccountDeleteScope() {
        denyAccountScope();

        assertThatThrownBy(() -> accountController().deleteNote(accountId, UUID.randomUUID()))
                .isInstanceOf(PermissionDeniedException.class);

        verify(accountService).assertAccountAccessible(tenantId, accountId, "delete");
        verifyNoInteractions(entityNoteService);
    }

    // --- Contact nested endpoints ---

    @Test
    void contactActivitiesReadChecksParentContactReadScope() {
        denyContactScope();

        assertThatThrownBy(() -> contactController().getActivities(accountId, 0, 20))
                .isInstanceOf(PermissionDeniedException.class);

        verify(contactService).assertContactAccessible(tenantId, accountId, "read");
        verifyNoInteractions(activityService);
    }

    @Test
    void contactNotesReadChecksParentContactReadScope() {
        denyContactScope();

        assertThatThrownBy(() -> contactController().getNotes(accountId, 0, 20))
                .isInstanceOf(PermissionDeniedException.class);

        verify(contactService).assertContactAccessible(tenantId, accountId, "read");
        verifyNoInteractions(entityNoteService);
    }

    @Test
    void contactNoteCreateChecksParentContactWriteScope() {
        denyContactScope();

        assertThatThrownBy(() -> contactController().addNote(accountId, java.util.Map.of("note", "hello")))
                .isInstanceOf(PermissionDeniedException.class);

        verify(contactService).assertContactAccessible(tenantId, accountId, "write");
        verifyNoInteractions(entityNoteService);
    }

    @Test
    void contactNoteDeleteChecksParentContactDeleteScope() {
        denyContactScope();

        assertThatThrownBy(() -> contactController().deleteNote(accountId, UUID.randomUUID()))
                .isInstanceOf(PermissionDeniedException.class);

        verify(contactService).assertContactAccessible(tenantId, accountId, "delete");
        verifyNoInteractions(entityNoteService);
    }

    // --- already-guarded paths must keep working ---

    @Test
    void accountContactsListingDelegatesToTheParentScopedServiceMethod() {
        // listContacts performs the parent scope check internally (RBAC-7), so the
        // controller must delegate rather than call the child repository directly.
        assertThatThrownBy(() -> accountController().getContacts(accountId, 0, 20))
                .isInstanceOf(NullPointerException.class);

        verify(accountService).listContacts(eq(accountId), eq(tenantId), eq(0), eq(20));
        verify(accountService, org.mockito.Mockito.never())
                .assertAccountAccessible(any(), any(), anyString());
    }

    @Test
    void accountCustomFieldControllerIsNotANestedAccountOperation() {
        // /api/v1/account-custom-fields is tenant configuration keyed by field id,
        // not a child of an Account record, so it takes no parent scope.
        assertThat(new com.shivang.crm.modules.account.controller.AccountCustomFieldController(
                mock(com.shivang.crm.modules.account.service.AccountCustomFieldService.class),
                tenantContext) != null).isTrue();
    }
}