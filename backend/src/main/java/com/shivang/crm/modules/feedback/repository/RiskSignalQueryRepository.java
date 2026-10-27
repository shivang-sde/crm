package com.shivang.crm.modules.feedback.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.shivang.crm.modules.feedback.dto.RiskSignalResponse.RiskSignalSeverity;
import com.shivang.crm.modules.feedback.dto.RiskSignalResponse.RiskSignalType;

import lombok.RequiredArgsConstructor;

/**
 * L3.4-P7: database-side risk-signal search.
 *
 * <p>The cross-account listing must filter, order, count and page the whole
 * signal set. Doing that in Java requires loading every matching source row,
 * and the previous per-source 500-row ceiling silently dropped the rest. This
 * query pushes the rule predicates, the caller's domain scopes, the optional
 * type/severity filters and the global ordering into PostgreSQL, so the LIMIT
 * is applied to the final signal set.
 *
 * <p>The query returns only source coordinates (type, account, source id,
 * detected-at). The service loads at most the current page's source rows and
 * reuses the same response mapping as the account-scoped path. No signal text
 * is built in SQL.
 */
@Repository
@RequiredArgsConstructor
public class RiskSignalQueryRepository {

    private static final String EMPTY_UNION = """
            select cast(null as varchar) as signal_type,
                   cast(null as integer) as severity_rank,
                   cast(null as uuid) as account_id,
                   cast(null as uuid) as source_id,
                   cast(null as timestamp) as detected_at
            where false
            """;

    private static final String OUTER_SELECT = """
            select signal_type, severity_rank, account_id, source_id, detected_at
              from (%s) signals
             order by severity_rank asc, detected_at desc nulls last, source_id asc nulls last, signal_type asc
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public Result search(Query query, long offset, int limit) {
        String union = union(query);
        Long total = jdbcTemplate.queryForObject(
                "select count(*) from (" + union + ") signals", params(query), Long.class);

        MapSqlParameterSource pageParams = params(query)
                .addValue("limit", limit)
                .addValue("offset", offset);
        List<SignalRow> rows = jdbcTemplate.query(
                OUTER_SELECT.formatted(union) + " limit :limit offset :offset",
                pageParams,
                this::mapRow);

        return new Result(rows, total == null ? 0L : total);
    }

    private String union(Query query) {
        List<String> branches = new ArrayList<>();
        for (RiskSignalType type : query.types()) {
            String branch = branch(query, type);
            if (branch != null) {
                branches.add(branch);
            }
        }
        return branches.isEmpty() ? EMPTY_UNION : String.join("\n union all \n", branches);
    }

    private String branch(Query query, RiskSignalType type) {
        return switch (type) {
            case NEGATIVE_NPS -> feedbackBranch(
                    query, type, "f.type = 'NPS'", "f.score between 0 and 6");
            case LOW_CSAT -> feedbackBranch(
                    query, type, "f.type = 'CSAT'", "f.score between 1 and 2");
            case HIGH_CES -> feedbackBranch(
                    query, type, "f.type = 'CES'", "f.score between 4 and 7");
            case RECENT_COMPLAINT -> feedbackBranch(
                    query, type, "f.type = 'COMPLAINT'", null);
            case ENGAGEMENT_FEEDBACK_SILENT -> feedbackSilenceBranch(query, type);
            case SLA_BREACHED_TICKET -> ticketBranch(
                    query, type, "t.sla_breached = true");
            case SUPPORT_NO_RESPONSE_PAST_DUE -> ticketBranch(
                    query, type,
                    "t.status in ('OPEN', 'IN_PROGRESS')"
                            + " and t.first_response_at is null"
                            + " and t.response_due_at is not null"
                            + " and t.response_due_at <= :now");
            case SUPPORT_SLOW_FIRST_RESPONSE -> ticketBranch(
                    query, type,
                    "t.status in ('OPEN', 'IN_PROGRESS')"
                            + " and t.first_response_at is not null"
                            + " and t.response_due_at is not null"
                            + " and t.first_response_at > t.response_due_at");
            case SUPPORT_PAST_RESOLUTION_DUE -> ticketBranch(
                    query, type,
                    "t.status in ('OPEN', 'IN_PROGRESS')"
                            + " and t.resolution_due_at is not null"
                            + " and t.resolution_due_at <= :now");
            case ENTITLEMENT_APPROACHING_EXPIRY -> entitlementApproachingBranch(query, type);
            case ENTITLEMENT_EXPIRED -> entitlementExpiredBranch(query, type);
            case RECENT_DEAL_LOSS -> dealLossBranch(query, type);
        };
    }

    private String feedbackBranch(
            Query query, RiskSignalType type, String typeFilter, String scoreFilter) {
        String visibility = visibility(query, "feedback", "f");
        if (visibility == null) {
            return null;
        }
        return "select '" + type.name() + "' as signal_type, " + rank(type)
                + " as severity_rank, f.account_id, f.id as source_id, f.created_at as detected_at"
                + " from feedback f"
                + " where f.tenant_id = :tenantId and f.deleted = false"
                + " and f.source is not null and f.source <> 'INTERNAL'"
                + " and " + typeFilter
                + (scoreFilter == null ? "" : " and " + scoreFilter)
                + " and f.created_at >= :from and f.created_at < :to"
                + account(query, "f")
                + accountVisibility(query, "f")
                + visibility;
    }

    private String feedbackSilenceBranch(Query query, RiskSignalType type) {
        String visibility = visibility(query, "feedback", "f");
        if (visibility == null) {
            return null;
        }
        return "select '" + type.name() + "' as signal_type, " + rank(type)
                + " as severity_rank, latest.account_id, latest.source_id, latest.detected_at"
                + " from ("
                + "   select distinct on (f.account_id)"
                + "          f.account_id, f.id as source_id, f.created_at as detected_at"
                + "     from feedback f"
                + "    where f.tenant_id = :tenantId and f.deleted = false"
                + "      and f.source is not null and f.source <> 'INTERNAL'"
                + account(query, "f")
                + accountVisibility(query, "f")
                + visibility
                + "    order by f.account_id, f.created_at desc, f.id desc"
                + " ) latest"
                + " where latest.detected_at < :from";
    }

    private String ticketBranch(Query query, RiskSignalType type, String extraPredicate) {
        String visibility = visibility(query, "ticket", "t");
        if (visibility == null) {
            return null;
        }
        return "select '" + type.name() + "' as signal_type, " + rank(type)
                + " as severity_rank, t.account_id, t.id as source_id, t.created_at as detected_at"
                + " from tickets t"
                + " where t.tenant_id = :tenantId and t.deleted = false"
                + " and " + extraPredicate
                + account(query, "t")
                + accountVisibility(query, "t")
                + visibility;
    }

    private String entitlementApproachingBranch(Query query, RiskSignalType type) {
        String visibility = visibility(query, "entitlement", "e");
        if (visibility == null) {
            return null;
        }
        return "select '" + type.name() + "' as signal_type, " + rank(type)
                + " as severity_rank, e.account_id, e.id as source_id, e.created_at as detected_at"
                + " from customer_entitlements e"
                + " where e.tenant_id = :tenantId and e.deleted = false"
                + " and e.status = 'ACTIVE'"
                + " and e.end_date is not null and e.end_date >= :today"
                + " and (e.end_date <= :horizon"
                + "      or (e.renewable = true and e.renewal_due_date is not null"
                + "          and e.renewal_due_date >= :today and e.renewal_due_date <= :horizon))"
                + account(query, "e")
                + accountVisibility(query, "e")
                + visibility;
    }

    private String entitlementExpiredBranch(Query query, RiskSignalType type) {
        String visibility = visibility(query, "entitlement", "e");
        if (visibility == null) {
            return null;
        }
        String liveVisibility = visibility(query, "entitlement", "live");
        return "select '" + type.name() + "' as signal_type, " + rank(type)
                + " as severity_rank, e.account_id, e.id as source_id, e.end_date::timestamp as detected_at"
                + " from customer_entitlements e"
                + " where e.tenant_id = :tenantId and e.deleted = false"
                + " and e.status = 'EXPIRED'"
                + " and e.end_date is not null and e.end_date < :today"
                + account(query, "e")
                + accountVisibility(query, "e")
                + visibility
                + " and not exists ("
                + "   select 1 from customer_entitlements live"
                + "    where live.tenant_id = e.tenant_id and live.deleted = false"
                + "      and live.status in ('ACTIVE', 'PENDING')"
                + "      and live.end_date is not null and live.end_date >= :today"
                + accountVisibility(query, "live")
                + liveVisibility
                + "      and (live.renewed_from_entitlement_id = e.id"
                + "           or e.renewed_to_entitlement_id = live.id"
                + "           or (e.renewal_deal_id is not null"
                + "               and live.renewal_deal_id = e.renewal_deal_id)"
                + "           or (e.account_id is not null and e.offering_id is not null"
                + "               and live.account_id = e.account_id"
                + "               and live.offering_id = e.offering_id"
                + "               and (live.start_date is null or live.start_date <= e.end_date + 1)))"
                + " )";
    }

    private String dealLossBranch(Query query, RiskSignalType type) {
        String visibility = visibility(query, "deal", "d");
        if (visibility == null) {
            return null;
        }
        String lossAt = "coalesce(d.closed_date::timestamp, d.stage_entered_at)";
        return "select '" + type.name() + "' as signal_type, " + rank(type)
                + " as severity_rank, d.account_id, d.id as source_id, "
                + lossAt + " as detected_at"
                + " from deals d"
                + " join deal_stages ds on ds.id = d.stage_id"
                + " where d.tenant_id = :tenantId and d.deleted = false"
                + " and ds.record_category = 'CLOSED_LOST'"
                + " and " + lossAt + " >= :from and " + lossAt + " < :to"
                + account(query, "d")
                + accountVisibility(query, "d")
                + visibility;
    }

    private String account(Query query, String alias) {
        return query.accountId() == null ? "" : " and " + alias + ".account_id = :accountId";
    }

    /**
     * SQL equivalent of the account list's record scope. An unattached deal or
     * entitlement has no account to authorize, so it remains governed by its
     * source-domain scope and carries a null account id.
     */
    private String accountVisibility(Query query, String alias) {
        Scope scope = query.accountScope();
        if (scope == null || !scope.visible()) {
            return " and false";
        }
        if ("ALL".equals(scope.name())) {
            return "";
        }
        StringBuilder predicate = new StringBuilder(" and (")
                .append(alias).append(".account_id is null")
                .append(" or exists (select 1 from accounts acc where acc.id = ")
                .append(alias).append(".account_id")
                .append(" and acc.tenant_id = :tenantId and acc.deleted = false")
                .append(" and (acc.owner_user_id = :userId")
                .append(" or acc.created_by = :userId");
        if ("TEAM".equals(scope.name()) && !scope.teamUserIds().isEmpty()) {
            predicate.append(" or acc.owner_user_id in (:accountTeamIds)");
        }
        return predicate.append(")))").toString();
    }

    /**
     * SQL equivalent of the domain {@code visibleToUser} specifications. Returns
     * {@code null} when the domain is not readable at all, so the caller omits the
     * branch; a non-null empty string means the tenant-wide ALL scope.
     */
    private String visibility(Query query, String domain, String alias) {
        Scope scope = query.scopes().get(domain);
        if (scope == null || !scope.visible()) {
            return null;
        }
        if ("ALL".equals(scope.name())) {
            return "";
        }
        StringBuilder predicate = new StringBuilder(" and (")
                .append(alias).append(".owner_user_id = :userId")
                .append(" or ").append(alias).append(".created_by = :userId");
        if ("TEAM".equals(scope.name()) && !scope.teamUserIds().isEmpty()) {
            predicate.append(" or ").append(alias)
                    .append(".owner_user_id in (:").append(domain).append("TeamIds)");
        }
        return predicate.append(")").toString();
    }

    private MapSqlParameterSource params(Query query) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", query.tenantId())
                .addValue("from", utc(query.from()))
                .addValue("to", utc(query.to()))
                .addValue("now", utc(query.now()))
                .addValue("today", query.today())
                .addValue("horizon", query.today().plusDays(
                        com.shivang.crm.modules.feedback.service.RiskSignalService.EXPIRY_HORIZON_DAYS))
                .addValue("userId", query.userId());

        if (query.accountId() != null) {
            params.addValue("accountId", query.accountId());
        }
        if (query.accountScope() != null && !query.accountScope().teamUserIds().isEmpty()) {
            params.addValue("accountTeamIds", query.accountScope().teamUserIds());
        }
        query.scopes().forEach((domain, scope) -> {
            if (!scope.teamUserIds().isEmpty()) {
                params.addValue(domain + "TeamIds", scope.teamUserIds());
            }
        });
        return params;
    }

    private SignalRow mapRow(java.sql.ResultSet rs, int rowNumber) throws java.sql.SQLException {
        int rank = rs.getInt("severity_rank");
        Timestamp detectedAt = rs.getTimestamp("detected_at", utcCalendar());
        return new SignalRow(
                RiskSignalType.valueOf(rs.getString("signal_type")),
                rank == RiskSignalSeverity.HIGH.rank()
                        ? RiskSignalSeverity.HIGH : RiskSignalSeverity.MEDIUM,
                rs.getObject("account_id", UUID.class),
                rs.getObject("source_id", UUID.class),
                detectedAt == null ? null : detectedAt.toInstant());
    }

    private static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Calendar utcCalendar() {
        return Calendar.getInstance(TimeZone.getTimeZone(ZoneOffset.UTC));
    }

    private static int rank(RiskSignalType type) {
        return switch (type) {
            case NEGATIVE_NPS, RECENT_COMPLAINT, SLA_BREACHED_TICKET,
                    SUPPORT_NO_RESPONSE_PAST_DUE, ENTITLEMENT_EXPIRED -> RiskSignalSeverity.HIGH.rank();
            case LOW_CSAT, HIGH_CES, ENGAGEMENT_FEEDBACK_SILENT,
                    SUPPORT_SLOW_FIRST_RESPONSE, SUPPORT_PAST_RESOLUTION_DUE,
                    ENTITLEMENT_APPROACHING_EXPIRY, RECENT_DEAL_LOSS -> RiskSignalSeverity.MEDIUM.rank();
        };
    }

    public record Query(
            UUID tenantId,
            UUID accountId,
            UUID userId,
            Instant from,
            Instant to,
            Instant now,
            LocalDate today,
            List<RiskSignalType> types,
            Scope accountScope,
            Map<String, Scope> scopes) {
    }

    public record Scope(String name, UUID userId, List<UUID> teamUserIds) {
        public boolean visible() {
            return "ALL".equals(name) || "TEAM".equals(name) || "OWN".equals(name);
        }
    }

    public record SignalRow(
            RiskSignalType type,
            RiskSignalSeverity severity,
            UUID accountId,
            UUID sourceId,
            Instant detectedAt) {
    }

    public record Result(List<SignalRow> rows, long total) {
    }
}
