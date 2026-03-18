package io.kestra.core.ai.tool.services;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.ai.tool.models.Tool;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.ai.tool.repositories.ToolRepositoryInterface;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;

/**
 * Service for managing Tools (flows exposed as AI tools).
 */
@Singleton
@Slf4j
public class ToolService {
    @Inject
    private ToolRepositoryInterface toolRepository;

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Inject
    private ModelValidator modelValidator;

    /**
     * Validates that the referenced flow exists.
     *
     * @param tenantId the tenant ID.
     * @param tool     the tool to validate.
     * @throws IllegalArgumentException if the flow doesn't exist.
     */
    public void validateFlowReference(String tenantId, Tool tool) {
        Optional<Flow> flow = flowRepository.findById(tenantId, tool.getNamespace(), tool.getFlowId());
        if (flow.isEmpty()) {
            throw new IllegalArgumentException(
                "Flow '" + tool.getFlowId() + "' in namespace '" + tool.getNamespace() + "' does not exist"
            );
        }
    }

    /**
     * Finds all enabled tools for a tenant.
     *
     * @param tenantId the tenant ID.
     * @return list of enabled tools.
     */
    public List<Tool> findEnabledTools(String tenantId) {
        return toolRepository.findAllEnabled(tenantId);
    }
}
