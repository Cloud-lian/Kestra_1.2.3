package io.kestra.webserver.controllers.api;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.ai.tool.models.McpServer;
import io.kestra.core.ai.tool.repositories.McpServerRepositoryInterface;
import io.kestra.core.tenant.TenantService;
import io.modelcontextprotocol.spec.McpSchema.JSONRPCResponse;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.micronaut.http.HttpRequest.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@KestraTest
class McpControllerTest {
    private static final String MCP_PATH = "/api/v1/main/mcp";

    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Inject
    McpServerRepositoryInterface mcpServerRepository;

    @Inject
    TenantService tenantService;

    @BeforeEach
    void enableMcp() {
        mcpServerRepository.save(McpServer.builder()
            .tenantId(tenantService.resolveTenant())
            .enabled(true)
            .build());
    }

    @Test
    void initialize() {
        JSONRPCResponse response = postMcp(jsonRpcRequest(1, "initialize", Map.of()));

        assertThat(response.id()).isEqualTo(1);
        assertThat(response.error()).isNull();
        assertThat(response.result()).isNotNull();
    }

    @SuppressWarnings("unchecked")
    @Test
    void initializeReflectsPersistedSettings() {
        // Given - save MCP server config with custom server name and instructions
        mcpServerRepository.save(McpServer.builder()
            .tenantId(tenantService.resolveTenant())
            .enabled(true)
            .serverName("my-custom-server")
            .instructions("Use these tools carefully.")
            .build());

        // When
        JSONRPCResponse response = postMcp(jsonRpcRequest(20, "initialize", Map.of()));

        // Then
        assertThat(response.error()).isNull();
        Map<String, Object> result = (Map<String, Object>) response.result();
        assertThat(result.get("instructions")).isEqualTo("Use these tools carefully.");

        Map<String, Object> serverInfo = (Map<String, Object>) result.get("serverInfo");
        assertThat(serverInfo.get("name")).isEqualTo("my-custom-server");
    }

    @Test
    void toolsListEmpty() {
        JSONRPCResponse response = postMcp(jsonRpcRequest(2, "tools/list", Map.of()));

        assertThat(response.id()).isEqualTo(2);
        assertThat(response.error()).isNull();
        assertThat(response.result()).isNotNull();
    }

    @Test
    void unknownMethodReturnsError() {
        JSONRPCResponse response = postMcp(jsonRpcRequest(3, "unknown/method", Map.of()));

        assertThat(response.id()).isEqualTo(3);
        assertThat(response.error()).isNotNull();
        assertThat(response.error().code()).isEqualTo(-32601);
    }

    @Test
    void mcpDisabledReturns404() {
        mcpServerRepository.save(McpServer.builder()
            .tenantId(tenantService.resolveTenant())
            .enabled(false)
            .build());

        Map<String, Object> request = jsonRpcRequest(4, "initialize", Map.of());

        assertThatThrownBy(() -> postMcp(request))
            .isInstanceOf(HttpClientResponseException.class)
            .satisfies(e -> {
                HttpClientResponseException httpException = (HttpClientResponseException) e;
                assertThat(httpException.getStatus().getCode()).isEqualTo(404);
            });
    }

    @Test
    void ping() {
        JSONRPCResponse response = postMcp(jsonRpcRequest(5, "ping", Map.of()));

        assertThat(response.id()).isEqualTo(5);
        assertThat(response.error()).isNull();
        assertThat(response.result()).isNotNull();
    }

    @Test
    void toolsCallUnknownToolReturnsProtocolError() {
        Map<String, Object> params = Map.of("name", "nonexistent-tool", "arguments", Map.of());
        JSONRPCResponse response = postMcp(jsonRpcRequest(6, "tools/call", params));

        assertThat(response.id()).isEqualTo(6);
        assertThat(response.error()).isNotNull();
        assertThat(response.error().code()).isEqualTo(-32602);
        assertThat(response.error().message()).contains("Unknown tool");
    }

    @Test
    void notificationReturnsAccepted() {
        Map<String, Object> notification = new LinkedHashMap<>();
        notification.put("jsonrpc", "2.0");
        notification.put("method", "notifications/initialized");

        HttpResponse<?> response = client.toBlocking().exchange(
            POST(MCP_PATH, notification).contentType(MediaType.APPLICATION_JSON)
        );

        assertThat(response.getStatus().getCode()).isEqualTo(202);
    }

    @Test
    void getReturns405() {
        assertThatThrownBy(() -> client.toBlocking().exchange(
            GET(MCP_PATH).contentType(MediaType.APPLICATION_JSON)
        ))
            .isInstanceOf(HttpClientResponseException.class)
            .satisfies(e -> {
                HttpClientResponseException httpException = (HttpClientResponseException) e;
                assertThat(httpException.getStatus().getCode()).isEqualTo(405);
            });
    }

    @Test
    void deleteReturns405() {
        assertThatThrownBy(() -> client.toBlocking().exchange(
            DELETE(MCP_PATH).contentType(MediaType.APPLICATION_JSON)
        ))
            .isInstanceOf(HttpClientResponseException.class)
            .satisfies(e -> {
                HttpClientResponseException httpException = (HttpClientResponseException) e;
                assertThat(httpException.getStatus().getCode()).isEqualTo(405);
            });
    }

    @Test
    void batchRequestReturnsArrayOfResponses() {
        List<Map<String, Object>> batch = List.of(
            jsonRpcRequest(10, "initialize", Map.of()),
            jsonRpcRequest(11, "ping", Map.of())
        );

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> responses = client.toBlocking().retrieve(
            POST(MCP_PATH, batch).contentType(MediaType.APPLICATION_JSON),
            List.class
        );

        assertThat(responses).hasSize(2);
        assertThat((Object) responses.get(0).get("id")).isEqualTo(10);
        assertThat((Object) responses.get(1).get("id")).isEqualTo(11);
    }

    @Test
    void batchOfOnlyNotificationsReturns202() {
        Map<String, Object> notification1 = new LinkedHashMap<>();
        notification1.put("jsonrpc", "2.0");
        notification1.put("method", "notifications/initialized");

        Map<String, Object> notification2 = new LinkedHashMap<>();
        notification2.put("jsonrpc", "2.0");
        notification2.put("method", "notifications/cancelled");

        List<Map<String, Object>> batch = List.of(notification1, notification2);

        HttpResponse<?> response = client.toBlocking().exchange(
            POST(MCP_PATH, batch).contentType(MediaType.APPLICATION_JSON)
        );

        assertThat(response.getStatus().getCode()).isEqualTo(202);
    }

    private Map<String, Object> jsonRpcRequest(Object id, String method, Map<String, Object> params) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("id", id);
        request.put("method", method);
        request.put("params", params);
        return request;
    }

    private JSONRPCResponse postMcp(Map<String, Object> request) {
        return client.toBlocking().retrieve(
            POST(MCP_PATH, request).contentType(MediaType.APPLICATION_JSON),
            JSONRPCResponse.class
        );
    }
}
