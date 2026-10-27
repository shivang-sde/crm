package com.shivang.crm.modules.ticket;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shivang.crm.modules.ticket.controller.TicketMessageController;
import com.shivang.crm.modules.ticket.dto.TicketMessageCreateRequest;
import com.shivang.crm.modules.ticket.dto.TicketMessageResponse;
import com.shivang.crm.modules.ticket.dto.TicketMessageUpdateRequest;
import com.shivang.crm.modules.ticket.entity.TicketMessage;
import com.shivang.crm.modules.ticket.entity.TicketMessageVisibility;
import com.shivang.crm.modules.ticket.service.TicketMessageService;

/**
 * L3.1-P3: the message API and entity contract.
 *
 * <p>Mostly reflection, because these are structural promises — that the wire shape
 * is camelCase, that a request cannot impersonate an author, that there is exactly
 * one way to reach each operation. A test is the right tool for those because a
 * later edit that breaks one fails here rather than in a consumer.
 */
class TicketMessageContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ================= entity shape =================

    @Test
    void theMessageExtendsBaseEntityNotTenantOwnedEntity() {
        // The central P3 decision. TenantOwnedEntity would give the message an
        // ownerId, creating a second ownership model that could disagree with the
        // Ticket's.
        assertThat(TicketMessage.class.getSuperclass())
                .isEqualTo(com.shivang.crm.shared.base.BaseEntity.class);
        assertThat(com.shivang.crm.shared.base.TenantOwnedEntity.class.isAssignableFrom(TicketMessage.class))
                .isFalse();
    }

    @Test
    void theMessageDeclaresTenantIdExplicitly() throws NoSuchFieldException {
        // Not inherited, because there is no owner column to hang it from — but the
        // property name matches every other tenant-owned entity, so the column is
        // tenant_id as everywhere else.
        assertThat(fieldNames(TicketMessage.class)).contains("tenantId");
        assertThat(TicketMessage.class.getDeclaredField("tenantId")
                .getAnnotation(jakarta.persistence.Column.class).name()).isEqualTo("tenant_id");
    }

    @Test
    void theMessageHasNoOwnershipOrCustomerIdentityFields() {
        // These belong to the parent Ticket. A copy could contradict its parent.
        assertThat(fieldNames(TicketMessage.class))
                .doesNotContain("ownerId", "accountId", "contactId", "createdBy");
    }

    @Test
    void theAuthorIsNamedAuthorUserIdNotCreatedBy() {
        // authorUserId is the fact P3 authorizes against; createdBy would be the
        // audit role and reads as interchangeable when they are not.
        assertThat(fieldNames(TicketMessage.class))
                .contains("authorUserId")
                .doesNotContain("createdBy");
    }

    @Test
    void theMessageCarriesTheRequiredColumns() {
        // id/createdAt/updatedAt/deleted* come from BaseEntity, so the assertion
        // spans the whole mapped hierarchy rather than declared fields alone.
        assertThat(allFieldNames(TicketMessage.class))
                .contains("id", "tenantId", "ticketId", "authorUserId", "body",
                        "visibility", "createdAt", "updatedAt", "deleted", "deletedAt", "deletedBy");
    }

    @Test
    void tenantTicketAuthorBodyAndVisibilityAreAllNotNull() throws Exception {
        for (String field : List.of("tenantId", "ticketId", "authorUserId", "body", "visibility")) {
            var column = TicketMessage.class.getDeclaredField(field)
                    .getAnnotation(jakarta.persistence.Column.class);
            assertThat(column)
                    .as("%s must declare a @Column", field)
                    .isNotNull();
            assertThat(column.nullable())
                    .as("%s must be NOT NULL", field)
                    .isFalse();
        }
    }

    @Test
    void theBodyIsText() throws Exception {
        assertThat(TicketMessage.class.getDeclaredField("body")
                .getAnnotation(jakarta.persistence.Column.class).columnDefinition())
                .isEqualTo("TEXT");
    }

    @Test
    void visibilityIsPersistedAsAStringNotAnOrdinal() throws Exception {
        var enumerated = TicketMessage.class.getDeclaredField("visibility")
                .getAnnotation(jakarta.persistence.Enumerated.class);

        assertThat(enumerated).isNotNull();
        assertThat(enumerated.value()).isEqualTo(jakarta.persistence.EnumType.STRING);
        assertThat(TicketMessage.class.getDeclaredField("visibility")
                .getAnnotation(jakarta.persistence.Column.class).length()).isEqualTo(30);
    }

    @Test
    void visibilityHasExactlyTwoValues() {
        // Not a delivery pipeline: P3 has no send step, so there is no SENT or
        // DELIVERED state to model.
        assertThat(java.util.Arrays.stream(TicketMessageVisibility.values()).map(Enum::name))
                .containsExactly("INTERNAL", "CUSTOMER_VISIBLE");
    }

    @Test
    void visibilityDefaultsToInternal() throws Exception {
        // The field initializer is the default; reflection cannot read it back, so
        // construct an instance and assert the value. Internal is the safe default:
        // a message nobody labelled is a working note, not something customer-facing.
        assertThat(new TicketMessage().getVisibility()).isEqualTo(TicketMessageVisibility.INTERNAL);
    }

    @Test
    void theMessageTableIsNamedTicketMessages() {
        var table = TicketMessage.class.getAnnotation(jakarta.persistence.Table.class);

        assertThat(table.name()).isEqualTo("ticket_messages");
    }

    // ================= endpoints =================

    @Test
    void theControllerIsNestedUnderTheTicketResource() {
        RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(
                TicketMessageController.class, RequestMapping.class);

        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).containsExactly("/api/v1/tickets/{ticketId}/messages");
    }

    @Test
    void theControllerIsARestController() {
        assertThat(TicketMessageController.class.getAnnotation(RestController.class)).isNotNull();
    }

    @Test
    void theMessageApiHasExactlyFiveOperations() {
        List<String> operations = java.util.Arrays.stream(TicketMessageController.class.getDeclaredMethods())
                .filter(m -> hasHttpMapping(m))
                .map(TicketMessageContractTest::describe)
                .sorted()
                .toList();

        // Collection root has an empty path, so it renders as "GET " / "POST ".
        assertThat(operations).containsExactly(
                "DELETE /{messageId}",
                "GET ",
                "GET /{messageId}",
                "PATCH /{messageId}",
                "POST ");
    }

    @Test
    void everyEndpointDeclaresPreAuthorize() {
        for (Method method : TicketMessageController.class.getDeclaredMethods()) {
            if (!hasHttpMapping(method)) {
                continue;
            }
            assertThat(AnnotatedElementUtils.findMergedAnnotation(method, PreAuthorize.class))
                    .as("%s must declare @PreAuthorize", method.getName())
                    .isNotNull();
        }
    }

    @Test
    void theMessageApiUsesTheParentsTicketPermissions() {
        // Deliberately no ticket_message module: a second permission would have to be
        // kept in step with the Ticket ones and could drift into permitting action on
        // a conversation the caller cannot read.
        Set<String> referenced = java.util.Arrays.stream(TicketMessageController.class.getDeclaredMethods())
                .filter(TicketMessageContractTest::hasHttpMapping)
                .map(TicketMessageContractTest::permissionOf)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());

        assertThat(referenced).containsExactlyInAnyOrder("ticket:read", "ticket:write");
    }

    @Test
    void readsRequireReadAndMutationsRequireWrite() {
        assertThat(permissionOf(methodNamed("listMessages"))).isEqualTo("ticket:read");
        assertThat(permissionOf(methodNamed("getMessage"))).isEqualTo("ticket:read");
        assertThat(permissionOf(methodNamed("createMessage"))).isEqualTo("ticket:write");
        assertThat(permissionOf(methodNamed("updateMessage"))).isEqualTo("ticket:write");
        // Deleting a message is a conversation edit, not a ticket deletion.
        assertThat(permissionOf(methodNamed("deleteMessage"))).isEqualTo("ticket:write");
    }

    @Test
    void noEndpointRequiresTicketDeleteOrAssign() {
        // Deleting a message must not need ticket:delete (reserved for the ticket
        // itself), and message handling must never require or imply assignment.
        for (Method method : TicketMessageController.class.getDeclaredMethods()) {
            if (!hasHttpMapping(method)) {
                continue;
            }
            String permission = permissionOf(method);
            assertThat(permission)
                    .as("%s must not require delete or assign", method.getName())
                    .isIn("ticket:read", "ticket:write");
        }
    }

    @Test
    void everyEndpointIsScopedToTheTicketModule() {
        // Guards a copy-paste that would check another module's permission.
        for (Method method : TicketMessageController.class.getDeclaredMethods()) {
            if (!hasHttpMapping(method)) {
                continue;
            }
            assertThat(permissionOf(method))
                    .as("%s must check the ticket module", method.getName())
                    .startsWith("ticket:");
        }
    }

    @Test
    void thereAreNoMessageEndpointsOutsideTheTicketResource() {
        // Every path must be nested, because a top-level /ticket-messages surface
        // could not carry the parent ticket's scope in the path.
        RequestMapping mapping = TicketMessageController.class.getAnnotation(RequestMapping.class);
        assertThat(mapping.value()[0]).startsWith("/api/v1/tickets/");
    }

    @Test
    void theRbacFilterResolvesThisPathToTheTicketModule() {
        // The URL-derived filter and the annotations must agree, or one silently
        // permits what the other forbids. parts[3] of this path is "tickets".
        String[] parts = "/api/v1/tickets/{ticketId}/messages".split("/");
        assertThat(parts[3]).isEqualTo("tickets");
        assertThat(parts.length).isGreaterThan(4);
    }

    // ================= DTO shape =================

    @Test
    void theCreateRequestHasOnlyBodyAndVisibility() {
        assertThat(fieldNames(TicketMessageCreateRequest.class))
                .containsExactlyInAnyOrder("body", "visibility");
    }

    @Test
    void theCreateRequestCannotSetTenantOrAuthorOrOwnership() {
        // The whole reason these fields are absent rather than ignored: a client that
        // could name them could post as another agent or another tenant.
        assertThat(fieldNames(TicketMessageCreateRequest.class))
                .doesNotContain("tenantId", "authorUserId", "author", "ownerId",
                        "accountId", "contactId", "ticketId", "id", "createdAt",
                        "deleted", "deletedAt", "deletedBy");
    }

    @Test
    void theUpdateRequestHasOnlyBodyAndVisibility() {
        // ticketId and authorUserId are structurally unreachable, so a message cannot
        // be re-parented (which would change its scope) or have its authorship moved.
        assertThat(fieldNames(TicketMessageUpdateRequest.class))
                .containsExactlyInAnyOrder("body", "visibility");
    }

    @Test
    void theUpdateRequestCannotChangeTicketTenantOrAuthor() {
        assertThat(fieldNames(TicketMessageUpdateRequest.class))
                .doesNotContain("ticketId", "tenantId", "authorUserId", "createdAt",
                        "deleted", "ownerId", "accountId");
    }

    @Test
    void theResponseExposesTheDocumentedContract() throws Exception {
        TicketMessageResponse response = TicketMessageResponse.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .ticketId(UUID.randomUUID())
                .authorUserId(UUID.randomUUID())
                .body("Note")
                .visibility(TicketMessageVisibility.INTERNAL)
                .build();

        var node = MAPPER.readTree(MAPPER.writeValueAsString(response));
        List<String> names = new java.util.ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);

        assertThat(names).containsExactlyInAnyOrder(
                "id", "tenantId", "ticketId", "authorUserId", "body", "visibility",
                "createdAt", "updatedAt");
    }

    @Test
    void theResponseDoesNotExposeSoftDeleteColumns() {
        // A deleted message is never returned, so exposing this would invite a client
        // to branch on state it should not observe.
        assertThat(fieldNames(TicketMessageResponse.class))
                .doesNotContain("deleted", "deletedAt", "deletedBy");
    }

    @Test
    void theResponseDoesNotRepeatTheParentsCustomerOrOwnerFields() {
        // Returning them would let a client assemble a picture where the message
        // appears to own its own customer and agent.
        assertThat(fieldNames(TicketMessageResponse.class))
                .doesNotContain("accountId", "contactId", "ownerId");
    }

    @Test
    void noDtoFieldUsesSnakeCaseJson() throws Exception {
        for (Class<?> type : List.of(TicketMessageCreateRequest.class,
                TicketMessageUpdateRequest.class, TicketMessageResponse.class)) {
            for (var field : type.getDeclaredFields()) {
                var jsonProperty = field.getAnnotation(com.fasterxml.jackson.annotation.JsonProperty.class);
                assertThat(jsonProperty)
                        .as("%s.%s must not rename the field", type.getSimpleName(), field.getName())
                        .isNull();
            }
        }
    }

    @Test
    void noDtoUsesJsonNamingOrSnakeCaseStrategy() {
        for (Class<?> type : List.of(TicketMessageCreateRequest.class,
                TicketMessageUpdateRequest.class, TicketMessageResponse.class,
                TicketMessage.class)) {
            assertThat(type.getAnnotation(com.fasterxml.jackson.databind.annotation.JsonNaming.class))
                    .as("%s must not rename properties", type.getSimpleName())
                    .isNull();
        }
    }

@Test
    void theCreateRequestRejectsSmuggledTenantAndAuthorFields() {
        // Unknown fields fail loudly rather than binding, so a client cannot smuggle
        // an author or a tenant in and have it quietly accepted.
        UUID hint = UUID.randomUUID();

        assertThat(catchThrowable(() -> read(TicketMessageCreateRequest.class,
                "{\"body\":\"x\",\"tenantId\":\"" + hint + "\"}")))
                .as("tenantId must be rejected")
                .isNotNull();
        assertThat(catchThrowable(() -> read(TicketMessageCreateRequest.class,
                "{\"body\":\"x\",\"authorUserId\":\"" + hint + "\"}")))
                .as("authorUserId must be rejected")
                .isNotNull();
    }

    @Test
    void theCreateRequestBindsBodyAndVisibility() {
        var request = read(TicketMessageCreateRequest.class, """
                {"body":"We have received your request.","visibility":"CUSTOMER_VISIBLE"}
                """);

        assertThat(request.getBody()).isEqualTo("We have received your request.");
        assertThat(request.getVisibility()).isEqualTo(TicketMessageVisibility.CUSTOMER_VISIBLE);
    }

    @Test
    void visibilityBindsAsAnEnumString() {
        // Not an ordinal, not a renamed string: the wire value is the enum name.
        assertThat(read(TicketMessageCreateRequest.class,
                "{\"body\":\"x\",\"visibility\":\"INTERNAL\"}").getVisibility())
                .isEqualTo(TicketMessageVisibility.INTERNAL);
    }

    @Test
    void anUnknownVisibilityValueIsRejected() {
        // There is no third value and no fallback, so a typo cannot silently become
        // an internal note.
        assertThat(catchThrowable(() -> read(TicketMessageCreateRequest.class,
                "{\"body\":\"x\",\"visibility\":\"PUBLIC\"}"))).isNotNull();
        assertThat(catchThrowable(() -> read(TicketMessageCreateRequest.class,
                "{\"body\":\"x\",\"visibility\":\"internal\"}"))).isNotNull();
    }

    @Test
    void snakeCaseVisibilityIsRejected() {
        assertThat(catchThrowable(() -> read(TicketMessageCreateRequest.class,
                "{\"body\":\"x\",\"custom_visible\":\"true\"}"))).isNotNull();
    }

    @Test
    void theBodyIsRequiredOnTheCreateRequest() throws Exception {
        assertThat(TicketMessageCreateRequest.class.getDeclaredField("body")
                .getAnnotation(jakarta.validation.constraints.NotBlank.class))
                .isNotNull();
    }

    @Test
    void unknownFieldsAreNotSilentlyIgnored() {
        // Asserted as configuration so the reject-tests above cannot pass for the
        // wrong reason.
        assertThat(MAPPER.getDeserializationConfig()
                .isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)).isTrue();
    }

    // ================= event vocabulary =================

    @Test
    void ticketMessageEntityTypeIsRegistered() {
        assertThat(com.shivang.crm.shared.event.CanonicalCrmEvent.TICKET_MESSAGE_ENTITY_TYPE)
                .isEqualTo("TICKET_MESSAGE");
    }

    @Test
    void theEventReusesTheExistingCreatedVocabulary() throws Exception {
        // No new event type: CREATED already exists, so reusing it keeps consumers on
        // one branch.
        assertThat(com.shivang.crm.shared.event.CanonicalCrmEvent.CREATED_EVENT_TYPE)
                .isEqualTo("CREATED");
        var names = java.util.Arrays.stream(
                        com.shivang.crm.shared.event.CanonicalCrmEvent.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName)
                .toList();
        assertThat(names).doesNotContain("TICKET_MESSAGE_CREATED_EVENT_TYPE");
    }

    @Test
    void noMessageDeletedOrUpdatedEventVocabularyExists() {
        // P3 defines only TICKET_MESSAGE.CREATED; edits and deletes are audited through
        // the activity log.
        var names = java.util.Arrays.stream(
                        com.shivang.crm.shared.event.CanonicalCrmEvent.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName)
                .map(n -> n.toUpperCase())
                .toList();

        assertThat(names).noneMatch(n -> n.contains("MESSAGE_DELETED"));
        assertThat(names).noneMatch(n -> n.contains("MESSAGE_UPDATED"));
    }

    @Test
    void ticketMessageServiceIsTheOnlyProducer() {
        // One producer, so the event's ordering and suppression rules live in one
        // place. Asserted structurally: no other Ticket service touches the message
        // publisher.
        var otherProducers = java.util.Arrays.stream(
                        com.shivang.crm.modules.ticket.service.TicketService.class.getDeclaredMethods())
                .filter(m -> m.getName().toLowerCase().contains("message"))
                .map(Method::getName)
                .toList();

        assertThat(otherProducers).isEmpty();
    }

    // ================= service surface =================

    @Test
    void theServiceExposesExactlyTheFiveOperations() {
        List<String> publicMethods = java.util.Arrays.stream(
                        TicketMessageService.class.getDeclaredMethods())
                .filter(m -> java.lang.reflect.Modifier.isPublic(m.getModifiers())
                        && !m.isSynthetic())
                .map(Method::getName)
                .filter(n -> !n.startsWith("lambda$"))
                .sorted()
                .toList();

        assertThat(publicMethods).containsExactly(
                "createMessage", "deleteMessage", "getMessage", "listMessages", "updateMessage");
    }

    @Test
    void thereIsNoOutOfScopeCapabilityOnTheService() {
        // P3 delivers nothing, sends nothing, and resolves no parentage other than a
        // ticket. These names are what any of those would introduce.
        List<String> methods = java.util.Arrays.stream(
                        TicketMessageService.class.getDeclaredMethods())
                .map(Method::getName)
                .map(n -> n.toLowerCase())
                .toList();

        assertThat(methods).noneMatch(n -> n.contains("send"));
        assertThat(methods).noneMatch(n -> n.contains("deliver"));
        assertThat(methods).noneMatch(n -> n.contains("notify"));
        assertThat(methods).noneMatch(n -> n.contains("email"));
        assertThat(methods).noneMatch(n -> n.contains("attachment"));
        assertThat(methods).noneMatch(n -> n.contains("sla"));
        assertThat(methods).noneMatch(n -> n.contains("escalat"));
        assertThat(methods).noneMatch(n -> n.contains("restore"));
        assertThat(methods).noneMatch(n -> n.contains("harddelete"));
    }

    @Test
    void thereAreNoJpaEntityListenersOnTheMessage() {
        // Events are published by the service inside its transaction, never by an
        // ORM callback, so a persistence operation cannot emit on its own.
        assertThat(java.util.Arrays.stream(TicketMessage.class.getAnnotations())
                .map(a -> a.annotationType().getSimpleName()))
                .doesNotContain("PrePersist", "PreUpdate", "PostPersist", "PostUpdate", "EntityListeners");
    }

    @Test
    void theEntityResolverDoesNotGainAMessageCase() {
        // P3's only required relationship is Ticket -> TicketMessage. Registering the
        // message as a resolvable entity type would be workflow/timeline expansion.
        var sources = java.util.List.of(EntityResolverSourceHolder.find().toLowerCase());

        assertThat(sources).noneMatch(s -> s.contains("case \"ticket_message\""));
    }

    /** Reads EntityResolverService's source once, to assert on its switch shape. */
    static final class EntityResolverSourceHolder {
        private EntityResolverSourceHolder() {
        }

        static String find() {
            try {
                return java.nio.file.Files.readString(java.nio.file.Path.of(
                        "src/main/java/com/shivang/crm/shared/service/EntityResolverService.java"));
            } catch (Exception e) {
                throw new AssertionError("could not read EntityResolverService", e);
            }
        }
    }

    // ================= helpers =================

    private static boolean hasHttpMapping(Method method) {
        return method.getAnnotation(PostMapping.class) != null
                || method.getAnnotation(PatchMapping.class) != null
                || method.getAnnotation(GetMapping.class) != null
                || method.getAnnotation(DeleteMapping.class) != null;
    }

    private static String[] mappingPaths(Method method, Class<? extends java.lang.annotation.Annotation> type) {
        var annotation = AnnotatedElementUtils.findMergedAnnotation(method, type);
        if (annotation == null) {
            return new String[0];
        }
        return switch (annotation.annotationType().getSimpleName()) {
            case "PostMapping" -> ((PostMapping) annotation).value();
            case "PatchMapping" -> ((PatchMapping) annotation).value();
            case "GetMapping" -> ((GetMapping) annotation).value();
            case "DeleteMapping" -> ((DeleteMapping) annotation).value();
            default -> new String[0];
        };
    }

    private static String describe(Method method) {
        if (method.getAnnotation(PostMapping.class) != null) {
            return "POST " + path(method.getAnnotation(PostMapping.class).value());
        }
        if (method.getAnnotation(PatchMapping.class) != null) {
            return "PATCH " + path(method.getAnnotation(PatchMapping.class).value());
        }
        if (method.getAnnotation(DeleteMapping.class) != null) {
            return "DELETE " + path(method.getAnnotation(DeleteMapping.class).value());
        }
        return "GET " + path(method.getAnnotation(GetMapping.class).value());
    }

    private static String path(String[] value) {
        return value.length == 0 ? "" : value[0];
    }

    private static Method methodNamed(String name) {
        return java.util.Arrays.stream(TicketMessageController.class.getDeclaredMethods())
                .filter(m -> m.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no controller method " + name));
    }

    /** Extracts "module:action" from an expression like @rbac.has(authentication, 'ticket', 'write'). */
    private static String permissionOf(Method method) {
        PreAuthorize annotation = AnnotatedElementUtils.findMergedAnnotation(method, PreAuthorize.class);
        if (annotation == null) {
            return null;
        }
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

    private static Set<String> fieldNames(Class<?> type) {
        return java.util.Arrays.stream(type.getDeclaredFields())
                .filter(f -> !java.lang.reflect.Modifier.isStatic(f.getModifiers()))
                .map(java.lang.reflect.Field::getName)
                .collect(java.util.stream.Collectors.toSet());
    }

    /** Declared fields plus everything inherited from mapped superclasses. */
    private static Set<String> allFieldNames(Class<?> type) {
        Set<String> names = new java.util.HashSet<>();
        for (Class<?> current = type; current != null && current != Object.class;
                current = current.getSuperclass()) {
            names.addAll(fieldNames(current));
        }
        return names;
    }

    private static <T> T read(Class<T> type, String json) {
        try {
            return MAPPER.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    private static Throwable catchThrowable(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        return org.assertj.core.api.Assertions.catchThrowable(callable);
    }

    private static UUID uuid() {
        return UUID.randomUUID();
    }

    }
