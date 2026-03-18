package io.kestra.repository.h2;

import io.kestra.core.events.CrudEvent;
import io.kestra.core.ai.tool.models.Tool;
import io.kestra.jdbc.repository.AbstractJdbcToolRepository;
import io.micronaut.context.event.ApplicationEventPublisher;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jooq.Condition;

@Singleton
@H2RepositoryEnabled
public class H2ToolRepository extends AbstractJdbcToolRepository {
    @Inject
    public H2ToolRepository(@Named("tools") H2Repository<Tool> repository,
                             ApplicationEventPublisher<CrudEvent<Tool>> eventPublisher) {
        super(repository, eventPublisher);
    }

    @Override
    protected Condition findCondition(String query) {
        return H2ToolRepositoryService.findCondition(this.jdbcRepository, query);
    }
}
