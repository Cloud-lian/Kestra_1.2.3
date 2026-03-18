package io.kestra.core.ai.tool.repositories;

import io.kestra.core.ai.tool.models.McpServer;
import jakarta.annotation.Nullable;

import java.util.Optional;

/**
 * Repository for the per-tenant MCP server configuration.
 */
public interface McpServerRepositoryInterface {

    /**
     * Get the MCP server config for the given tenant.
     */
    Optional<McpServer> get(@Nullable String tenantId);

    /**
     * Save (create or update) the MCP server config for the given tenant.
     */
    McpServer save(McpServer mcpServer);

    /**
     * Delete the MCP server config for the given tenant.
     */
    void delete(@Nullable String tenantId);
}
