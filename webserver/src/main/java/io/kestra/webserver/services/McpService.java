package io.kestra.webserver.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.models.Label;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.Input;
import io.kestra.core.models.flows.input.MultiselectInput;
import io.kestra.core.models.flows.input.SelectInput;
import io.kestra.core.ai.tool.models.McpServer;
import io.kestra.core.ai.tool.models.Tool;
import io.kestra.core.ai.tool.models.ToolAnnotations;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.ai.tool.repositories.McpServerRepositoryInterface;
import io.kestra.core.runners.FlowInputOutput;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.services.ExecutionStreamingService;
import io.kestra.core.ai.tool.services.ToolService;
import io.kestra.core.utils.ListUtils;
import io.micronaut.context.annotation.Value;
import io.micronaut.http.sse.Event;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.*;

/**
 * Core MCP (Model Context Protocol) logic for discovering and invoking Kestra tools.
 * Uses the official MCP Java SDK types.
 */
@Singleton
@Slf4j
public class McpService {
    private static final ObjectMapper MAPPER = JacksonMapper.ofJson(false);

    @Inject
    private McpServerRepositoryInterface mcpServerRepository;

    @Inject
    private ToolService toolService;

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Inject
    private FlowInputOutput flowInputOutput;

    @Inject
    private DispatchQueueInterface<Execution> executionQueue;

    @Inject
    private ExecutionStreamingService streamingService;

    @Value("${kestra.mcp.execution-timeout:PT5M}")
    private Duration executionTimeout;

    /**
     * Retrieve the persisted MCP server config for the given tenant, or defaults if none saved.
     */
    public McpServer getMcpServer(String tenantId) {
        return mcpServerRepository.get(tenantId)
            .orElseGet(() -> McpServer.builder().tenantId(tenantId).build());
    }

    /**
     * Check if MCP is enabled for the given tenant.
     */
    public boolean isMcpEnabled(String tenantId) {
        return getMcpServer(tenantId).isEnabled();
    }

    /**
     * List all enabled tools as MCP SDK Tool definitions.
     */
    public List<McpSchema.Tool> listTools(String tenantId) {
        List<Tool> tools = toolService.findEnabledTools(tenantId);
        List<McpSchema.Tool> result = new ArrayList<>();

        for (Tool tool : tools) {
            Optional<Flow> flowOpt = flowRepository.findById(tenantId, tool.getNamespace(), tool.getFlowId());
            if (flowOpt.isEmpty()) {
                log.warn("Tool '{}' references non-existent flow '{}/{}', skipping", tool.getName(), tool.getNamespace(), tool.getFlowId());
                continue;
            }

            Flow flow = flowOpt.get();
            McpSchema.JsonSchema inputSchema = buildInputSchema(flow);
            McpSchema.Tool.Builder toolBuilder = McpSchema.Tool.builder()
                .name(tool.getName())
                .title(tool.getTitle())
                .description(tool.getDescription())
                .inputSchema(inputSchema);

            if (tool.getAnnotations() != null) {
                toolBuilder.annotations(toMcpAnnotations(tool.getAnnotations()));
            }

            result.add(toolBuilder.build());
        }

        return result;
    }

    /**
     * Check if a tool with the given name exists and is enabled.
     */
    public boolean toolExists(String tenantId, String toolName) {
        return toolService.findEnabledTools(tenantId).stream()
            .anyMatch(t -> t.getName().equals(toolName));
    }

    private McpSchema.ToolAnnotations toMcpAnnotations(ToolAnnotations annotations) {
        return new McpSchema.ToolAnnotations(
            null, // title — already set at Tool level
            annotations.isReadOnlyHint(),
            annotations.isDestructiveHint(),
            annotations.isIdempotentHint(),
            annotations.isOpenWorldHint(),
            null  // returnDirect
        );
    }

    /**
     * Call a tool by name: execute the underlying flow, wait for completion, return outputs.
     */
    public CallToolResult callTool(String tenantId, String toolName, Map<String, Object> arguments) {
        List<Tool> tools = toolService.findEnabledTools(tenantId);
        Tool tool = tools.stream()
            .filter(t -> t.getName().equals(toolName))
            .findFirst()
            .orElse(null);

        if (tool == null) {
            return CallToolResult.builder()
                .addTextContent("Tool not found: " + toolName)
                .isError(true)
                .build();
        }

        Optional<Flow> flowOpt = flowRepository.findById(tenantId, tool.getNamespace(), tool.getFlowId());
        if (flowOpt.isEmpty()) {
            return CallToolResult.builder()
                .addTextContent("Flow not found for tool: " + toolName)
                .isError(true)
                .build();
        }

        Flow flow = flowOpt.get();
        Map<String, Object> inputs = arguments != null ? arguments : Map.of();

        // Create execution
        Execution execution = Execution.newExecution(
            flow,
            null,
            List.of(new Label("system_mcp", "true")),
            Optional.empty()
        );

        // Resolve inputs through FlowInputOutput
        Map<String, Object> resolvedInputs;
        try {
            resolvedInputs = flowInputOutput.readExecutionInputs(flow, execution, inputs);
        } catch (Exception e) {
            return CallToolResult.builder()
                .addTextContent("Input validation failed: " + e.getMessage())
                .isError(true)
                .build();
        }

        execution = execution.withInputs(resolvedInputs);

        try {
            executionQueue.emit(execution);
        } catch (QueueException e) {
            return CallToolResult.builder()
                .addTextContent("Failed to queue execution: " + e.getMessage())
                .isError(true)
                .build();
        }

        // Wait for execution completion
        String subscriberId = UUID.randomUUID().toString();
        final Execution emittedExecution = execution;
        try {
            Execution completed = Flux.<Event<Execution>>create(emitter ->
                    streamingService.registerSubscriber(
                        emittedExecution.getId(),
                        subscriberId,
                        emitter,
                        flow
                    )
                )
                .last()
                .map(Event::getData)
                .timeout(executionTimeout)
                .doFinally(signalType -> streamingService.unregisterSubscriber(emittedExecution.getId(), subscriberId))
                .block();

            if (completed == null) {
                return CallToolResult.builder()
                    .addTextContent("Execution completed but no result returned")
                    .isError(true)
                    .build();
            }

            if (completed.getState().isFailed()) {
                return CallToolResult.builder()
                    .addTextContent("Execution failed with state: " + completed.getState().getCurrent())
                    .isError(true)
                    .build();
            }

            // Return outputs as JSON text content
            Map<String, Object> outputs = completed.getOutputs();
            String outputText;
            if (outputs == null || outputs.isEmpty()) {
                outputText = "Execution completed successfully with no outputs.";
            } else {
                try {
                    outputText = MAPPER.writeValueAsString(outputs);
                } catch (JsonProcessingException e) {
                    outputText = outputs.toString();
                }
            }

            return CallToolResult.builder()
                .addTextContent(outputText)
                .build();
        } catch (Exception e) {
            streamingService.unregisterSubscriber(emittedExecution.getId(), subscriberId);
            return CallToolResult.builder()
                .addTextContent("Execution error: " + e.getMessage())
                .isError(true)
                .build();
        }
    }

    /**
     * Build MCP SDK JsonSchema for the flow's inputs, skipping FILE inputs.
     */
    McpSchema.JsonSchema buildInputSchema(Flow flow) {
        List<Input<?>> inputs = ListUtils.emptyOnNull(flow.getInputs());

        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        for (Input<?> input : inputs) {
            // Skip FILE inputs (not usable via MCP)
            if (input.getType() == io.kestra.core.models.flows.Type.FILE) {
                continue;
            }

            Map<String, Object> prop = inputToJsonSchema(input);
            if (input.getDescription() != null) {
                prop.put("description", input.getDescription());
            }
            properties.put(input.getId(), prop);

            if (Boolean.TRUE.equals(input.getRequired())) {
                required.add(input.getId());
            }
        }

        return new McpSchema.JsonSchema("object", properties, required.isEmpty() ? null : required, null, null, null);
    }

    private Map<String, Object> inputToJsonSchema(Input<?> input) {
        Map<String, Object> schema = new LinkedHashMap<>();

        switch (input.getType()) {
            case STRING, DURATION, SECRET -> schema.put("type", "string");
            case INT -> schema.put("type", "integer");
            case FLOAT -> schema.put("type", "number");
            case BOOL -> schema.put("type", "boolean");
            case DATE -> {
                schema.put("type", "string");
                schema.put("format", "date");
            }
            case DATETIME -> {
                schema.put("type", "string");
                schema.put("format", "date-time");
            }
            case TIME -> {
                schema.put("type", "string");
                schema.put("format", "time");
            }
            case JSON, YAML -> schema.put("type", "object");
            case URI -> {
                schema.put("type", "string");
                schema.put("format", "uri");
            }
            case EMAIL -> {
                schema.put("type", "string");
                schema.put("format", "email");
            }
            case SELECT -> {
                schema.put("type", "string");
                if (input instanceof SelectInput selectInput && selectInput.getValues() != null) {
                    schema.put("enum", selectInput.getValues());
                }
            }
            case MULTISELECT -> {
                schema.put("type", "array");
                Map<String, Object> items = new LinkedHashMap<>();
                items.put("type", "string");
                if (input instanceof MultiselectInput msInput && msInput.getValues() != null) {
                    items.put("enum", msInput.getValues());
                }
                schema.put("items", items);
            }
            case ARRAY -> schema.put("type", "array");
            default -> schema.put("type", "string");
        }

        return schema;
    }
}
