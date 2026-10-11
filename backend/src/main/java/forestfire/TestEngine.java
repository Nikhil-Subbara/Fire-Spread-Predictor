package forestfire;

import forestfire.engine.FireSimulator;
import forestfire.engine.SpreadCalculator;
import forestfire.io.CsvLoader;
import forestfire.model.Cell;
import forestfire.model.ForestGrid;
import forestfire.model.Graph;
import forestfire.model.Wind;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * =============================================================================
 * 🔥 Forest Fire Spread Predictor — Automated Java Unit & Integration Test Suite
 * Problem Statement No. 86 | RGMCET B.Tech CSE (Data Science)
 * =============================================================================
 * File: backend/src/main/java/forestfire/TestEngine.java
 * =============================================================================
 */
public class TestEngine {

    private static int testsPassed = 0;
    private static int testsFailed = 0;

    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null && actual == null) {
            testsPassed++;
            return;
        }
        if (expected != null && expected.equals(actual)) {
            testsPassed++;
        } else {
            testsFailed++;
            System.err.printf("[TEST FAILED] %s | Expected: %s, Actual: %s\n", message, expected, actual);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (condition) {
            testsPassed++;
        } else {
            testsFailed++;
            System.err.printf("[TEST FAILED] %s | Expected: true, Actual: false\n", message);
        }
    }

    public static void main(String[] args) {
        System.out.println("Running Java OOP Backend Unit Tests...\n");

        testCellModel();
        testWindModel();
        testForestGridAndGraph();
        testSpreadCalculatorPhysics();
        testFireSimulatorCellularAutomaton();
        testCsvLoader();

        System.out.println("\n--------------------------------------------------");
        System.out.printf("Test Execution Summary: %d PASSED, %d FAILED\n", testsPassed, testsFailed);
        System.out.println("--------------------------------------------------");

        if (testsFailed > 0) {
            System.exit(1);
        }
    }

    private static void testCellModel() {
        System.out.print("Testing Cell Model & State Transitions... ");
        Cell cell = new Cell(2, 3, 0.75, 250.0, 15.0, Cell.FireState.UNBURNED);

        assertEquals(2, cell.getRow(), "Cell row mismatch");
        assertEquals(3, cell.getCol(), "Cell col mismatch");
        assertTrue(cell.isUnburned(), "Cell should be unburned initially");

        // Double-buffering test
        cell.stageIgnition();
        assertTrue(cell.isUnburned(), "Cell state should remain UNBURNED before commit");
        assertEquals(Cell.FireState.BURNING, cell.getNextState(), "nextState should be BURNING");

        cell.commitNextState();
        assertTrue(cell.isBurning(), "Cell state should be BURNING after commit");
        assertEquals(1, cell.getBurnTicks(), "Burn ticks should be 1");

        cell.stageBurnout();
        cell.commitNextState();
        assertTrue(cell.isBurned(), "Cell state should be BURNED after burnout");
        assertEquals(0.0, cell.getRiskScore(), "Burned cells must have 0.0 risk");

        // Risk classification
        cell.setRiskScore(0.85);
        assertEquals(Cell.RiskLevel.VERY_HIGH, cell.getRiskLevel(), "Risk level should be VERY_HIGH for 0.85");
        cell.setRiskScore(0.35);
        assertEquals(Cell.RiskLevel.MODERATE, cell.getRiskLevel(), "Risk level should be MODERATE for 0.35");

        System.out.println("OK");
    }

    private static void testWindModel() {
        System.out.print("Testing Wind Model & Compass Bearings... ");
        Wind windNorth = new Wind(15.0, 0.0);
        assertEquals("N", windNorth.getCompassDirection(), "0° should be N");

        Wind windSW = new Wind(20.0, 225.0);
        assertEquals("SW", windSW.getCompassDirection(), "225° should be SW");

        // Angle normalisation
        Wind wrapWind = new Wind(10.0, 450.0);
        assertEquals(90.0, wrapWind.getDirection(), "450° should normalise to 90°");
        assertEquals("E", wrapWind.getCompassDirection(), "90° should be E");

        System.out.println("OK");
    }

    private static void testForestGridAndGraph() {
        System.out.print("Testing ForestGrid & 8-Neighbour Moore Graph... ");
        ForestGrid grid = new ForestGrid(5, 5);
        for (int r = 0; r < 5; r++) {
            for (int c = 0; c < 5; c++) {
                grid.setCell(r, c, new Cell(r, c, 0.5, 100.0));
            }
        }
        grid.buildNeighbourGraph();
        Graph graph = grid.getGraph();

        // Corner cell (0, 0) has 3 Moore neighbours
        List<Cell> cornerNeighbours = graph.getNeighbours(0, 0);
        assertEquals(3, cornerNeighbours.size(), "Corner cell should have 3 neighbours");

        // Edge cell (0, 2) has 5 Moore neighbours
        List<Cell> edgeNeighbours = graph.getNeighbours(0, 2);
        assertEquals(5, edgeNeighbours.size(), "Edge cell should have 5 neighbours");

        // Center cell (2, 2) has 8 Moore neighbours
        List<Cell> centerNeighbours = graph.getNeighbours(2, 2);
        assertEquals(8, centerNeighbours.size(), "Center cell should have 8 neighbours");

        // Multi-source BFS distance check
        Cell center = grid.getCell(2, 2);
        Map<Cell, Integer> distances = graph.computeMultiSourceBfsDistances(List.of(center), 2);
        assertEquals(0, distances.get(center), "Center distance should be 0");
        assertEquals(1, distances.get(grid.getCell(1, 2)), "Direct neighbour distance should be 1");
        assertEquals(2, distances.get(grid.getCell(0, 2)), "2-hop neighbour distance should be 2");

        System.out.println("OK");
    }

    private static void testSpreadCalculatorPhysics() {
        System.out.print("Testing SpreadCalculator Fire Spread Physics... ");
        SpreadCalculator calc = new SpreadCalculator(0.40, 0.05, 0.08, 10.0, null);

        Cell source = new Cell(1, 1, 0.8, 100.0, 0.0, Cell.FireState.BURNING);
        Cell northNeighbour = new Cell(0, 1, 0.8, 100.0, 0.0, Cell.FireState.UNBURNED);
        Cell southNeighbour = new Cell(2, 1, 0.8, 100.0, 0.0, Cell.FireState.UNBURNED);

        // Wind blowing North (0°): spread to north should be boosted, south suppressed
        Wind windNorth = new Wind(20.0, 0.0);
        double fWindNorth = calc.computeWindFactor(source, northNeighbour, windNorth);
        double fWindSouth = calc.computeWindFactor(source, southNeighbour, windNorth);

        assertTrue(fWindNorth > 1.0, "Downwind spread factor must be > 1.0");
        assertTrue(fWindSouth < 1.0, "Upwind spread factor must be < 1.0");

        // Slope test: target uphill (elevation 150m vs 100m)
        Cell uphillNeighbour = new Cell(0, 1, 0.8, 150.0, 0.0, Cell.FireState.UNBURNED);
        Cell downhillNeighbour = new Cell(2, 1, 0.8, 50.0, 0.0, Cell.FireState.UNBURNED);

        double fSlopeUphill = calc.computeSlopeFactor(source, uphillNeighbour);
        double fSlopeDownhill = calc.computeSlopeFactor(source, downhillNeighbour);

        assertTrue(fSlopeUphill > 1.0, "Uphill slope factor must be > 1.0");
        assertTrue(fSlopeDownhill < 1.0, "Downhill slope factor must be < 1.0");

        // Zero fuel test
        Cell barrenNeighbour = new Cell(0, 1, 0.0, 100.0, 0.0, Cell.FireState.UNBURNED);
        double pBarren = calc.computeSpreadProbability(source, barrenNeighbour, windNorth);
        assertEquals(0.0, pBarren, "Spread probability into zero fuel cell must be 0.0");

        System.out.println("OK");
    }

    private static void testFireSimulatorCellularAutomaton() {
        System.out.print("Testing FireSimulator Synchronous Double-Buffering... ");
        ForestGrid grid = new ForestGrid(3, 3);
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                grid.setCell(r, c, new Cell(r, c, 0.9, 100.0));
            }
        }
        grid.buildNeighbourGraph();
        grid.igniteCell(1, 1); // Ignite center

        // Deterministic spread calculator with 100% spread probability
        SpreadCalculator certainSpread = new SpreadCalculator(1.0, 0.0, 0.0, 10.0, null) {
            @Override
            public boolean testIgnition(Cell s, Cell t, Wind w) {
                return true;
            }
        };

        FireSimulator sim = new FireSimulator(grid, certainSpread, 1);
        assertEquals(1, sim.getActiveFrontier().size(), "Step 0: Center cell must be burning");

        // Step 1: Center cell burns out; all 8 neighbours ignite synchronously
        FireSimulator.StepSnapshot step1 = sim.advanceStep();
        assertEquals(8, step1.getBurningCount(), "Step 1: All 8 neighbours must be burning");
        assertEquals(1, step1.getBurnedCount(), "Step 1: Center cell must be burned out");
        assertEquals(0, step1.getUnburnedCount(), "Step 1: No unburned cells should remain");

        // Step 2: All 8 neighbours burn out; no unburned cells remain -> fire extinguishes
        FireSimulator.StepSnapshot step2 = sim.advanceStep();
        assertEquals(0, step2.getBurningCount(), "Step 2: No cells should be burning");
        assertEquals(9, step2.getBurnedCount(), "Step 2: All 9 cells must be burned");
        assertTrue(step2.isExtinguished(), "Step 2: Fire must be extinguished");

        System.out.println("OK");
    }

    private static void testCsvLoader() {
        System.out.print("Testing CsvLoader Stream Parsing & Auto-Dimensions... ");
        String sampleCsv = "row,col,vegetation,elevation,slope,wind_dir,wind_speed,fire\n" +
                "0,0,0.8,200,5.0,90,12,0\n" +
                "0,1,0.7,210,6.0,90,12,0\n" +
                "1,0,0.6,220,7.0,90,12,1\n" +
                "1,1,0.5,230,8.0,90,12,0\n";

        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(sampleCsv.getBytes(StandardCharsets.UTF_8));
            CsvLoader.LoadResult result = CsvLoader.loadFromStream(bais);

            ForestGrid grid = result.getGrid();
            assertEquals(2, grid.getRows(), "Inferred rows should be 2");
            assertEquals(2, grid.getCols(), "Inferred cols should be 2");
            assertEquals(4, result.getRowsParsed(), "Parsed rows should be 4");
            assertEquals(1, result.getIgnitionPointsCount(), "Ignition count should be 1");
            assertTrue(grid.getCell(1, 0).isBurning(), "Cell (1, 0) should be BURNING");
            assertTrue(grid.getCell(0, 0).isUnburned(), "Cell (0, 0) should be UNBURNED");

            System.out.println("OK");
        } catch (Exception ex) {
            testsFailed++;
            System.err.println("[TEST FAILED] CsvLoader exception: " + ex.getMessage());
        }
    }
}
