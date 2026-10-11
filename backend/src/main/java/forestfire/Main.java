package forestfire;

import forestfire.engine.FireSimulator;
import forestfire.engine.SpreadCalculator;
import forestfire.io.CsvLoader;
import forestfire.model.Cell;
import forestfire.model.ForestGrid;
import forestfire.model.Wind;

import java.io.File;
import java.util.List;

/**
 * =============================================================================
 * 🔥 Forest Fire Spread Predictor — Problem Statement No. 86
 * Semester Project | RGMCET B.Tech CSE (Data Science)
 * =============================================================================
 * File: backend/src/main/java/forestfire/Main.java
 *
 * Standalone verification entrypoint for Part 2 (Java OOP Simulation Engine).
 * Demonstrates CSV grid loading, graph construction, wind setup,
 * and cellular automaton fire propagation over multiple generations.
 * =============================================================================
 */
public class Main {

    public static void main(String[] args) {
        String csvPath = (args.length > 0) ? args[0] : resolveDefaultCsvPath();
        System.out.println("==============================================================================");
        System.out.println("  [JAVA CORE] FOREST FIRE SPREAD PREDICTOR -- PART 2 OOP ENGINE VERIFICATION");
        System.out.println("==============================================================================");
        System.out.println("Loading preprocessed grid from: " + csvPath);

        File file = new File(csvPath);
        if (!file.exists()) {
            System.err.println("[ERROR] CSV file does not exist at: " + file.getAbsolutePath());
            System.err.println("Please run Python preprocessing first: python python/preprocess.py --generate-sample");
            System.exit(1);
        }

        try {
            // 1. Load CSV and Auto-Detect Grid Dimensions
            CsvLoader.LoadResult result = CsvLoader.loadFromFile(csvPath);
            ForestGrid grid = result.getGrid();

            System.out.println("[SUCCESS] ForestGrid instantiated successfully!");
            System.out.printf("  * Dimensions       : %d Rows x %d Columns (%d total cells)\n",
                    grid.getRows(), grid.getCols(), grid.getTotalCells());
            System.out.printf("  * Records parsed   : %d\n", result.getRowsParsed());
            System.out.printf("  * Graph Topology   : 8-neighbour Moore Graph with %d undirected edges\n",
                    grid.getGraph().getEdgeCount());
            System.out.printf("  * Initial Wind     : %s\n", grid.getWind());
            System.out.printf("  * Pre-set fires    : %d ignition points detected\n",
                    result.getIgnitionPointsCount());

            // 2. Ensure at least one ignition cell is active
            if (result.getIgnitionPointsCount() == 0) {
                int startR = grid.getRows() / 2;
                int startC = grid.getCols() / 2;
                System.out.printf("  * No pre-set fire found: igniting center cell (%d, %d)...\n", startR, startC);
                grid.igniteCell(startR, startC);
            }

            // 3. Configure Wind Conditions (e.g. Southwest wind at 18 km/h)
            Wind customWind = new Wind(18.0, 225.0); // 225° SW wind blowing towards NE
            grid.setWind(customWind);
            System.out.println("  * Wind Override    : " + customWind + " (Blowing towards North-East)");

            // 4. Initialise Cellular Automaton Simulator with Seed for Deterministic Run
            SpreadCalculator calculator = new SpreadCalculator(42L); // Seeded for reproducibility
            FireSimulator simulator = new FireSimulator(grid, calculator, 1);

            System.out.println("\n------------------------------------------------------------------------------");
            System.out.println("  STARTING CELLULAR AUTOMATON SIMULATION TICKS");
            System.out.println("------------------------------------------------------------------------------");

            // Print initial step 0
            System.out.printf("Step %-3d | Burning: %-4d | Burned: %-4d | Unburned: %-4d | [INITIAL IGNITION]\n",
                    0, grid.getBurningCells().size(), grid.getBurnedCells().size(), grid.getUnburnedCells().size());

            // Advance 15 simulation generations or until extinguished
            int maxSteps = 15;
            for (int step = 1; step <= maxSteps; step++) {
                FireSimulator.StepSnapshot snapshot = simulator.advanceStep();
                System.out.println(snapshot);

                if (snapshot.isExtinguished()) {
                    System.out.println("\n>> Fire completely extinguished at Step " + step + "!");
                    break;
                }
            }

            // 5. Final Statistics Audit
            System.out.println("------------------------------------------------------------------------------");
            System.out.println("  SIMULATION AUDIT RESULTS");
            System.out.println("------------------------------------------------------------------------------");
            int total = grid.getTotalCells();
            int burned = grid.getBurnedCells().size();
            int burning = grid.getBurningCells().size();
            int unburned = grid.getUnburnedCells().size();
            double pctBurned = ((double) burned / total) * 100.0;

            System.out.printf("  * Total Cells      : %d\n", total);
            System.out.printf("  * Total Burned Out : %d (%.1f%% of forest area)\n", burned, pctBurned);
            System.out.printf("  * Actively Burning : %d\n", burning);
            System.out.printf("  * Remaining Intact : %d (%.1f%% preserved)\n", unburned, ((double) unburned / total) * 100.0);
            System.out.println("==============================================================================\n");

        } catch (Exception ex) {
            System.err.println("[FATAL ERROR] Simulation failed: " + ex.getMessage());
            ex.printStackTrace();
            System.exit(2);
        }
    }

    private static String resolveDefaultCsvPath() {
        String[] candidates = {
                "data/processed/processed_grid.csv",
                "../data/processed/processed_grid.csv",
                "../../data/processed/processed_grid.csv"
        };
        for (String c : candidates) {
            File f = new File(c);
            if (f.exists()) {
                return f.getAbsolutePath();
            }
        }
        return "data/processed/processed_grid.csv";
    }
}
