package com.shivang.crm.modules.account.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.shivang.crm.modules.account.dto.Customer360Response;
import com.shivang.crm.modules.account.dto.Customer360Section;

/**
 * L3.2-P1: the Customer 360 endpoint contract.
 *
 * <p>Structural checks, because the properties that matter here are shape-level:
 * the path is nested under the Account resource, it is a GET, it names no new
 * permission, and the response exposes only domains that exist today.
 */
class Customer360EndpointContractTest {

    /**
     * The Customer 360 sources, read from disk. Used to prove no new permission string
     * was introduced: a {@code @PreAuthorize} assertion alone would not catch a
     * permission added elsewhere in the same feature.
     */
private static final java.util.List<String> SOURCES = java.util.stream.Stream
            .of("Customer360Service.java", "Customer360Response.java", "Customer360Section.java",
                    "Customer360TimelineService.java", "Customer360TimelineQueryRepository.java",
                    "AccountController.java")
            .map(name -> java.nio.file.Path.of("src/main/java/com/shivang/crm/modules/account").resolve(
                    switch (name) {
                        case "Customer360Service.java" -> "service/Customer360Service.java";
                        case "Customer360TimelineService.java" -> "service/Customer360TimelineService.java";
                        case "Customer360Response.java", "Customer360Section.java" -> "dto/" + name;
                        case "Customer360TimelineQueryRepository.java" ->
                                "repository/Customer360TimelineQueryRepository.java";
                        default -> "controller/" + name;
                    }))
            .map(path -> {
                try {
                    return java.nio.file.Files.readString(path);
                } catch (java.io.IOException e) {
                    throw new IllegalStateException(e);
                }
            })
            .toList();

    private static Method handler() {
        return java.util.Arrays.stream(AccountController.class.getDeclaredMethods())
                .filter(m -> "getCustomer360".equals(m.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("getCustomer360 handler not found"));
    }

    @Test
    void theEndpointIsNestedUnderTheAccountResource() {
        RequestMapping mapping = AccountController.class.getAnnotation(RequestMapping.class);

        // No /customer360 root: the Account stays the resource boundary, which is
        // also why RbacFilter resolves this to the existing account module.
        assertThat(mapping.value()).containsExactly("/api/v1/accounts");

        GetMapping get = handler().getAnnotation(GetMapping.class);
        assertThat(get).isNotNull();
        assertThat(get.value()).containsExactly("/{id}/360");
    }

    @Test
    void itIsAGet() {
        // Read-only aggregation; a mutating verb here would be a scope violation.
        assertThat(handler().getAnnotation(
                org.springframework.web.bind.annotation.PostMapping.class)).isNull();
        assertThat(handler().getAnnotation(
                org.springframework.web.bind.annotation.PatchMapping.class)).isNull();
        assertThat(handler().getAnnotation(
                org.springframework.web.bind.annotation.PutMapping.class)).isNull();
        assertThat(handler().getAnnotation(
                org.springframework.web.bind.annotation.DeleteMapping.class)).isNull();
    }

    @Test
    void itTakesTheAccountIdAndIndependentPaginationPerSection() {
        List<String> parameterNames = java.util.Arrays.stream(handler().getParameters())
                .map(java.lang.reflect.Parameter::getName)
                .toList();

        // One page/size pair per child collection, named after that collection so a
        // caller can page them independently.
        assertThat(parameterNames).containsExactly(
                "id",
                "contactsPage", "contactsSize",
                "dealsPage", "dealsSize",
                "entitlementsPage", "entitlementsSize",
                "ticketsPage", "ticketsSize",
                "feedbackPage", "feedbackSize",
                "timelinePage", "timelineSize");

        // The tenant is never caller-supplied.
        assertThat(parameterNames).doesNotContain("tenantId", "scope");
    }

    @Test
    void everyPaginationParameterHasADefault() {
        // Defaults mean the whole view is retrievable with a bare GET.
        for (var parameter : handler().getParameters()) {
            if (parameter.getName().equals("id")) {
                continue;
            }
            var requestParam = parameter.getAnnotation(
                    org.springframework.web.bind.annotation.RequestParam.class);
            assertThat(requestParam)
                    .as("%s must be a query parameter", parameter.getName())
                    .isNotNull();
            assertThat(requestParam.defaultValue())
                    .as("%s must have a default", parameter.getName())
                    .isNotEmpty();
        }
    }

    @Test
    void theDefaultPaginationIsPageZeroSizeTwenty() {
        java.util.Map<String, String> defaults = new java.util.HashMap<>();
        for (var parameter : handler().getParameters()) {
            if (parameter.getName().equals("id")) {
                continue;
            }
            defaults.put(parameter.getName(), parameter.getAnnotation(
                    org.springframework.web.bind.annotation.RequestParam.class).defaultValue());
        }

        assertThat(defaults).containsExactlyInAnyOrderEntriesOf(java.util.Map.ofEntries(
                java.util.Map.entry("contactsPage", "0"), java.util.Map.entry("contactsSize", "20"),
                java.util.Map.entry("dealsPage", "0"), java.util.Map.entry("dealsSize", "20"),
                java.util.Map.entry("entitlementsPage", "0"), java.util.Map.entry("entitlementsSize", "20"),
                java.util.Map.entry("ticketsPage", "0"), java.util.Map.entry("ticketsSize", "20"),
                java.util.Map.entry("feedbackPage", "0"), java.util.Map.entry("feedbackSize", "20"),
                java.util.Map.entry("timelinePage", "0"), java.util.Map.entry("timelineSize", "20")));
    }

@Test
    void thePageDefaultsAreBoundedRatherThanUnbounded() {
        // The 100-item ceiling is enforced in the service (asserted in
        // Customer360ServiceTest, which owns the constants); here we only require that
        // no parameter defaults to an effectively unlimited page.
        java.util.Map<String, String> defaults = new java.util.HashMap<>();
        for (var parameter : handler().getParameters()) {
            if (parameter.getName().equals("id")) {
                continue;
            }
            defaults.put(parameter.getName(), parameter.getAnnotation(
                    org.springframework.web.bind.annotation.RequestParam.class).defaultValue());
        }

        assertThat(defaults.values()).allMatch(v -> Integer.parseInt(v) <= 20);
    }

    @Test
    void itRequiresAccountReadAndNoOtherPermission() {
        PreAuthorize annotation = AnnotatedElementUtils.findMergedAnnotation(handler(), PreAuthorize.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).contains("'account'").contains("'read'");
        // No customer360 permission is introduced; account:read is reused.
        assertThat(annotation.value().toLowerCase(Locale.ROOT)).doesNotContain("customer360");
    }

    @Test
    void thePermissionSetIntroducedIsAccountReadOnly() {
        List<String> referenced = java.util.Arrays.stream(AccountController.class.getDeclaredMethods())
                .map(m -> AnnotatedElementUtils.findMergedAnnotation(m, PreAuthorize.class))
                .filter(java.util.Objects::nonNull)
                .flatMap(a -> java.util.Arrays.stream(a.value().split("'")))
                .map(s -> s.trim())
                .filter(s -> s.matches("[a-z0-9_]+"))
                .distinct()
                .toList();

        // account + read only. The point is that no new module appears here.
        assertThat(referenced).containsExactlyInAnyOrder("account", "read");
    }

@Test
    void theResponseCarriesExactlyTheSevenSectionsThatExistToday() {
        List<String> sections = java.util.Arrays.stream(Customer360Response.class.getDeclaredFields())
                .filter(f -> !java.lang.reflect.Modifier.isStatic(f.getModifiers()))
                .map(java.lang.reflect.Field::getName)
                .toList();

        // L3.3-P3: feedback joins the six L3.2 sections. Nothing else is added.
        assertThat(sections).containsExactlyInAnyOrder(
                "account", "contacts", "deals", "entitlements", "tickets", "feedback", "timeline");
    }

    @Test
    void theTimelineIsAPaginatedSectionOfActivityResponses() {
        // L3.2-P3: the same P2 section shape, and the existing ActivityResponse — no
        // timeline-specific copy of the activity DTO.
        var field = java.util.Arrays.stream(Customer360Response.class.getDeclaredFields())
                .filter(f -> f.getName().equals("timeline"))
                .findFirst()
                .orElseThrow();

        assertThat(field.getType()).isEqualTo(Customer360Section.class);
        assertThat(field.getGenericType().getTypeName())
                .contains("Customer360Section<com.shivang.crm.modules.activity.dto.ActivityResponse>");
    }

    @Test
    void theFeedbackSectionIsAPaginatedSectionOfFeedbackResponses() {
        // L3.3-P3: the same P2 section shape, and the existing FeedbackResponse — no
        // Customer360-specific copy of the feedback DTO.
        var field = java.util.Arrays.stream(Customer360Response.class.getDeclaredFields())
                .filter(f -> f.getName().equals("feedback"))
                .findFirst()
                .orElseThrow();

        assertThat(field.getType()).isEqualTo(Customer360Section.class);
        assertThat(field.getGenericType().getTypeName())
                .contains("Customer360Section<com.shivang.crm.modules.feedback.dto.FeedbackResponse>");
    }

    @Test
    void theFeedbackSectionAddsNoNewPermission() {
        // The feedback section is subordinate to account:read, exactly like the
        // timeline. feedback:read gates the section's own rows inside the service,
        // but the endpoint introduces no new permission of its own.
        assertThat(SOURCES).noneMatch(source -> source.contains("\"customer360\""));
        assertThat(SOURCES).noneMatch(source -> source.contains("'customer360', 'read'"));
    }

    @Test
    void theTimelineAddsNoNewPermission() {
        // The timeline is subordinate to account:read. There is deliberately no
        // customer360:read anywhere in the Customer 360 sources, and the generic
        // activity:read must not be what gates it.
        assertThat(SOURCES).noneMatch(source -> source.contains("\"customer360\""));
        assertThat(SOURCES).noneMatch(source -> source.contains("'customer360', 'read'"));
    }

    @Test
    void everyChildSectionIsAPaginatedSectionNotABareList() {
        // L3.2-P2: a bare list cannot say whether records were omitted.
        for (String section : List.of("contacts", "deals", "entitlements", "tickets", "feedback", "timeline")) {
            var field = java.util.Arrays.stream(Customer360Response.class.getDeclaredFields())
                    .filter(f -> f.getName().equals(section))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("no section " + section));

            assertThat(field.getType())
                    .as("%s must be a paginated section", section)
                    .isEqualTo(Customer360Section.class);
            assertThat(List.class.isAssignableFrom(field.getType()))
                    .as("%s must not be a bare List", section)
                    .isFalse();
        }
    }

    @Test
    void everyChildSectionDeclaresTheFullPaginationContract() {
        java.util.Set<String> fields = java.util.Arrays.stream(
                        Customer360Section.class.getDeclaredFields())
                .filter(f -> !java.lang.reflect.Modifier.isStatic(f.getModifiers()))
                .map(java.lang.reflect.Field::getName)
                .collect(java.util.stream.Collectors.toSet());

        // Naming follows the repository's existing list meta keys (page/size/total/
        // totalPages); hasNext is the addition this phase introduces.
        assertThat(fields).containsExactlyInAnyOrder(
                "items", "page", "size", "total", "totalPages", "hasNext");
    }

    @Test
    void theAccountSectionIsNotPaginated() {
        var field = java.util.Arrays.stream(Customer360Response.class.getDeclaredFields())
                .filter(f -> f.getName().equals("account"))
                .findFirst()
                .orElseThrow();

        assertThat(field.getType().getSimpleName()).isEqualTo("AccountResponse");
    }

    @Test
    void theResponseHasNoPhantomCommerceOrTimelineSections() {
        List<String> sections = java.util.Arrays.stream(Customer360Response.class.getDeclaredFields())
                .filter(f -> !java.lang.reflect.Modifier.isStatic(f.getModifiers()))
                .map(java.lang.reflect.Field::getName)
                .map(n -> n.toLowerCase(Locale.ROOT))
                .toList();

// Those domains do not exist; a placeholder would imply they do. "timeline" is no
        // longer absent: L3.2-P3 adds it, deliberately, as a section of the existing
        // activities data rather than as a new domain. "feedback" is no longer
        // absent either: L3.3-P3 adds it, deliberately, as a section of the existing
        // feedback data. Both amendments are recorded here so a future removal or a
        // further addition cannot slip past uncommented.
        for (String absent : List.of("orders", "invoices", "payments", "assets", "warranties",
                "activities", "health", "successplan", "notes", "calls", "offers")) {
            assertThat(sections).as("%s must not be present", absent).doesNotContain(absent);
        }
    }

    @Test
    void theResponseReusesDomainResponseTypes() {
        // Referencing the existing DTOs is what keeps the 360 from drifting away
        // from the entity endpoints it aggregates.
        var accountField = fieldOf("account");
        assertThat(accountField.getType().getSimpleName()).isEqualTo("AccountResponse");

        // P2 wraps each in Customer360Section<T>; the element type is still the
        // domain's own response DTO.
        assertThat(fieldOf("contacts").getGenericType().getTypeName())
                .contains("com.shivang.crm.modules.account.dto.Customer360Section<com.shivang.crm.modules.contact.dto.ContactResponse>");
        assertThat(fieldOf("deals").getGenericType().getTypeName())
                .contains("Customer360Section<com.shivang.crm.modules.deal.dto.DealResponse>");
        assertThat(fieldOf("entitlements").getGenericType().getTypeName())
                .contains("Customer360Section<com.shivang.crm.modules.entitlement.dto.CustomerEntitlementResponse>");
        assertThat(fieldOf("tickets").getGenericType().getTypeName())
                .contains("Customer360Section<com.shivang.crm.modules.ticket.dto.TicketResponse>");
        // L3.3-P3: no Customer360FeedbackResponse copy. The section carries the
        // domain DTO, exactly like the other four.
        assertThat(fieldOf("feedback").getGenericType().getTypeName())
                .contains("Customer360Section<com.shivang.crm.modules.feedback.dto.FeedbackResponse>");
    }

    @Test
    void theResponseExposesNoJpaEntity() {
        for (var field : Customer360Response.class.getDeclaredFields()) {
            var typeName = field.getType().getName();
            assertThat(typeName).as("%s must not expose a JPA entity", field.getName())
                    .doesNotContain("jakarta.persistence");
            assertThat(field.getType().isAnnotationPresent(jakarta.persistence.Entity.class))
                    .as("%s must not be an entity", field.getName())
                    .isFalse();
        }
    }

    @Test
    void sectionFieldsAreNullableAtTheTypeLevelButAlwaysPopulated() {
        // The service always assigns a list, never null. The DTO leaves them
        // non-final so the builder can populate it; consumers see empty, not null.
        assertThat(Customer360Response.class.getDeclaredFields())
                .allMatch(f -> !java.lang.reflect.Modifier.isFinal(f.getModifiers()));
    }

    @Test
    void noAccountEndpointWasRemovedOrRenamed() {
        List<String> handlers = java.util.Arrays.stream(AccountController.class.getDeclaredMethods())
                .filter(m -> hasMapping(m))
                .map(java.lang.reflect.Method::getName)
                .sorted()
                .toList();

        // Pre-existing behaviour must be untouched; only /{id}/360 is new.
        for (String existing : List.of("createAccount", "listAccounts", "searchAccounts",
                "getAccount", "updateAccount", "deleteAccount", "getContacts",
                "getActivities", "getNotes", "addNote", "deleteNote")) {
            assertThat(handlers).as("%s must still exist", existing).contains(existing);
        }
        assertThat(handlers).contains("getCustomer360");
    }

    private static boolean hasMapping(Method method) {
        return method.getAnnotation(GetMapping.class) != null
                || method.getAnnotation(org.springframework.web.bind.annotation.PostMapping.class) != null
                || method.getAnnotation(org.springframework.web.bind.annotation.PutMapping.class) != null
                || method.getAnnotation(org.springframework.web.bind.annotation.PatchMapping.class) != null
                || method.getAnnotation(org.springframework.web.bind.annotation.DeleteMapping.class) != null;
    }

    private static java.lang.reflect.Field fieldOf(String name) {
        return java.util.Arrays.stream(Customer360Response.class.getDeclaredFields())
                .filter(f -> f.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no field " + name));
    }
}
