package io.kestra.repository.postgres;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.utils.Either;
import io.kestra.jdbc.AbstractJdbcRepository;
import org.jooq.Condition;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;

import java.util.*;
import java.util.stream.Collectors;

public abstract class PostgresExecutionRepositoryService {
    public static Condition findCondition(AbstractJdbcRepository<Execution> jdbcRepository, String query, Map<String, String> labels) {
        List<Condition> conditions = new ArrayList<>();

        if (query != null) {
            conditions.add(jdbcRepository.fullTextCondition(Collections.singletonList("fulltext"), query));
        }

        if (labels != null)  {
            labels.forEach((key, value) -> {
                String sql = "value -> 'labels' @> '[{\"key\":\"" + key + "\", \"value\":\"" + value + "\"}]'";
                conditions.add(DSL.condition(sql));
            });
        }

        return conditions.isEmpty() ? DSL.trueCondition() : DSL.and(conditions);
    }

    public static Condition findLabelCondition(Either<Map<?, ?>, String> input, QueryFilter.Op operation) {
        List<Condition> conditions = new ArrayList<>();
        List<Condition> inConditions = new ArrayList<>();
        if (input.isRight()) {
            var query = input.right().get();
            if (Objects.requireNonNull(operation) == QueryFilter.Op.CONTAINS) {
                String sql = "EXISTS (" +
                    " SELECT 1 FROM jsonb_array_elements(COALESCE(value -> 'labels', '[]'::jsonb)) AS lbl" +
                    " WHERE lower(lbl ->> 'value') LIKE lower('%' || ? || '%')" +
                    "    OR lower(lbl ->> 'key') LIKE lower('%' || ? || '%')" +
                    ")";
                conditions.add(DSL.condition(sql, query, query));
            } else {
                throw new UnsupportedOperationException("Unsupported operation for query: " + operation);
            }
        } else {
            var labels = input.getLeft();
            labels.forEach((key, value) -> {
                String sql = "value -> 'labels' @> '[{\"key\":\"" + key + "\", \"value\":\"" + value + "\"}]'";
                switch (operation) {
                    case EQUALS -> conditions.add(DSL.condition(sql));
                    case NOT_EQUALS, NOT_IN -> conditions.add(DSL.not(DSL.condition(sql)));
                    case IN -> inConditions.add(DSL.condition(sql));
                    default -> throw new UnsupportedOperationException("Unsupported operation: " + operation);
                }
            });
        }

        if (!inConditions.isEmpty()) {
            conditions.add(DSL.or(inConditions));
        }
        return conditions.isEmpty() ? DSL.trueCondition() : DSL.and(conditions);
    }

    public static Condition findOutputCondition(Either<Map<?, ?>, String> input, QueryFilter.Op operation) {
        return findIoCondition(input, operation, "outputs");
    }

    public static Condition findInputCondition(Either<Map<?, ?>, String> input, QueryFilter.Op operation) {
        return findIoCondition(input, operation, "inputs");
    }

    private static Condition findIoCondition(Either<Map<?, ?>, String> input, QueryFilter.Op operation, String fieldName) {
        List<Condition> conditions = new ArrayList<>();
        List<Condition> inConditions = new ArrayList<>();
        if (input.isRight()) {
            var query = input.right().get();
            switch (Objects.requireNonNull(operation)) {
                case CONTAINS -> conditions.add(DSL.condition(
                    "EXISTS (" +
                        " SELECT 1 FROM jsonb_each_text(COALESCE(value -> '" + fieldName + "', '{}'::jsonb)) AS kv" +
                        " WHERE lower(kv.value) LIKE lower('%' || ? || '%')" +
                        "    OR lower(kv.key) LIKE lower('%' || ? || '%')" +
                        ")",
                    query, query
                ));
                case KEY_EQUALS -> conditions.add(DSL.condition("jsonb_exists(value -> '" + fieldName + "', ?)", query));
                case KEY_NOT_EQUALS -> conditions.add(DSL.condition("value -> '" + fieldName + "' IS NULL OR NOT jsonb_exists(value -> '" + fieldName + "', ?)", query));
                default -> throw new UnsupportedOperationException("Unsupported operation for query: " + operation);
            }
        } else {
            var values = input.getLeft();
            values.forEach((key, value) -> {
                if (value instanceof List<?> list) {
                    String jsonArray = toJsonArray(list);

                    String containsSql = "value -> '" + fieldName + "' @> '{\"" + key + "\":" + jsonArray + "}'";
                    String lengthSql = "jsonb_array_length(value -> '" + fieldName + "' -> '" + key + "') = " + list.size();
                    Condition equalsCond = DSL.condition(containsSql + " AND " + lengthSql);
                    switch (operation) {
                        case EQUALS -> conditions.add(equalsCond);
                        case NOT_EQUALS, NOT_IN -> conditions.add(DSL.not(equalsCond));
                        case IN -> inConditions.add(equalsCond);
                        default -> throw new UnsupportedOperationException("Unsupported operation: " + operation);
                    }
                } else {
                    String sql = "value -> '" + fieldName + "' @> '{\"" + key + "\":\"" + value + "\"}'";
                    switch (operation) {
                        case EQUALS -> conditions.add(DSL.condition(sql));
                        case NOT_EQUALS, NOT_IN -> conditions.add(DSL.not(DSL.condition(sql)));
                        case IN -> inConditions.add(DSL.condition(sql));
                        default -> throw new UnsupportedOperationException("Unsupported operation: " + operation);
                    }
                }
            });
        }

        if (!inConditions.isEmpty()) {
            conditions.add(DSL.or(inConditions));
        }
        return conditions.isEmpty() ? DSL.trueCondition() : DSL.and(conditions);
    }

    private static String toJsonArray(List<?> list) {
        String elements = list.stream()
            .map(e -> "\"" + e.toString().replace("\\", "\\\\").replace("\"", "\\\"") + "\"")
            .collect(Collectors.joining(","));
        return "[" + elements + "]";
    }

    public static Condition statesFilter(List<State.Type> state) {
        return DSL.or(state
            .stream()
            .map(Enum::name)
            .map(s -> DSL.field("state_current")
                .eq(DSL.field("CAST(? AS state_type)", SQLDataType.VARCHAR(50).getArrayType(), s)
                ))
            .toList()
        );
    }

}
