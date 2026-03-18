package io.kestra.repository.postgres;

import io.kestra.core.events.CrudEvent;
import io.kestra.core.ai.tool.models.Tool;
import io.kestra.jdbc.repository.AbstractJdbcToolRepository;
import io.micronaut.context.event.ApplicationEventPublisher;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jooq.Condition;

@Singleton
@PostgresRepositoryEnabled
public class PostgresToolRepository extends AbstractJdbcToolRepository {
    @Inject
    public PostgresToolRepository(@Named("tools") PostgresRepository<Tool> repository,
                                  ApplicationEventPublisher<CrudEvent<Tool>> eventPublisher) {
        super(repository, eventPublisher);
    }

    @Override
    protected Condition findCondition(String query) {
        return PostgresToolRepositoryService.findCondition(this.jdbcRepository, query);
    }
}
