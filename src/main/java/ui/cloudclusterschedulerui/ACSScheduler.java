package ui.cloudclusterschedulerui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ACSScheduler implements Scheduler {
    @Override public String name() { return "Adaptive Container Scheduler (ACS)"; }

    @Override
    public SimulationResult run(List<Process> input, int quantum, int cs) {
        SimulationResult result = new SimulationResult(name());

        List<Process> pending = new ArrayList<>();
        for (Process p : input) {
            pending.add(p.copy());
        }
        pending.sort(Comparator.comparingInt(p -> p.arrival));

        List<Process> tier1 = new ArrayList<>();
        List<Process> tier2 = new ArrayList<>();

        int currentTime = 0;
        int completed = 0;
        int n = pending.size();
        Process previousProcess = null;

        while (completed < n || !pending.isEmpty()) {
            admit(pending, tier1, tier2, currentTime, result, false);

            if (tier1.isEmpty() && tier2.isEmpty()) {
                result.addSlot("IDLE", currentTime, currentTime + 1);
                result.logAt(currentTime, "Idle");
                currentTime++;
                previousProcess = null;
                continue;
            }

            boolean fromTier1 = !tier1.isEmpty();
            Process currentProcess = fromTier1
                    ? tier1.get(0)
                    : Aging.selectHighestPriority(tier2, currentTime);

            boolean switching = previousProcess != null && currentProcess != previousProcess && cs > 0;

            if (switching) {
                result.logAt(currentTime, "Context switch saves " + previousProcess.id + " and loads " + currentProcess.id);
            }
            result.logAt(currentTime, formatTier1(tier1));
            if (fromTier1) {
                result.logAt(currentTime, Aging.formatTier2(tier2, currentTime));
            } else {
                Aging.logTier2(tier2, currentTime, result);
            }

            if (fromTier1) tier1.remove(currentProcess);
            else tier2.remove(currentProcess);

            if (switching) {
                for (int i = 0; i < cs; i++) {
                    result.addSlot("CS", currentTime, currentTime + 1);
                    currentTime++;
                    admit(pending, tier1, tier2, currentTime, result, true);
                }
            }

            previousProcess = currentProcess;

            if (currentProcess.firstRun == -1) {
                currentProcess.firstRun = currentTime;
            }

            int slice = fromTier1 ? Math.min(quantum, currentProcess.remaining) : currentProcess.remaining;

            for (int i = 0; i < slice; i++) {
                result.addSlot(currentProcess.id, currentTime, currentTime + 1);
                currentProcess.remaining--;
                currentTime++;

                if (i == slice - 1) {
                    if (currentProcess.remaining == 0) {
                        currentProcess.finish = currentTime;
                        completed++;
                        result.processes.add(currentProcess);
                    } else {
                        Aging.registerDemotion(currentProcess, currentTime);
                        tier2.add(currentProcess);
                    }
                }

                admit(pending, tier1, tier2, currentTime, result, i < slice - 1);
            }
        }

        result.logAt(currentTime, formatTier1(tier1));
        result.logAt(currentTime, Aging.formatTier2(tier2, currentTime));
        return result;
    }

    private static void admit(List<Process> pending, List<Process> tier1, List<Process> tier2,
                              int time, SimulationResult result, boolean log) {
        boolean arrived = false;
        while (!pending.isEmpty() && pending.get(0).arrival <= time) {
            tier1.add(pending.remove(0));
            arrived = true;
        }
        if (arrived && log) {
            result.logAt(time, formatTier1(tier1));
            result.logAt(time, Aging.formatTier2(tier2, time));
        }
    }

    private static String formatTier1(List<Process> tier1) {
        if (tier1.isEmpty()) {
            return "Tier 1: [Empty]";
        }
        StringBuilder sb = new StringBuilder("Tier 1: [");
        for (int i = 0; i < tier1.size(); i++) {
            sb.append(tier1.get(i));
            if (i != tier1.size() - 1) sb.append(", ");
        }
        return sb.append("]").toString();
    }
}