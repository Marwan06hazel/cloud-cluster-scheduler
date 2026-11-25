package ui.cloudclusterschedulerui;

public class Process {
    // Input data
    public final String id;
    public final int arrival;
    public final int burst;
    public final int priority;          // LOWER number = HIGHER priority

    // Schedulers update
    public int remaining;
    public int firstRun = -1;           // time the process first got the CPU (-1 = never yet)
    public int finish = -1;             // completion time (-1 = not finished)
    public int enteredTier2Time = -1;   // time it was demoted into Tier 2 (used for aging)
    public int tier = 1;                // 1 = Round Robin, 2 = Priority (SJF scheduler ignores this)

    public Process(String id, int arrival, int burst, int priority) {
        this.id = id;
        this.arrival = arrival;
        this.burst = burst;
        this.priority = priority;
        this.remaining = burst;
    }

    public Process copy() {
        return new Process(id, arrival, burst, priority);
    }

    public boolean isFinished() { return remaining == 0; }

    // Metrics
    public int turnaround() { return finish - arrival;     }
    public int waiting()    { return turnaround() - burst; }
    public int response()   { return firstRun - arrival;   }

    @Override
    public String toString() {
        return id + " (remaining: " + remaining + ", Priority: " + priority + ")";
    }
}