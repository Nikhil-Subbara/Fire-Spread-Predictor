package forestfire.model;

import java.util.ArrayList;
import java.util.List;

/**
 * =============================================================================
 * 🔥 Forest Fire Spread Predictor — Problem Statement No. 86
 * Semester Project | RGMCET B.Tech CSE (Data Science)
 * =============================================================================
 * File: backend/src/main/java/forestfire/model/ForestGrid.java
 *
 * Represents the discretised 2-D forest landscape of size (rows × cols).
 * Manages spatial access to Cell objects, state inspections, wind conditions,
 * and maintains the associated graph adjacency topology.
 * =============================================================================
 */
public class ForestGrid {

    private final int rows;
    private final int cols;
    private final Cell[][] cells;
    private Wind wind;
    private Graph graph;

    /**
     * Constructs an empty ForestGrid of specified dimensions.
     *
     * @param rows Total number of grid rows (height)
     * @param cols Total number of grid columns (width)
     */
    public ForestGrid(int rows, int cols) {
        if (rows <= 0 || cols <= 0) {
            throw new IllegalArgumentException(String.format("Invalid grid dimensions: %dx%d", rows, cols));
        }
        this.rows = rows;
        this.cols = cols;
        this.cells = new Cell[rows][cols];
        this.wind = new Wind();
    }

    /**
     * Checks if given row and column indices fall strictly within the grid boundaries.
     */
    public boolean isValidCoordinate(int row, int col) {
        return row >= 0 && row < rows && col >= 0 && col < cols;
    }

    /**
     * Retrieves the Cell at (row, col) or null if out of bounds.
     */
    public Cell getCell(int row, int col) {
        if (!isValidCoordinate(row, col)) {
            return null;
        }
        return cells[row][col];
    }

    /**
     * Sets a Cell at the designated (row, col) location.
     */
    public void setCell(int row, int col, Cell cell) {
        if (!isValidCoordinate(row, col)) {
            throw excitingOutOfBoundsException(row, col);
        }
        cells[row][col] = cell;
    }

    private IndexOutOfBoundsException excitingOutOfBoundsException(int row, int col) {
        return new IndexOutOfBoundsException(
                String.format("Coordinate (%d, %d) is outside grid boundaries [%dx%d]", row, col, rows, cols)
        );
    }

    /**
     * Initialises and builds the 8-neighbour adjacency graph for this grid.
     */
    public void buildNeighbourGraph() {
        this.graph = new Graph(this);
    }

    /**
     * Ignites a cell at the specified coordinates, transitioning its state to BURNING.
     */
    public boolean igniteCell(int row, int col) {
        Cell cell = getCell(row, col);
        if (cell != null) {
            cell.ignite();
            return true;
        }
        return false;
    }

    /**
     * Resets all cells back to their initial state prior to simulation.
     */
    public void resetAllCells() {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (cells[r][c] != null) {
                    cells[r][c].reset();
                }
            }
        }
    }

    /**
     * Clears all active or past fires across the entire grid, setting all cells to UNBURNED.
     */
    public void clearAllFires() {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (cells[r][c] != null) {
                    cells[r][c].setState(Cell.FireState.UNBURNED);
                    cells[r][c].setBurnTicks(0);
                    cells[r][c].setRiskScore(0.0);
                }
            }
        }
    }

    /**
     * Returns a list of all currently BURNING cells.
     */
    public List<Cell> getBurningCells() {
        List<Cell> burning = new ArrayList<>();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (cells[r][c] != null && cells[r][c].isBurning()) {
                    burning.add(cells[r][c]);
                }
            }
        }
        return burning;
    }

    /**
     * Returns a list of all BURNED cells.
     */
    public List<Cell> getBurnedCells() {
        List<Cell> burned = new ArrayList<>();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (cells[r][c] != null && cells[r][c].isBurned()) {
                    burned.add(cells[r][c]);
                }
            }
        }
        return burned;
    }

    /**
     * Returns a list of all UNBURNED cells.
     */
    public List<Cell> getUnburnedCells() {
        List<Cell> unburned = new ArrayList<>();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (cells[r][c] != null && cells[r][c].isUnburned()) {
                    unburned.add(cells[r][c]);
                }
            }
        }
        return unburned;
    }

    // =========================================================================
    // Getters and Setters
    // =========================================================================

    public int getRows() {
        return rows;
    }

    public int getCols() {
        return cols;
    }

    public int getTotalCells() {
        return rows * cols;
    }

    public Cell[][] getCells() {
        return cells;
    }

    public Wind getWind() {
        return wind;
    }

    public void setWind(Wind wind) {
        this.wind = (wind != null) ? wind : new Wind();
    }

    public Graph getGraph() {
        if (this.graph == null) {
            buildNeighbourGraph();
        }
        return graph;
    }

    @Override
    public String toString() {
        return String.format("ForestGrid[%dx%d | Burning: %d, Burned: %d, Unburned: %d | %s]",
                rows, cols, getBurningCells().size(), getBurnedCells().size(), getUnburnedCells().size(), wind);
    }
}
