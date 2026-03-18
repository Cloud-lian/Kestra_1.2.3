package io.kestra.webserver.controllers.api;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.ai.tool.models.McpServer;
import io.kestra.core.ai.tool.models.Tool;
import io.kestra.core.ai.tool.models.ToolAnnotations;
import io.kestra.core.ai.tool.repositories.ToolRepositoryInterface;
import io.kestra.webserver.controllers.api.ToolController.ApiToolCreateRequest;
import io.kestra.webserver.controllers.api.ToolController.ToolResponse;
import io.kestra.webserver.responses.PagedResults;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.micronaut.http.HttpRequest.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@KestraTest
class ToolControllerTest {
    public static final String TOOLS_PATH = "/api/v1/main/tools";

    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Inject
    ToolRepositoryInterface toolRepository;

    @Test
    @LoadFlows({"flows/valids/minimal.yaml"})
    void crud() {
        // Given - create request DTO referencing the loaded test flow
        ToolAnnotations annotations = ToolAnnotations.builder()
            .readOnlyHint(true)
            .destructiveHint(false)
            .build();

        ApiToolCreateRequest createRequest = new ApiToolCreateRequest(
            "crud_test_tool",
            "CRUD Test Tool",
            "A tool for CRUD testing",
            List.of("test", "crud"),
            "io.kestra.tests",
            "minimal",
            annotations,
            true
        );

        // When - create
        ToolResponse created = client.toBlocking().retrieve(
            POST(TOOLS_PATH, createRequest).contentType(MediaType.APPLICATION_JSON),
            ToolResponse.class
        );

        // Then - id is auto-generated
        assertThat(created).isNotNull();
        assertThat(created.getId()).isNotNull();
        assertThat(created.getId()).isNotBlank();
        assertThat(created.getName()).isEqualTo("crud_test_tool");
        assertThat(created.getTitle()).isEqualTo("CRUD Test Tool");
        assertThat(created.getDescription()).isEqualTo("A tool for CRUD testing");
        assertThat(created.getNamespace()).isEqualTo("io.kestra.tests");
        assertThat(created.getFlowId()).isEqualTo("minimal");
        assertThat(created.isEnabled()).isTrue();
        assertThat(created.getTags()).containsExactly("test", "crud");
        assertThat(created.getAnnotations()).isNotNull();
        assertThat(created.getAnnotations().isReadOnlyHint()).isTrue();
        assertThat(created.getAnnotations().isDestructiveHint()).isFalse();
        assertThat(created.getCreated()).isNotNull();
        assertThat(created.getUpdated()).isNotNull();

        String generatedId = created.getId();

        // When - get by id
        ToolResponse fetched = client.toBlocking().retrieve(
            GET(TOOLS_PATH + "/" + generatedId),
            ToolResponse.class
        );

        // Then
        assertThat(fetched).isNotNull();
        assertThat(fetched.getId()).isEqualTo(generatedId);
        assertThat(fetched.getName()).isEqualTo("crud_test_tool");
        assertThat(fetched.getTitle()).isEqualTo("CRUD Test Tool");

        // When - list
        PagedResults<ToolResponse> list = client.toBlocking().retrieve(
            GET(TOOLS_PATH),
            Argument.of(PagedResults.class, ToolResponse.class)
        );

        // Then
        assertThat(list.getResults()).isNotEmpty();

        // When - update (uses full Tool with id)
        Tool updated = Tool.builder()
            .id(generatedId)
            .name("updated_tool")
            .title("Updated Tool")
            .description("Updated description")
            .namespace("io.kestra.tests")
            .flowId("minimal")
            .enabled(true)
            .build();

        ToolResponse updatedResponse = client.toBlocking().retrieve(
            PUT(TOOLS_PATH + "/" + generatedId, updated).contentType(MediaType.APPLICATION_JSON),
            ToolResponse.class
        );

        // Then
        assertThat(updatedResponse.getName()).isEqualTo("updated_tool");
        assertThat(updatedResponse.getTitle()).isEqualTo("Updated Tool");

        // When - toggle
        ToolResponse toggled = client.toBlocking().retrieve(
            PATCH(TOOLS_PATH + "/" + generatedId + "/toggle", new ToolController.ToggleRequest(false))
                .contentType(MediaType.APPLICATION_JSON),
            ToolResponse.class
        );

        // Then
        assertThat(toggled.isEnabled()).isFalse();

        // When - delete
        HttpResponse<Void> deleted = client.toBlocking().exchange(
            DELETE(TOOLS_PATH + "/" + generatedId)
        );

        // Then
        assertThat(deleted.code()).isEqualTo(204);
    }

    @Test
    void shouldReturnNotFoundForMissingTool() {
        assertThatThrownBy(() -> client.toBlocking().retrieve(
            GET(TOOLS_PATH + "/non-existing"),
            ToolResponse.class
        ))
            .isInstanceOf(HttpClientResponseException.class)
            .satisfies(e -> {
                HttpClientResponseException httpException = (HttpClientResponseException) e;
                assertThat(httpException.getStatus().getCode()).isEqualTo(404);
            });
    }

    @Test
    void mcpServer() {
        // When - save disabled
        McpServer disabled = McpServer.builder().enabled(false).build();
        McpServer savedDisabled = client.toBlocking().retrieve(
            POST(TOOLS_PATH + "/settings/mcp", disabled).contentType(MediaType.APPLICATION_JSON),
            McpServer.class
        );

        // Then
        assertThat(savedDisabled.isEnabled()).isFalse();

        // When - get after save disabled
        McpServer fetchedDisabled = client.toBlocking().retrieve(
            GET(TOOLS_PATH + "/settings/mcp"),
            McpServer.class
        );

        // Then
        assertThat(fetchedDisabled).isNotNull();
        assertThat(fetchedDisabled.isEnabled()).isFalse();

        // When - save enabled with server name and instructions
        McpServer enabled = McpServer.builder()
            .enabled(true)
            .serverName("my-server")
            .instructions("Use tools wisely.")
            .build();
        McpServer saved = client.toBlocking().retrieve(
            POST(TOOLS_PATH + "/settings/mcp", enabled).contentType(MediaType.APPLICATION_JSON),
            McpServer.class
        );

        // Then
        assertThat(saved.isEnabled()).isTrue();
        assertThat(saved.getServerName()).isEqualTo("my-server");
        assertThat(saved.getInstructions()).isEqualTo("Use tools wisely.");

        // When - get after save enabled
        McpServer fetched = client.toBlocking().retrieve(
            GET(TOOLS_PATH + "/settings/mcp"),
            McpServer.class
        );

        // Then
        assertThat(fetched.isEnabled()).isTrue();
        assertThat(fetched.getServerName()).isEqualTo("my-server");
        assertThat(fetched.getInstructions()).isEqualTo("Use tools wisely.");
    }
}
