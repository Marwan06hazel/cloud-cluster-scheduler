import java.util.List;

public class SJFScheduler implements Scheduler {
    @Override public String name() { return "Preemptive SJF"; }

    @Override
    public SimulationResult run(List<Process> input, int quantum, int cs) {
        return new SimulationResult("SJFScheduler class Still not implemented yet");
    }
}