package io.kestra.core.ai.tool.models;

import io.micronaut.core.annotation.Introspected;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * MCP tool annotations — hints describing tool behavior to clients.
 * <p>
 * All properties are hints and not guaranteed to provide a faithful description
 * of tool behavior. Clients should never make tool use decisions based on
 * annotations received from untrusted servers.
 */
@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Introspected
public class ToolAnnotations {
    /**
     * If true, the tool does not modify its environment.
     */
    @Builder.Default
    private boolean readOnlyHint = false;

    /**
     * If true, the tool may perform destructive updates to its environment.
     * Only meaningful when readOnlyHint is false.
     */
    @Builder.Default
    private boolean destructiveHint = true;

    /**
     * If true, calling the tool repeatedly with the same arguments will have
     * no additional effect on its environment.
     * Only meaningful when readOnlyHint is false.
     */
    @Builder.Default
    private boolean idempotentHint = false;

    /**
     * If true, this tool may interact with an "open world" of external entities.
     * If false, the tool's domain of interaction is closed.
     */
    @Builder.Default
    private boolean openWorldHint = true;
}
