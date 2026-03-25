package io.kestra.core.models.executions;

public record LoopRun(String executionId, String taskId, String taskRunId, String value, int iteration) {
}
