package io.kestra.webserver.services;

import io.kestra.core.ai.tool.models.McpServer;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.*;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * Factory that builds MCP JSON-RPC responses using the official MCP SDK types.
 * <p>
 * Since tools are tenant-scoped and dynamically configured in the database,
 * we use the SDK's schema types ({@link McpSchema.Tool}, {@link CallToolResult},
 * {@link JSONRPCResponse}, etc.) for protocol compliance while handling
 * dispatch and tenant resolution in the Micronaut controller layer.
 */
@Singleton
@Slf4j
public class McpServerFactory {
    private static final String JSONRPC_VERSION = "2.0";
    private static final String PROTOCOL_VERSION = "2025-03-26";
    private static final String SERVER_VERSION = "1.0.0";

    @Inject
    private McpService mcpService;

    /**
     * Handle an MCP JSON-RPC request and return the appropriate response.
     * Returns null for notifications (no id).
     */
    public JSONRPCResponse handleRequest(String tenantId, JSONRPCRequest request) {
        try {
            return switch (request.method()) {
                case "initialize" -> handleInitialize(request, tenantId);
                case "ping" -> successResponse(request.id(), Map.of());
                case "tools/list" -> handleToolsList(request, tenantId);
                case "tools/call" -> handleToolsCall(request, tenantId);
                default -> errorResponse(request.id(), ErrorCodes.METHOD_NOT_FOUND,
                    "Method not found: " + request.method());
            };
        } catch (Exception e) {
            log.error("Error handling MCP request method={}", request.method(), e);
            return errorResponse(request.id(), ErrorCodes.INTERNAL_ERROR,
                "Internal error: " + e.getMessage());
        }
    }

    /**
     * Handle an MCP notification (no response expected).
     */
    public void handleNotification(JSONRPCNotification notification) {
        // Notifications like "notifications/initialized" are acknowledged silently
        log.debug("Received MCP notification: {}", notification.method());
    }

    private JSONRPCResponse handleInitialize(JSONRPCRequest request, String tenantId) {
        McpServer mcpServer = mcpService.getMcpServer(tenantId);
        String serverName = mcpServer.getServerName() != null
            ? mcpServer.getServerName()
            : McpServer.DEFAULT_SERVER_NAME;

        InitializeResult result = new InitializeResult(
            PROTOCOL_VERSION,
            ServerCapabilities.builder()
                .tools(true)
                .build(),
            new Implementation(serverName, SERVER_VERSION),
            mcpServer.getInstructions()
        );
        return successResponse(request.id(), result);
    }

    private JSONRPCResponse handleToolsList(JSONRPCRequest request, String tenantId) {
        List<McpSchema.Tool> tools = mcpService.listTools(tenantId);
        ListToolsResult result = new ListToolsResult(tools, null);
        return successResponse(request.id(), result);
    }

    @SuppressWarnings("unchecked")
    private JSONRPCResponse handleToolsCall(JSONRPCRequest request, String tenantId) {
        Map<String, Object> params = request.params() instanceof Map<?, ?>
            ? (Map<String, Object>) request.params()
            : Map.of();

        String name = (String) params.get("name");
        Map<String, Object> arguments = params.get("arguments") instanceof Map<?, ?>
            ? (Map<String, Object>) params.get("arguments")
            : Map.of();

        if (name == null) {
            return errorResponse(request.id(), ErrorCodes.INVALID_PARAMS,
                "Invalid params: missing tool name");
        }

        if (!mcpService.toolExists(tenantId, name)) {
            return errorResponse(request.id(), ErrorCodes.INVALID_PARAMS,
                "Unknown tool: " + name);
        }

        CallToolResult result = mcpService.callTool(tenantId, name, arguments);
        return successResponse(request.id(), result);
    }

    private JSONRPCResponse successResponse(Object id, Object result) {
        return new JSONRPCResponse(JSONRPC_VERSION, id, result, null);
    }

    private JSONRPCResponse errorResponse(Object id, int code, String message) {
        return new JSONRPCResponse(JSONRPC_VERSION, id, null,
            new JSONRPCResponse.JSONRPCError(code, message, null));
    }

    /**
     * MCP standard error codes.
     */
    static final class ErrorCodes {
        static final int PARSE_ERROR = -32700;
        static final int INVALID_REQUEST = -32600;
        static final int METHOD_NOT_FOUND = -32601;
        static final int INVALID_PARAMS = -32602;
        static final int INTERNAL_ERROR = -32603;
    }
}
