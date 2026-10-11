package forestfire.model;

import java.util.*;

/**
 * =============================================================================
 * 🔥 Forest Fire Spread Predictor — Problem Statement No. 86
 * Semester Project | RGMCET B.Tech CSE (Data Science)
 * =============================================================================
 * File: backend/src/main/java/forestfire/model/Graph.java
 *
 * Implements an 8-neighbour (Moore Neighbourhood) undirected graph topology
 * over the ForestGrid. Provides BFS traversal algorithms for frontier propagation,
 * connected components, and multi-source distance maps for live risk heatmaps.
 * =============================================================================
 */
public class Graph {

    // 8-Directional Moore Neighbourhood Offsets: {dRow, dCol}
    // Cardinal (Orthogonal): N, S, W, E
    // Intercardinal (Diagonal): NW, NE, SW, SE
    public static final int[][] MOORE_DIRECTIONS = {
        {-1,  0}, // 0: North
        { 1,  0}, // 1: South
        { 0, -1}, // 2: West
        { 0,  1}, // 3: East
        {-1, -1}, // 4: North-West (Diagonal)
        {-1,  1}, // 5: North-East (Diagonal)
        { 1, -1}, // 6: South-West (Diagonal)
        { 1,  1}  // 7: South-East (Diagonal)
    };

    private final ForestGrid grid;
    // Pre-computed adjacency list: cell -> list of valid 8 neighbours
    private final Map<Cell, List<Cell>> adjacencyList;

    /**
     * Constructs the graph and pre-computes 8-neighbour connections for each cell.
     *
     * @param grid The associated ForestGrid
     */
    public Graph(ForestGrid grid) {
        this.grid = Objects.requireNonNull(grid, "ForestGrid cannot be null");
        this.adjacencyList = new HashMap<>(grid.getTotalCells());
        buildAdjacency();
    }

    /**
     * Iterates over all grid cells and connects Moore neighbours within bounds.
     */
    private void buildAdjacency() {
        int rows = grid.getRows();
        int cols = grid.getCols();

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                Cell current = grid.getCell(r, c);
                if (current == null) {
                    continue;
                }

                List<Cell> neighbours = new ArrayList<>(8);
                for (int[] dir : MOORE_DIRECTIONS) {
                    int nr = r + dir[0];
                    int nc = c + dir[1];
                    if (grid.isValidCoordinate(nr, nc)) {
                        Cell neighbour = grid.getCell(nr, nc);
                        if (neighbour != null) {
                            neighbours.add(neighbour);
                        }
                    }
                }
                adjacencyList.put(current, Collections.unmodifiableList(neighbours));
            }
        }
    }

    /**
     * Retrieves all valid 8-neighbours for a given cell.
     */
    public List<Cell> getNeighbours(Cell cell) {
        if (cell == null) {
            return Collections.emptyList();
        }
        return adjacencyList.getOrDefault(cell, Collections.emptyList());
    }

    /**
     * Retrieves all valid 8-neighbours for coordinates (row, col).
     */
    public List<Cell> getNeighbours(int row, int col) {
        Cell cell = grid.getCell(row, col);
        return getNeighbours(cell);
    }

    /**
     * Returns only the neighbours that are currently UNBURNED (susceptible to spread).
     */
    public List<Cell> getUnburnedNeighbours(Cell cell) {
        List<Cell> all = getNeighbours(cell);
        List<Cell> unburned = new ArrayList<>(all.size());
        for (Cell n : all) {
            if (n.isUnburned()) {
                unburned.add(n);
            }
        }
        return unburned;
    }

    /**
     * Computes multi-source BFS shortest graph distances from a collection of source cells.
     * Used by the Live Risk Engine to compute proximity decay away from the active fire front.
     *
     * @param sources Active burning cells (ignition front)
     * @param maxHops Maximum graph search depth (e.g., 5-10 hops)
     * @return Map of Cell -> integer shortest distance in hops
     */
    public Map<Cell, Integer> computeMultiSourceBfsDistances(Collection<Cell> sources, int maxHops) {
        Map<Cell, Integer> distanceMap = new HashMap<>();
        if (sources == null || sources.isEmpty()) {
            return distanceMap;
        }

        Queue<Cell> queue = new ArrayDeque<>();
        for (Cell src : sources) {
            if (src != null) {
                distanceMap.put(src, 0);
                queue.offer(src);
            }
        }

        while (!queue.isEmpty()) {
            Cell current = queue.poll();
            int currentDist = distanceMap.get(current);

            if (currentDist >= maxHops) {
                continue;
            }

            for (Cell neighbour : getNeighbours(current)) {
                if (!distanceMap.containsKey(neighbour)) {
                    distanceMap.put(neighbour, currentDist + 1);
                    queue.offer(neighbour);
                }
            }
        }

        return distanceMap;
    }

    /**
     * Checks if two cells are direct neighbours (Moore distance <= 1).
     */
    public boolean areNeighbours(Cell a, Cell b) {
        if (a == null || b == null) return false;
        int dRow = Math.abs(a.getRow() - b.getRow());
        int dCol = Math.abs(a.getCol() - b.getCol());
        return (dRow <= 1 && dCol <= 1) && !(dRow == 0 && dCol == 0);
    }

    /**
     * Checks if the relationship between a and b is diagonal (vs orthogonal).
     */
    public static boolean isDiagonal(Cell a, Cell b) {
        return Math.abs(a.getRow() - b.getRow()) == 1 && Math.abs(a.getCol() - b.getCol()) == 1;
    }

    public ForestGrid getGrid() {
        return grid;
    }

    public int getEdgeCount() {
        int total = 0;
        for (List<Cell> list : adjacencyList.values()) {
            total += list.size();
        }
        return total / 2; // Undirected graph
    }
}
