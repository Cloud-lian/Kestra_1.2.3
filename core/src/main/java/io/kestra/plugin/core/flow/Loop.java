package io.kestra.plugin.core.flow;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKind;
import io.kestra.core.models.executions.NextTaskRun;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.hierarchies.GraphCluster;
import io.kestra.core.models.hierarchies.RelationType;
import io.kestra.core.models.tasks.FlowableTask;
import io.kestra.core.models.tasks.ResolvedTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.FlowableUtils;
import io.kestra.core.runners.RunContext;
import io.kestra.core.utils.GraphUtils;
import io.kestra.core.utils.MapUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "TODO",
    description = """
        TODO"""
)
@Plugin
public class Loop extends Task implements FlowableTask<Loop.Output> {
    public static final String ITERATION_COUNT_OUTPUT = "iterationCount";
    public static final String RUNNING_ITERATIONS_OUTPUT = "runningIterations";
    public static final String TERMINATED_ITERATIONS_OUTPUT = "terminatedIterations";

    @Valid
    protected List<Task> errors;

    @Valid
    @JsonProperty("finally")
    @Getter(AccessLevel.NONE)
    protected List<Task> _finally;

    public List<Task> getFinally() {
        return this._finally;
    }

    @Valid
    @PluginProperty
    @NotEmpty(message = "The 'tasks' property cannot be empty")
    private List<Task> tasks;

    // TODO support URI directly
    @NotNull
    @PluginProperty(dynamic = true)
    @Schema(
        title = "The list of values for which Kestra will execute a group of tasks",
        description = """
            Values can be defined as:
            - A list of objects, individual objects will be coalesce to strings
            - A string which will be deserialized as a JSON array""",
        oneOf = { String.class, Object[].class }
    )
    private Object values;

    @PositiveOrZero
    @NotNull
    @Builder.Default
    @Schema(
        title = "The number of concurrent task groups for each value in the `values` array",
        description = """
            A `concurrencyLimit` of 0 means no limit — all task groups run in parallel.

            A `concurrencyLimit` of 1 means full serialization — only one task group runs at a time, in order.

            A `concurrencyLimit` greater than 1 allows up to the specified number of task groups to run in parallel.
            """
    )
    @PluginProperty
    private final Integer concurrencyLimit = 1;

    @Builder.Default
    @Schema(
        title = "Flag specifying whether to fail the current task if any loop iteration fails or is killed."
    )
    @PluginProperty
    private final Boolean transmitFailed = true;

    // FIXME there are a lot of duplication with Sequential but as it needs to return a different ouput it cannot extend it

    @Override
    public GraphCluster tasksTree(Execution execution, TaskRun taskRun, List<String> parentValues) throws IllegalVariableEvaluationException {
        GraphCluster subGraph = new GraphCluster(this, taskRun, parentValues, RelationType.DYNAMIC);

        // Loop executes task groups concurrently, not the task inside the group concurrently,
        // so the topology should display it as a sequential.
        GraphUtils.sequential(
            subGraph,
            this.getTasks(),
            this.getErrors(),
            this.getFinally(),
            taskRun,
            execution
        );

        return subGraph;
    }

    @Override
    public List<Task> allChildTasks() {
        return Stream
            .concat(
                this.getTasks() != null ? this.getTasks().stream() : Stream.empty(),
                Stream.concat(
                    this.getErrors() != null ? this.getErrors().stream() : Stream.empty(),
                    this.getFinally() != null ? this.getFinally().stream() : Stream.empty()
                )
            )
            .toList();
    }

    @Override
    public List<ResolvedTask> childTasks(RunContext runContext, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        return FlowableUtils.resolveTasks(this.getTasks(), parentTaskRun);
    }

    @Override
    public Optional<State.Type> resolveState(RunContext runContext, Execution execution, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        List<ResolvedTask> childTasks = this.childTasks(runContext, parentTaskRun);

        return FlowableUtils.resolveSequentialState(
            execution,
            childTasks,
            FlowableUtils.resolveTasks(this.getErrors(), parentTaskRun),
            FlowableUtils.resolveTasks(this.getFinally(), parentTaskRun),
            parentTaskRun,
            runContext,
            this.isAllowFailure(),
            this.isAllowWarning()
        );
    }

    @Override
    public List<NextTaskRun> resolveNexts(RunContext runContext, Execution execution, TaskRun parentTaskRun) throws IllegalVariableEvaluationException {
        if (execution.getKind() != ExecutionKind.LOOP) {
            // We are in the main execution, not a loop iteration execution,
            // so we don't resolve any next tasks to avoid executing subtasks in the main execution.
            return Collections.emptyList();
        }

        return FlowableUtils.resolveSequentialNexts(
            execution,
            this.childTasks(runContext, parentTaskRun),
            FlowableUtils.resolveTasks(this.errors, parentTaskRun),
            FlowableUtils.resolveTasks(this._finally, parentTaskRun),
            parentTaskRun
        );
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "The counter of iterations for each loop branch execution"
        )
        private Integer iterations;
    }

    @Override
    public Output outputs(RunContext runContext) throws Exception {
        var currentOutputs = runContext.currentOutput();
        if (!MapUtils.isEmpty(currentOutputs) && currentOutputs.containsKey("iterations")) {
            Integer iterations = (Integer) currentOutputs.get("iterations");
            return Output.builder().iterations(iterations).build();
        } else {
            return Output.builder().iterations(0).build();
        }
    }
}
