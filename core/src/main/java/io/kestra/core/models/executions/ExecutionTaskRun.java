package io.kestra.core.models.executions;

import io.kestra.core.models.assets.AssetsInOut;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.retrys.AbstractRetry;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

public record ExecutionTaskRun(
    @NotNull String id,
    @NotNull String taskId,
    String parentTaskRunId,
    String value,
    List<TaskRunAttempt> attempts,
    AssetsInOut assets,
    State state,
    Integer iteration,
    Boolean dynamic,
    Boolean forceExecution) {

    public static ExecutionTaskRun of(TaskRun taskRun) {
        return new ExecutionTaskRun(taskRun.getId(), taskRun.getTaskId(), taskRun.getParentTaskRunId(), taskRun.getValue(), taskRun.getAttempts(), taskRun.getAssets(), taskRun.getState(), taskRun.getIteration(), taskRun.getDynamic(), taskRun.getForceExecution());
    }

    public TaskRun to(Execution execution) {
        return new TaskRun(execution.getTenantId(), id, execution.getId(), execution.getNamespace(), execution.getFlowId(), taskId, parentTaskRunId, value, attempts, assets, state, iteration, dynamic, forceExecution);
    }

    boolean isSame(TaskRun taskRun) {
        return this.id().equals(taskRun.getId()) &&
            ((this.value() == null && taskRun.getValue() == null) || (this.value() != null && this.value().equals(taskRun.getValue()))) &&
            ((this.iteration() == null && taskRun.getIteration() == null) || (this.iteration() != null && this.iteration().equals(taskRun.getIteration())));
    }

    String toStringState() {
        return "TaskRun(" +
            "id=" + this.id() +
            ", taskId=" + this.taskId() +
            ", value=" + this.value() +
            ", state=" + this.state().getCurrent().toString() +
            ")";
    }

    String toPrettyString() {
        return "TaskRun(" +
            "id=" + this.id() +
            ", taskId=" + this.taskId() +
            ", value=" + this.value() +
            ", parentTaskRunId=" + this.parentTaskRunId() +
            ", state=" + this.state().getCurrent().toString() +
            ", assets=" + this.assets() +
            ", attempts=" + this.attempts() +
            ")";
    }

    boolean shouldBeRetried(AbstractRetry retry) {
        if (retry == null) {
            return false;
        }
        return this.nextRetryDate(retry) != null;
    }

    public Instant nextRetryDate(AbstractRetry retry) {
        if (this.attempts == null || this.attempts.isEmpty() || (retry.getMaxAttempts() != null && this.attemptNumber() >= retry.getMaxAttempts())) {

            return null;
        }
        Instant base = this.lastAttempt().getState().maxDate();
        Instant nextDate = retry.nextRetryDate(this.attempts.size(), base);
        if (retry.getMaxDuration() != null && nextDate.isAfter(this.attempts.getFirst().getState().minDate().plus(retry.getMaxDuration()))) {

            return null;
        }

        return nextDate;
    }

    int attemptNumber() {
        if (this.attempts == null) {
            return 0;
        }

        return this.attempts.size();
    }

    TaskRunAttempt lastAttempt() {
        if (this.attempts == null || this.attempts.isEmpty()) {
            return null;
        }

        return this.attempts.getLast();
    }

    /**
     * This method is used when the retry is apply on a task
     * but the retry type is NEW_EXECUTION
     *
     * @param retry     Contains the retry configuration
     * @param execution Contains the attempt number and original creation date
     * @return The next retry date, null if maxAttempt || maxDuration is reached
     */
    public Instant nextRetryDate(AbstractRetry retry, Execution execution) {
        if (this.attempts == null || this.attempts.isEmpty() || retry.getMaxAttempts() != null && execution.getMetadata().getAttemptNumber() >= retry.getMaxAttempts()) {
            return null;
        }
        Instant base = this.lastAttempt().getState().maxDate();
        Instant nextDate = retry.nextRetryDate(execution.getMetadata().getAttemptNumber(), base);
        if (retry.getMaxDuration() != null && nextDate.isAfter(execution.getMetadata().getOriginalCreatedDate().plus(retry.getMaxDuration()))) {

            return null;
        }

        return nextDate;
    }

    public ExecutionTaskRun withState(State.Type type) {
        return new ExecutionTaskRun(this.id, this.taskId, this.parentTaskRunId, this.value, this.attempts, this.assets, this.state.withState(type), this.iteration, this.dynamic, this.forceExecution);
    }
}
