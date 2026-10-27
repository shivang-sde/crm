package com.shivang.crm.modules.deal.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.shivang.crm.modules.deal.entity.ForecastCategory;
import com.shivang.crm.modules.deal.entity.RecordCategory;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;

/**
 * L3.0-P5: the Deal and DealLineItem JSON contract.
 *
 * <p>These tests exist because nothing in the repository previously checked the
 * Deal wire format. The Deal DTOs carried per-field {@code @JsonProperty} names in
 * snake_case while the frontend sent camelCase, and because every backend test
 * builds DTOs through Java setters, that break was invisible to the whole suite.
 *
 * <p>Uses Jackson 3's ObjectMapper, the same one Spring Boot 4 auto-configures,
 * so unknown-property handling here matches production. That matters: unknown
 * properties are ignored rather than rejected, which is exactly why the broken
 * contract failed silently instead of erroring.
 */
class DealContractNamingTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setup() {
        mapper = new ObjectMapper();
    }

    private JsonNode write(Object value) {
        return mapper.valueToTree(value);
    }

    private void assertNoSnakeCaseKeys(JsonNode tree) {
        List<String> offenders = new ArrayList<>();
        collectSnakeCaseKeys(tree, "", offenders);
        assertThat(offenders).isEmpty();
    }

    private void collectSnakeCaseKeys(JsonNode tree, String path, List<String> offenders) {
        if (tree.isObject()) {
            tree.propertyStream().forEach(entry -> {
                if (entry.getKey().matches(".*[a-z][0-9]*_[a-z0-9_]+.*")) {
                    offenders.add(path + "/" + entry.getKey());
                }
                collectSnakeCaseKeys(entry.getValue(), path + "/" + entry.getKey(), offenders);
            });
        } else if (tree instanceof ArrayNode array) {
            for (int i = 0; i < array.size(); i++) {
                collectSnakeCaseKeys(array.get(i), path + "/" + i, offenders);
            }
        }
    }

    private boolean hasDeclaredField(Class<?> type, String name) {
        return Arrays.stream(type.getDeclaredFields()).anyMatch(f -> f.getName().equals(name));
    }

    // --- Deal create request: camelCase binding ---

    @Test
    void dealCreateRequestBindsCamelCase() {
        DealCreateRequest bound = mapper.readValue("""
                {
                  "name": "Enterprise Deal",
                  "stageId": "11111111-1111-1111-1111-111111111111",
                  "accountId": "22222222-2222-2222-2222-222222222222",
                  "contactId": "33333333-3333-3333-3333-333333333333",
                  "leadId": "44444444-4444-4444-4444-444444444444",
                  "ownerUserId": "55555555-5555-5555-5555-555555555555",
                  "amount": 150000.00,
                  "expectedCloseDate": "2026-12-31",
                  "closedDate": "2026-11-30",
                  "probability": 75,
                  "forecastCategory": "COMMIT",
                  "nextStep": "Send paperwork",
                  "dealType": "NEW_BUSINESS",
                  "leadSource": "Referral",
                  "campaignSource": "Q1",
                  "wonReason": "Signed",
                  "lostReason": "Too expensive",
                  "customData": {"segment": "enterprise"}
                }
                """, DealCreateRequest.class);

        assertThat(bound.getName()).isEqualTo("Enterprise Deal");
        assertThat(bound.getStageId()).hasToString("11111111-1111-1111-1111-111111111111");
        assertThat(bound.getAccountId()).hasToString("22222222-2222-2222-2222-222222222222");
        assertThat(bound.getContactId()).hasToString("33333333-3333-3333-3333-333333333333");
        assertThat(bound.getLeadId()).hasToString("44444444-4444-4444-4444-444444444444");
        assertThat(bound.getOwnerUserId()).hasToString("55555555-5555-5555-5555-555555555555");
        assertThat(bound.getAmount()).isEqualByComparingTo("150000.00");
        assertThat(bound.getExpectedCloseDate()).hasToString("2026-12-31");
        assertThat(bound.getClosedDate()).hasToString("2026-11-30");
        assertThat(bound.getProbability()).isEqualTo(75);
        assertThat(bound.getForecastCategory()).isEqualTo(ForecastCategory.COMMIT);
        assertThat(bound.getNextStep()).isEqualTo("Send paperwork");
        assertThat(bound.getDealType().name()).isEqualTo("NEW_BUSINESS");
        assertThat(bound.getLeadSource()).isEqualTo("Referral");
        assertThat(bound.getCampaignSource()).isEqualTo("Q1");
        assertThat(bound.getWonReason()).isEqualTo("Signed");
        assertThat(bound.getLostReason()).isEqualTo("Too expensive");
        assertThat(bound.getCustomData()).containsEntry("segment", "enterprise");
    }

    /**
     * The old contract. These spellings used to be the only accepted ones, and
     * the frontend never sent them, so the fields silently arrived empty.
     *
     * <p>Production ignores unknown properties, so the honest assertion is that
     * the snake_case spelling no longer binds rather than that it throws.
     */
    @Test
    void theOldSnakeCaseNamesNoLongerBind() {
        DealCreateRequest bound = mapper.readValue("""
                {
                  "name": "Deal",
                  "stage_id": "11111111-1111-1111-1111-111111111111",
                  "account_id": "22222222-2222-2222-2222-222222222222",
                  "contact_id": "33333333-3333-3333-3333-333333333333",
                  "lead_id": "44444444-4444-4444-4444-444444444444",
                  "owner_user_id": "55555555-5555-5555-5555-555555555555",
                  "expected_close_date": "2026-12-31",
                  "closed_date": "2026-11-30",
                  "forecast_category": "COMMIT",
                  "next_step": "Send paperwork",
                  "deal_type": "NEW_BUSINESS",
                  "lead_source": "Referral",
                  "campaign_source": "Q1",
                  "won_reason": "Signed",
                  "lost_reason": "Too expensive",
                  "custom_data": {"segment": "enterprise"}
                }
                """, DealCreateRequest.class);

        assertThat(bound.getName()).isEqualTo("Deal");
        assertThat(bound.getStageId()).isNull();
        assertThat(bound.getAccountId()).isNull();
        assertThat(bound.getContactId()).isNull();
        assertThat(bound.getLeadId()).isNull();
        assertThat(bound.getOwnerUserId()).isNull();
        assertThat(bound.getExpectedCloseDate()).isNull();
        assertThat(bound.getClosedDate()).isNull();
        assertThat(bound.getForecastCategory()).isNull();
        assertThat(bound.getNextStep()).isNull();
        assertThat(bound.getDealType()).isNull();
        assertThat(bound.getLeadSource()).isNull();
        assertThat(bound.getCampaignSource()).isNull();
        assertThat(bound.getWonReason()).isNull();
        assertThat(bound.getLostReason()).isNull();
        assertThat(bound.getCustomData()).isNull();
    }

    @Test
    void dealUpdateRequestBindsCamelCase() {
        DealUpdateRequest bound = mapper.readValue("""
                {
                  "name": "Renamed",
                  "stageId": "11111111-1111-1111-1111-111111111111",
                  "accountId": "22222222-2222-2222-2222-222222222222",
                  "contactId": "33333333-3333-3333-3333-333333333333",
                  "ownerUserId": "55555555-5555-5555-5555-555555555555",
                  "expectedCloseDate": "2026-12-31",
                  "closedDate": "2026-11-30",
                  "wonReason": "Signed",
                  "lostReason": "Too expensive"
                }
                """, DealUpdateRequest.class);

        assertThat(bound.getName()).isEqualTo("Renamed");
        assertThat(bound.getStageId()).hasToString("11111111-1111-1111-1111-111111111111");
        assertThat(bound.getAccountId()).hasToString("22222222-2222-2222-2222-222222222222");
        assertThat(bound.getContactId()).hasToString("33333333-3333-3333-3333-333333333333");
        assertThat(bound.getOwnerUserId()).hasToString("55555555-5555-5555-5555-555555555555");
        assertThat(bound.getExpectedCloseDate()).hasToString("2026-12-31");
        assertThat(bound.getClosedDate()).hasToString("2026-11-30");
        assertThat(bound.getWonReason()).isEqualTo("Signed");
        assertThat(bound.getLostReason()).isEqualTo("Too expensive");
    }

    @Test
    void dealUpdateRequestDoesNotAcceptLineItems() {
        // Line-item mutation stays owned by DealLineItemService. Accepting
        // lineItems here would let a Deal update read as "replace all lines",
        // which is a destructive operation nobody asked for.
        assertThat(hasDeclaredField(DealUpdateRequest.class, "lineItems")).isFalse();

        DealUpdateRequest bound = mapper.readValue("""
                {"name":"Deal","lineItems":[]}
                """, DealUpdateRequest.class);
        assertThat(bound.getName()).isEqualTo("Deal");
        assertThat(write(bound).has("lineItems")).isFalse();
    }

    // --- Deal create request: nested initial line items ---

    @Test
    void dealCreateRequestBindsNestedLineItems() {
        DealCreateRequest bound = mapper.readValue("""
                {
                  "name": "Won Deal",
                  "lineItems": [
                    {
                      "offeringId": "66666666-6666-6666-6666-666666666666",
                      "quantity": 2,
                      "unitPrice": 1000.00,
                      "discountAmount": 0.00,
                      "taxAmount": 180.00,
                      "serviceStartDate": "2026-01-01",
                      "serviceEndDate": "2026-12-31",
                      "renewable": false,
                      "renewalNoticeDays": 30,
                      "description": "Annual licence"
                    },
                    {
                      "offeringId": "77777777-7777-7777-7777-777777777777",
                      "quantity": 1,
                      "unitPrice": 500.00
                    }
                  ]
                }
                """, DealCreateRequest.class);

        assertThat(bound.getLineItems()).hasSize(2);

        DealLineItemCreateRequest first = bound.getLineItems().get(0);
        assertThat(first.getOfferingId()).hasToString("66666666-6666-6666-6666-666666666666");
        assertThat(first.getQuantity()).isEqualByComparingTo("2");
        assertThat(first.getUnitPrice()).isEqualByComparingTo("1000.00");
        assertThat(first.getDiscountAmount()).isEqualByComparingTo("0.00");
        assertThat(first.getTaxAmount()).isEqualByComparingTo("180.00");
        assertThat(first.getServiceStartDate()).hasToString("2026-01-01");
        assertThat(first.getServiceEndDate()).hasToString("2026-12-31");
        assertThat(first.getRenewable()).isFalse();
        assertThat(first.getRenewalNoticeDays()).isEqualTo(30);
        assertThat(first.getDescription()).isEqualTo("Annual licence");

        DealLineItemCreateRequest second = bound.getLineItems().get(1);
        assertThat(second.getOfferingId()).hasToString("77777777-7777-7777-7777-777777777777");
        assertThat(second.getQuantity()).isEqualByComparingTo("1");
        assertThat(second.getUnitPrice()).isEqualByComparingTo("500.00");
    }

    @Test
    void dealCreateRequestTreatsAbsentLineItemsAsAbsent() {
        // OPEN deals must stay creatable without lines, as before P5.
        DealCreateRequest bound = mapper.readValue("""
                {"name":"Open Deal"}
                """, DealCreateRequest.class);
        assertThat(bound.getLineItems()).isNull();

        DealCreateRequest empty = mapper.readValue("""
                {"name":"Open Deal","lineItems":[]}
                """, DealCreateRequest.class);
        assertThat(empty.getLineItems()).isEmpty();
    }

    /**
     * The percentage spellings the frontend used to invent are not the contract.
     * Because unknown properties are ignored rather than rejected, they silently
     * became a zero discount and a zero tax on every line created through the UI.
     */
    @Test
    void theFrontendPercentageSpellingsNeverBoundToMoney() {
        DealCreateRequest bound = mapper.readValue("""
                {
                  "name": "Won Deal",
                  "lineItems": [
                    {"offeringId":"66666666-6666-6666-6666-666666666666","quantity":1,
                     "unitPrice":1000.00,
                     "discountPercent":10,"taxPercent":18,
                     "startDate":"2026-01-01","endDate":"2026-12-31",
                     "lineName":"Licence","lineDescription":"Annual"}
                  ]
                }
                """, DealCreateRequest.class);

        DealLineItemCreateRequest line = bound.getLineItems().get(0);
        // The only value that bound is the real one.
        assertThat(line.getUnitPrice()).isEqualByComparingTo("1000.00");
        assertThat(line.getDiscountAmount()).isNull();
        assertThat(line.getTaxAmount()).isNull();
        assertThat(line.getServiceStartDate()).isNull();
        assertThat(line.getServiceEndDate()).isNull();
        assertThat(line.getDescription()).isNull();

        // And none of the invented names exist on the DTO to bind onto.
        assertThat(hasDeclaredField(DealLineItemCreateRequest.class, "discountPercent")).isFalse();
        assertThat(hasDeclaredField(DealLineItemCreateRequest.class, "taxPercent")).isFalse();
        assertThat(hasDeclaredField(DealLineItemUpdateRequest.class, "discountPercent")).isFalse();
        assertThat(hasDeclaredField(DealLineItemUpdateRequest.class, "taxPercent")).isFalse();
        assertThat(hasDeclaredField(DealLineItemResponse.class, "discountPercent")).isFalse();
        assertThat(hasDeclaredField(DealLineItemResponse.class, "taxPercent")).isFalse();
        assertThat(hasDeclaredField(DealLineItemResponse.class, "lineName")).isFalse();
        assertThat(hasDeclaredField(DealLineItemResponse.class, "lineDescription")).isFalse();
        assertThat(hasDeclaredField(DealLineItemResponse.class, "startDate")).isFalse();
        assertThat(hasDeclaredField(DealLineItemResponse.class, "endDate")).isFalse();
        assertThat(hasDeclaredField(DealLineItemResponse.class, "offeringName")).isFalse();
        assertThat(hasDeclaredField(DealLineItemResponse.class, "offeringCode")).isFalse();
    }

    // --- Deal response: camelCase serialization ---

    @Test
    void dealResponseSerializesCamelCaseWithNoSnakeCase() {
        DealStageResponse stage = DealStageResponse.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .name("Negotiation")
                .displayOrder(2)
                .isDefault(true)
                .isClosed(false)
                .recordCategory(RecordCategory.OPEN)
                .defaultProbability(50)
                .defaultForecastCategory(ForecastCategory.BEST_CASE)
                .build();

        DealResponse response = DealResponse.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .name("Enterprise Deal")
                .stage(stage)
                .accountId(UUID.randomUUID())
                .contactId(UUID.randomUUID())
                .leadId(UUID.randomUUID())
                .ownerUserId(UUID.randomUUID())
                .amount(new BigDecimal("150000.00"))
                .expectedCloseDate(LocalDate.of(2026, 12, 31))
                .closedDate(LocalDate.of(2026, 11, 30))
                .expectedRevenue(new BigDecimal("50000.00"))
                .forecastCategory(ForecastCategory.COMMIT)
                .recordCategory(RecordCategory.OPEN)
                .isWon(false)
                .isLost(false)
                .createdBy(UUID.randomUUID())
                .updatedBy(UUID.randomUUID())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        JsonNode json = write(response);

        assertThat(json.has("accountId")).isTrue();
        assertThat(json.has("contactId")).isTrue();
        assertThat(json.has("leadId")).isTrue();
        assertThat(json.has("ownerUserId")).isTrue();
        assertThat(json.has("expectedCloseDate")).isTrue();
        assertThat(json.has("closedDate")).isTrue();
        assertThat(json.has("expectedRevenue")).isTrue();
        assertThat(json.has("forecastCategory")).isTrue();
        assertThat(json.has("recordCategory")).isTrue();
        assertThat(json.has("isWon")).isTrue();
        assertThat(json.has("isLost")).isTrue();
        assertThat(json.has("createdBy")).isTrue();
        assertThat(json.has("updatedBy")).isTrue();
        assertThat(json.has("createdAt")).isTrue();
        assertThat(json.has("updatedAt")).isTrue();
        assertThat(json.has("tenantId")).isTrue();

        // The nested stage must follow the same convention, or the Deal response
        // is internally mixed even though each DTO is individually consistent.
        JsonNode nestedStage = json.get("stage");
        assertThat(nestedStage.has("displayOrder")).isTrue();
        assertThat(nestedStage.has("isDefault")).isTrue();
        assertThat(nestedStage.has("isClosed")).isTrue();
        assertThat(nestedStage.has("recordCategory")).isTrue();
        assertThat(nestedStage.has("defaultProbability")).isTrue();
        assertThat(nestedStage.has("defaultForecastCategory")).isTrue();
        assertThat(nestedStage.has("tenantId")).isTrue();

        assertNoSnakeCaseKeys(json);
    }

    @Test
    void dealResponseRoundTripsBackIntoItself() {
        DealResponse original = DealResponse.builder()
                .id(UUID.randomUUID())
                .name("Round Trip")
                .accountId(UUID.randomUUID())
                .ownerUserId(UUID.randomUUID())
                .expectedCloseDate(LocalDate.of(2026, 12, 31))
                .closedDate(LocalDate.of(2026, 11, 30))
                .amount(new BigDecimal("150000.00"))
                .isWon(true)
                .build();

        DealResponse roundTripped =
                mapper.readValue(mapper.writeValueAsString(original), DealResponse.class);

        assertThat(roundTripped.getId()).isEqualTo(original.getId());
        assertThat(roundTripped.getName()).isEqualTo("Round Trip");
        assertThat(roundTripped.getAccountId()).isEqualTo(original.getAccountId());
        assertThat(roundTripped.getOwnerUserId()).isEqualTo(original.getOwnerUserId());
        assertThat(roundTripped.getExpectedCloseDate()).isEqualTo(original.getExpectedCloseDate());
        assertThat(roundTripped.getClosedDate()).isEqualTo(original.getClosedDate());
        assertThat(roundTripped.getAmount()).isEqualByComparingTo("150000.00");
        assertThat(roundTripped.getIsWon()).isTrue();
    }

    // --- DealLineItem response: camelCase serialization ---

    @Test
    void dealLineItemResponseSerializesCamelCaseWithNoSnakeCase() {
        DealLineItemResponse response = DealLineItemResponse.builder()
                .id(UUID.randomUUID())
                .dealId(UUID.randomUUID())
                .offeringId(UUID.randomUUID())
                .itemName("Enterprise Licence")
                .itemCode("ENT")
                .description("Annual")
                .quantity(new BigDecimal("2"))
                .unitPrice(new BigDecimal("1000.00"))
                .discountAmount(new BigDecimal("50.00"))
                .taxAmount(new BigDecimal("180.00"))
                .currencyCode("USD")
                .lineTotal(new BigDecimal("2130.00"))
                .serviceStartDate(LocalDate.of(2026, 1, 1))
                .serviceEndDate(LocalDate.of(2026, 12, 31))
                .renewable(true)
                .renewalNoticeDays(30)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        JsonNode json = write(response);

        assertThat(json.has("dealId")).isTrue();
        assertThat(json.has("offeringId")).isTrue();
        assertThat(json.has("itemName")).isTrue();
        assertThat(json.has("itemCode")).isTrue();
        assertThat(json.has("unitPrice")).isTrue();
        assertThat(json.has("discountAmount")).isTrue();
        assertThat(json.has("taxAmount")).isTrue();
        assertThat(json.has("currencyCode")).isTrue();
        assertThat(json.has("lineTotal")).isTrue();
        assertThat(json.has("serviceStartDate")).isTrue();
        assertThat(json.has("serviceEndDate")).isTrue();
        assertThat(json.has("renewalNoticeDays")).isTrue();
        assertThat(json.has("createdAt")).isTrue();
        assertThat(json.has("updatedAt")).isTrue();

        assertNoSnakeCaseKeys(json);
    }

    /**
     * The currency is readable but never writable. P4 made the Offering the
     * source of truth, so a client must not be able to name a different one.
     */
    @Test
    void lineItemCurrencyIsReadOnly() {
        assertThat(hasDeclaredField(DealLineItemResponse.class, "currencyCode")).isTrue();
        assertThat(hasDeclaredField(DealLineItemCreateRequest.class, "currencyCode")).isFalse();
        assertThat(hasDeclaredField(DealLineItemUpdateRequest.class, "currencyCode")).isFalse();

        DealLineItemCreateRequest bound = mapper.readValue("""
                {"offeringId":"66666666-6666-6666-6666-666666666666","quantity":1,
                 "currencyCode":"EUR"}
                """, DealLineItemCreateRequest.class);

        // Nowhere to land, so it is dropped rather than silently trusted.
        assertThat(write(bound).has("currencyCode")).isFalse();
    }

    @Test
    void dealLineItemCreateRequestBindsCamelCase() {
        DealLineItemCreateRequest bound = mapper.readValue("""
                {
                  "offeringId": "66666666-6666-6666-6666-666666666666",
                  "quantity": 2,
                  "unitPrice": 1000.00,
                  "discountAmount": 50.00,
                  "taxAmount": 180.00,
                  "serviceStartDate": "2026-01-01",
                  "serviceEndDate": "2026-12-31",
                  "renewable": true,
                  "renewalNoticeDays": 30,
                  "description": "Annual licence",
                  "customData": {"seat": "5"}
                }
                """, DealLineItemCreateRequest.class);

        assertThat(bound.getOfferingId()).hasToString("66666666-6666-6666-6666-666666666666");
        assertThat(bound.getQuantity()).isEqualByComparingTo("2");
        assertThat(bound.getUnitPrice()).isEqualByComparingTo("1000.00");
        assertThat(bound.getDiscountAmount()).isEqualByComparingTo("50.00");
        assertThat(bound.getTaxAmount()).isEqualByComparingTo("180.00");
        assertThat(bound.getServiceStartDate()).hasToString("2026-01-01");
        assertThat(bound.getServiceEndDate()).hasToString("2026-12-31");
        assertThat(bound.getRenewable()).isTrue();
        assertThat(bound.getRenewalNoticeDays()).isEqualTo(30);
        assertThat(bound.getDescription()).isEqualTo("Annual licence");
        assertThat(bound.getCustomData()).containsEntry("seat", "5");
    }

    @Test
    void dealLineItemUpdateRequestBindsCamelCase() {
        DealLineItemUpdateRequest bound = mapper.readValue("""
                {
                  "quantity": 3,
                  "unitPrice": 250.00,
                  "discountAmount": 25.00,
                  "taxAmount": 40.50,
                  "serviceStartDate": "2026-02-01",
                  "serviceEndDate": "2026-11-30",
                  "renewable": true,
                  "renewalNoticeDays": 14,
                  "description": "Annual"
                }
                """, DealLineItemUpdateRequest.class);

        assertThat(bound.getQuantity()).isEqualByComparingTo("3");
        assertThat(bound.getUnitPrice()).isEqualByComparingTo("250.00");
        assertThat(bound.getDiscountAmount()).isEqualByComparingTo("25.00");
        assertThat(bound.getTaxAmount()).isEqualByComparingTo("40.50");
        assertThat(bound.getServiceStartDate()).hasToString("2026-02-01");
        assertThat(bound.getServiceEndDate()).hasToString("2026-11-30");
        assertThat(bound.getRenewable()).isTrue();
        assertThat(bound.getRenewalNoticeDays()).isEqualTo(14);
        assertThat(bound.getDescription()).isEqualTo("Annual");
    }

    // --- stage change ---

    @Test
    void dealStageChangeRequestBindsCamelCase() {
        DealStageChangeRequest bound = mapper.readValue("""
                {
                  "stageId": "11111111-1111-1111-1111-111111111111",
                  "closedDate": "2026-11-30",
                  "wonReason": "Signed",
                  "lostReason": "Too expensive"
                }
                """, DealStageChangeRequest.class);

        assertThat(bound.getStageId()).hasToString("11111111-1111-1111-1111-111111111111");
        assertThat(bound.getClosedDate()).hasToString("2026-11-30");
        assertThat(bound.getWonReason()).isEqualTo("Signed");
        assertThat(bound.getLostReason()).isEqualTo("Too expensive");

        DealStageChangeRequest stale = mapper.readValue("""
                {"stage_id":"11111111-1111-1111-1111-111111111111"}
                """, DealStageChangeRequest.class);
        assertThat(stale.getStageId()).isNull();
    }

    // --- no module-wide naming strategy was introduced ---

    @Test
    void dealDtosCarryNoPerFieldJsonPropertyRenames() {
        // The convention is now simply "use the field name". Keeping a per-field
        // rename anywhere in this contract would be a latent break again.
        List<Class<?>> dtos = List.of(
                DealCreateRequest.class, DealUpdateRequest.class, DealResponse.class,
                DealLineItemCreateRequest.class, DealLineItemUpdateRequest.class, DealLineItemResponse.class,
                DealStageChangeRequest.class, DealStageResponse.class);

        for (Class<?> dto : dtos) {
            assertThat(Arrays.stream(dto.getDeclaredFields())
                    .noneMatch(f -> f.isAnnotationPresent(JsonProperty.class)))
                    .as("%s must rely on its field names, not per-field renames", dto.getSimpleName())
                    .isTrue();
        }
    }

    @Test
    void currencyIsNotInventedAtTheDealLevel() {
        // P5 forbids a Deal-level currency: money is per line item and per
        // offering. The frontend used to send a phantom Deal.currency that the
        // backend has never had a home for.
        assertThat(hasDeclaredField(DealResponse.class, "currency")).isFalse();
        assertThat(hasDeclaredField(DealCreateRequest.class, "currency")).isFalse();
        assertThat(hasDeclaredField(DealUpdateRequest.class, "currency")).isFalse();
    }

    @Test
    void expectedRevenueStaysReadOnlyOnCreate() {
        // The frontend sent it on create but the backend only ever derives it from
        // line items. Asserted so the phantom field cannot quietly return.
        assertThat(hasDeclaredField(DealCreateRequest.class, "expectedRevenue")).isFalse();
        assertThat(hasDeclaredField(DealResponse.class, "expectedRevenue")).isTrue();
    }
}
