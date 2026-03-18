package io.kestra.core.ai.tool.repositories;

import io.kestra.core.ai.tool.models.Tool;
import io.kestra.core.repositories.ArrayListTotal;
import io.micronaut.data.model.Pageable;
import jakarta.annotation.Nullable;

import java.util.List;
import java.util.Optional;

public interface ToolRepositoryInterface {

    /**
     * Gets the total number of Tools across all tenants.
     *
     * @return the total number.
     */
    long countAllForAllTenants();

    Boolean isEnabled();

    Optional<Tool> get(String tenantId, String id);

    ArrayListTotal<Tool> list(Pageable pageable, String tenantId, @Nullable String query);

    List<Tool> findAll(String tenantId);

    List<Tool> findAllEnabled(String tenantId);

    Tool save(@Nullable Tool previousTool, Tool tool);

    default Tool save(Tool tool) {
        return this.save(null, tool);
    }

    Tool delete(String tenantId, String id);
}
