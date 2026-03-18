package io.kestra.repository.h2;

import io.kestra.core.ai.tool.models.McpServer;
import io.kestra.jdbc.repository.AbstractJdbcMcpServerRepository;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@H2RepositoryEnabled
public class H2McpServerRepository extends AbstractJdbcMcpServerRepository {
    @Inject
    public H2McpServerRepository(@Named("mcp_servers") H2Repository<McpServer> repository) {
        super(repository);
    }
}
