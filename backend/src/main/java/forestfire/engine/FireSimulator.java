package forestfire.engine;

import forestfire.model.Cell;
import forestfire.model.ForestGrid;
import forestfire.model.Graph;

import java.util.*;

/**
 * =============================================================================
 * 🔥 Forest Fire Spread Predictor — Problem Statement No. 86
 * Semester Project | RGMCET B.Tech CSE (Data Science)
 * =============================================================================
 * File: backend/src/main/java/forestfire/engine/FireSimulator.java
 *
 * Core Cellular Automaton Simulation Engine with synchronous double-buffering.
 * Propagates fire along the active frontier using graph neighbourhood traversals,
 * eliminating the need for wasteful full-grid iterations.
 *
 * State Transitions:
 *   UNBURNED ──(spread condition met)──► BURNING ──(fuel exhausted)──► BURNED
 * =============================================================================
 */
public class FireSimulator {

    /**
     * DTO snapshot capturing the state of the simulation at a given step.
     */
    public static class StepSnapshot {
        private final int step;
        private final int burningCount;
        private final int burnedCount;
        private final int unburnedCount;
        private final int newlyIgnitedCount;
        private final double centroidRow;
        private final double centroidCol;
        private final boolean extinguished;

        public StepSnapshot(int step, int burningCount, int burnedCount, int unburnedCount,
                            int newlyIgnitedCount, double centroidRow, double centroidCol) {
            this.step = step;
            this.burningCount = burningCount;
            this.burnedCount = burnedCount;
            this.unburnedCount = unburnedCount;
            this.newlyIgnitedCount = newlyIgnitedCount;
            this.centroidRow = centroidRow;
            this.centroidCol = centroidCol;
            this.extinguished = (burningCount == 0);
        }

        public int getStep() { return step; }
        public int getBurningCount() { return burningCount; }
        public int getBurnedCount() { return burnedCount; }
        public int getUnburnedCount() { return unburnedCount; }
        public int getNewlyIgnitedCount() { return newlyIgnitedCount; }
        public double getCentroidRow() { return centroidRow; }
        public double getCentroidCol() { return centroidCol; }
        public boolean isExtinguished() { return extinguished; }

        @Override
        public String toString() {
            return String.format("Step %-3d | Burning: %-4d | Burned: %-4d | Unburned: %-4d | New: %-3d | Centroid: (%.1f, %.1f) | %s",
                    step, burningCount, burnedCount, unburnedCount, newlyIgnitedCount,
                    centroidRow, centroidCol, extinguished ? "[EXTINGUISHED]" : "[ACTIVE]");
        }
    }

    private final ForestGrid grid;
    private final SpreadCalculator spreadCalculator;
    private final Set<Cell> activeFrontier;
    private final List<StepSnapshot> stepHistory;

    private int currentStep;
    private int burnDuration; // How many steps a cell remains BURNING before turning BURNED (default: 1)
    private boolean isFinished;

    /**
     * Constructs a FireSimulator for the given ForestGrid using default spread rules.
     */
    public FireSimulator(ForestGrid grid) {
        this(grid, new SpreadCalculator(), 1);
    }

    /**
     * Constructs a FireSimulator with custom spread calculator and burn duration.
     */
    public FireSimulator(ForestGrid grid, SpreadCalculator spreadCalculator, int burnDuration) {
        this.grid = Objects.requireNonNull(grid, "ForestGrid cannot be null");
        this.spreadCalculator = (spreadCalculator != null) ? spreadCalculator : new SpreadCalculator();
        this.burnDuration = Math.max(1, burnDuration);
        this.activeFrontier = new LinkedHashSet<>();
        this.stepHistory = new ArrayList<>();
        this.currentStep = 0;
        this.isFinished = false;

        initialiseFrontier();
    }

    /**
     * Inspects the grid and populates the initial active frontier with any pre-ignited BURNING cells.
     */
    public void initialiseFrontier() {
        activeFrontier.clear();
        for (Cell cell : grid.getBurningCells()) {
            activeFrontier.add(cell);
        }
        if (activeFrontier.isEmpty()) {
            this.isFinished = true;
        }
    }

    /**
     * Manually ignites a cell at (row, col) and enqueues it into the active frontier.
     */
    public boolean ignite(int row, int col) {
        if (grid.igniteCell(row, col)) {
            Cell cell = grid.getCell(row, col);
            activeFrontier.add(cell);
            this.isFinished = false;
            return true;
        }
        return false;
    }

    /**
     * Executes one synchronous Cellular Automaton step.
     * Uses double-buffering so cell evaluation order never introduces directional bias.
     *
     * @return StepSnapshot with live statistics of the generation
     */
    public StepSnapshot advanceStep() {
        if (activeFrontier.isEmpty()) {
            this.isFinished = true;
            return createCurrentSnapshot(0);
        }

        currentStep++;
        Graph graph = grid.getGraph();

        // Staging sets for double-buffered synchronous transition
        Set<Cell> newlyIgnited = new HashSet<>();
        Set<Cell> turningBurned = new HashSet<>();

        // 1. Evaluate Spread from all active burning cells to their unburned neighbours
        for (Cell burningCell : activeFrontier) {
            List<Cell> unburnedNeighbours = graph.getUnburnedNeighbours(burningCell);

            for (Cell candidate : unburnedNeighbours) {
                // If already staged for ignition by another neighbour this step, skip redundant checks
                if (newlyIgnited.contains(candidate)) {
                    continue;
                }

                if (spreadCalculator.testIgnition(burningCell, candidate, grid.getWind())) {
                    candidate.stageIgnition();
                    newlyIgnited.add(candidate);
                }
            }

            // Check if burning cell has reached its burn duration
            if (burningCell.getBurnTicks() >= burnDuration) {
                burningCell.stageBurnout();
                turningBurned.add(burningCell);
            }
        }

        // 2. Synchronous Commit Phase (Double-buffering applied)
        for (Cell cell : newlyIgnited) {
            cell.commitNextState();
        }
        for (Cell cell : turningBurned) {
            cell.commitNextState();
        }
        for (Cell cell : activeFrontier) {
            if (!turningBurned.contains(cell)) {
                cell.commitNextState();
            }
        }

        // 3. Update Active Frontier Queue
        activeFrontier.removeAll(turningBurned);
        activeFrontier.addAll(newlyIgnited);

        if (activeFrontier.isEmpty()) {
            this.isFinished = true;
        }

        // 4. Capture Step Snapshot & Statistics
        StepSnapshot snapshot = createCurrentSnapshot(newlyIgnited.size());
        stepHistory.add(snapshot);

        return snapshot;
    }

    /**
     * Helper to compute fire centroid and package the generation snapshot.
     */
    private StepSnapshot createCurrentSnapshot(int newlyIgnitedCount) {
        int burningCount = activeFrontier.size();
        int burnedCount = grid.getBurnedCells().size();
        int unburnedCount = grid.getUnburnedCells().size();

        // Compute centroid (mean row, mean col) of active burning fire front
        double sumR = 0.0;
        double sumC = 0.0;
        if (!activeFrontier.isEmpty()) {
            for (Cell cell : activeFrontier) {
                sumR += cell.getRow();
                sumC += cell.getCol();
            }
            sumR /= activeFrontier.size();
            sumC /= activeFrontier.size();
        }

        return new StepSnapshot(currentStep, burningCount, burnedCount, unburnedCount,
                newlyIgnitedCount, sumR, sumC);
    }

    /**
     * Runs the simulation repeatedly until either the fire extinguishes
     * or maxSteps is reached.
     *
     * @param maxSteps Maximum allowed simulation ticks
     * @return List of snapshots recorded for every step
     */
    public List<StepSnapshot> runSimulation(int maxSteps) {
        int stepsToRun = Math.max(1, maxSteps);
        while (!isFinished && currentStep < stepsToRun) {
            advanceStep();
        }
        return getStepHistory();
    }

    /**
     * Resets the entire grid and simulator back to Step 0.
     */
    public void reset() {
        grid.resetAllCells();
        activeFrontier.clear();
        stepHistory.clear();
        currentStep = 0;
        isFinished = false;
        initialiseFrontier();
    }

    // =========================================================================
    // Getters and Status Checks
    // =========================================================================

    public ForestGrid getGrid() {
        return grid;
    }

    public SpreadCalculator getSpreadCalculator() {
        return spreadCalculator;
    }

    public Set<Cell> getActiveFrontier() {
        return Collections.unmodifiableSet(activeFrontier);
    }

    public List<StepSnapshot> getStepHistory() {
        return Collections.unmodifiableList(stepHistory);
    }

    public int getCurrentStep() {
        return currentStep;
    }

    public int getBurnDuration() {
        return burnDuration;
    }

    public void setBurnDuration(int burnDuration) {
        this.burnDuration = Math.max(1, burnDuration);
    }

    public boolean isExtinguished() {
        return isFinished;
    }
}
