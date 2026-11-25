package ui.cloudclusterschedulerui;

import java.util.List;

public class Aging {

    private static final int AGING_INTERVAL = 4;

    // Register demotion from Tier 1 to Tier 2
    public static void registerDemotion(
            Process p, int currentTime) {

        p.enteredTier2Time = currentTime;
        p.tier = 2;
    }

    // Calculate effective priority
    public static int getEffectivePriority(
            Process p, int currentTime) {

        if (p.enteredTier2Time == -1) {
            return p.priority;
        }

        int waitingTime
                = currentTime - p.enteredTier2Time;

        int aging = waitingTime / AGING_INTERVAL;

        return p.priority - aging;
    }

    // Select the process with the highest priority
    public static Process selectHighestPriority(
            List<Process> tier2, int currentTime) {

        Process selected = null;

        for (Process p : tier2) {

            if (selected == null) {
                selected = p;
            } else if (getEffectivePriority(p, currentTime)
                    < getEffectivePriority(selected, currentTime)) {

                selected = p;
            }
        }

        return selected;
    }

    // Format a single process
    public static String formatProcess(
            Process p, int currentTime) {

        int effectivePriority
                = getEffectivePriority(p, currentTime);

        return p.id
                + " (remaining: " + p.remaining
                + ", Priority: " + effectivePriority
                + ")";
    }

    // Log aging details for a single process
    public static void logAging(
            Process p,
            int currentTime,
            SimulationResult result) {

        if (p.enteredTier2Time == -1) {
            return;
        }

        int waiting
                = currentTime - p.enteredTier2Time;

        int effectivePriority
                = getEffectivePriority(p, currentTime);

        result.logAt(currentTime,
                p.id
                + ": Waiting = " + waiting
                + ", Aging = " + waiting / AGING_INTERVAL
                + ", Effective Priority = " + effectivePriority);
    }

    // Format the entire Tier 2 queue
    public static String formatTier2(
            List<Process> tier2, int currentTime) {

        if (tier2.isEmpty()) {
            return "Tier 2: [Empty]";
        }

        String output = "Tier 2: [";

        for (int i = 0; i < tier2.size(); i++) {

            Process p = tier2.get(i);

            output += formatProcess(p, currentTime);

            if (i != tier2.size() - 1) {
                output += ", ";
            }
        }

        output += "]";

        return output;
    }

    // Log the entire Tier 2 queue
    public static void logTier2(
            List<Process> tier2,
            int currentTime,
            SimulationResult result) {

        for (Process p : tier2) {

            logAging(p, currentTime, result);
        }

        result.logAt(
                currentTime,
                formatTier2(tier2, currentTime)
        );
    }
}
