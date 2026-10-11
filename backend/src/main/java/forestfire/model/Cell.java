package forestfire.model;

/**
 * =============================================================================
 * 🔥 Forest Fire Spread Predictor — Problem Statement No. 86
 * Semester Project | RGMCET B.Tech CSE (Data Science)
 * =============================================================================
 * File: backend/src/main/java/forestfire/model/Cell.java
 *
 * Represents an individual discretised forest cell in the 2-D cellular automaton.
 * Stores spatial coordinates, terrain topography, fuel load, fire state,
 * and live dynamic risk metrics.
 * =============================================================================
 */
public class Cell {

    /**
     * Enumeration representing the discrete state of a cell in the cellular automaton.
     */
    public enum FireState {
        UNBURNED(0),
        BURNING(1),
        BURNED(2);

        private final int code;

        FireState(int code) {
            this.code = code;
        }

        public int getCode() {
            return code;
        }

        public static FireState fromCode(int code) {
            for (FireState state : values()) {
                if (state.code == code) {
                    return state;
                }
            }
            return UNBURNED;
        }
    }

    /**
     * Enumeration representing the real-time assessed risk level of a cell.
     */
    public enum RiskLevel {
        LOW("Low", "#2ecc71"),
        MODERATE("Moderate", "#f1c40f"),
        HIGH("High", "#e67e22"),
        VERY_HIGH("Very High", "#e74c3c");

        private final String label;
        private final String hexColor;

        RiskLevel(String label, String hexColor) {
            this.label = label;
            this.hexColor = hexColor;
        }

        public String getLabel() {
            return label;
        }

        public String getHexColor() {
            return hexColor;
        }
    }

    // Grid Coordinates
    private final int row;
    private final int col;

    // Environmental & Topographical Attributes
    private double vegetationDensity; // Fuel load normalised strictly in [0.0, 1.0]
    private double elevation;         // Altitude in meters above sea level
    private double slope;             // Terrain incline in degrees [0.0, 89.0]

    // Cellular Automaton State & Double-Buffering
    private FireState state;          // Current synchronous generation state
    private FireState nextState;      // Double-buffered staging state for synchronous step
    private int burnTicks;            // Number of simulation steps the cell has been actively burning
    private int initialFireCode;      // Original state recorded at initialisation (for reset)

    // Dynamic Live Risk Attributes
    private double riskScore;         // Dynamic risk probability in [0.0, 1.0]
    private RiskLevel riskLevel;      // Categorised risk level (Low -> Very High)

    /**
     * Parameterised constructor for initialising a forest cell.
     *
     * @param row               Grid row index (0-indexed)
     * @param col               Grid column index (0-indexed)
     * @param vegetationDensity Normalised fuel load in [0.0, 1.0]
     * @param elevation         Elevation in meters above sea level
     * @param slope             Terrain slope in degrees
     * @param initialFireState  Initial fire state (UNBURNED, BURNING, or BURNED)
     */
    public Cell(int row, int col, double vegetationDensity, double elevation, double slope, FireState initialFireState) {
        this.row = row;
        this.col = col;
        this.vegetationDensity = Math.max(0.0, Math.min(1.0, vegetationDensity));
        this.elevation = elevation;
        this.slope = Math.max(0.0, slope);
        this.state = (initialFireState != null) ? initialFireState : FireState.UNBURNED;
        this.nextState = this.state;
        this.initialFireCode = this.state.getCode();
        this.burnTicks = (this.state == FireState.BURNING) ? 1 : 0;
        this.riskScore = 0.0;
        this.riskLevel = RiskLevel.LOW;
    }

    /**
     * Convenience constructor defaulting to UNBURNED fire state and 0.0 slope.
     */
    public Cell(int row, int col, double vegetationDensity, double elevation) {
        this(row, col, vegetationDensity, elevation, 0.0, FireState.UNBURNED);
    }

    // =========================================================================
    // Cellular Automaton State Transitions
    // =========================================================================

    /**
     * Checks if the cell is currently actively burning.
     */
    public boolean isBurning() {
        return this.state == FireState.BURNING;
    }

    /**
     * Checks if the cell has burned out and exhausted its available fuel.
     */
    public boolean isBurned() {
        return this.state == FireState.BURNED;
    }

    /**
     * Checks if the cell is unburned and susceptible to ignition.
     */
    public boolean isUnburned() {
        return this.state == FireState.UNBURNED;
    }

    /**
     * Forces immediate ignition of the cell (used for manual ignition points).
     */
    public void ignite() {
        this.state = FireState.BURNING;
        this.nextState = FireState.BURNING;
        this.burnTicks = 1;
    }

    /**
     * Stages the cell for ignition in the next synchronous generation.
     */
    public void stageIgnition() {
        if (this.state == FireState.UNBURNED) {
            this.nextState = FireState.BURNING;
        }
    }

    /**
     * Stages the cell for burnout in the next synchronous generation.
     */
    public void stageBurnout() {
        if (this.state == FireState.BURNING) {
            this.nextState = FireState.BURNED;
        }
    }

    /**
     * Commits the double-buffered nextState to the active state synchronously.
     */
    public void commitNextState() {
        if (this.nextState == FireState.BURNING) {
            if (this.state == FireState.UNBURNED) {
                this.burnTicks = 1;
            } else if (this.state == FireState.BURNING) {
                this.burnTicks++;
            }
        }
        this.state = this.nextState;
        if (this.state == FireState.BURNED) {
            this.riskScore = 0.0; // Burned out cells have zero remaining fuel risk
            this.riskLevel = RiskLevel.LOW;
        }
    }

    /**
     * Resets the cell back to its initial state before simulation started.
     */
    public void reset() {
        this.state = FireState.fromCode(this.initialFireCode);
        this.nextState = this.state;
        this.burnTicks = (this.state == FireState.BURNING) ? 1 : 0;
        this.riskScore = 0.0;
        this.riskLevel = RiskLevel.LOW;
    }

    // =========================================================================
    // Getters and Setters
    // =========================================================================

    public int getRow() {
        return row;
    }

    public int getCol() {
        return col;
    }

    public double getVegetationDensity() {
        return vegetationDensity;
    }

    public void setVegetationDensity(double vegetationDensity) {
        this.vegetationDensity = Math.max(0.0, Math.min(1.0, vegetationDensity));
    }

    public double getElevation() {
        return elevation;
    }

    public void setElevation(double elevation) {
        this.elevation = elevation;
    }

    public double getSlope() {
        return slope;
    }

    public void setSlope(double slope) {
        this.slope = Math.max(0.0, slope);
    }

    public FireState getState() {
        return state;
    }

    public void setState(FireState state) {
        this.state = state;
        this.nextState = state;
    }

    public FireState getNextState() {
        return nextState;
    }

    public void setNextState(FireState nextState) {
        this.nextState = nextState;
    }

    public int getBurnTicks() {
        return burnTicks;
    }

    public void setBurnTicks(int burnTicks) {
        this.burnTicks = burnTicks;
    }

    public double getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(double riskScore) {
        this.riskScore = Math.max(0.0, Math.min(1.0, riskScore));
        // Dynamically classify into risk levels
        if (this.riskScore >= 0.75) {
            this.riskLevel = RiskLevel.VERY_HIGH;
        } else if (this.riskScore >= 0.50) {
            this.riskLevel = RiskLevel.HIGH;
        } else if (this.riskScore >= 0.25) {
            this.riskLevel = RiskLevel.MODERATE;
        } else {
            this.riskLevel = RiskLevel.LOW;
        }
    }

    public RiskLevel getRiskLevel() {
        return riskLevel;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Cell cell = (Cell) o;
        return row == cell.row && col == cell.col;
    }

    @Override
    public int hashCode() {
        return 31 * row + col;
    }

    @Override
    public String toString() {
        return String.format("Cell[%d,%d | %s | veg=%.2f, elev=%.1fm, slope=%.1f deg, risk=%.2f(%s)]",
                row, col, state, vegetationDensity, elevation, slope, riskScore, riskLevel.getLabel());
    }
}
