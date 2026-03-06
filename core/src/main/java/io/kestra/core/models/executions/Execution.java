package io.kestra.core.models.executions;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.kestra.core.debug.Breakpoint;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.SoftDeletable;
import io.kestra.core.models.HasUID;
import io.kestra.core.models.Label;
import io.kestra.core.models.TenantInterface;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.ResolvedTask;
import io.kestra.core.queues.event.DispatchEvent;
import io.kestra.core.runners.FlowableUtils;
import io.kestra.core.runners.RunContextLogger;
import io.kestra.core.serializers.ListOrMapOfLabelDeserializer;
import io.kestra.core.serializers.ListOrMapOfLabelSerializer;
import io.kestra.core.services.LabelService;
import io.kestra.core.test.flow.TaskFixture;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.ListUtils;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.chrono.ChronoZonedDateTime;
import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.CRC32;

@Builder(toBuilder = true)
@Slf4j
@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@AllArgsConstructor
@ToString
@EqualsAndHashCode
public class Execution implements SoftDeletable<Execution>, TenantInterface, HasUID, DispatchEvent {

    @With
    @Hidden
    @Pattern(regexp = "^[a-z0-9][a-z0-9_-]*")
    String tenantId;

    @NotNull
    String id;

    @NotNull
    String namespace;

    @NotNull
    String flowId;

    @NotNull
    @With
    Integer flowRevision;

    @With
    List<ExecutionTaskRun> executionTaskRuns;

    @With
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(implementation = Object.class)
    Map<String, Object> inputs;

    @With
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(implementation = Object.class)
    Map<String, Object> outputs;

    @JsonSerialize(using = ListOrMapOfLabelSerializer.class)
    @JsonDeserialize(using = ListOrMapOfLabelDeserializer.class)
    List<Label> labels;

    @With
    @Schema(implementation = Object.class)
    Map<String, Object> variables;

    @NotNull
    State state;

    String parentId;

    String originalId;

    @With
    ExecutionTrigger trigger;

    @NotNull
    @Builder.Default
    boolean deleted = false;

    @With
    ExecutionMetadata metadata;

    @With
    @Nullable
    Instant scheduleDate;

    @NonFinal
    @Setter
    String traceParent;

    @With
    @Nullable
    List<TaskFixture> fixtures;

    @Nullable
    ExecutionKind kind;

    @Nullable
    List<Breakpoint> breakpoints;

    public Execution with(List<TaskRun> taskRunList) {
        return new Execution(
            this.tenantId,
            this.id,
            this.namespace,
            this.flowId,
            this.flowRevision,
            taskRunList.stream().map(ExecutionTaskRun::of).collect(Collectors.toList()),
            this.inputs,
            this.outputs,
            this.labels,
            this.variables,
            this.state,
            this.parentId,
            this.originalId,
            this.trigger,
            this.deleted,
            this.metadata,
            this.scheduleDate,
            this.traceParent,
            this.fixtures,
            this.kind,
            this.breakpoints
        );
    }

    // FIXME remove it at some point
    @JsonIgnore
    public List<TaskRun> getTaskRunList() {
        return ListUtils.emptyOnNull(this.executionTaskRuns).stream().map(it -> it.to(this)).toList();
    }

    @Override
    @JsonIgnore
    public String uid() {
        return id;
    }

    /**
     * Factory method for constructing a new {@link Execution} object for the given {@link Flow}.
     *
     * @param flow The Flow.
     * @param labels The Flow labels.
     * @return a new {@link Execution}.
     */
    public static Execution newExecution(final FlowInterface flow, final List<Label> labels) {
        return newExecution(flow, null, labels, Optional.empty());
    }

    public List<Label> getLabels() {
        return ListUtils.emptyOnNull(this.labels);
    }

    /**
     * Factory method for constructing a new {@link Execution} object for the given {@link Flow} and
     * inputs.
     *
     * @param flow The Flow.
     * @param inputs The Flow's inputs.
     * @param labels The Flow labels.
     * @return a new {@link Execution}.
     */
    public static Execution newExecution(final FlowInterface flow,
        final BiFunction<FlowInterface, Execution, Map<String, Object>> inputs,
        final List<Label> labels,
        final Optional<ZonedDateTime> scheduleDate) {
        return newExecution(flow, inputs, labels, scheduleDate, null);
    }

    /**
     * Factory method for constructing a new {@link Execution} object for the given {@link Flow} and
     * inputs.
     *
     * @param flow The Flow.
     * @param inputs The Flow's inputs.
     * @param labels The Flow labels.
     * @param kind The ExecutionKind.
     *
     * @return a new {@link Execution}.
     */
    public static Execution newExecution(final FlowInterface flow,
                                         final BiFunction<FlowInterface, Execution, Map<String, Object>> inputs,
                                         final List<Label> labels,
                                         final Optional<ZonedDateTime> scheduleDate,
                                         @Nullable final ExecutionKind kind) {
        Execution execution = builder()
            .id(IdUtils.create())
            .tenantId(flow.getTenantId())
            .namespace(flow.getNamespace())
            .flowId(flow.getId())
            .flowRevision(flow.getRevision())
            .state(new State())
            .scheduleDate(scheduleDate.map(ChronoZonedDateTime::toInstant).orElse(null))
            .variables(flow.getVariables())
            .kind(kind)
            .build();

        List<Label> executionLabels = new ArrayList<>(LabelService.labelsExcludingSystem(flow.getLabels()));
        if (labels != null) {
            executionLabels.addAll(labels);
        }
        if (executionLabels.stream().noneMatch(label -> Label.CORRELATION_ID.equals(label.key()))) {
            // add a correlation ID if none exist
            executionLabels.add(new Label(Label.CORRELATION_ID, execution.getId()));
        }
        execution = execution.withLabels(executionLabels);

        if (inputs != null) {
            execution = execution.withInputs(inputs.apply(flow, execution));
        }

        return execution;
    }

    @Override
    public String key() {
        return id;
    }

    public Execution withTaskRunList(List<TaskRun> taskRunList) {
        return new Execution(
            this.tenantId,
            this.id,
            this.namespace,
            this.flowId,
            this.flowRevision,
            taskRunList.stream().map(ExecutionTaskRun::of).collect(Collectors.toList()),
            this.inputs,
            this.outputs,
            this.labels,
            this.variables,
            this.state,
            this.parentId,
            this.originalId,
            this.trigger,
            this.deleted,
            this.metadata,
            this.scheduleDate,
            this.traceParent,
            this.fixtures,
            this.kind,
            this.breakpoints
        );
    }


    /**
     * Customization of Lombok-generated builder.
     */
    public static class ExecutionBuilder {

        /**
         * Enforce unique values of {@link Label} when using the builder.
         *
         * @param labels The labels.
         * @return Deduplicated labels.
         */
        public ExecutionBuilder labels(List<Label> labels) {
            this.labels = Label.deduplicate(labels);
            return this;
        }

        void prebuild() {
            this.originalId = this.id;
            this.metadata = ExecutionMetadata.builder()
                .originalCreatedDate(Instant.now())
                .build();
        }
    }

    public static ExecutionBuilder builder() {
        return new CustomExecutionBuilder();
    }

    private static class CustomExecutionBuilder extends ExecutionBuilder {

        @Override
        public Execution build() {
            this.prebuild();
            return super.build();
        }
    }

    public Execution withState(State.Type state) {
        return new Execution(
            this.tenantId,
            this.id,
            this.namespace,
            this.flowId,
            this.flowRevision,
            this.executionTaskRuns,
            this.inputs,
            this.outputs,
            this.labels,
            this.variables,
            this.state.withState(state),
            this.parentId,
            this.originalId,
            this.trigger,
            this.deleted,
            this.metadata,
            this.scheduleDate,
            this.traceParent,
            this.fixtures,
            this.kind,
            this.breakpoints
        );
    }

    public Execution withLabels(List<Label> labels) {
        return new Execution(
            this.tenantId,
            this.id,
            this.namespace,
            this.flowId,
            this.flowRevision,
            this.executionTaskRuns,
            this.inputs,
            this.outputs,
            Label.deduplicate(labels),
            this.variables,
            this.state,
            this.parentId,
            this.originalId,
            this.trigger,
            this.deleted,
            this.metadata,
            this.scheduleDate,
            this.traceParent,
            this.fixtures,
            this.kind,
            this.breakpoints
        );
    }

    public Execution withTaskRun(TaskRun taskRun) throws InternalException {
        List<ExecutionTaskRun> newTaskRunList = this.executionTaskRuns == null ? new ArrayList<>() : new ArrayList<>(this.executionTaskRuns);

        boolean b = Collections.replaceAll(
            newTaskRunList,
            this.findExecutionTaskRunByTaskRunId(taskRun.getId()),
            ExecutionTaskRun.of(taskRun)
        );

        if (!b) {
            throw new IllegalStateException(
                "Can't replace taskRun '" + taskRun.getId() + "' on execution'" + this.getId()
                    + "'");
        }

        return new Execution(
            this.tenantId,
            this.id,
            this.namespace,
            this.flowId,
            this.flowRevision,
            newTaskRunList,
            this.inputs,
            this.outputs,
            this.labels,
            this.variables,
            this.state,
            this.parentId,
            this.originalId,
            this.trigger,
            this.deleted,
            this.metadata,
            this.scheduleDate,
            this.traceParent,
            this.fixtures,
            this.kind,
            this.breakpoints
        );
    }

    public Execution withExecutionTaskRun(ExecutionTaskRun taskRun) throws InternalException {
        List<ExecutionTaskRun> newTaskRunList = this.executionTaskRuns == null ? new ArrayList<>() : new ArrayList<>(this.executionTaskRuns);

        boolean b = Collections.replaceAll(
            newTaskRunList,
            this.findExecutionTaskRunByTaskRunId(taskRun.id()),
            taskRun
        );

        if (!b) {
            throw new IllegalStateException(
                "Can't replace taskRun '" + taskRun.id() + "' on execution'" + this.getId()
                    + "'");
        }

        return new Execution(
            this.tenantId,
            this.id,
            this.namespace,
            this.flowId,
            this.flowRevision,
            newTaskRunList,
            this.inputs,
            this.outputs,
            this.labels,
            this.variables,
            this.state,
            this.parentId,
            this.originalId,
            this.trigger,
            this.deleted,
            this.metadata,
            this.scheduleDate,
            this.traceParent,
            this.fixtures,
            this.kind,
            this.breakpoints
        );
    }

    public Execution withBreakpoints(List<Breakpoint> newBreakpoints) {
        return new Execution(
            this.tenantId,
            this.id,
            this.namespace,
            this.flowId,
            this.flowRevision,
            this.executionTaskRuns,
            this.inputs,
            this.outputs,
            this.labels,
            this.variables,
            this.state,
            this.parentId,
            this.originalId,
            this.trigger,
            this.deleted,
            this.metadata,
            this.scheduleDate,
            this.traceParent,
            this.fixtures,
            this.kind,
            newBreakpoints
        );
    };

    public Execution addLabel(Label label) {
        List<Label> existingLabel = this.labels == null ? new ArrayList<>(1) : new ArrayList<>(this.labels);
        if (existingLabel.stream().noneMatch(l -> l.key().equals(label.key()))) {
            existingLabel.add(label);
        }

        return withLabels(existingLabel);
    }

    public Execution childExecution(String childExecutionId, List<TaskRun> taskRunList,
        State state) {
        return new Execution(
            this.tenantId,
            childExecutionId != null ? childExecutionId : this.getId(),
            this.namespace,
            this.flowId,
            this.flowRevision,
            taskRunList.stream().map(ExecutionTaskRun::of).collect(Collectors.toList()),
            this.inputs,
            this.outputs,
            this.labels,
            this.variables,
            state,
            childExecutionId != null ? this.getId() : null,
            this.originalId,
            this.trigger,
            this.deleted,
            this.metadata,
            this.scheduleDate,
            this.traceParent,
            this.fixtures,
            this.kind,
            this.breakpoints
        );
    }

    public List<TaskRun> findTaskRunsByTaskId(String id) {
        if (this.executionTaskRuns == null) {
            return Collections.emptyList();
        }

        return this.executionTaskRuns
            .stream()
            .filter(taskRun -> taskRun.taskId().equals(id))
            .map(taskRun -> taskRun.to(this))
            .toList();
    }

    ExecutionTaskRun findExecutionTaskRunByTaskRunId(String id) throws InternalException {
        Optional<ExecutionTaskRun> find = (this.executionTaskRuns == null ? Collections.<ExecutionTaskRun>emptyList()
            : this.executionTaskRuns)
            .stream()
            .filter(taskRun -> taskRun.id().equals(id))
            .findFirst();

        if (find.isEmpty()) {
            throw new InternalException(
                "Can't find taskrun with taskrunId '" + id + "' on execution '" + this.id + "' "
                    + this.toStringState());
        }

        return find.get();
    }

    public TaskRun findTaskRunByTaskRunId(String id) throws InternalException {
        return findExecutionTaskRunByTaskRunId(id).to(this);
    }

    public TaskRun findTaskRunByTaskIdAndValue(String id, List<String> values)
        throws InternalException {
        Optional<ExecutionTaskRun> find = (this.executionTaskRuns == null ? Collections.<ExecutionTaskRun>emptyList()
            : this.executionTaskRuns)
            .stream()
            .filter(taskRun -> taskRun.taskId().equals(id) && findParentsValues(taskRun.to(this), true).equals(values))
            .findFirst();

        if (find.isEmpty()) {
            throw new InternalException(
                "Can't find taskrun with taskrunId '" + id + "' & value '" + values
                    + "' on execution '" + this.id + "' " + this.toStringState());
        }

        return find.get().to(this);
    }

    /**
     * Determine if the current execution is on error &amp; normal tasks Used only from the flow
     *
     * @param resolvedTasks normal tasks
     * @param resolvedErrors errors tasks
     * @param resolvedFinally finally tasks
     * @return the flow we need to follow
     */
    public List<ResolvedTask> findTaskDependingFlowState(
        List<ResolvedTask> resolvedTasks,
        List<ResolvedTask> resolvedErrors,
        List<ResolvedTask> resolvedFinally
    ) {
        return this.findTaskDependingFlowState(resolvedTasks, resolvedErrors, resolvedFinally, null);
    }

    /**
     * Determine if the current execution is on error &amp; normal tasks
     * <p>
     * if the current have errors, return tasks from errors if not, return the normal tasks
     *
     * @param resolvedTasks normal tasks
     * @param resolvedErrors errors tasks
     * @param resolvedFinally finally tasks
     * @param parentTaskRun the parent task
     * @return the flow we need to follow
     */
    public List<ResolvedTask> findTaskDependingFlowState(
        List<ResolvedTask> resolvedTasks,
        @Nullable List<ResolvedTask> resolvedErrors,
        @Nullable List<ResolvedTask> resolvedFinally,
        TaskRun parentTaskRun
    ) {
        return findTaskDependingFlowState(resolvedTasks, resolvedErrors, resolvedFinally, parentTaskRun, null);
    }

    /**
     * Determine if the current execution is on error &amp; normal tasks
     * <p>
     * if the current have errors, return tasks from errors if not, return the normal tasks
     *
     * @param resolvedTasks normal tasks
     * @param resolvedErrors errors tasks
     * @param resolvedFinally finally tasks
     * @param parentTaskRun the parent task
     * @param terminalState the parent task terminal state
     * @return the flow we need to follow
     */
    public List<ResolvedTask> findTaskDependingFlowState(
        List<ResolvedTask> resolvedTasks,
        @Nullable List<ResolvedTask> resolvedErrors,
        @Nullable List<ResolvedTask> resolvedFinally,
        TaskRun parentTaskRun,
        @Nullable State.Type terminalState
    ) {
        resolvedTasks = removeDisabled(resolvedTasks);
        resolvedErrors = removeDisabled(resolvedErrors);
        resolvedFinally = removeDisabled(resolvedFinally);

        List<ExecutionTaskRun> errorsFlow = this.findExecutionTaskRunByTasks(resolvedErrors, parentTaskRun);
        List<ExecutionTaskRun> finallyFlow = this.findExecutionTaskRunByTasks(resolvedFinally, parentTaskRun);

        // finally is already started, just continue these finally
        if (!finallyFlow.isEmpty()) {
            return resolvedFinally == null ? Collections.emptyList() : resolvedFinally;
        }

        // check if the parent task should fail, and there is error tasks so we start them
        if (errorsFlow.isEmpty() && terminalState == State.Type.FAILED) {
            return resolvedErrors == null ? resolvedFinally == null ? Collections.emptyList() : resolvedFinally : resolvedErrors;
        }

        // Check if flow has failed tasks
        if (!errorsFlow.isEmpty() || this.hasFailed(resolvedTasks, parentTaskRun)) {
            // Check if among the failed task, they will be retried
            if (!this.hasFailedNoRetry(resolvedTasks, parentTaskRun) && terminalState != State.Type.FAILED) {
                return Collections.emptyList();
            }

            if (resolvedFinally != null && resolvedErrors != null && !this.isTerminated(resolvedErrors, parentTaskRun)) {
                return resolvedErrors;
            } else if (resolvedFinally == null) {
                return resolvedErrors == null ? Collections.emptyList() : resolvedErrors;
            }
        }

        if (resolvedFinally != null && (
            this.isTerminated(resolvedTasks, parentTaskRun) || this.hasFailedNoRetry(resolvedTasks, parentTaskRun
        ))) {
            return resolvedFinally;
        }

        return resolvedTasks;
    }

    /**
     * Remove disabled tasks from the list of resolved tasks.
     */
    public List<ResolvedTask> removeDisabled(List<ResolvedTask> tasks) {
        if (tasks == null) {
            return null;
        }

        return tasks
            .stream()
            .filter(resolvedTask -> !resolvedTask.getTask().getDisabled())
            .toList();
    }

    public List<ExecutionTaskRun> findExecutionTaskRunByTasks(List<ResolvedTask> resolvedTasks, TaskRun parentTaskRun) {
        if (resolvedTasks == null || this.executionTaskRuns == null) {
            return Collections.emptyList();
        }

        return this
            .executionTaskRuns
            .stream()
            .filter(t -> resolvedTasks
                .stream()
                .anyMatch(
                    resolvedTask -> FlowableUtils.isExecutionTaskRunFor(resolvedTask, t, parentTaskRun))
            )
            .toList();
    }

    public List<TaskRun> findTaskRunByTasks(List<ResolvedTask> resolvedTasks, TaskRun parentTaskRun) {
        return findExecutionTaskRunByTasks(resolvedTasks, parentTaskRun).stream().map(t -> t.to(this)).toList();
    }

    public Optional<TaskRun> findFirstByState(State.Type state) {
        if (this.executionTaskRuns == null) {
            return Optional.empty();
        }

        return this.executionTaskRuns
            .stream()
            .filter(t -> t.state().getCurrent() == state)
            .map(t -> t.to(this))
            .findFirst();
    }

    public Optional<TaskRun> findFirstRunning() {
        if (this.executionTaskRuns == null) {
            return Optional.empty();
        }

        return this.executionTaskRuns
            .stream()
            .filter(t -> t.state().isRunning())
            .map(t -> t.to(this))
            .findFirst();
    }

    /*
     * Using reversed().findFirst() is intended for better performance,
     * as these methods are used heavily.
     * Do not replace it with Streams.findLast() in these methods,
     * as Streams.findLast() performs worse.
     *
     * See: @see <a href="https://github.com/kestra-io/kestra/pull/14385">KESTRA#14385</a>
     */
    public Optional<TaskRun> findLastNotTerminated() {
        if (this.executionTaskRuns == null) {
            return Optional.empty();
        }

        return this.executionTaskRuns
            .reversed()
            .stream()
            .filter(t -> !t.state().isTerminated() || !t.state().isPaused())
            .map(t -> t.to(this))
            .findFirst();
    }


    Optional<ExecutionTaskRun> findLastByState(List<ExecutionTaskRun> taskRuns, State.Type state) {
        return taskRuns
            .reversed()
            .stream()
            .filter(t -> t.state().getCurrent() == state)
            .findFirst();
    }

    public Optional<TaskRun> findLastCreated(List<TaskRun> taskRuns) {
        return taskRuns
            .reversed()
            .stream()
            .filter(t -> t.getState().isCreated())
            .findFirst();
    }

    public Optional<TaskRun> findLastSubmitted(List<TaskRun> taskRuns) {
        return taskRuns
            .reversed()
            .stream()
            .filter(t -> t.getState().getCurrent() == State.Type.SUBMITTED)
            .findFirst();
    }

    public Optional<TaskRun> findLastRunning(List<TaskRun> taskRuns) {
        return taskRuns
            .reversed()
            .stream()
            .filter(t -> t.getState().isRunning())
            .findFirst();
    }

    public Optional<TaskRun> findLastTerminated(List<TaskRun> taskRuns) {
        return taskRuns
            .reversed()
            .stream()
            .filter(t -> t.getState().isTerminated())
            .findFirst();
    }

    public boolean isTerminated(List<ResolvedTask> resolvedTasks) {
        return this.isTerminated(resolvedTasks, null);
    }

    public boolean isTerminated(List<ResolvedTask> resolvedTasks, TaskRun parentTaskRun) {
        long terminatedCount = this
            .findExecutionTaskRunByTasks(resolvedTasks, parentTaskRun)
            .stream()
            .filter(taskRun -> taskRun.state().isTerminated())
            .count();

        return terminatedCount == resolvedTasks.size();
    }

    public boolean hasWarning() {
        return this.executionTaskRuns != null && this.executionTaskRuns
            .stream()
            .anyMatch(taskRun -> taskRun.state().getCurrent() == State.Type.WARNING);
    }

    public boolean hasWarning(List<ResolvedTask> resolvedTasks) {
        return this.hasWarning(resolvedTasks, null);
    }

    public boolean hasWarning(List<ResolvedTask> resolvedTasks, TaskRun parentTaskRun) {
        return this.findExecutionTaskRunByTasks(resolvedTasks, parentTaskRun)
            .stream()
            .anyMatch(taskRun -> taskRun.state().getCurrent() == State.Type.WARNING);
    }

    public boolean hasFailed() {
        return this.executionTaskRuns != null && this.executionTaskRuns
            .stream()
            .anyMatch(taskRun -> taskRun.state().isFailed());
    }

    public boolean hasFailed(List<ResolvedTask> resolvedTasks) {
        return this.hasFailed(resolvedTasks, null);
    }

    public boolean hasFailed(List<ResolvedTask> resolvedTasks, TaskRun parentTaskRun) {
        return this.findExecutionTaskRunByTasks(resolvedTasks, parentTaskRun)
            .stream()
            .anyMatch(taskRun -> taskRun.state().isFailed());
    }

    public boolean hasFailedNoRetry(List<ResolvedTask> resolvedTasks, TaskRun parentTaskRun) {
        return this.findExecutionTaskRunByTasks(resolvedTasks, parentTaskRun)
            .stream()
            // NOTE: we check on isFailed first to avoid the costly shouldBeRetried() method
            .anyMatch(taskRun -> taskRun.state().isFailed() && shouldNotBeRetried(resolvedTasks, parentTaskRun, taskRun));
    }

    private static boolean shouldNotBeRetried(List<ResolvedTask> resolvedTasks, TaskRun parentTaskRun, ExecutionTaskRun taskRun) {
        ResolvedTask resolvedTask = resolvedTasks.stream()
            .filter(t -> t.getTask().getId().equals(taskRun.taskId())).findFirst()
            .orElse(null);
        if (resolvedTask == null) {
            log.warn("Can't find task for taskRun '{}' in parentTaskRun '{}'",
                taskRun.id(), parentTaskRun.getId());
            return false;
        }
        return !taskRun.shouldBeRetried(resolvedTask.getTask().getRetry());
    }

    public boolean hasCreated() {
        return this.executionTaskRuns != null && this.executionTaskRuns
            .stream()
            .anyMatch(taskRun -> taskRun.state().isCreated());
    }

    public boolean hasCreated(List<ResolvedTask> resolvedTasks) {
        return this.hasCreated(resolvedTasks, null);
    }

    public boolean hasCreated(List<ResolvedTask> resolvedTasks, TaskRun parentTaskRun) {
        return this.findExecutionTaskRunByTasks(resolvedTasks, parentTaskRun)
            .stream()
            .anyMatch(taskRun -> taskRun.state().isCreated());
    }

    public boolean hasRunning(List<ResolvedTask> resolvedTasks) {
        return this.hasRunning(resolvedTasks, null);
    }

    public boolean hasRunning(List<ResolvedTask> resolvedTasks, TaskRun parentTaskRun) {
        return this.findExecutionTaskRunByTasks(resolvedTasks, parentTaskRun)
            .stream()
            .anyMatch(taskRun -> taskRun.state().isRunning());
    }

    public State.Type guessFinalState(Flow flow) {
        return this.guessFinalState(ResolvedTask.of(flow.getTasks()), null, false, false);
    }

    public State.Type guessFinalState(List<ResolvedTask> currentTasks, TaskRun parentTaskRun,
        boolean allowFailure, boolean allowWarning) {
        return guessFinalState(currentTasks, parentTaskRun, allowFailure, allowWarning, State.Type.SUCCESS);
    }

    public State.Type guessFinalState(List<ResolvedTask> currentTasks, TaskRun parentTaskRun,
                                      boolean allowFailure, boolean allowWarning, State.Type terminalState) {
        List<ExecutionTaskRun> taskRuns = this.findExecutionTaskRunByTasks(currentTasks, parentTaskRun);
        var state = this
            .findLastByState(taskRuns, State.Type.KILLED)
            .map(taskRun -> taskRun.state().getCurrent())
            .or(() -> this
                .findLastByState(taskRuns, State.Type.FAILED)
                .map(taskRun -> taskRun.state().getCurrent())
            )
            .or(() -> this
                .findLastByState(taskRuns, State.Type.WARNING)
                .map(taskRun -> taskRun.state().getCurrent())
            )
            .or(() -> this
                .findLastByState(taskRuns, State.Type.PAUSED)
                .map(taskRun -> taskRun.state().getCurrent())
            )
            .orElse(terminalState);

        if (state == State.Type.FAILED && allowFailure) {
            if (allowWarning) {
                return State.Type.SUCCESS;
            }
            return State.Type.WARNING;
        }
        if (State.Type.WARNING.equals(state) && allowWarning) {
            return State.Type.SUCCESS;
        }
        return state;
    }

    @JsonIgnore
    public boolean hasTaskRunJoinable(TaskRun taskRun) {
        if (this.executionTaskRuns == null) {
            return true;
        }

        ExecutionTaskRun current = this.executionTaskRuns
            .stream()
            .filter(r -> r.isSame(taskRun))
            .findFirst()
            .orElse(null);

        if (current == null) {
            return true;
        }

        // attempts & retry need to be saved
        if (
            (current.attempts() == null && taskRun.getAttempts() != null) ||
                (current.attempts() != null && taskRun.getAttempts() != null
                    && current.attempts().size() < taskRun.getAttempts().size())
        ) {
            return true;
        }

        // same status
        if (current.state().getCurrent() == taskRun.getState().getCurrent()) {
            return false;
        }

        // failedExecutionFromExecutor call before, so the workerTaskResult
        // don't have changed to failed but taskRunList will contain a failed
        // same for restart, the CREATED status is directly on execution taskrun
        // so we don't changed if current execution is terminated
        if (current.state().isTerminated() && !taskRun.getState().isTerminated()) {
            return false;
        }

        // restart case mostly
        // execution contains more state than taskrun so workerTaskResult is outdated
        if (current.state().getHistories().size() > taskRun.getState().getHistories().size()) {
            return false;
        }

        return true;
    }

    /**
     * Convert an exception on Executor and add log to the current {@code RUNNING} taskRun, on the
     * lastAttempts. If no Attempt is found, we create one (must be nominal case). The executor will
     * catch the {@code FAILED} taskRun emitted and will fail the execution. In the worst case, we
     * FAILED the execution (only from {@link io.kestra.plugin.core.trigger.Flow}).
     *
     * @param e the exception throw from Executor
     * @return a new execution with taskrun failed if possible or execution failed is other case
     */
    public FailedExecutionWithLog failedExecutionFromExecutor(Exception e) {
        if (log.isWarnEnabled()) {
            log.warn(
                "[namespace: {}] [flow: {}] [execution: {}] Flow failed from executor in {} with exception '{}'",
                this.getNamespace(),
                this.getFlowId(),
                this.getId(),
                this.getState().humanDuration(),
                e.getMessage(),
                e
            );
        }

        return this
            .findLastNotTerminated()
            .map(taskRun -> {
                TaskRunAttempt lastAttempt = taskRun.lastAttempt();
                if (lastAttempt == null) {
                    return newAttemptsTaskRunForFailedExecution(taskRun, e);
                } else {
                    return lastAttemptsTaskRunForFailedExecution(taskRun, lastAttempt, e);
                }
            })
            .map(t -> {
                try {
                    return new FailedExecutionWithLog(
                        this.withTaskRun(t.taskRun()),
                        t.logs()
                    );
                } catch (InternalException ex) {
                    return null;
                }
            })
            .orElseGet(() -> new FailedExecutionWithLog(
                    this.state.getCurrent() != State.Type.FAILED ? this.withState(State.Type.FAILED)
                        : this,
                    RunContextLogger.logEntries(loggingEventFromException(e), LogEntry.of(this))
                )
            );
    }

    public Optional<TaskFixture> getFixtureForTaskRun(ExecutionTaskRun taskRun) {
        if (this.fixtures == null) {
            return Optional.empty();
        }

        return this.fixtures.stream()
            .filter(fixture -> Objects.equals(fixture.getId(), taskRun.taskId()) && Objects.equals(fixture.getValue(), taskRun.value()))
            .findFirst();
    }

    /**
     * Create a new attempt for failed worker execution
     *
     * @param taskRun the task run where we need to add an attempt
     * @param e the exception raise
     * @return new taskRun with added attempt
     */
    private FailedTaskRunWithLog newAttemptsTaskRunForFailedExecution(TaskRun taskRun,
        Exception e) {
        return new FailedTaskRunWithLog(
            taskRun
                .withAttempts(
                    Collections.singletonList(TaskRunAttempt.builder()
                        .state(new State())
                        .build()
                        .withState(State.Type.FAILED))
                )
                .withState(State.Type.FAILED),
            RunContextLogger.logEntries(loggingEventFromException(e), LogEntry.of(taskRun, kind))
        );
    }

    /**
     * Add exception log to last attempts
     *
     * @param taskRun the task run where we need to add an attempt
     * @param lastAttempt the lastAttempt found to add
     * @param e the exception raise
     * @return new taskRun with updated attempt with logs
     */
    private FailedTaskRunWithLog lastAttemptsTaskRunForFailedExecution(TaskRun taskRun, TaskRunAttempt lastAttempt, Exception e) {
        TaskRun failed = taskRun
            .withAttempts(
                Stream
                    .concat(
                        taskRun.getAttempts().stream().limit(taskRun.getAttempts().size() - 1),
                        Stream.of(lastAttempt.getState().isFailed() ? lastAttempt : lastAttempt.withState(State.Type.FAILED))
                    )
                    .toList()
            );
        return new FailedTaskRunWithLog(
            failed.getState().isFailed() ? failed : failed.withState(State.Type.FAILED),
            RunContextLogger.logEntries(loggingEventFromException(e), LogEntry.of(taskRun, kind))
        );
    }

    public record FailedTaskRunWithLog(
        TaskRun taskRun,
        List<LogEntry> logs) {
    }

    public record FailedExecutionWithLog(
        Execution execution,
        List<LogEntry> logs) {
    }

    /**
     * Transform an exception to {@link ILoggingEvent}
     *
     * @param e the current exception
     * @return the {@link ILoggingEvent} waited to generate {@link LogEntry}
     */
    public static ILoggingEvent loggingEventFromException(Throwable e) {
        LoggingEvent loggingEvent = new LoggingEvent();
        loggingEvent.setLevel(ch.qos.logback.classic.Level.ERROR);
        loggingEvent.setThrowableProxy(new ThrowableProxy(e));
        loggingEvent.setMessage(e.getMessage());
        loggingEvent.setThreadName(Thread.currentThread().getName());
        loggingEvent.setTimeStamp(Instant.now().toEpochMilli());
        loggingEvent.setLoggerName(Execution.class.getName());

        return loggingEvent;
    }

    /**
     * Find all parents from this {@link TaskRun}. The list is starting from deeper parent and end
     * on the closest parent, so the first element is the task that starts first. This method
     * doesn't return the current tasks.
     *
     * @param taskRun current child
     * @return List of parent {@link TaskRun}
     */
    public List<TaskRun> findParents(TaskRun taskRun) {
        if (taskRun.getParentTaskRunId() == null || this.executionTaskRuns == null) {
            return Collections.emptyList();
        }

        List<TaskRun> result = new ArrayList<>();
        boolean ended = false;
        while (!ended) {
            final TaskRun finalTaskRun = taskRun;
            Optional<TaskRun> find = this.executionTaskRuns
                .stream()
                .filter(t -> t.id().equals(finalTaskRun.getParentTaskRunId()))
                .map(it -> it.to(this))
                .findFirst();

            if (find.isPresent()) {
                result.add(find.get());
                taskRun = find.get();
            } else {
                ended = true;
            }
        }

        Collections.reverse(result);

        return result;
    }

    /**
     * Find all children of this {@link TaskRun}.
     */
    public List<TaskRun> findChildren(ExecutionTaskRun parentTaskRun) {
        return executionTaskRuns.stream()
            .filter(taskRun -> parentTaskRun.id().equals(taskRun.parentTaskRunId()))
            .map(taskRun -> taskRun.to(this))
            .toList();
    }


    public List<String> findParentsValues(TaskRun taskRun, boolean withCurrent) {
        return (withCurrent ?
            Stream.concat(findParents(taskRun).stream(), Stream.of(taskRun)) :
            findParents(taskRun).stream()
        )
            .filter(t -> t.getValue() != null)
            .map(TaskRun::getValue)
            .toList();
    }

    @Override
    public Execution toDeleted() {
        return this.toBuilder()
            .deleted(true)
            .build();
    }

    public String toString(boolean pretty) {
        if (!pretty) {
            return super.toString();
        }

        return "Execution(" +
            "\n  id=" + this.getId() +
            "\n  flowId=" + this.getFlowId() +
            "\n  state=" + this.getState().getCurrent().toString() +
            "\n  taskRunList=" +
            "\n  [" +
            "\n    " +
            (this.executionTaskRuns == null ? "" : this.executionTaskRuns
                .stream()
                .map(t -> t.toPrettyString())
                .collect(Collectors.joining(",\n    "))
            ) +
            "\n  ], " +
            "\n  inputs=" + this.getInputs() +
            "\n)";
    }

    public String toStringState() {
        return "(" +
            "\n  state=" + this.getState().getCurrent().toString() +
            "\n  taskRunList=" +
            "\n  [" +
            "\n    " +
            (this.executionTaskRuns == null ? "" : this.executionTaskRuns
                .stream()
                .map(ExecutionTaskRun::toStringState)
                .collect(Collectors.joining(",\n    "))
            ) +
            "\n  ] " +
            "\n)";
    }

    public Long toCrc32State() {
        CRC32 crc32 = new CRC32();
        crc32.update(this.toStringState().getBytes());

        return crc32.getValue();
    }
}
