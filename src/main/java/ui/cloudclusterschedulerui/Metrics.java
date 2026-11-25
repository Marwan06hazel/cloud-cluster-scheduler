package ui.cloudclusterschedulerui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Metrics {

    public static void printTable(SimulationResult r) {
        List<Process> list = new ArrayList<>(r.processes);
        list.sort(Comparator.comparingInt((Process p) -> p.id.length()).thenComparing(p -> p.id));

        System.out.printf("%-8s %-8s %-8s %-11s %-8s %-8s%n",
                "Process", "Arrival", "Finish", "Turnaround", "Waiting", "Response");
        for (Process p : list) {
            System.out.printf("%-8s %-8d %-8d %-11d %-8d %-8d%n",
                    p.id, p.arrival, p.finish, p.turnaround(), p.waiting(), p.response());
        }
        System.out.printf("Average Turnaround Time: %.2f time units%n", avgTurnaround(r));
        System.out.printf("Average Waiting Time: %.2f time units%n", avgWaiting(r));
        System.out.printf("Average Response Time: %.2f time units%n", avgResponse(r));
    }

    public static double avgWaiting(SimulationResult r) {
        if (r.processes.isEmpty()) return 0;
        double sum = 0;
        for (Process p : r.processes) sum += p.waiting();
        return sum / r.processes.size();
    }

    public static double avgTurnaround(SimulationResult r) {
        if (r.processes.isEmpty()) return 0;
        double sum = 0;
        for (Process p : r.processes) sum += p.turnaround();
        return sum / r.processes.size();
    }

    public static double avgResponse(SimulationResult r) {
        if (r.processes.isEmpty()) return 0;
        double sum = 0;
        for (Process p : r.processes) sum += p.response();
        return sum / r.processes.size();
    }
}
