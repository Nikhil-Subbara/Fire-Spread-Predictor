package forestfire.report;

import forestfire.engine.FireSimulator;
import forestfire.engine.RiskAssessment;
import forestfire.model.Cell;
import forestfire.model.ForestGrid;

import java.util.*;

/**
 * =============================================================================
 * 🔥 Forest Fire Spread Predictor — Problem Statement No. 86
 * Semester Project | RGMCET B.Tech CSE (Data Science)
 * =============================================================================
 * File: backend/src/main/java/forestfire/report/ReportManager.java
 *
 * Compiles, structures, and exports simulation statistics, live step snapshots,
 * and comprehensive post-simulation impact reports into JSON-ready representations.
 * =============================================================================
 */
public class ReportManager {

    public static final double CELL_AREA_SQ_METERS = 100.0; // 10m × 10m cell = 100 m²
    public static final double SQ_METERS_PER_HECTARE = 10_000.0; // 1 Hectare = 10,000 m²

    /**
     * DTO containing step-level simulation metrics formatted for the web frontend.
     */
    public static class StepPayload {
        private final int step;
        private final int[][] states;
        private final double[][] risk;
        private final Map<String, Object> stats;

        public StepPayload(int step, int[][] states, double[][] risk, Map<String, Object> stats) {
            this.step = step;
            this.states = states;
            this.risk = risk;
            this.stats = stats;
        }

        public int getStep() { return step; }
        public int[][] getStates() { return states; }
        public double[][] getRisk() { return risk; }
        public Map<String, Object> getStats() { return stats; }
    }

    /**
     * DTO containing final post-simulation summary metrics.
     */
    public static class FinalReport {
        private final int totalSteps;
        private final int totalCells;
        private final int totalBurned;
        private final double burnedPercentage;
        private final double affectedAreaHectares;
        private final int peakActiveBurning;
        private final String dominantSpreadDirection;
        private final List<Map<String, Object>> stepHistory;

        public FinalReport(int totalSteps, int totalCells, int totalBurned,
                           double burnedPercentage, double affectedAreaHectares,
                           int peakActiveBurning, String dominantSpreadDirection,
                           List<Map<String, Object>> stepHistory) {
            this.totalSteps = totalSteps;
            this.totalCells = totalCells;
            this.totalBurned = totalBurned;
            this.burnedPercentage = burnedPercentage;
            this.affectedAreaHectares = affectedAreaHectares;
            this.peakActiveBurning = peakActiveBurning;
            this.dominantSpreadDirection = dominantSpreadDirection;
            this.stepHistory = stepHistory;
        }

        public int getTotalSteps() { return totalSteps; }
        public int getTotalCells() { return totalCells; }
        public int getTotalBurned() { return totalBurned; }
        public double getBurnedPercentage() { return burnedPercentage; }
        public double getAffectedAreaHectares() { return affectedAreaHectares; }
        public int getPeakActiveBurning() { return peakActiveBurning; }
        public String getDominantSpreadDirection() { return dominantSpreadDirection; }
        public List<Map<String, Object>> getStepHistory() { return stepHistory; }
    }

    /**
     * DTO containing full grid state, environmental attributes, and statistics for JSON export.
     * Returned by GET /api/simulation/state.
     */
    public static class StatePayload {
        private final int step;
        private final int rows;
        private final int cols;
        private final int totalCells;
        private final int[][] states;
        private final double[][] risk;
        private final String[][] riskLevels;
        private final double[][] vegetation;
        private final double[][] elevation;
        private final double[][] slope;
        private final Map<String, Object> wind;
        private final Map<String, Object> stats;

        public StatePayload(int step, int rows, int cols, int totalCells,
                            int[][] states, double[][] risk, String[][] riskLevels,
                            double[][] vegetation, double[][] elevation, double[][] slope,
                            Map<String, Object> wind, Map<String, Object> stats) {
            this.step = step;
            this.rows = rows;
            this.cols = cols;
            this.totalCells = totalCells;
            this.states = states;
            this.risk = risk;
            this.riskLevels = riskLevels;
            this.vegetation = vegetation;
            this.elevation = elevation;
            this.slope = slope;
            this.wind = wind;
            this.stats = stats;
        }

        public int getStep() { return step; }
        public int getRows() { return rows; }
        public int getCols() { return cols; }
        public int getTotalCells() { return totalCells; }
        public int[][] getStates() { return states; }
        public double[][] getRisk() { return risk; }
        public String[][] getRiskLevels() { return riskLevels; }
        public double[][] getVegetation() { return vegetation; }
        public double[][] getElevation() { return elevation; }
        public double[][] getSlope() { return slope; }
        public Map<String, Object> getWind() { return wind; }
        public Map<String, Object> getStats() { return stats; }
    }

    private final List<Map<String, Object>> recordedSteps;
    private int peakBurning;

    public ReportManager() {
        this.recordedSteps = new ArrayList<>();
        this.peakBurning = 0;
    }

    /**
     * Builds the JSON-ready payload matching the REST API specification:
     * POST /api/simulation/step
     *
     * @param grid      Current ForestGrid
     * @param snapshot  Latest step snapshot
     * @param riskStats Recalibrated risk distributions
     * @return StepPayload containing cell state matrix, risk matrix, and statistics
     */
    public StepPayload buildStepPayload(ForestGrid grid,
                                        FireSimulator.StepSnapshot snapshot,
                                        RiskAssessment.RiskStats riskStats) {
        int rows = grid.getRows();
        int cols = grid.getCols();

        int[][] states = new int[rows][cols];
        double[][] risk = new double[rows][cols];

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                Cell cell = grid.getCell(r, c);
                if (cell != null) {
                    states[r][c] = cell.getState().getCode();
                    risk[r][c] = Math.round(cell.getRiskScore() * 100.0) / 100.0;
                }
            }
        }

        int burningCount = snapshot.getBurningCount();
        int burnedCount = snapshot.getBurnedCount();
        int unburnedCount = snapshot.getUnburnedCount();

        this.peakBurning = Math.max(this.peakBurning, burningCount);

        // Calculate affected area in hectares
        double totalAffectedCells = burningCount + burnedCount;
        double affectedAreaHa = Math.round(((totalAffectedCells * CELL_AREA_SQ_METERS) / SQ_METERS_PER_HECTARE) * 10.0) / 10.0;

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("burning", burningCount);
        stats.put("burned", burnedCount);
        stats.put("unburned", unburnedCount);
        stats.put("newlyIgnited", snapshot.getNewlyIgnitedCount());
        stats.put("riskCounts", riskStats.getCountsMap());
        stats.put("spreadDirection", riskStats.getDominantThreatDirection());
        stats.put("affectedAreaHa", affectedAreaHa);
        stats.put("isExtinguished", snapshot.isExtinguished());
        stats.put("centroidRow", Math.round(snapshot.getCentroidRow() * 10.0) / 10.0);
        stats.put("centroidCol", Math.round(snapshot.getCentroidCol() * 10.0) / 10.0);

        // Record history entry
        Map<String, Object> historyEntry = new LinkedHashMap<>(stats);
        historyEntry.put("step", snapshot.getStep());
        recordedSteps.add(historyEntry);

        return new StepPayload(snapshot.getStep(), states, risk, stats);
    }

    /**
     * Builds the comprehensive final simulation report.
     */
    public FinalReport buildFinalReport(ForestGrid grid, int totalSteps, String spreadDirection) {
        int total = grid.getTotalCells();
        int burned = grid.getBurnedCells().size() + grid.getBurningCells().size();
        double pct = Math.round(((double) burned / total) * 1000.0) / 10.0;
        double areaHa = Math.round(((burned * CELL_AREA_SQ_METERS) / SQ_METERS_PER_HECTARE) * 10.0) / 10.0;

        return new FinalReport(
                totalSteps,
                total,
                burned,
                pct,
                areaHa,
                peakBurning,
                (spreadDirection != null) ? spreadDirection : "NE",
                new ArrayList<>(recordedSteps)
        );
    }

    /**
     * Builds the full grid state payload matching GET /api/simulation/state.
     * Captures cellular automaton states, live risk scores & levels, environmental attributes,
     * wind vectors, and aggregate statistics.
     *
     * @param grid           The current ForestGrid model
     * @param step           Current simulation generation/tick
     * @param riskStats      Current dynamic risk assessment distributions
     * @param isExtinguished True if fire is extinguished or stopped
     * @return StatePayload containing comprehensive simulation snapshot
     */
    public StatePayload buildStatePayload(ForestGrid grid, int step,
                                          RiskAssessment.RiskStats riskStats,
                                          boolean isExtinguished) {
        int rows = grid.getRows();
        int cols = grid.getCols();

        int[][] states = new int[rows][cols];
        double[][] risk = new double[rows][cols];
        String[][] riskLevels = new String[rows][cols];
        double[][] vegetation = new double[rows][cols];
        double[][] elevation = new double[rows][cols];
        double[][] slope = new double[rows][cols];

        int burningCount = 0;
        int burnedCount = 0;
        int unburnedCount = 0;
        double sumR = 0;
        double sumC = 0;

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                Cell cell = grid.getCell(r, c);
                if (cell != null) {
                    states[r][c] = cell.getState().getCode();
                    risk[r][c] = Math.round(cell.getRiskScore() * 100.0) / 100.0;
                    riskLevels[r][c] = RiskAssessment.getEffectiveRiskCategory(cell);
                    vegetation[r][c] = Math.round(cell.getVegetationDensity() * 100.0) / 100.0;
                    elevation[r][c] = Math.round(cell.getElevation() * 10.0) / 10.0;
                    slope[r][c] = Math.round(cell.getSlope() * 10.0) / 10.0;

                    if (cell.isBurning()) {
                        burningCount++;
                        sumR += r;
                        sumC += c;
                    } else if (cell.isBurned()) {
                        burnedCount++;
                    } else {
                        unburnedCount++;
                    }
                }
            }
        }

        double centroidR = (burningCount > 0) ? Math.round((sumR / burningCount) * 10.0) / 10.0 : 0.0;
        double centroidC = (burningCount > 0) ? Math.round((sumC / burningCount) * 10.0) / 10.0 : 0.0;

        double totalAffected = burningCount + burnedCount;
        double affectedAreaHa = Math.round(((totalAffected * CELL_AREA_SQ_METERS) / SQ_METERS_PER_HECTARE) * 10.0) / 10.0;

        Map<String, Object> windInfo = new LinkedHashMap<>();
        if (grid.getWind() != null) {
            windInfo.put("speed", grid.getWind().getSpeed());
            windInfo.put("direction", grid.getWind().getDirection());
            windInfo.put("compass", grid.getWind().getCompassDirection());
        }

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("burning", burningCount);
        stats.put("burned", burnedCount);
        stats.put("unburned", unburnedCount);
        stats.put("newlyIgnited", 0);
        stats.put("riskCounts", riskStats.getCountsMap());
        stats.put("spreadDirection", riskStats.getDominantThreatDirection());
        stats.put("affectedAreaHa", affectedAreaHa);
        stats.put("isExtinguished", isExtinguished || (burningCount == 0 && burnedCount > 0));
        stats.put("centroidRow", centroidR);
        stats.put("centroidCol", centroidC);

        return new StatePayload(step, rows, cols, rows * cols, states, risk, riskLevels, vegetation, elevation, slope, windInfo, stats);
    }

    /**
     * Clears recorded step history on simulation reset.
     */
    public void reset() {
        this.recordedSteps.clear();
        this.peakBurning = 0;
    }

    public List<Map<String, Object>> getRecordedSteps() {
        return Collections.unmodifiableList(recordedSteps);
    }
}
