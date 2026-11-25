package ui.cloudclusterschedulerui;

import java.util.ArrayList;
import java.util.List;

public class SJFScheduler implements Scheduler {
    @Override public String name() { return "Preemptive SJF"; }

    @Override
    public SimulationResult run(List<Process> input, int quantum, int cs) {
        SimulationResult result = new  SimulationResult(name());

        List<Process> processes = new ArrayList<>();
        for(Process p : input){
            processes.add(p.copy());
        }

        int currentTime = 0;
        int completed = 0;
        int n = processes.size();
        Process previousProcess = null;

        while (completed < n) {
            Process currentProcess = null;

            //choose the SRTF from the processes that arrived and still has remaining time
            for(Process p : processes){
                if(p.arrival <= currentTime && p.remaining > 0){
                    if(currentProcess == null || p.remaining < currentProcess.remaining
                            || (p.remaining == currentProcess.remaining && p == previousProcess)){
                        currentProcess = p;
                    }
                }
            }

            //if there is no process available wait(idle)
            if(currentProcess == null){
                result.addSlot("IDLE", currentTime, currentTime+1);
                result.logAt(currentTime, "Idle");
                currentTime++;
                previousProcess = null;
                continue;
            }

            //if switching from process to another add CS
            if(previousProcess != null && currentProcess != previousProcess && cs > 0){
                result.addSlot("CS", currentTime, currentTime+cs);
                result.logAt(currentTime, "Context Switch: " + previousProcess.id + " to " + currentProcess.id);
                currentTime += cs;
            }

            previousProcess = currentProcess;

            // Start time
            if(currentProcess.firstRun == -1) {
                currentProcess.firstRun = currentTime;
            }

            // Run process for 1s
            result.addSlot(currentProcess.id, currentTime, currentTime+1);
            result.logAt(currentTime, currentProcess.id + " running, remaining = " + currentProcess.remaining);
            currentProcess.remaining--;
            currentTime++;

            // process is done
            if(currentProcess.remaining == 0){
                currentProcess.finish = currentTime;
                completed++;
                result.processes.add(currentProcess);
                result.logAt(currentTime, currentProcess.id + " completed");
            }
        }
        return result;
    }
}
