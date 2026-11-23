import java.util.List;

public class ACSScheduler implements Scheduler {
    @Override public String name() { return "Adaptive Container Scheduler (ACS)"; }

    @Override
    public SimulationResult run(List<Process> input, int quantum, int cs) {
        return new SimulationResult("ACSScheduler class Still not implemented yet");
    }
}