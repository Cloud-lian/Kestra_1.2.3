package io.kestra.core.runners;

import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.TestInstance;

@KestraTest(startRunner = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractExecutorTest {

    @Inject
    protected TestRunnerUtils runnerUtils;


}