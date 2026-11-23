import java.util.List;

/**
 * Contract every scheduler should follow
 */
public interface Scheduler {
    String name();

    /**
     * @param input    original processes (read only)
     * @param quantum  time quantum Q (SJF ignores it)
     * @param cs       context-switch time
     */
    SimulationResult run(List<Process> input, int quantum, int cs);
}