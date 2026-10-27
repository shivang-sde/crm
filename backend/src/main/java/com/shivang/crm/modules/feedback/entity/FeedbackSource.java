package com.shivang.crm.modules.feedback.entity;

/**
 * L3.3-P1: where a Feedback record originated.
 *
 * <p>Provenance metadata only. It is not the integration mechanism — the API is the
 * integration boundary — but it IS part of the external-reference deduplication key
 * ({@code tenant + source + externalReference}). That is why it is an enum rather
 * than free text: with a free-text source, the same {@code externalReference} sent
 * as "website" and "WEBBSITE" would create two distinct rows and defeat the dedup
 * guarantee the index is there to provide.
 *
 * <p>Adding a channel is a one-line enum constant plus a migration; it deliberately
 * does not require a registry table in P1.
 */
public enum FeedbackSource {

    /** First-party web widget on the customer's portal or site. */
    WEBSITE,

    /** Typeform. */
    TYPEFORM,

    /** Google Forms. */
    GOOGLE_FORMS,

    /** WhatsApp conversation. */
    WHATSAPP,

    /** Email reply or forwarded mail. */
    EMAIL,

    /** Server-to-server call by an external system. */
    API,

    /** Captured by CRM staff inside the product. */
    INTERNAL
}
