package ui.cloudclusterschedulerui;

import javafx.scene.control.*;

import java.util.*;


public class SimulatorView extends ScrollPane {

    @FunctionalInterface
    public interface SimulationListener {
        void onComplete(List<SimulationResult> results, int quantum, int cs);
    }

    public SimulatorView (SimulationListener listener) {

    }

}