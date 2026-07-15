package com.shivang.crm.modules.integration.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.shivang.crm.modules.integration.dto.ConnectorExecutionContext;
import com.shivang.crm.modules.integration.dto.ConnectorExecutionResult;
import com.shivang.crm.modules.integration.entity.ConnectorInstance;
import com.shivang.crm.modules.integration.entity.ProviderActionDefinition;
import com.shivang.crm.modules.integration.service.impl.HttpProviderActionExecutor;

import com.sun.net.httpserver.HttpServer;

class HttpProviderActionExecutorTest {

    @Test
    void executesPostRequestAndCapturesResponse() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> receivedBody = new AtomicReference<>();
        server.createContext("/dialer/clicktocall", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            receivedBody.set(new String(body, StandardCharsets.UTF_8));
            String response = "{\"ok\":true}";
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length());
            exchange.getResponseBody().write(response.getBytes(StandardCharsets.UTF_8));
            exchange.close();
        });
        server.start();

        try {
            RestClient restClient = RestClient.builder()
                .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                .build();

            HttpProviderActionExecutor executor = new HttpProviderActionExecutor(restClient, new ConnectorAuditSanitizer());

            ConnectorExecutionContext context = new ConnectorExecutionContext();
            context.setTenantId(UUID.randomUUID());
            context.setUserId(UUID.randomUUID());
            context.setProviderKey("sellspark_voice");
            context.setActionKey("CLICK_TO_CALL");
            context.setEntity(Map.of("id", UUID.randomUUID(), "phone", "1234567890"));
            context.setInput(Map.of("phoneNumber", "9876543210"));
            context.setCredential(Map.of("username", "agent", "password", "secret-pass"));

            ProviderActionDefinition action = new ProviderActionDefinition();
            action.setEndpointTemplate("/dialer/clicktocall");
            action.setHttpMethod("POST");
            action.setRequestTemplate(Map.of(
                "userId", "{{credential.username}}",
                "password", "{{credential.password}}",
                "number", "{{input.phoneNumber}}",
                "leadId", "{{entity.id}}"
            ));

            ConnectorInstance connectorInstance = new ConnectorInstance();
            connectorInstance.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());

            ConnectorExecutionResult result = executor.execute(context, action, connectorInstance, Map.of());

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getStatusCode()).isEqualTo(200);
            assertThat(receivedBody.get()).contains("agent");
            assertThat(receivedBody.get()).contains("9876543210");
            assertThat(result.getResponseBody()).containsEntry("ok", true);
        } finally {
            server.stop(0);
        }
    }
}
