import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

// Scheduler should return it
public class SimulationResult {
    public final String schedulerName;
    public final List<Process> processes = new ArrayList<>();      // finished processes
    public final List<GanttSlot> gantt = new ArrayList<>();       // execution timeline
    public final TreeMap<Integer, String> log = new TreeMap<>(); // per-time-step table lines (queue snapshots)

    public SimulationResult(String schedulerName) { this.schedulerName = schedulerName; }

    // One block on the Gantt chart. pid is a process id, "CS" for a context switch, or "IDLE".
    public static class GanttSlot {
        public final String pid;
        public final int start, end;
        public GanttSlot(String pid, int start, int end) {
            this.pid = pid;
            this.start = start;
            this.end = end;
        }
        @Override public String toString() { return "[" + start + "-" + end + "] " + pid; }
    }

    public void logAt(int time, String text) {
        log.merge(time, text, (old, add) -> old + "\n" + add);
    }

    // Adding and merging slots
    public void addSlot(String pid, int start, int end) {
        if (end <= start) return;
        if (!gantt.isEmpty()) {
            GanttSlot last = gantt.get(gantt.size() - 1);
            if (last.pid.equals(pid) && last.end == start){
                gantt.set(gantt.size() - 1, new GanttSlot(pid, last.start, end));
                return;
            }
        }
        gantt.add(new GanttSlot(pid, start, end));
    }
}