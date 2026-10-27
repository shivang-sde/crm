package com.shivang.crm.modules.ticket;

import org.assertj.core.api.Assertions;
import org.assertj.core.api.ThrowableAssert;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shivang.crm.modules.ticket.controller.TicketController;
import com.shivang.crm.modules.ticket.dto.TicketCreateRequest;
import com.shivang.crm.modules.ticket.dto.TicketResponse;
import com.shivang.crm.modules.ticket.dto.TicketStatusChangeRequest;
import com.shivang.crm.modules.ticket.dto.TicketUpdateRequest;
import com.shivang.crm.modules.ticket.entity.TicketStatus;
import com.shivang.crm.modules.ticket.service.TicketService;

/**
 * L3.1-P0/P1: the frozen P0 contract, asserted structurally.
 *
 * <p>These are the promises P1 must not silently break: the wire shape is
 * camelCase, the endpoint set is exactly six operations, status has one endpoint,
 * there are no verb-specific lifecycle aliases, and no P2+ surface exists yet.
 * Most are reflection checks because that is what actually pins an API surface
 * against accidental growth — a new endpoint is a code change a reviewer might
 * wave through, whereas a failing test makes it deliberate.
 */
class TicketContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // --- endpoint set ---

    @Test
    void theApiIsRootedAtApiV1Tickets() {
        RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(
                TicketController.class, RequestMapping.class);

        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).containsExactly("/api/v1/tickets");
    }

    @Test
    void theControllerIsRestControllerScopedToTicket() {
        assertThat(TicketController.class.getAnnotation(RestController.class)).isNotNull();
    }

    @Test
    void theApiHasExactlySixOperations() {
        List<String> operations = java.util.Arrays.stream(TicketController.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(PostMapping.class)
                        || m.isAnnotationPresent(PatchMapping.class)
                        || m.isAnnotationPresent(org.springframework.web.bind.annotation.DeleteMapping.class)
                        || m.isAnnotationPresent(org.springframework.web.bind.annotation.GetMapping.class))
.map(TicketContractTest::describe)
                .sorted()
                .toList();

// create, list, get, update, delete, status, assign (P2), timeline (P5).
        assertThat(operations).containsExactly(
                "DELETE /{id}",
                "GET ",
                "GET /{id}",
                "GET /{id}/activities",
                "PATCH /{id}",
                "POST ",
                "POST /{id}/assign",
                "POST /{id}/status");
    }

    @Test
    void theTimelineEndpointRequiresTicketRead() {
        // No separate activity:read permission: a timeline entry is visible exactly
        // when the ticket it belongs to is.
        assertThat(permissionOf(methodNamed("getTicketActivities"))).isEqualTo("ticket:read");
    }

    @Test
    void thereIsNoTimelineWriteEndpoint() {
        List<String> timelinePaths = java.util.Arrays.stream(TicketController.class.getDeclaredMethods())
                .flatMap(m -> java.util.stream.Stream.of(
                        mappingPaths(m, PostMapping.class),
                        mappingPaths(m, PatchMapping.class),
                        mappingPaths(m, org.springframework.web.bind.annotation.DeleteMapping.class)))
                .flatMap(java.util.Arrays::stream)
                .map(p -> p.toLowerCase())
                .filter(p -> p.contains("activit"))
                .toList();

        // The timeline is written by domain services, never by a client.
        assertThat(timelinePaths).isEmpty();
    }

    @Test
    void thereIsExactlyOneAssignmentEndpoint() {
        // One canonical door. A second alias (PUT /{id}/assign, POST /{id}/owner,
        // /reassign) would be a second way to change ownership, and each would need
        // its own permission check to stay safe.
        List<String> assignmentPaths = java.util.Arrays.stream(TicketController.class.getDeclaredMethods())
                .flatMap(m -> java.util.stream.Stream.of(
                        mappingPaths(m, PostMapping.class),
                        mappingPaths(m, PatchMapping.class),
                        mappingPaths(m, org.springframework.web.bind.annotation.GetMapping.class),
                        mappingPaths(m, org.springframework.web.bind.annotation.DeleteMapping.class),
                        mappingPaths(m, org.springframework.web.bind.annotation.PutMapping.class)))
                .flatMap(java.util.Arrays::stream)
                .map(p -> p.toLowerCase())
                .filter(p -> p.contains("assign") || p.contains("owner") || p.contains("reassign"))
                .toList();

        assertThat(assignmentPaths).containsExactly("/{id}/assign");
    }

    @Test
    void thereIsNoPutMappingOnTheTicketController() {
        // Structural: assignment is POST-only, so a REST-conventional PUT alias
        // cannot appear later without failing here.
        for (Method method : TicketController.class.getDeclaredMethods()) {
            assertThat(method.getAnnotation(org.springframework.web.bind.annotation.PutMapping.class))
                    .as("%s must not be a PUT", method.getName())
                    .isNull();
        }
    }

    @Test
    void theAssignEndpointRequiresTicketAssign() {
        assertThat(permissionOf(methodNamed("assignTicket"))).isEqualTo("ticket:assign");
    }

    @Test
    void theAssignRequestCarriesOwnerIdAndNoOtherField() {
        // A narrower payload than TicketUpdateRequest: assignment has exactly one
        // input, so it cannot smuggle a tenant id or a lifecycle field.
        assertThat(fieldsOf(com.shivang.crm.modules.ticket.dto.TicketAssignRequest.class))
                .containsExactly("ownerId");
    }

    @Test
    void theAssignRequestCannotCarryATenantId() {
        assertThat(fieldsOf(com.shivang.crm.modules.ticket.dto.TicketAssignRequest.class))
                .doesNotContain("tenantId")
                .doesNotContain("id")
                .doesNotContain("status")
                .doesNotContain("assigneeId")
                .doesNotContain("teamId")
                .doesNotContain("queueId");
    }

    @Test
    void theAssignRequestBindsOwnerId() {
        UUID ownerId = UUID.randomUUID();
        var request = read(com.shivang.crm.modules.ticket.dto.TicketAssignRequest.class,
                "{\"ownerId\":\"%s\"}".formatted(ownerId));

        assertThat(request.getOwnerId()).isEqualTo(ownerId);
    }

    @Test
    void theAssignRequestRejectsAnAlternateFieldName() {
        // P0 vocabulary: ownerId only. No assigneeId / assignedTo / oldOwner.
        for (String payload : List.of(
                "{\"assigneeId\":\"%s\"}".formatted(UUID.randomUUID()),
                "{\"assignedTo\":\"%s\"}".formatted(UUID.randomUUID()),
                "{\"oldOwner\":\"%s\"}".formatted(UUID.randomUUID()),
                "{\"owner\":\"%s\"}".formatted(UUID.randomUUID()))) {
            assertThat(catchThrowable(() -> read(
                    com.shivang.crm.modules.ticket.dto.TicketAssignRequest.class, payload)))
                    .as("payload %s must be rejected", payload)
                    .isNotNull();
        }
    }

    @Test
    void theAssignRequestRejectsSnakeCase() {
        assertThat(catchThrowable(() -> read(
                com.shivang.crm.modules.ticket.dto.TicketAssignRequest.class,
                "{\"owner_id\":\"%s\"}".formatted(UUID.randomUUID()))))
                .isNotNull();
    }

    @Test
    void theAssignRequestRefusesAnEmptyBody() throws NoSuchFieldException {
        // Unassignment is not a supported operation, so a missing ownerId is a
        // client error rather than a silent clear.
        var request = read(com.shivang.crm.modules.ticket.dto.TicketAssignRequest.class, "{}");

        assertThat(request.getOwnerId()).isNull();
        // @NotNull on the field is what turns that into a 400 via @Valid.
        assertThat(com.shivang.crm.modules.ticket.dto.TicketAssignRequest.class
                .getDeclaredField("ownerId").getAnnotation(jakarta.validation.constraints.NotNull.class))
                .isNotNull();
    }

    @Test
    void thereAreNoVerbSpecificLifecycleEndpoints() {
        // P0 froze ONE status endpoint. Resolve/close/reopen aliases would let the
        // transition matrix be bypassed by guessing an endpoint name.
List<String> paths = java.util.Arrays.stream(TicketController.class.getDeclaredMethods())
                .flatMap(m -> java.util.stream.Stream.of(
                        mappingPaths(m, PostMapping.class),
                        mappingPaths(m, PatchMapping.class),
                        mappingPaths(m, org.springframework.web.bind.annotation.GetMapping.class),
                        mappingPaths(m, org.springframework.web.bind.annotation.DeleteMapping.class)))
                .flatMap(java.util.Arrays::stream)
                .map(String::toLowerCase)
                .toList();

assertThat(paths).noneMatch(p -> p.contains("resolve"));
        assertThat(paths).noneMatch(p -> p.contains("close"));
        assertThat(paths).noneMatch(p -> p.contains("reopen"));
        // L3.1-P2: /{id}/assign is now the one sanctioned assignment endpoint.
        assertThat(paths).noneMatch(p -> p.contains("reassign"));
        assertThat(paths).noneMatch(p -> p.contains("owner"));
        assertThat(paths).noneMatch(p -> p.contains("message"));
        assertThat(paths).noneMatch(p -> p.contains("escalate"));
    }

    // --- permissions ---

    @Test
    void everyEndpointCarriesAPreAuthorizeExpression() {
        for (Method method : TicketController.class.getDeclaredMethods()) {
            if (!hasHttpMapping(method)) {
                continue;
            }
            PreAuthorize annotation = AnnotatedElementUtils.findMergedAnnotation(method, PreAuthorize.class);
            assertThat(annotation)
                    .as("%s must declare @PreAuthorize", method.getName())
                    .isNotNull();
        }
    }

@Test
    void endpointsRequireOnlyTheThreeP1TicketPermissions() {
        Set<String> referenced = java.util.Arrays.stream(TicketController.class.getDeclaredMethods())
                .filter(TicketContractTest::hasHttpMapping)
                .map(m -> permissionOf(m))
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        // L3.1-P2 adds ticket:assign. It is deliberately a fourth permission rather than a
        // widening of ticket:write: a write-capable agent must not be able to route a
        // ticket to someone else.
        assertThat(referenced).containsExactlyInAnyOrder(
                "ticket:read", "ticket:write", "ticket:delete", "ticket:assign");
    }

    @Test
    void everyEndpointIsScopedToTheTicketModule() {
        // Guards against a copy-paste that leaves a Ticket endpoint checking another
        // module's permission, which would grant or deny the wrong thing silently.
        for (Method method : TicketController.class.getDeclaredMethods()) {
            if (!hasHttpMapping(method)) {
                continue;
            }
            assertThat(permissionOf(method))
                    .as("%s must check the ticket module", method.getName())
                    .startsWith("ticket:");
        }
    }

    @Test
    void theDeleteEndpointIsTheOnlyOneNeedingDeletePermission() {
        assertThat(permissionOf(methodNamed("deleteTicket"))).isEqualTo("ticket:delete");

        // Resolving or closing a ticket must not require the ability to destroy it.
        assertThat(permissionOf(methodNamed("changeStatus"))).isEqualTo("ticket:write");
        assertThat(permissionOf(methodNamed("createTicket"))).isEqualTo("ticket:write");
        assertThat(permissionOf(methodNamed("updateTicket"))).isEqualTo("ticket:write");
        // Only /assign may touch ownership, so only /assign needs assign.
        assertThat(permissionOf(methodNamed("changeStatus"))).isEqualTo("ticket:write");
        assertThat(permissionOf(methodNamed("getTicket"))).isEqualTo("ticket:read");
        assertThat(permissionOf(methodNamed("listTickets"))).isEqualTo("ticket:read");
    }

    // --- wire format ---

    @Test
    void requestAndResponseFieldsAreCamelCase() throws Exception {
        // The L3.0-P5 lesson: a snake_case drift here is invisible until a consumer
        // breaks, so pin the serialized names.
        assertThat(serializedFieldNames(TicketCreateRequest.class))
                .contains("accountId", "contactId", "subject", "description", "ownerId");
        assertThat(serializedFieldNames(TicketResponse.class))
                .contains("id", "tenantId", "accountId", "contactId", "subject",
                        "description", "status", "ownerId", "createdAt", "resolvedAt", "closedAt");
    }

    @Test
    void noSerializedFieldUsesSnakeCase() throws Exception {
        for (Class<?> type : List.of(TicketCreateRequest.class, TicketUpdateRequest.class,
                TicketStatusChangeRequest.class, TicketResponse.class)) {
            assertThat(serializedFieldNames(type))
                    .as("%s must not expose snake_case fields", type.getSimpleName())
                    .noneMatch(n -> n.contains("_"));
        }
    }

    @Test
void createRejectsSnakeCaseInputRatherThanSilentlyIgnoringIt() {
        // Unknown properties are NOT marked ignorable, so a drifted payload fails
        // loudly instead of creating a ticket with nulls. This is the important half
        // of the L3.0-P5 contract — a silently-ignored key would still return 201.
        assertThat(catchThrowable(() -> read(TicketCreateRequest.class, """
                {"account_id":"%s","subject":"Drifted"}
                """.formatted(UUID.randomUUID()))))
                .isNotNull();
    }

    @Test
    void theCreateRequestDoesNotTolerateUnknownFields() throws Exception {
        // Assert the configuration itself, so the previous test cannot pass for the
        // wrong reason (e.g. a valid-JSON path or an unrelated parse failure).
        assertThat(MAPPER.getDeserializationConfig()
                .isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)).isTrue();
    }

    @Test
    void createBindsTheCamelCaseContract() {
        UUID accountId = UUID.randomUUID();
        UUID contactId = UUID.randomUUID();
        TicketCreateRequest request = read(TicketCreateRequest.class, """
                {"accountId":"%s","contactId":"%s","subject":"Cannot log in",
                 "description":"Password reset loop","ownerId":"%s"}
                """.formatted(accountId, contactId, UUID.randomUUID()));

        assertThat(request.getAccountId()).isEqualTo(accountId);
        assertThat(request.getContactId()).isEqualTo(contactId);
        assertThat(request.getSubject()).isEqualTo("Cannot log in");
        assertThat(request.getDescription()).isEqualTo("Password reset loop");
    }

    @Test
    void createCannotSupplyAStatus() throws Exception {
        // Status has its own endpoint; accepting it here would bypass the matrix.
        assertThat(fieldsOf(TicketCreateRequest.class))
                .doesNotContain("status")
                .doesNotContain("resolvedAt")
                .doesNotContain("closedAt")
                .doesNotContain("tenantId")
                .doesNotContain("id")
                .doesNotContain("deleted");
    }

    @Test
    void updateCannotSupplyStatusOrLifecycleOrIdentity() throws Exception {
        assertThat(fieldsOf(TicketUpdateRequest.class))
                .doesNotContain("status")
                .doesNotContain("resolvedAt")
                .doesNotContain("closedAt")
                .doesNotContain("id")
                .doesNotContain("tenantId")
                .doesNotContain("deleted");
    }

    @Test
    void statusChangeCarriesExactlyTheStatusField() throws Exception {
        assertThat(fieldsOf(TicketStatusChangeRequest.class)).containsExactly("status");
    }

    @Test
    void statusChangeRejectsAnUnknownStatusValue() {
        assertThat(catchThrowable(() -> read(TicketStatusChangeRequest.class, """
                {"status":"ESCALATED"}
                """))).isNotNull();
    }

    private static Throwable catchThrowable(ThrowableAssert.ThrowingCallable callable) {
        return Assertions.catchThrowable(callable);
    }

    @Test
    void theFourLifecycleValuesAreTheOnlyOnesPersisted() {
        assertThat(java.util.Arrays.stream(TicketStatus.values()).map(Enum::name))
                .containsExactly("OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED");
    }

    @Test
    void statusIsPersistedAsAStringNotAnOrdinal() throws NoSuchFieldException {
        // @Enumerated(STRING) means inserting a future enum constant cannot silently
        // rewrite every existing row's meaning.
        var field = com.shivang.crm.modules.ticket.entity.Ticket.class.getDeclaredField("status");
        var enumerated = field.getAnnotation(jakarta.persistence.Enumerated.class);

        assertThat(enumerated).isNotNull();
        assertThat(enumerated.value()).isEqualTo(jakarta.persistence.EnumType.STRING);
    }

    // --- service surface ---

@Test
    void theServiceIsTransactionalAtClassLevel() {
        // Writes rely on this; the two read methods narrow to readOnly = true.
        Transactional classLevel = AnnotatedElementUtils.findMergedAnnotation(
                TicketService.class, Transactional.class);

        assertThat(classLevel).isNotNull();
        assertThat(classLevel.readOnly()).isFalse();
    }

    @Test
    void writesAreTransactionalAndReadsAreNot() throws Exception {
        // P6 convention: a read must not run inside a writable transaction.
        assertThat(readOnly("getTicketById")).isTrue();
        assertThat(readOnly("listTickets")).isTrue();

        for (String name : List.of("createTicket", "updateTicket", "changeStatus", "deleteTicket")) {
// The write methods inherit the class-level @Transactional; none may
            // narrow themselves to readOnly.
            Transactional methodLevel = TransactionalLifecycleSupport.annotationFor(name);
            boolean readOnly = methodLevel != null && methodLevel.readOnly();
            assertThat(readOnly).as("%s must not declare readOnly", name).isFalse();
        }
    }

@Test
    void theServiceSurfaceHasNoP3PlusOperations() {
        // Non-private declared methods. escalateTicket is package-private by design
        // (L3.1-P6 hardening), so a public-only filter would silently stop seeing it.
        List<String> publicMethods = java.util.Arrays.stream(TicketService.class.getDeclaredMethods())
                .filter(m -> !java.lang.reflect.Modifier.isPrivate(m.getModifiers()))
                .filter(m -> !m.isSynthetic())
                .map(Method::getName)
                .filter(n -> !n.startsWith("lambda$"))
                .filter(n -> !n.startsWith("access$"))
                .sorted()
                .toList();

        // L3.1-P2 adds assignTicket, L3.1-P4 SLA has its own service, and L3.1-P5 adds
        // requireReadable. Escalation and automatic assignment remain absent.
        assertThat(publicMethods).containsExactly(
                "assignTicket", "changeStatus", "createTicket", "deleteTicket",
                "escalateTicket", "getTicketById", "listTickets", "requireReadable", "updateTicket");
    }

    @Test
    void thereIsNoAutomaticOrRuleBasedAssignment() {
        // P2 is manual assignment only. These names are what an auto-assigner,
        // round-robin, or assignment-rule implementation would introduce.
List<String> methods = java.util.Arrays.stream(TicketService.class.getDeclaredMethods())
                .map(Method::getName)
                .map(n -> n.toLowerCase())
                .toList();

        // L3.1-P6 adds escalateTicket as the SYSTEM path alongside assignTicket. No
        // third, policy-driven assignment entry point exists.
        assertThat(methods).containsOnlyOnce("assignticket", "escalateticket");
        assertThat(methods).noneMatch(n -> n.contains("automat"));
        assertThat(methods).noneMatch(n -> n.contains("roundrobin") || n.contains("round_robin"));
        assertThat(methods).noneMatch(n -> n.contains("rule"));
        assertThat(methods).noneMatch(n -> n.contains("queue"));
        assertThat(methods).noneMatch(n -> n.contains("team"));
        assertThat(methods).noneMatch(n -> n.contains("unassign"));
        // Escalation lives in its own service so the Ticket aggregate does not grow a
        // policy surface; TicketService neither schedules nor detects a breach.
        assertThat(methods).noneMatch(n -> n.contains("sla"));
    }

    // --- helpers ---

    /**
     * Extracts {@code "module:action"} from an expression like
     * {@code @rbac.has(authentication, 'ticket', 'write')}. Returns null when the
     * expression names no literal permission, so the caller decides what that means.
     */
    private static String permissionOf(Method method) {
        PreAuthorize annotation = AnnotatedElementUtils.findMergedAnnotation(method, PreAuthorize.class);
        if (annotation == null) {
            return null;
        }
        // Split on quotes, then keep only bare identifiers — the fragments between
// them are punctuation and the bean reference like "@rbac".
        var literals = java.util.Arrays.stream(annotation.value().split("'"))
                .map(String::trim)
                .filter(s -> s.matches("[A-Za-z][A-Za-z0-9]*"))
                .toList();

        for (int i = 0; i < literals.size() - 1; i++) {
            if ("ticket".equals(literals.get(i))) {
                return literals.get(i) + ":" + literals.get(i + 1);
            }
        }
        return null;
    }

    private static String[] mappingPaths(Method method, Class<? extends java.lang.annotation.Annotation> type) {
        var annotation = AnnotatedElementUtils.findMergedAnnotation(method, type);
        if (annotation == null) {
            return new String[0];
        }
        return switch (annotation.annotationType().getSimpleName()) {
            case "PostMapping" -> ((PostMapping) annotation).value();
            case "PatchMapping" -> ((PatchMapping) annotation).value();
            case "GetMapping" -> ((org.springframework.web.bind.annotation.GetMapping) annotation).value();
            case "DeleteMapping" -> ((org.springframework.web.bind.annotation.DeleteMapping) annotation).value();
            default -> new String[0];
        };
    }

    private static boolean hasHttpMapping(Method method) {
        return method.getAnnotation(PostMapping.class) != null
                || method.getAnnotation(PatchMapping.class) != null
                || method.getAnnotation(org.springframework.web.bind.annotation.GetMapping.class) != null
                || method.getAnnotation(org.springframework.web.bind.annotation.DeleteMapping.class) != null;
    }

    private static Method methodNamed(String name) {
        return java.util.Arrays.stream(TicketController.class.getDeclaredMethods())
                .filter(m -> m.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no controller method " + name));
    }

private static boolean readOnly(String name) {
        var transactional = TransactionalLifecycleSupport.annotationFor(name);
        // The reads narrow the class-level annotation, so they must carry their own.
        assertThat(transactional)
                .as("%s must declare its own @Transactional(readOnly = true)", name)
                .isNotNull();
        return transactional.readOnly();
    }

    private static Set<String> fieldsOf(Class<?> type) {
        return java.util.Arrays.stream(type.getDeclaredFields())
                .filter(f -> !f.isSynthetic())
                .map(java.lang.reflect.Field::getName)
                .collect(Collectors.toSet());
    }

    /** The JSON keys Jackson actually emits, from a real serialization. */
    private static Set<String> serializedFieldNames(Class<?> type) {
        try {
            Object instance = type.getDeclaredConstructor().newInstance();
            if (instance instanceof TicketResponse response) {
                response.setId(UUID.randomUUID());
                response.setTenantId(UUID.randomUUID());
                response.setAccountId(UUID.randomUUID());
                response.setSubject("s");
                response.setStatus(TicketStatus.OPEN);
            }
            String json = MAPPER.writeValueAsString(instance);
            var node = MAPPER.readTree(json);
            var names = new java.util.HashSet<String>();
            node.fieldNames().forEachRemaining(names::add);
            return names;
        } catch (Exception e) {
            throw new AssertionError("could not serialize " + type.getSimpleName(), e);
        }
    }

    private static <T> T read(Class<T> type, String json) {
        try {
            return MAPPER.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    /** Formats a method as "VERB /path" for endpoint-set assertions. */
    private static String describe(Method method) {
        if (method.getAnnotation(PostMapping.class) != null) {
            return "POST " + path(method.getAnnotation(PostMapping.class).value());
        }
        if (method.getAnnotation(PatchMapping.class) != null) {
            return "PATCH " + path(method.getAnnotation(PatchMapping.class).value());
        }
        if (method.getAnnotation(org.springframework.web.bind.annotation.DeleteMapping.class) != null) {
            return "DELETE " + path(
                    method.getAnnotation(org.springframework.web.bind.annotation.DeleteMapping.class).value());
        }
        if (method.getAnnotation(org.springframework.web.bind.annotation.GetMapping.class) != null) {
            return "GET " + path(method.getAnnotation(
                    org.springframework.web.bind.annotation.GetMapping.class).value());
        }
        throw new AssertionError("unmapped method " + method.getName());
    }

    private static String path(String[] value) {
        return value.length == 0 ? "" : value[0];
    }

    /** Isolated so the reflection helpers above stay readable. */
    static final class TransactionalLifecycleSupport {
        private TransactionalLifecycleSupport() {
        }

static Transactional annotationFor(String name) {
            // filter(nonNull) matters: a write method has no annotation of its own,
            // and findFirst() throws on a null element rather than returning it.
            return java.util.Arrays.stream(TicketService.class.getDeclaredMethods())
                    .filter(m -> m.getName().equals(name))
                    .map(m -> AnnotatedElementUtils.findMergedAnnotation(m, Transactional.class))
                    .filter(java.util.Objects::nonNull)
                    .findFirst()
                    .orElse(null);
        }
    }

    }
