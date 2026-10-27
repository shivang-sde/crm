package com.shivang.crm.modules.operations.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * Configuration for the operator message recovery tooling (OPS-01 / OPS-02).
 *
 * <p>Every value is a deployment concern, not a policy baked into code. The
 * defaults are deliberately conservative.
 */
@Component
@ConfigurationProperties(prefix = "app.operator-tools")
@Getter
@Setter
public class OperatorToolsProperties {

    /** Master switch. When false both inspectors refuse to do anything. */
    private boolean enabled = false;

    private final Dlq dlq = new Dlq();
    private final Outbox outbox = new Outbox();

    @Getter
    @Setter
    public static class Dlq {

        /**
         * Base URL of the RabbitMQ management HTTP API, e.g.
         * {@code http://rabbitmq:15672}. Required for DLQ inspection.
         *
         * <p>Never derived from request input. The management API exposes far more
         * than message peeking (user creation, policy changes), so the target host
         * is fixed at deploy time and this service only ever calls two read/write
         * queue endpoints under it.
         *
         * <p>No default: if unset, DLQ inspection reports that it is unavailable
         * instead of guessing a host.
         */
        private String managementUrl;

        /**
         * Virtual host. Defaults to {@code /} when unset. The management API encodes
         * the vhost into the path, so {@code /} becomes {@code %2F}.
         */
        private String vhost = "/";

        /**
         * Management API credentials. Defaults to the AMQP credentials when unset so
         * a single-operator deployment needs no extra secret. The value is used only
         * to build an Authorization header and is never logged.
         */
        private String username;

        private String password;

        /** Seconds to wait for the management API. */
        private int connectTimeoutMs = 3_000;

        private int readTimeoutMs = 5_000;

        /**
         * Messages fetched per inspection request. Bounded so a large queue is
         * never loaded into memory.
         */
        private int pageSize = 20;

        /**
         * How many single-message fetches a single-message lookup may perform
         * before giving up. Each fetch uses the non-destructive requeue mode, so
         * a bounded scan is safe; it only bounds the work.
         */
        private int maxScanMessages = 50;

        /**
         * Publisher confirm timeout for a re-drive, in milliseconds. A re-drive
         * that cannot be confirmed is reported as failed and the original dead
         * letter is left untouched.
         */
        private int confirmTimeoutMs = 5_000;

        /**
         * Exchanges a re-drive is permitted to target. Empty means "derive from
         * x-death but require the exchange to be the CRM event exchange". This is
         * the allow list that stops a crafted dead letter from being used to
         * publish to an arbitrary exchange.
         */
        private java.util.List<String> allowedExchanges = new java.util.ArrayList<>();
    }

    @Getter
    @Setter
    public static class Outbox {

        /** Rows per page for the DEAD listing. */
        private int pageSize = 20;

        /** Refuse pages larger than this regardless of the request. */
        private int maxPageSize = 200;
    }
}
