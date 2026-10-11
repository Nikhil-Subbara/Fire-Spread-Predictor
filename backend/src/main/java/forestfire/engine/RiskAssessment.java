package forestfire.engine;

import forestfire.model.Cell;
import forestfire.model.ForestGrid;
import forestfire.model.Graph;
import forestfire.model.Wind;

import java.util.*;

/**
 * =============================================================================
 * 🔥 Forest Fire Spread Predictor — Problem Statement No. 86
 * Semester Project | RGMCET B.Tech CSE (Data Science)
 * =============================================================================
 * File: backend/src/main/java/forestfire/engine/RiskAssessment.java
 *
 * Implements the Dynamic Live Risk Assessment & Heatmap Recalibration Engine.
 *
 * Responsibilities:
 *   1. Recalibrates risk scores continuously across the grid at every step.
 *   2. Evaluates non-burned cells based on:
 *      - Graph BFS proximity to the active burning frontier (distance decay)
 *      - Downwind alignment (wind boosts danger towards downwind unburned cells)
 *      - Topographical slope (fire spreads uphill, increasing danger above the fire front)
 *      - Fuel availability (vegetation density)
 *   3. Zeroes risk on fuel-exhausted (BURNED) cells.
 *   4. Classifies cells dynamically into Low, Moderate, High, and Very High risk tiers.
 *   5. Aggregates live risk distribution statistics for dashboard visualization.
 * =============================================================================
 */
public class RiskAssessment {

    /**
     * DTO containing aggregated risk class distributions and metrics.
     */
    public static class RiskStats {
        private final int lowCount;
        private final int moderateCount;
        private final int highCount;
        private final int veryHighCount;
        private final double averageRisk;
        private final double maxRisk;
        private final String dominantThreatDirection;

        public RiskStats(int lowCount, int moderateCount, int highCount, int veryHighCount,
                         double averageRisk, double maxRisk, String dominantThreatDirection) {
            this.lowCount = lowCount;
            this.moderateCount = moderateCount;
            this.highCount = highCount;
            this.veryHighCount = veryHighCount;
            this.averageRisk = averageRisk;
            this.maxRisk = maxRisk;
            this.dominantThreatDirection = dominantThreatDirection;
        }

        public int getLowCount() { return lowCount; }
        public int getModerateCount() { return moderateCount; }
        public int getHighCount() { return highCount; }
        public int getVeryHighCount() { return veryHighCount; }
        public double getAverageRisk() { return averageRisk; }
        public double getMaxRisk() { return maxRisk; }
        public String getDominantThreatDirection() { return dominantThreatDirection; }

        public Map<String, Integer> getCountsMap() {
            Map<String, Integer> map = new LinkedHashMap<>();
            map.put("low", lowCount);
            map.put("moderate", moderateCount);
            map.put("high", highCount);
            map.put("veryHigh", veryHighCount);
            return map;
        }

        @Override
        public String toString() {
            return String.format("RiskStats[Low: %d, Mod: %d, High: %d, VHigh: %d | AvgRisk: %.2f, Max: %.2f | Threat: %s]",
                    lowCount, moderateCount, highCount, veryHighCount, averageRisk, maxRisk, dominantThreatDirection);
        }
    }

    private final int maxProximityHops;
    private final double kWindRisk;
    private final double kSlopeRisk;

    /**
     * Default constructor with empirical parameters.
     */
    public RiskAssessment() {
        this(8, 0.040, 0.060);
    }

    /**
     * Parameterised constructor for tuning risk sensitivity.
     *
     * @param maxProximityHops Maximum BFS depth away from the fire front considered at-risk.
     * @param kWindRisk        Sensitivity to downwind hazard alignment.
     * @param kSlopeRisk       Sensitivity to uphill slope hazard.
     */
    public RiskAssessment(int maxProximityHops, double kWindRisk, double kSlopeRisk) {
        this.maxProximityHops = Math.max(2, maxProximityHops);
        this.kWindRisk = Math.max(0.0, kWindRisk);
        this.kSlopeRisk = Math.max(0.0, kSlopeRisk);
    }

    /**
     * Recalibrates risk values for every cell in the ForestGrid using the grid's
     * currently active BURNING cells as the frontier.
     *
     * @param grid The forest grid model
     * @return RiskStats containing risk distribution and counts
     */
    public RiskStats recalibrate(ForestGrid grid) {
        Objects.requireNonNull(grid, "ForestGrid cannot be null");
        return recalibrate(grid, grid.getBurningCells());
    }

    /**
     * Recalibrates dynamic risk scores across the entire ForestGrid based on fire proximity,
     * downwind alignment, slope, and fuel availability.
     *
     * @param grid The forest grid model
     * @return RiskStats containing updated risk distribution and counts
     */
    public RiskStats updateRiskHeatmap(ForestGrid grid) {
        return recalibrate(grid);
    }

    /**
     * Returns the dynamic risk classification label for a cell,
     * factoring in state transitions (Burned, Burning / Very High, High, Moderate, Low).
     *
     * @param cell The cell to inspect
     * @return Category string: "Burned", "Very High", "High", "Moderate", or "Low"
     */
    public static String getEffectiveRiskCategory(Cell cell) {
        if (cell == null) return "Unknown";
        if (cell.isBurned()) return "Burned";
        if (cell.isBurning()) return "Very High";
        return cell.getRiskLevel().getLabel();
    }

    /**
     * Recalibrates risk values for every cell in the ForestGrid according to the
     * active burning frontier, wind, slope, and fuel.
     *
     * @param grid            The forest grid model
     * @param burningFrontier The set of currently active BURNING cells
     * @return RiskStats containing risk distribution and counts
     */
    public RiskStats recalibrate(ForestGrid grid, Collection<Cell> burningFrontier) {
        Objects.requireNonNull(grid, "ForestGrid cannot be null");
        Graph graph = grid.getGraph();
        Wind wind = grid.getWind();

        // If no active fire exists, cool down the grid to neutral baseline
        if (burningFrontier == null || burningFrontier.isEmpty()) {
            return coolDownGrid(grid);
        }

        // 1. Compute Multi-Source BFS distance layers from the active fire frontier
        Map<Cell, Integer> bfsDistances = graph.computeMultiSourceBfsDistances(burningFrontier, maxProximityHops);

        // Map each evaluated cell to its nearest burning source for vector calculations
        Map<Cell, Cell> nearestBurningMap = resolveNearestBurningSources(burningFrontier, bfsDistances, graph);

        int low = 0;
        int moderate = 0;
        int high = 0;
        int veryHigh = 0;
        double sumRisk = 0.0;
        double maxRisk = 0.0;
        int unburnedCount = 0;

        // Threat vector accumulator (for dominant threat direction)
        double threatVectorX = 0.0; // Eastward
        double threatVectorY = 0.0; // Northward

        int rows = grid.getRows();
        int cols = grid.getCols();

        // 2. Evaluate risk for every cell in the grid
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                Cell cell = grid.getCell(r, c);
                if (cell == null) continue;

                // BURNED CELLS: Fuel exhausted, risk drops strictly to 0.0
                if (cell.isBurned()) {
                    cell.setRiskScore(0.0);
                    continue;
                }

                // BURNING CELLS: Immediate active hazard (1.0)
                if (cell.isBurning()) {
                    cell.setRiskScore(1.0);
                    veryHigh++;
                    continue;
                }

                // UNBURNED CELLS: Calculate dynamic threat from approaching fire front
                unburnedCount++;
                Integer distance = bfsDistances.get(cell);

                if (distance == null || distance > maxProximityHops) {
                    // Cell is far beyond the active frontier; assign baseline ambient fuel risk
                    double ambientRisk = Math.min(0.15, 0.05 + 0.10 * cell.getVegetationDensity());
                    cell.setRiskScore(ambientRisk);
                    low++;
                    sumRisk += ambientRisk;
                    continue;
                }

                // Distance Decay: Closer cells have much higher risk
                // d=1: 0.85, d=2: 0.65, d=3: 0.48, etc.
                double distanceFactor = Math.exp(-0.35 * (distance - 1));

                // Environmental Multipliers relative to nearest active fire cell
                Cell nearestBurning = nearestBurningMap.get(cell);
                double windMultiplier = 1.0;
                double slopeMultiplier = 1.0;

                if (nearestBurning != null) {
                    windMultiplier = computeWindRiskMultiplier(nearestBurning, cell, wind);
                    slopeMultiplier = computeSlopeRiskMultiplier(nearestBurning, cell);

                    // If high risk, accumulate threat direction vector
                    if (distanceFactor * cell.getVegetationDensity() > 0.40) {
                        threatVectorX += (cell.getCol() - nearestBurning.getCol());
                        threatVectorY += (nearestBurning.getRow() - cell.getRow()); // North is -row
                    }
                }

                // Combine: Proximity * Fuel * WindAlignment * TerrainSlope
                double fuelFactor = cell.getVegetationDensity();
                double computedRisk = distanceFactor * fuelFactor * windMultiplier * slopeMultiplier;

                // Clamp strictly to [0.0, 1.0]
                computedRisk = Math.max(0.0, Math.min(1.0, computedRisk));
                cell.setRiskScore(computedRisk);

                // Update metrics
                sumRisk += computedRisk;
                maxRisk = Math.max(maxRisk, computedRisk);

                Cell.RiskLevel level = cell.getRiskLevel();
                switch (level) {
                    case LOW: low++; break;
                    case MODERATE: moderate++; break;
                    case HIGH: high++; break;
                    case VERY_HIGH: veryHigh++; break;
                }
            }
        }

        double avgRisk = (unburnedCount > 0) ? (sumRisk / unburnedCount) : 0.0;
        String dominantThreat = computeDominantDirection(threatVectorX, threatVectorY, wind);

        return new RiskStats(low, moderate, high, veryHigh, avgRisk, maxRisk, dominantThreat);
    }

    /**
     * Resolves the closest burning cell for each at-risk cell in the BFS tree.
     */
    private Map<Cell, Cell> resolveNearestBurningSources(Collection<Cell> sources,
                                                         Map<Cell, Integer> distances,
                                                         Graph graph) {
        Map<Cell, Cell> map = new HashMap<>();
        for (Cell src : sources) {
            map.put(src, src);
        }

        // Propagate source identity along shortest path
        List<Map.Entry<Cell, Integer>> sorted = new ArrayList<>(distances.entrySet());
        sorted.sort(Comparator.comparingInt(Map.Entry::getValue));

        for (Map.Entry<Cell, Integer> entry : sorted) {
            Cell cell = entry.getKey();
            if (map.containsKey(cell)) continue;

            int dist = entry.getValue();
            for (Cell n : graph.getNeighbours(cell)) {
                if (distances.getOrDefault(n, Integer.MAX_VALUE) == dist - 1 && map.containsKey(n)) {
                    map.put(cell, map.get(n));
                    break;
                }
            }
        }
        return map;
    }

    /**
     * Computes wind risk multiplier: boosts risk for downwind cells ahead of fire.
     */
    private double computeWindRiskMultiplier(Cell source, Cell target, Wind wind) {
        if (wind == null || wind.getSpeed() <= 0.0) {
            return 1.0;
        }
        double spreadAngle = SpreadCalculator.computeSpreadAngle(source, target);
        double windAngle = wind.getDirection();

        double diff = Math.abs(windAngle - spreadAngle);
        if (diff > 180.0) {
            diff = 360.0 - diff;
        }

        double cosTheta = Math.cos(Math.toRadians(diff));
        // Wind carries sparks and heat downwind (cos > 0)
        double factor = 1.0 + (kWindRisk * wind.getSpeed() * cosTheta);
        return Math.max(0.2, Math.min(2.5, factor));
    }

    /**
     * Computes slope risk multiplier: boosts risk if target cell lies uphill from fire front.
     */
    private double computeSlopeRiskMultiplier(Cell source, Cell target) {
        double dElevation = target.getElevation() - source.getElevation();
        double distance = Graph.isDiagonal(source, target) ? 14.14 : 10.0;
        double tanAlpha = dElevation / distance;

        double factor = Math.exp(kSlopeRisk * tanAlpha);
        return Math.max(0.3, Math.min(2.2, factor));
    }

    /**
     * Determines dominant compass direction where fire risk is primarily threatening.
     */
    private String computeDominantDirection(double vx, double vy, Wind wind) {
        if (Math.abs(vx) < 0.1 && Math.abs(vy) < 0.1) {
            return (wind != null) ? wind.getCompassDirection() : "NE";
        }
        double angleRad = Math.atan2(vx, vy); // vx = East, vy = North
        double angleDeg = Math.toDegrees(angleRad);
        if (angleDeg < 0) {
            angleDeg += 360.0;
        }
        return new Wind(1.0, angleDeg).getCompassDirection();
    }

    /**
     * Resets risk values across grid when fire has completely burned out or is unignited.
     */
    private RiskStats coolDownGrid(ForestGrid grid) {
        int low = 0;
        int rows = grid.getRows();
        int cols = grid.getCols();

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                Cell cell = grid.getCell(r, c);
                if (cell != null) {
                    if (cell.isBurned()) {
                        cell.setRiskScore(0.0);
                    } else {
                        double baseline = Math.min(0.10, 0.05 * cell.getVegetationDensity());
                        cell.setRiskScore(baseline);
                        low++;
                    }
                }
            }
        }
        return new RiskStats(low, 0, 0, 0, 0.05, 0.10, "NONE");
    }
}
