package io.kestra.core.ai.tool.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.kestra.core.models.HasUID;
import io.kestra.core.models.SoftDeletable;
import io.kestra.core.utils.IdUtils;
import io.micronaut.core.annotation.Introspected;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Represents a Kestra flow exposed as an AI tool via the MCP protocol.
 * <p>
 * The {@code name} field is the MCP tool identifier used in {@code tools/call}.
 * The {@code title} field is an optional human-readable display name.
 */
@SuperBuilder(toBuilder = true)
@Getter
@NoArgsConstructor
@Introspected
@ToString
public class Tool implements HasUID, SoftDeletable<Tool> {
    @Hidden
    @Pattern(regexp = "^[a-z0-9][a-z0-9_-]*")
    private String tenantId;

    @NotNull
    @NotBlank
    private String id;

    /**
     * MCP tool name — unique identifier used in {@code tools/call}.
     * Must contain only alphanumeric characters, underscores, and hyphens.
     */
    @NotNull
    @NotBlank
    @Pattern(regexp = "^[a-zA-Z0-9][a-zA-Z0-9_-]*$", message = "Tool name must start with a letter or digit and contain only letters, digits, underscores, or hyphens")
    private String name;

    /**
     * Optional human-readable display name for the tool.
     */
    private String title;

    private String description;

    private List<String> tags;

    @NotNull
    @NotBlank
    private String namespace;

    @NotNull
    @NotBlank
    private String flowId;

    @Valid
    private ToolAnnotations annotations;

    @Builder.Default
    private boolean enabled = true;

    @Hidden
    @NotNull
    @Builder.Default
    private boolean deleted = false;

    @Hidden
    private Instant created;

    @Hidden
    private Instant updated;

    @Override
    @JsonIgnore
    public String uid() {
        return IdUtils.fromParts(tenantId, id);
    }

    @Override
    public Tool toDeleted() {
        return this.toBuilder()
            .deleted(true)
            .build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Tool tool = (Tool) o;
        return enabled == tool.enabled &&
            deleted == tool.deleted &&
            Objects.equals(tenantId, tool.tenantId) &&
            Objects.equals(id, tool.id) &&
            Objects.equals(name, tool.name) &&
            Objects.equals(title, tool.title) &&
            Objects.equals(description, tool.description) &&
            Objects.equals(tags, tool.tags) &&
            Objects.equals(namespace, tool.namespace) &&
            Objects.equals(flowId, tool.flowId) &&
            Objects.equals(annotations, tool.annotations);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, id, name, title, description, tags, namespace, flowId, annotations, enabled, deleted);
    }
}
