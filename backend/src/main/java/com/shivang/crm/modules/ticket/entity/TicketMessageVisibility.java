package com.shivang.crm.modules.ticket.entity;

/**
 * L3.1-P3: who a Ticket message is addressed to.
 *
 * <p>Persisted as a {@code VARCHAR(30)} string via {@code @Enumerated(EnumType.STRING)},
 * matching {@link TicketStatus} and every other enum in this schema. Ordinal
 * persistence is never used, so reordering this enum cannot rewrite existing rows.
 *
 * <p>Deliberately two states, not a scale. A three- or four-value scale (DRAFT,
 * SENT, DELIVERED, FAILED) would describe a delivery pipeline that P3 does not have:
 * nothing here sends a message to anybody. {@link #CUSTOMER_VISIBLE} records that a
 * message is intended for the customer and nothing more.
 *
 * <p><b>Intended-for is not delivered.</b> No email, SMS, WhatsApp or portal
 * delivery exists in P3, so a {@code CUSTOMER_VISIBLE} message stays a row in this
 * database. Treating the enum as a delivery signal would be the whole mistake this
 * javadoc exists to prevent.
 */
public enum TicketMessageVisibility {

    /**
     * Agent-only working note. Never shown to a customer by any mechanism, because
     * P3 implements no customer-facing mechanism at all.
     */
    INTERNAL,

    /**
     * Intended to be shared with the customer. Recorded as intent only — P3 performs
     * no delivery, notification or external communication of any kind.
     */
    CUSTOMER_VISIBLE
}
