package com.shivang.crm.modules.workflow.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;

class HttpApiResponseInterpreterTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode json(String raw) {
        try { return mapper.readTree(raw); } catch (Exception e) { throw new RuntimeException(e); }
    }

    @Test
    void successTrue_shouldBeSuccess() {
        JsonNode n = json("{\"success\": true}");
        var r = HttpApiResponseInterpreter.interpret(n);
        assertThat(r.outcome()).isEqualTo(HttpApiApplicationOutcome.SUCCESS);
    }

    @Test
    void successFalse_shouldBeFailure() {
        JsonNode n = json("{\"success\": false, \"message\": \"Invalid API key\"}");
        var r = HttpApiResponseInterpreter.interpret(n);
        assertThat(r.outcome()).isEqualTo(HttpApiApplicationOutcome.FAILURE);
        assertThat(r.userMessage()).isEqualTo("Invalid API key");
    }

    @Test
    void statusSuccess_shouldBeSuccess() {
        JsonNode n = json("{\"status\": \"success\"}");
        var r = HttpApiResponseInterpreter.interpret(n);
        assertThat(r.outcome()).isEqualTo(HttpApiApplicationOutcome.SUCCESS);
    }

    @Test
    void statusError_shouldBeFailure() {
        JsonNode n = json("{\"status\": \"Error\", \"response\": \"Connect With Admin\"}");
        var r = HttpApiResponseInterpreter.interpret(n);
        assertThat(r.outcome()).isEqualTo(HttpApiApplicationOutcome.FAILURE);
        assertThat(r.userMessage()).isEqualTo("Connect With Admin");
        assertThat(r.reason()).contains("status field");
    }

    @Test
    void statusErrorLowercase_shouldBeFailure() {
        JsonNode n = json("{\"status\": \"error\"}");
        assertThat(HttpApiResponseInterpreter.interpret(n).outcome()).isEqualTo(HttpApiApplicationOutcome.FAILURE);
    }

    @Test
    void resultFailed_shouldBeFailure() {
        JsonNode n = json("{\"result\": \"failed\", \"reason\": \"Authentication failed\"}");
        var r = HttpApiResponseInterpreter.interpret(n);
        assertThat(r.outcome()).isEqualTo(HttpApiApplicationOutcome.FAILURE);
        assertThat(r.userMessage()).isEqualTo("Authentication failed");
    }

    @Test
    void okFalse_shouldBeFailure() {
        JsonNode n = json("{\"ok\": false}");
        assertThat(HttpApiResponseInterpreter.interpret(n).outcome()).isEqualTo(HttpApiApplicationOutcome.FAILURE);
    }

    @Test
    void okTrue_shouldBeSuccess() {
        JsonNode n = json("{\"ok\": true, \"data\": {}}");
        assertThat(HttpApiResponseInterpreter.interpret(n).outcome()).isEqualTo(HttpApiApplicationOutcome.SUCCESS);
    }

    @Test
    void structuredError_shouldBeFailure() {
        JsonNode n = json("{\"error\": \"Something went wrong\"}");
        assertThat(HttpApiResponseInterpreter.interpret(n).outcome()).isEqualTo(HttpApiApplicationOutcome.FAILURE);
    }

    @Test
    void structuredErrorObject_shouldBeFailure() {
        JsonNode n = json("{\"error\": {\"code\": \"AUTH\", \"message\": \"bad\"}}");
        assertThat(HttpApiResponseInterpreter.interpret(n).outcome()).isEqualTo(HttpApiApplicationOutcome.FAILURE);
    }

    @Test
    void arbitraryMessageContainingError_shouldNotBeFailure() {
        JsonNode n = json("{\"message\": \"No error occurred\"}");
        var r = HttpApiResponseInterpreter.interpret(n);
        // Must NOT be flagged as failure from arbitrary text
        assertThat(r.outcome()).isEqualTo(HttpApiApplicationOutcome.UNKNOWN);
        assertThat(r.userMessage()).isEqualTo("No error occurred");
    }

    @Test
    void arbitraryDescriptionContainingFailure_shouldNotBeFailure() {
        JsonNode n = json("{\"description\": \"Previous failure was resolved\"}");
        assertThat(HttpApiResponseInterpreter.interpret(n).outcome()).isEqualTo(HttpApiApplicationOutcome.UNKNOWN);
    }

    @Test
    void unknownJsonResponse_shouldBeUnknown() {
        JsonNode n = json("{\"data\": {\"id\": 123}, \"count\": 5}");
        assertThat(HttpApiResponseInterpreter.interpret(n).outcome()).isEqualTo(HttpApiApplicationOutcome.UNKNOWN);
    }

    @Test
    void plainTextResponse_shouldBeUnknown() {
        JsonNode n = mapper.getNodeFactory().textNode("OK - plain text");
        assertThat(HttpApiResponseInterpreter.interpret(n).outcome()).isEqualTo(HttpApiApplicationOutcome.UNKNOWN);
    }

    @Test
    void http201WithSuccess_shouldBeSuccess() {
        // 201 handling is HTTP-level; interpreter still sees success:true as SUCCESS
        JsonNode n = json("{\"success\": true, \"id\": \"new\"}");
        assertThat(HttpApiResponseInterpreter.interpret(n).outcome()).isEqualTo(HttpApiApplicationOutcome.SUCCESS);
    }

    @Test
    void codeAuthFailed_shouldBeFailure() {
        JsonNode n = json("{\"code\": \"AUTH_FAILED\", \"message\": \"Invalid credentials\"}");
        var r = HttpApiResponseInterpreter.interpret(n);
        assertThat(r.outcome()).isEqualTo(HttpApiApplicationOutcome.FAILURE);
        assertThat(r.userMessage()).isEqualTo("Invalid credentials");
    }

    @Test
    void codeSuccess_shouldBeSuccess() {
        JsonNode n = json("{\"code\": \"SUCCESS\"}");
        assertThat(HttpApiResponseInterpreter.interpret(n).outcome()).isEqualTo(HttpApiApplicationOutcome.SUCCESS);
    }

    @Test
    void nullResponse_shouldBeUnknown() {
        assertThat(HttpApiResponseInterpreter.interpret(null).outcome()).isEqualTo(HttpApiApplicationOutcome.UNKNOWN);
    }

    @Test
    void nullNode_shouldBeUnknown() {
        JsonNode n = mapper.nullNode();
        assertThat(HttpApiResponseInterpreter.interpret(n).outcome()).isEqualTo(HttpApiApplicationOutcome.UNKNOWN);
    }

    @Test
    void emptyObject_shouldBeUnknown() {
        JsonNode n = json("{}");
        assertThat(HttpApiResponseInterpreter.interpret(n).outcome()).isEqualTo(HttpApiApplicationOutcome.UNKNOWN);
    }

    @Test
    void successFalsePrecedenceOverStatusSuccess_shouldBeFailure() {
        // Failure has higher precedence than success
        JsonNode n = json("{\"success\": false, \"status\": \"success\"}");
        assertThat(HttpApiResponseInterpreter.interpret(n).outcome()).isEqualTo(HttpApiApplicationOutcome.FAILURE);
    }

    @Test
    void statusCompleted_shouldBeSuccess() {
        JsonNode n = json("{\"status\": \"completed\"}");
        assertThat(HttpApiResponseInterpreter.interpret(n).outcome()).isEqualTo(HttpApiApplicationOutcome.SUCCESS);
    }

    @Test
    void resultSuccess_shouldBeSuccess() {
        JsonNode n = json("{\"result\": \"success\"}");
        assertThat(HttpApiResponseInterpreter.interpret(n).outcome()).isEqualTo(HttpApiApplicationOutcome.SUCCESS);
    }

    @Test
    void errorsArrayNonEmpty_shouldBeFailure() {
        JsonNode n = json("{\"errors\": [\"one\", \"two\"]}");
        assertThat(HttpApiResponseInterpreter.interpret(n).outcome()).isEqualTo(HttpApiApplicationOutcome.FAILURE);
    }

    @Test
    void errorsArrayEmpty_shouldBeUnknown() {
        JsonNode n = json("{\"errors\": []}");
        assertThat(HttpApiResponseInterpreter.interpret(n).outcome()).isEqualTo(HttpApiApplicationOutcome.UNKNOWN);
    }

    @Test
    void acceptanceCase_statusErrorResponseConnectWithAdmin() {
        JsonNode n = json("{\"status\": \"Error\", \"response\": \"Connect With Admin\"}");
        var r = HttpApiResponseInterpreter.interpret(n);
        assertThat(r.outcome()).isEqualTo(HttpApiApplicationOutcome.FAILURE);
        assertThat(r.userMessage()).isEqualTo("Connect With Admin");
        // Ensure arbitrary string containing error does not misclassify — this is explicit status field, so it IS failure
    }
}
