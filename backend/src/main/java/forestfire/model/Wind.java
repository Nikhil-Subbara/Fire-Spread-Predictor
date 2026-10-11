package forestfire.model;

/**
 * =============================================================================
 * 🔥 Forest Fire Spread Predictor — Problem Statement No. 86
 * Semester Project | RGMCET B.Tech CSE (Data Science)
 * =============================================================================
 * File: backend/src/main/java/forestfire/model/Wind.java
 *
 * Encapsulates environmental atmospheric conditions: wind speed and direction.
 * Provides meteorological compass bearings and vector calculations for
 * determining fire propagation alignment angles.
 * =============================================================================
 */
public class Wind {

    // Meteorological Compass Directions
    private static final String[] COMPASS_DIRECTIONS = {
        "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
        "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"
    };

    private double speed;     // Wind speed in km/h (or m/s according to dataset configuration)
    private double direction; // Wind direction in azimuth degrees: 0° = North, 90° = East, 180° = South, 270° = West

    /**
     * Default constructor initialising to calm conditions (0 km/h, 0° North).
     */
    public Wind() {
        this(0.0, 0.0);
    }

    /**
     * Parameterised constructor.
     *
     * @param speed     Wind velocity (non-negative)
     * @param direction Wind direction in degrees [0.0, 360.0)
     */
    public Wind(double speed, double direction) {
        this.speed = Math.max(0.0, speed);
        this.direction = normaliseDirection(direction);
    }

    /**
     * Normalises any angle in degrees to the standard half-open interval [0.0, 360.0).
     */
    public static double normaliseDirection(double degrees) {
        double normalised = degrees % 360.0;
        if (normalised < 0) {
            normalised += 360.0;
        }
        return normalised;
    }

    /**
     * Calculates the unit vector components (dx, dy) where dx is Eastward (+col) and dy is Northward (-row).
     *
     * @return double[] {dx, dy}
     */
    public double[] getDirectionVector() {
        double radians = Math.toRadians(direction);
        // In navigation/GIS: 0° is North (dy = +1 in math space), 90° is East (dx = +1)
        double dx = Math.sin(radians);
        double dy = Math.cos(radians);
        return new double[]{dx, dy};
    }

    /**
     * Returns the 16-point meteorological compass bearing abbreviation (e.g., "NE", "SSW").
     */
    public String getCompassDirection() {
        int index = (int) Math.round(((direction % 360.0) / 22.5)) % 16;
        return COMPASS_DIRECTIONS[index];
    }

    // =========================================================================
    // Getters and Setters
    // =========================================================================

    public double getSpeed() {
        return speed;
    }

    public void setSpeed(double speed) {
        this.speed = Math.max(0.0, speed);
    }

    public double getDirection() {
        return direction;
    }

    public void setDirection(double direction) {
        this.direction = normaliseDirection(direction);
    }

    @Override
    public String toString() {
        return String.format("Wind[speed=%.1f km/h, dir=%.1f deg (%s)]", speed, direction, getCompassDirection());
    }
}
