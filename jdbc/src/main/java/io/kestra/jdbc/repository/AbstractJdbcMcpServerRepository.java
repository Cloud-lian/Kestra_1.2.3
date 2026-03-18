package io.kestra.jdbc.repository;

import io.kestra.core.ai.tool.models.McpServer;
import io.kestra.core.ai.tool.repositories.McpServerRepositoryInterface;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.impl.DSL;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Abstract JDBC repository for per-tenant MCP server configuration.
 * <p>
 * Unlike most entities, {@link McpServer} has no {@code deleted} column,
 * so {@code defaultFilter} is overridden to skip the deleted check.
 */
@Slf4j
public abstract class AbstractJdbcMcpServerRepository extends AbstractJdbcCrudRepository<McpServer> implements McpServerRepositoryInterface {

    public AbstractJdbcMcpServerRepository(io.kestra.jdbc.AbstractJdbcRepository<McpServer> jdbcRepository) {
        super(jdbcRepository);
    }

    @Override
    protected Condition defaultFilter(String tenantId) {
        return buildTenantCondition(tenantId);
    }

    @Override
    protected Condition defaultFilter(String tenantId, boolean allowDeleted) {
        return buildTenantCondition(tenantId);
    }

    @Override
    public Optional<McpServer> get(String tenantId) {
        return findOne(tenantId, DSL.trueCondition());
    }

    @Override
    public McpServer save(McpServer mcpServer) {
        Optional<McpServer> existing = get(mcpServer.getTenantId());

        if (existing.isPresent()) {
            if (existing.get().equals(mcpServer)) {
                return existing.get();
            }
            mcpServer = mcpServer.toBuilder()
                .created(existing.get().getCreated())
                .updated(Instant.now())
                .build();
        } else {
            mcpServer = mcpServer.toBuilder()
                .created(Instant.now())
                .updated(Instant.now())
                .build();
        }

        Map<Field<Object>, Object> fields = this.jdbcRepository.persistFields(mcpServer);
        this.jdbcRepository.persist(mcpServer, fields);

        return mcpServer;
    }

    @Override
    public void delete(String tenantId) {
        Optional<McpServer> existing = get(tenantId);
        existing.ifPresent(mcpServer ->
            this.jdbcRepository.delete(mcpServer)
        );
    }
}
