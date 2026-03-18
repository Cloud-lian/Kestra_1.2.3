package io.kestra.core.ai.tool.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.kestra.core.models.HasUID;
import io.kestra.core.utils.IdUtils;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.constraints.Pattern;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents the MCP server configuration for a tenant.
 * <p>
 * One MCP server per tenant. Stores the settings required to serve the
 * MCP {@code initialize} response: server name, instructions, and enabled state.
 */
@SuperBuilder(toBuilder = true)
@Getter
@NoArgsConstructor
@Introspected
@ToString
public class McpServer implements HasUID {
    public static final String DEFAULT_SERVER_NAME = "kestra-mcp";

    @Hidden
    @Pattern(regexp = "^[a-z0-9][a-z0-9_-]*")
    private String tenantId;

    @Builder.Default
    private boolean enabled = false;

    /**
     * The server name advertised in the MCP {@code initialize} response.
     * Defaults to {@value #DEFAULT_SERVER_NAME}.
     */
    @Nullable
    @Builder.Default
    private String serverName = DEFAULT_SERVER_NAME;

    /**
     * Optional instructions sent to MCP clients in the {@code initialize} response.
     * Tells AI agents how to use this server's tools.
     */
    @Nullable
    private String instructions;

    @Hidden
    private Instant created;

    @Hidden
    private Instant updated;

    private static final String UID_SUFFIX = "mcp-server";

    @Override
    @JsonIgnore
    public String uid() {
        return IdUtils.fromParts(tenantId, UID_SUFFIX);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        McpServer that = (McpServer) o;
        return enabled == that.enabled &&
            Objects.equals(tenantId, that.tenantId) &&
            Objects.equals(serverName, that.serverName) &&
            Objects.equals(instructions, that.instructions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, enabled, serverName, instructions);
    }
}
