package forestfire.engine;

import forestfire.model.Cell;
import forestfire.model.Graph;
import forestfire.model.Wind;

import java.util.Random;

/**
 * =============================================================================
 * 🔥 Forest Fire Spread Predictor — Problem Statement No. 86
 * Semester Project | RGMCET B.Tech CSE (Data Science)
 * =============================================================================
 * File: backend/src/main/java/forestfire/engine/SpreadCalculator.java
 *
 * Implements the mathematical physics-based fire propagation model specified
 * in the system architecture (README Section 1.4).
 *
 * Formula:
 *   P(spread) = clamp( P_base × f_vegetation × f_wind × f_slope , 0.0 , 1.0 )
 *
 * Factors:
 *   1. f_vegetation = vegetationDensity of target cell (0.0 to 1.0)
 *   2. f_wind       = max(0, 1 + k_w · windSpeed · cos(θ))
 *                     where θ is the angle between wind vector and spread direction
 *   3. f_slope      = exp( k_s · tan(slope_uphill) )
 *                     accelerates uphill (> 1) and decelerates downhill (< 1)
 * =============================================================================
 */
public class SpreadCalculator {

    // Default empirical coefficients for the Rothermel-based CA model
    public static final double DEFAULT_P_BASE = 0.38;       // Base ignition probability
    public static final double DEFAULT_K_WIND = 0.045;      // Wind sensitivity coefficient (per km/h)
    public static final double DEFAULT_K_SLOPE = 0.080;     // Slope exponential coefficient
    public static final double DEFAULT_CELL_SIZE_M = 10.0;  // Spatial cell dimension (meters)

    private final double pBase;
    private final double kWind;
    private final double kSlope;
    private final double cellSizeMeters;
    private final Random random;

    /**
     * Default constructor with empirical parameters and unseeded random.
     */
    public SpreadCalculator() {
        this(DEFAULT_P_BASE, DEFAULT_K_WIND, DEFAULT_K_SLOPE, DEFAULT_CELL_SIZE_M, new Random());
    }

    /**
     * Constructor supporting reproducible pseudo-random seed.
     */
    public SpreadCalculator(long seed) {
        this(DEFAULT_P_BASE, DEFAULT_K_WIND, DEFAULT_K_SLOPE, DEFAULT_CELL_SIZE_M, new Random(seed));
    }

    /**
     * Parameterised constructor for tuning simulation parameters.
     */
    public SpreadCalculator(double pBase, double kWind, double kSlope, double cellSizeMeters, Random random) {
        this.pBase = Math.max(0.01, Math.min(1.0, pBase));
        this.kWind = Math.max(0.0, kWind);
        this.kSlope = Math.max(0.0, kSlope);
        this.cellSizeMeters = Math.max(1.0, cellSizeMeters);
        this.random = (random != null) ? random : new Random();
    }

    /**
     * Computes the angle (in degrees [0, 360)) from source cell to target neighbour cell.
     * Coordinate convention:
     *   North: dRow = -1, dCol = 0  -> 0°
     *   East:  dRow =  0, dCol = +1 -> 90°
     *   South: dRow = +1, dCol = 0  -> 180°
     *   West:  dRow =  0, dCol = -1 -> 270°
     */
    public static double computeSpreadAngle(Cell source, Cell target) {
        int dRow = target.getRow() - source.getRow();
        int dCol = target.getCol() - source.getCol();

        // In standard map orientation: y axis points North (-dRow), x axis points East (+dCol)
        double angleRad = Math.atan2(dCol, -dRow);
        double angleDeg = Math.toDegrees(angleRad);
        if (angleDeg < 0) {
            angleDeg += 360.0;
        }
        return angleDeg;
    }

    /**
     * Evaluates the wind multiplier f_wind based on alignment between wind and spread direction.
     *
     * @param source Burning cell
     * @param target Unburned neighbour cell
     * @param wind   Current wind state
     * @return Multiplier >= 0.0
     */
    public double computeWindFactor(Cell source, Cell target, Wind wind) {
        if (wind == null || wind.getSpeed() <= 0.0) {
            return 1.0; // Calm air produces neutral factor
        }

        double spreadAngle = computeSpreadAngle(source, target);
        double windAngle = wind.getDirection();

        // Angular difference theta between wind vector and fire propagation vector
        double diff = Math.abs(windAngle - spreadAngle);
        if (diff > 180.0) {
            diff = 360.0 - diff;
        }

        double cosTheta = Math.cos(Math.toRadians(diff));
        // Wind aids spread downwind (cos > 0) and suppresses upwind (cos < 0)
        double fWind = 1.0 + (kWind * wind.getSpeed() * cosTheta);
        return Math.max(0.05, fWind); // Floor factor at 0.05 to avoid negative or total zeroing
    }

    /**
     * Evaluates the slope multiplier f_slope based on elevation differences.
     * Fire pre-heats unburned fuel uphill, accelerating propagation exponentially.
     *
     * @param source Burning cell
     * @param target Unburned neighbour cell
     * @return Multiplier (> 1 for uphill, < 1 for downhill)
     */
    public double computeSlopeFactor(Cell source, Cell target) {
        double dElevation = target.getElevation() - source.getElevation();

        // Distance between cell centers (orthogonal vs diagonal)
        double distance = Graph.isDiagonal(source, target)
                ? cellSizeMeters * Math.sqrt(2.0)
                : cellSizeMeters;

        // Gradient tan(alpha) = dElevation / distance
        double tanAlpha = dElevation / distance;

        // Exponential response: positive tanAlpha -> >1 (uphill), negative -> <1 (downhill)
        double fSlope = Math.exp(kSlope * tanAlpha);

        // Clamp within realistic ecological bounds [0.15, 3.5]
        return Math.max(0.15, Math.min(3.5, fSlope));
    }

    /**
     * Computes the overall spread probability P(spread) for the burning -> neighbour pair.
     *
     * @param source Currently BURNING cell
     * @param target Currently UNBURNED neighbour cell
     * @param wind   Current atmospheric wind
     * @return Normalised probability clamped to [0.0, 1.0]
     */
    public double computeSpreadProbability(Cell source, Cell target, Wind wind) {
        // Zero probability if target has no available fuel
        if (target.getVegetationDensity() <= 0.001) {
            return 0.0;
        }

        double fVeg = target.getVegetationDensity();
        double fWind = computeWindFactor(source, target, wind);
        double fSlope = computeSlopeFactor(source, target);

        double rawProbability = pBase * fVeg * fWind * fSlope;
        return Math.max(0.0, Math.min(1.0, rawProbability));
    }

    /**
     * Stochastically evaluates whether ignition succeeds given the computed probability.
     */
    public boolean testIgnition(Cell source, Cell target, Wind wind) {
        double p = computeSpreadProbability(source, target, wind);
        return random.nextDouble() < p;
    }

    // =========================================================================
    // Getters for Inspection & Report Generation
    // =========================================================================

    public double getPBase() {
        return pBase;
    }

    public double getKWind() {
        return kWind;
    }

    public double getKSlope() {
        return kSlope;
    }

    public double getCellSizeMeters() {
        return cellSizeMeters;
    }
}
