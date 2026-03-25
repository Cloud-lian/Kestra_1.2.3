package io.kestra.plugin.core.flow;

import java.util.List;
import java.util.concurrent.TimeoutException;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueException;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.runners.TestRunnerUtils;
import io.kestra.core.services.TaskOutputService;
import io.micronaut.data.model.Pageable;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import static org.assertj.core.api.Assertions.assertThat;

@Singleton
public class LoopCaseTest {
    @Inject
    private TestRunnerUtils runnerUtils;

    @Inject
    private TaskOutputService taskOutputService;

    @Inject
    private ExecutionRepositoryInterface executionRepository;

    public void loopSerial(String tenantId) throws TimeoutException, QueueException, InternalException {
        // Given / When
        Execution execution = runnerUtils.runOne(tenantId, "io.kestra.tests", "loop-serial");

        // Then
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList()).hasSize(1);
        TaskRun loopTaskRun = execution.getTaskRunList().getFirst();
        assertThat(loopTaskRun.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(taskOutputService.getOutputs(loopTaskRun))
            .containsEntry(Loop.ITERATION_COUNT_OUTPUT, 3)
            .containsEntry(Loop.TERMINATED_ITERATIONS_OUTPUT, 3);

        // 3 loop sub-executions, one per iteration, all with SUCCESS
        List<Execution> subExecutions = loopSubExecutions(tenantId, execution, "loop-serial");
        assertThat(subExecutions).hasSize(3);
        assertThat(subExecutions).allMatch(sub -> sub.getState().getCurrent() == State.Type.SUCCESS);
    }

    public void loopSerialMultipleTasks(String tenantId) throws TimeoutException, QueueException, InternalException {
        // Given / When
        Execution execution = runnerUtils.runOne(tenantId, "io.kestra.tests", "loop-serial-multiple-tasks");

        // Then
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList()).hasSize(1);
        TaskRun loopTaskRun = execution.getTaskRunList().getFirst();
        assertThat(loopTaskRun.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(taskOutputService.getOutputs(loopTaskRun))
            .containsEntry(Loop.ITERATION_COUNT_OUTPUT, 2)
            .containsEntry(Loop.TERMINATED_ITERATIONS_OUTPUT, 2);

        // 2 loop sub-executions, one per iteration, each running 2 child tasks
        List<Execution> subExecutions = loopSubExecutions(tenantId, execution, "loop-serial-multiple-tasks");
        assertThat(subExecutions).hasSize(2);
        assertThat(subExecutions).allMatch(sub -> sub.getState().getCurrent() == State.Type.SUCCESS);
        assertThat(subExecutions).allMatch(sub -> sub.getTaskRunList().size() == 2);
    }

    public void loopFailed(String tenantId) throws TimeoutException, QueueException, InternalException {
        // Given / When
        Execution execution = runnerUtils.runOne(tenantId, "io.kestra.tests", "loop-failed");

        // Then — first failing iteration terminates the loop immediately with transmitFailed=true (default)
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(execution.getTaskRunList()).hasSize(1);
        assertThat(execution.getTaskRunList().getFirst().getState().getCurrent()).isEqualTo(State.Type.FAILED);

        // Only one sub-execution ran before the loop was terminated
        List<Execution> subExecutions = loopSubExecutions(tenantId, execution, "loop-failed");
        assertThat(subExecutions).hasSize(1);
        assertThat(subExecutions.getFirst().getState().getCurrent()).isEqualTo(State.Type.FAILED);
    }

    public void loopTransmitFailedFalse(String tenantId) throws TimeoutException, QueueException, InternalException {
        // Given / When
        Execution execution = runnerUtils.runOne(tenantId, "io.kestra.tests", "loop-failed-no-transmit");

        // Then — all iterations run but failures are not propagated, loop ends with SUCCESS
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList()).hasSize(1);
        TaskRun loopTaskRun = execution.getTaskRunList().getFirst();
        assertThat(loopTaskRun.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(taskOutputService.getOutputs(loopTaskRun))
            .containsEntry(Loop.ITERATION_COUNT_OUTPUT, 3)
            .containsEntry(Loop.TERMINATED_ITERATIONS_OUTPUT, 3);

        // All 3 sub-executions ran and each failed individually (failure not propagated to the loop)
        List<Execution> subExecutions = loopSubExecutions(tenantId, execution, "loop-failed-no-transmit");
        assertThat(subExecutions).hasSize(3);
        assertThat(subExecutions).allMatch(sub -> sub.getState().getCurrent() == State.Type.FAILED);
    }

    public void loopParallelUnlimited(String tenantId) throws TimeoutException, QueueException, InternalException {
        // Given / When — concurrencyLimit: 0 means all 3 iterations start in parallel
        Execution execution = runnerUtils.runOne(tenantId, "io.kestra.tests", "loop-parallel-unlimited");

        // Then
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList()).hasSize(1);
        TaskRun loopTaskRun = execution.getTaskRunList().getFirst();
        assertThat(loopTaskRun.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(taskOutputService.getOutputs(loopTaskRun))
            .containsEntry(Loop.ITERATION_COUNT_OUTPUT, 3)
            .containsEntry(Loop.TERMINATED_ITERATIONS_OUTPUT, 3);

        List<Execution> subExecutions = loopSubExecutions(tenantId, execution, "loop-parallel-unlimited");
        assertThat(subExecutions).hasSize(3);
        assertThat(subExecutions).allMatch(sub -> sub.getState().getCurrent() == State.Type.SUCCESS);
    }

    public void loopParallelEqual(String tenantId) throws TimeoutException, QueueException, InternalException {
        // Given / When — concurrencyLimit equals the number of iterations: all 3 start at once
        Execution execution = runnerUtils.runOne(tenantId, "io.kestra.tests", "loop-parallel-equal");

        // Then
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList()).hasSize(1);
        TaskRun loopTaskRun = execution.getTaskRunList().getFirst();
        assertThat(loopTaskRun.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(taskOutputService.getOutputs(loopTaskRun))
            .containsEntry(Loop.ITERATION_COUNT_OUTPUT, 3)
            .containsEntry(Loop.TERMINATED_ITERATIONS_OUTPUT, 3);

        List<Execution> subExecutions = loopSubExecutions(tenantId, execution, "loop-parallel-equal");
        assertThat(subExecutions).hasSize(3);
        assertThat(subExecutions).allMatch(sub -> sub.getState().getCurrent() == State.Type.SUCCESS);
    }

    public void loopParallelMore(String tenantId) throws TimeoutException, QueueException, InternalException {
        // Given / When — concurrencyLimit (5) exceeds the number of iterations (3): all 3 start at once
        Execution execution = runnerUtils.runOne(tenantId, "io.kestra.tests", "loop-parallel-more");

        // Then
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList()).hasSize(1);
        TaskRun loopTaskRun = execution.getTaskRunList().getFirst();
        assertThat(loopTaskRun.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(taskOutputService.getOutputs(loopTaskRun))
            .containsEntry(Loop.ITERATION_COUNT_OUTPUT, 3)
            .containsEntry(Loop.TERMINATED_ITERATIONS_OUTPUT, 3);

        List<Execution> subExecutions = loopSubExecutions(tenantId, execution, "loop-parallel-more");
        assertThat(subExecutions).hasSize(3);
        assertThat(subExecutions).allMatch(sub -> sub.getState().getCurrent() == State.Type.SUCCESS);
    }

    public void loopParallelLess(String tenantId) throws TimeoutException, QueueException, InternalException {
        // Given / When — concurrencyLimit (2) is less than the number of iterations (4): runs in batches
        Execution execution = runnerUtils.runOne(tenantId, "io.kestra.tests", "loop-parallel-less");

        // Then
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList()).hasSize(1);
        TaskRun loopTaskRun = execution.getTaskRunList().getFirst();
        assertThat(loopTaskRun.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(taskOutputService.getOutputs(loopTaskRun))
            .containsEntry(Loop.ITERATION_COUNT_OUTPUT, 4)
            .containsEntry(Loop.TERMINATED_ITERATIONS_OUTPUT, 4);

        List<Execution> subExecutions = loopSubExecutions(tenantId, execution, "loop-parallel-less");
        assertThat(subExecutions).hasSize(4);
        assertThat(subExecutions).allMatch(sub -> sub.getState().getCurrent() == State.Type.SUCCESS);
    }

    /** Returns the loop sub-executions for the given parent execution, sorted by iteration index. */
    private List<Execution> loopSubExecutions(String tenantId, Execution parentExecution, String flowId) {
        return executionRepository.findByFlowId(tenantId, "io.kestra.tests", flowId, Pageable.UNPAGED)
            .stream()
            .filter(exec -> parentExecution.getId().equals(exec.getParentId()))
            .sorted(java.util.Comparator.comparingInt(exec -> exec.getLoopRun().iteration()))
            .toList();
    }
}
