package io.kestra.repository.h2;

import io.kestra.core.ai.tool.models.Tool;
import io.kestra.jdbc.AbstractJdbcRepository;
import org.jooq.Condition;
import org.jooq.impl.DSL;

import java.util.ArrayList;
import java.util.List;

public abstract class H2ToolRepositoryService {
    public static Condition findCondition(AbstractJdbcRepository<Tool> jdbcRepository, String query) {
        List<Condition> conditions = new ArrayList<>();

        if (query != null) {
            conditions.add(jdbcRepository.fullTextCondition(List.of("fulltext"), query));
        }

        return conditions.isEmpty() ? DSL.trueCondition() : DSL.and(conditions);
    }
}
