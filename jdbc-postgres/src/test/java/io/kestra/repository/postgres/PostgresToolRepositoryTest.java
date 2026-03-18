package io.kestra.repository.postgres;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.ai.tool.models.Tool;
import io.kestra.core.ai.tool.models.ToolAnnotations;
import io.kestra.core.ai.tool.repositories.ToolRepositoryInterface;
import io.micronaut.data.model.Pageable;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@KestraTest
class PostgresToolRepositoryTest {
    @Inject
    ToolRepositoryInterface toolRepository;

    @Test
    void crudOperations() {
        // Given
        Tool tool = Tool.builder()
            .id("test-tool")
            .name("test_tool")
            .title("Test Tool")
            .description("A test tool")
            .namespace("io.kestra.test")
            .flowId("test-flow")
            .annotations(ToolAnnotations.builder().readOnlyHint(true).build())
            .enabled(true)
            .build();

        // When - create
        Tool saved = toolRepository.save(null, tool);

        // Then
        assertThat(saved).isNotNull();
        assertThat(saved.getId()).isEqualTo("test-tool");
        assertThat(saved.getName()).isEqualTo("test_tool");
        assertThat(saved.getTitle()).isEqualTo("Test Tool");
        assertThat(saved.getAnnotations()).isNotNull();
        assertThat(saved.getAnnotations().isReadOnlyHint()).isTrue();
        assertThat(saved.getCreated()).isNotNull();
        assertThat(saved.getUpdated()).isNotNull();

        // When - get
        Optional<Tool> found = toolRepository.get(null, "test-tool");

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("test_tool");
        assertThat(found.get().getTitle()).isEqualTo("Test Tool");

        // When - update
        Tool updated = found.get().toBuilder().title("Updated Tool").build();
        Tool savedUpdate = toolRepository.save(found.get(), updated);

        // Then
        assertThat(savedUpdate.getTitle()).isEqualTo("Updated Tool");

        // When - list
        var list = toolRepository.list(Pageable.from(0, 10), null, null);

        // Then
        assertThat(list).isNotEmpty();
        assertThat(list.getTotal()).isGreaterThanOrEqualTo(1);

        // When - search
        var searchResults = toolRepository.list(Pageable.from(0, 10), null, "Updated");

        // Then
        assertThat(searchResults).isNotEmpty();

        // When - delete
        Tool deleted = toolRepository.delete(null, "test-tool");

        // Then
        assertThat(deleted).isNotNull();
        assertThat(deleted.isDeleted()).isTrue();

        // Verify soft deleted
        Optional<Tool> afterDelete = toolRepository.get(null, "test-tool");
        assertThat(afterDelete).isEmpty();
    }

    @Test
    void findAllEnabled() {
        // Given
        Tool enabledTool = Tool.builder()
            .id("enabled-tool")
            .name("enabled_tool")
            .namespace("io.kestra.test")
            .flowId("test-flow")
            .enabled(true)
            .build();

        Tool disabledTool = Tool.builder()
            .id("disabled-tool")
            .name("disabled_tool")
            .namespace("io.kestra.test")
            .flowId("test-flow")
            .enabled(false)
            .build();

        toolRepository.save(null, enabledTool);
        toolRepository.save(null, disabledTool);

        // When
        List<Tool> enabled = toolRepository.findAllEnabled(null);

        // Then
        assertThat(enabled).extracting(Tool::getId).contains("enabled-tool");
        assertThat(enabled).extracting(Tool::getId).doesNotContain("disabled-tool");

        // Cleanup
        toolRepository.delete(null, "enabled-tool");
        toolRepository.delete(null, "disabled-tool");
    }

    @Test
    void deleteNonExisting() {
        assertThatThrownBy(() -> toolRepository.delete(null, "non-existing"))
            .isInstanceOf(IllegalStateException.class);
    }
}
