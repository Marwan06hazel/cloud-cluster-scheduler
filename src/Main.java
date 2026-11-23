import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

public class Main {

    static class Input {
        int q, cs;
        List<Process> processes = new ArrayList<>();
    }

    static Input read(Scanner sc) {
        Input in = new Input();
        System.out.println("Enter valid data in this sequence:\n'Quantum time' 'CS time' 'Number of processes'\nThen a line per each process like: 'P1 1 6 1'");
        while(true) {
            try {
                in.q = sc.nextInt();
                in.cs = sc.nextInt();
                int n = sc.nextInt();
                for (int i = 0; i < n; i++) {
                    String id = sc.next();
                    int arrival = sc.nextInt();
                    int burst = sc.nextInt();
                    int priority = sc.nextInt();
                    if (burst <= 0 || arrival < 0) {
                        System.out.println("Bad data for " + id + ": burst must be > 0 and/or arrival >= 0");
                        throw new IllegalArgumentException();
                    }
                    in.processes.add(new Process(id, arrival, burst, priority));
                }
                return in;
            } catch (Exception e) {
                System.out.println("Please, enter a valid data and sequence :(");
                sc.nextLine();
            }
        }
    }

    // Every schedular the program run
    static List<Scheduler> schedulers() {
        return Arrays.asList(new SJFScheduler(), new ACSScheduler()); /** Change names to match the classes names */
    }

    // Print the scheduler data
    static void print(SimulationResult r) {
        printTimeTable(r);
        System.out.println();
        Metrics.printTable(r);
        System.out.println();
    }

    // Print the main table of events
    static void printTimeTable(SimulationResult r) {
        if (r.gantt.isEmpty()) return;
        int end = r.gantt.get(r.gantt.size() - 1).end;
        String indent = " ".repeat(6 + 1 + 9 + 1); // 6 for time then space then 9 for process then space
        System.out.println();
        System.out.printf("%-6s %-9s %s%n", "Time", "Process", "Details");
        for (int t = 0; t <= end; t++) {
            String proc = "";
            SimulationResult.GanttSlot s = slotAt(r, t);
            if (s != null && s.start == t && !s.pid.equals("CS")) proc = s.pid;
            String[] lines = r.log.getOrDefault(t, "").split("\n");
            System.out.printf("%-6d %-9s %s%n", t, proc, lines[0]);
            for (int i = 1; i < lines.length; i++) System.out.println(indent + lines[i]);
        }
    }
    // Find the process for specific time
    static SimulationResult.GanttSlot slotAt(SimulationResult r, int t) {
        for (SimulationResult.GanttSlot g : r.gantt)
            if (t >= g.start && t < g.end) return g;
        return null;
    }

    /**
     * Use "java src/Main.java tests/t1.txt" or "java -cp out src/Main.java tests/t1.txt"
     * to run the test cases
     * Use "javac -d out src/*.java" to compile first if not
     */

    public static void main(String[] args) throws Exception {
        Scanner sc = (args.length > 0) ? new Scanner(new File(args[0])) : new Scanner(System.in);
        Input in = read(sc);

        System.out.println("Q = " + in.q + ", CS = " + in.cs);
        for (Scheduler s : schedulers()) {
            System.out.println("\n================ " + s.name() + " ================");
            SimulationResult r = s.run(in.processes, in.q, in.cs);
            print(r);
        }
    }
}