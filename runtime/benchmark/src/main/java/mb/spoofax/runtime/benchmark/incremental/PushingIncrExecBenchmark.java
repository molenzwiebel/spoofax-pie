package mb.spoofax.runtime.benchmark.incremental;

import mb.spoofax.runtime.benchmark.state.*;
import org.openjdk.jmh.annotations.*;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.TimeUnit;


@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
public class PushingIncrExecBenchmark {
    @Setup(Level.Trial)
    public void setupTrial(SpoofaxPieState spoofaxPieState, WorkspaceState workspaceState, IncrState incrState, InfraState infraState, PushingExecState pushingExecState) throws IOException {
        workspaceState.setup(spoofaxPieState);
        incrState.setup(workspaceState);
        infraState.setup(spoofaxPieState, workspaceState);
        pushingExecState.setup(infraState);

        this.spoofaxPieState = spoofaxPieState;
        this.workspaceState = workspaceState;
        this.incrState = incrState;
        this.infraState = infraState;
        this.pushingExecState = pushingExecState;

        pushingExecState.exec(workspaceState, Collections.emptyList());
    }

    private SpoofaxPieState spoofaxPieState;
    private WorkspaceState workspaceState;
    private IncrState incrState;
    private InfraState infraState;
    private PushingExecState pushingExecState;

    @Setup(Level.Invocation) public void setupInvocation() {
        infraState.renew(spoofaxPieState);
        pushingExecState.renew(infraState);
    }

    @Benchmark public void exec() {
        pushingExecState.exec(workspaceState, incrState.changedPaths);
    }
}
