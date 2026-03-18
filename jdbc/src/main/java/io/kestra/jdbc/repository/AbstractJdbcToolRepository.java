package io.kestra.jdbc.repository;

import io.kestra.core.events.CrudEvent;
import io.kestra.core.ai.tool.models.Tool;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.ai.tool.repositories.ToolRepositoryInterface;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.data.model.Pageable;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
import org.jooq.Field;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Abstract JDBC repository for Tool entities.
 */
@Slf4j
public abstract class AbstractJdbcToolRepository extends AbstractJdbcCrudRepository<Tool> implements ToolRepositoryInterface {
    private final ApplicationEventPublisher<CrudEvent<Tool>> eventPublisher;

    public AbstractJdbcToolRepository(io.kestra.jdbc.AbstractJdbcRepository<Tool> jdbcRepository,
                                      ApplicationEventPublisher<CrudEvent<Tool>> eventPublisher) {
        super(jdbcRepository);
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Optional<Tool> get(String tenantId, String id) {
        return findOne(tenantId, field("id", String.class).eq(id));
    }

    abstract protected Condition findCondition(String query);

    @Override
    public ArrayListTotal<Tool> list(Pageable pageable, String tenantId, String query) {
        return findPage(pageable, tenantId, this.findCondition(query));
    }

    @Override
    public List<Tool> findAllEnabled(String tenantId) {
        return find(tenantId, field("enabled", Boolean.class).eq(true));
    }

    @Override
    public Tool save(Tool previousTool, Tool tool) {
        if (previousTool != null && previousTool.equals(tool)) {
            return previousTool;
        }

        if (previousTool == null) {
            tool = tool.toBuilder().created(Instant.now()).updated(Instant.now()).build();
        } else {
            tool = tool.toBuilder()
                .id(previousTool.getId())
                .created(previousTool.getCreated())
                .updated(Instant.now())
                .build();
        }

        Map<Field<Object>, Object> fields = this.jdbcRepository.persistFields(tool);
        this.jdbcRepository.persist(tool, fields);
        this.eventPublisher.publishEvent(CrudEvent.of(previousTool, tool));

        return tool;
    }

    @Override
    public Tool delete(String tenantId, String id) {
        Optional<Tool> tool = this.get(tenantId, id);
        if (tool.isEmpty()) {
            throw new IllegalStateException("Tool " + id + " doesn't exist");
        }

        Tool deleted = tool.get().toDeleted();
        Map<Field<Object>, Object> fields = this.jdbcRepository.persistFields(deleted);
        this.jdbcRepository.persist(deleted, fields);
        this.eventPublisher.publishEvent(CrudEvent.delete(tool.get()));

        return deleted;
    }

    @Override
    public Boolean isEnabled() {
        return true;
    }
}
