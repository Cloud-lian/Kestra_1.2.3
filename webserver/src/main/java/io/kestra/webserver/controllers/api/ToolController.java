package io.kestra.webserver.controllers.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.kestra.core.ai.tool.models.McpServer;
import io.kestra.core.ai.tool.models.Tool;
import io.kestra.core.ai.tool.models.ToolAnnotations;
import io.kestra.core.models.validations.ManualConstraintViolation;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.models.validations.ValidateConstraintViolation;
import io.kestra.core.ai.tool.repositories.McpServerRepositoryInterface;
import io.kestra.core.ai.tool.repositories.ToolRepositoryInterface;
import io.kestra.core.ai.tool.services.ToolService;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.IdUtils;
import io.kestra.webserver.responses.PagedResults;
import io.kestra.webserver.utils.PageableUtils;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Validated
@Controller("/api/v1/{tenant}/tools")
@Slf4j
public class ToolController {
    @Inject
    private ToolRepositoryInterface toolRepository;

    @Inject
    private ToolService toolService;

    @Inject
    protected TenantService tenantService;

    @Inject
    protected McpServerRepositoryInterface mcpServerRepository;

    @Inject
    protected ModelValidator modelValidator;

    @ExecuteOn(TaskExecutors.IO)
    @Get
    @Operation(tags = {"Tools"}, summary = "Search for tools")
    public PagedResults<ToolResponse> searchTools(
        @Parameter(description = "The current page") @QueryValue(defaultValue = "1") @Min(1) int page,
        @Parameter(description = "The current page size") @QueryValue(defaultValue = "10") @Min(1) int size,
        @Parameter(description = "The filter query") @Nullable @QueryValue String q,
        @Parameter(description = "The sort of current page") @Nullable @QueryValue List<String> sort
    ) {
        return PagedResults.of(
            toolRepository.list(PageableUtils.from(page, size, sort), tenantService.resolveTenant(), q)
                .map(ToolResponse::new)
        );
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "{id}")
    @Operation(tags = {"Tools"}, summary = "Get a tool")
    public ToolResponse getTool(
        @Parameter(description = "The tool id") @PathVariable String id
    ) {
        return toolRepository.get(tenantService.resolveTenant(), id)
            .map(ToolResponse::new)
            .orElse(null);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(consumes = MediaType.APPLICATION_JSON)
    @Operation(tags = {"Tools"}, summary = "Create a tool")
    public HttpResponse<ToolResponse> createTool(
        @Body @Valid ApiToolCreateRequest request
    ) throws ConstraintViolationException {
        String tenantId = tenantService.resolveTenant();

        Tool tool = Tool.builder()
            .id(IdUtils.create())
            .tenantId(tenantId)
            .name(request.name())
            .title(request.title())
            .description(request.description())
            .tags(request.tags())
            .namespace(request.namespace())
            .flowId(request.flowId())
            .annotations(request.annotations())
            .enabled(request.enabled() != null ? request.enabled() : true)
            .deleted(false)
            .build();

        modelValidator.validate(tool);
        toolService.validateFlowReference(tenantId, tool);

        return HttpResponse.ok(new ToolResponse(toolRepository.save(null, tool)));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Put(uri = "{id}", consumes = MediaType.APPLICATION_JSON)
    @Operation(tags = {"Tools"}, summary = "Update a tool")
    public HttpResponse<ToolResponse> updateTool(
        @Parameter(description = "The tool id") @PathVariable String id,
        @Body @Valid Tool tool
    ) throws ConstraintViolationException {
        String tenantId = tenantService.resolveTenant();
        Optional<Tool> existing = toolRepository.get(tenantId, id);
        if (existing.isEmpty()) {
            return HttpResponse.status(HttpStatus.NOT_FOUND);
        }

        tool = tool.toBuilder().tenantId(tenantId).deleted(false).build();

        if (!tool.getId().equals(id)) {
            throw new ConstraintViolationException(Set.of(ManualConstraintViolation.of(
                "Illegal tool id update",
                tool,
                Tool.class,
                "tool.id",
                tool.getId()
            )));
        }

        modelValidator.validate(tool);
        toolService.validateFlowReference(tenantId, tool);

        return HttpResponse.ok(new ToolResponse(toolRepository.save(existing.get(), tool)));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Delete(uri = "{id}")
    @Operation(tags = {"Tools"}, summary = "Delete a tool")
    public HttpResponse<Void> deleteTool(
        @Parameter(description = "The tool id") @PathVariable String id
    ) {
        String tenantId = tenantService.resolveTenant();
        try {
            toolRepository.delete(tenantId, id);
            return HttpResponse.status(HttpStatus.NO_CONTENT);
        } catch (IllegalStateException e) {
            return HttpResponse.status(HttpStatus.NOT_FOUND);
        }
    }

    @ExecuteOn(TaskExecutors.IO)
    @Patch(uri = "{id}/toggle", consumes = MediaType.APPLICATION_JSON)
    @Operation(tags = {"Tools"}, summary = "Toggle a tool enabled/disabled")
    public HttpResponse<ToolResponse> toggleTool(
        @Parameter(description = "The tool id") @PathVariable String id,
        @Body ToggleRequest toggleRequest
    ) {
        String tenantId = tenantService.resolveTenant();
        Optional<Tool> existing = toolRepository.get(tenantId, id);
        if (existing.isEmpty()) {
            return HttpResponse.status(HttpStatus.NOT_FOUND);
        }

        Tool updated = existing.get().toBuilder().enabled(toggleRequest.enabled()).build();
        return HttpResponse.ok(new ToolResponse(toolRepository.save(existing.get(), updated)));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "validate", consumes = MediaType.APPLICATION_JSON)
    @Operation(tags = {"Tools"}, summary = "Validate a tool definition")
    public ValidateConstraintViolation validateTool(
        @Body ApiToolCreateRequest request
    ) {
        ValidateConstraintViolation.ValidateConstraintViolationBuilder<?, ?> builder = ValidateConstraintViolation.builder();
        builder.index(0);

        try {
            String tenantId = tenantService.resolveTenant();
            Tool tool = Tool.builder()
                .id("validation-placeholder")
                .tenantId(tenantId)
                .name(request.name())
                .title(request.title())
                .description(request.description())
                .tags(request.tags())
                .namespace(request.namespace())
                .flowId(request.flowId())
                .annotations(request.annotations())
                .enabled(request.enabled() != null ? request.enabled() : true)
                .deleted(false)
                .build();

            modelValidator.validate(tool);
            toolService.validateFlowReference(tenantId, tool);
        } catch (ConstraintViolationException e) {
            builder.constraints(e.getMessage());
        } catch (RuntimeException re) {
            log.error("Unable to validate the tool", re);
            builder.constraints("Unable to validate the tool: " + re.getMessage());
        }

        return builder.build();
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "settings/mcp")
    @Operation(tags = {"Tools"}, summary = "Get MCP server configuration")
    public HttpResponse<McpServer> getMcpServer() {
        String tenantId = tenantService.resolveTenant();
        McpServer mcpServer = mcpServerRepository.get(tenantId)
            .orElse(McpServer.builder().tenantId(tenantId).build());
        return HttpResponse.ok(mcpServer);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "settings/mcp", consumes = MediaType.APPLICATION_JSON)
    @Operation(tags = {"Tools"}, summary = "Save MCP server configuration")
    public HttpResponse<McpServer> saveMcpServer(
        @Body McpServer mcpServer
    ) {
        String tenantId = tenantService.resolveTenant();
        mcpServer = mcpServer.toBuilder().tenantId(tenantId).build();
        return HttpResponse.ok(mcpServerRepository.save(mcpServer));
    }

    /**
     * DTO for creating a tool. Does not include {@code id} (generated server-side)
     * or {@code tenantId}/{@code deleted} (set by the controller).
     */
    @Introspected
    public record ApiToolCreateRequest(
        @NotNull @NotBlank String name,
        @Nullable String title,
        @Nullable String description,
        @Nullable List<String> tags,
        @NotNull @NotBlank String namespace,
        @NotNull @NotBlank String flowId,
        @Nullable @Valid ToolAnnotations annotations,
        @Nullable Boolean enabled
    ) {}

    @Introspected
    public record ToggleRequest(boolean enabled) {}

    @Getter
    public static class ToolResponse {
        @jakarta.validation.constraints.Pattern(regexp = "^[a-z0-9][a-z0-9_-]*")
        private final String tenantId;
        @NotNull
        @NotBlank
        private final String id;
        @NotNull
        @NotBlank
        private final String name;
        private final String title;
        private final String description;
        private final List<String> tags;
        @NotNull
        @NotBlank
        private final String namespace;
        @NotNull
        @NotBlank
        private final String flowId;
        private final ToolAnnotations annotations;
        private final boolean enabled;
        @NotNull
        private final boolean deleted;
        private final Instant created;
        private final Instant updated;

        public ToolResponse(Tool tool) {
            this.tenantId = tool.getTenantId();
            this.id = tool.getId();
            this.name = tool.getName();
            this.title = tool.getTitle();
            this.description = tool.getDescription();
            this.tags = tool.getTags();
            this.namespace = tool.getNamespace();
            this.flowId = tool.getFlowId();
            this.annotations = tool.getAnnotations();
            this.enabled = tool.isEnabled();
            this.deleted = tool.isDeleted();
            this.created = tool.getCreated();
            this.updated = tool.getUpdated();
        }

        @JsonCreator
        public ToolResponse(String tenantId, String id, String name, String title, String description,
                            List<String> tags, String namespace, String flowId, ToolAnnotations annotations,
                            boolean enabled, boolean deleted, Instant created, Instant updated) {
            this.tenantId = tenantId;
            this.id = id;
            this.name = name;
            this.title = title;
            this.description = description;
            this.tags = tags;
            this.namespace = namespace;
            this.flowId = flowId;
            this.annotations = annotations;
            this.enabled = enabled;
            this.deleted = deleted;
            this.created = created;
            this.updated = updated;
        }
    }
}
