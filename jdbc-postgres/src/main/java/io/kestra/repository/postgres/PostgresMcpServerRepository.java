package io.kestra.repository.postgres;

import io.kestra.core.ai.tool.models.McpServer;
import io.kestra.jdbc.repository.AbstractJdbcMcpServerRepository;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@PostgresRepositoryEnabled
public class PostgresMcpServerRepository extends AbstractJdbcMcpServerRepository {
    @Inject
    public PostgresMcpServerRepository(@Named("mcp_servers") PostgresRepository<McpServer> repository) {
        super(repository);
    }
}
