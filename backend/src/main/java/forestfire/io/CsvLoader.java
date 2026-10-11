package forestfire.io;

import forestfire.model.Cell;
import forestfire.model.ForestGrid;
import forestfire.model.Wind;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * =============================================================================
 * 🔥 Forest Fire Spread Predictor — Problem Statement No. 86
 * Semester Project | RGMCET B.Tech CSE (Data Science)
 * =============================================================================
 * File: backend/src/main/java/forestfire/io/CsvLoader.java
 *
 * Robust CSV dataset parser for loading grid data into the Java simulation engine.
 * Automatically discovers grid dimensions (rows × cols), parses environmental
 * attributes, builds the ForestGrid, and pre-wires the 8-neighbour adjacency graph.
 * =============================================================================
 */
public class CsvLoader {

    /**
     * DTO for storing parsing and validation summary metadata.
     */
    public static class LoadResult {
        private final ForestGrid grid;
        private final int rowsParsed;
        private final int ignitionPointsCount;
        private final List<String> warnings;

        public LoadResult(ForestGrid grid, int rowsParsed, int ignitionPointsCount, List<String> warnings) {
            this.grid = grid;
            this.rowsParsed = rowsParsed;
            this.ignitionPointsCount = ignitionPointsCount;
            this.warnings = warnings;
        }

        public ForestGrid getGrid() {
            return grid;
        }

        public int getRowsParsed() {
            return rowsParsed;
        }

        public int getIgnitionPointsCount() {
            return ignitionPointsCount;
        }

        public List<String> getWarnings() {
            return warnings;
        }
    }

    /**
     * Internal raw row record before grid construction.
     */
    private static class RawRecord {
        int row;
        int col;
        double vegetation;
        double elevation;
        double slope;
        double windDir;
        double windSpeed;
        int fire;
    }

    /**
     * Loads a processed or raw CSV from a file path.
     */
    public static LoadResult loadFromFile(String filePath) throws IOException {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new FileNotFoundException("Grid CSV file not found: " + file.getAbsolutePath());
        }
        try (InputStream is = new FileInputStream(file)) {
            return loadFromStream(is);
        }
    }

    /**
     * Loads a processed or raw CSV from an InputStream (useful for Javalin multipart uploads).
     */
    public static LoadResult loadFromStream(InputStream inputStream) throws IOException {
        List<RawRecord> records = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        int maxRow = -1;
        int maxCol = -1;
        double lastWindSpeed = 0.0;
        double lastWindDir = 0.0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null || headerLine.trim().isEmpty()) {
                throw new IOException("CSV stream is empty or contains no headers.");
            }

            // Map header positions to canonical field indices
            Map<String, Integer> headerIndexMap = parseHeader(headerLine);
            validateHeaderMap(headerIndexMap);

            String line;
            int lineNumber = 1;

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue; // Skip comments and blank lines
                }

                String[] tokens = line.split(",");
                try {
                    RawRecord rec = new RawRecord();
                    rec.row = (int) Double.parseDouble(tokens[headerIndexMap.get("row")].trim());
                    rec.col = (int) Double.parseDouble(tokens[headerIndexMap.get("col")].trim());

                    if (rec.row < 0 || rec.col < 0) {
                        warnings.add(String.format("Line %d: Ignored negative coordinate (%d, %d)", lineNumber, rec.row, rec.col));
                        continue;
                    }

                    rec.vegetation = headerIndexMap.containsKey("vegetation")
                            ? Double.parseDouble(tokens[headerIndexMap.get("vegetation")].trim()) : 0.50;
                    rec.elevation = headerIndexMap.containsKey("elevation")
                            ? Double.parseDouble(tokens[headerIndexMap.get("elevation")].trim()) : 100.0;
                    rec.slope = headerIndexMap.containsKey("slope")
                            ? Double.parseDouble(tokens[headerIndexMap.get("slope")].trim()) : 0.0;

                    if (headerIndexMap.containsKey("wind_dir")) {
                        rec.windDir = Double.parseDouble(tokens[headerIndexMap.get("wind_dir")].trim());
                        lastWindDir = rec.windDir;
                    }
                    if (headerIndexMap.containsKey("wind_speed")) {
                        rec.windSpeed = Double.parseDouble(tokens[headerIndexMap.get("wind_speed")].trim());
                        lastWindSpeed = rec.windSpeed;
                    }
                    rec.fire = headerIndexMap.containsKey("fire")
                            ? (int) Double.parseDouble(tokens[headerIndexMap.get("fire")].trim()) : 0;

                    maxRow = Math.max(maxRow, rec.row);
                    maxCol = Math.max(maxCol, rec.col);
                    records.add(rec);

                } catch (Exception ex) {
                    warnings.add(String.format("Line %d: Parsing error (%s)", lineNumber, ex.getMessage()));
                }
            }
        }

        if (records.isEmpty()) {
            throw new IOException("No valid data rows found in CSV dataset.");
        }

        // Automatic dimension detection
        int totalRows = maxRow + 1;
        int totalCols = maxCol + 1;

        ForestGrid grid = new ForestGrid(totalRows, totalCols);
        grid.setWind(new Wind(lastWindSpeed, lastWindDir));

        int ignitionCount = 0;
        // Populate grid with parsed records
        for (RawRecord rec : records) {
            Cell.FireState state = Cell.FireState.fromCode(rec.fire);
            Cell cell = new Cell(rec.row, rec.col, rec.vegetation, rec.elevation, rec.slope, state);
            if (state == Cell.FireState.BURNING) {
                ignitionCount++;
            }
            grid.setCell(rec.row, rec.col, cell);
        }

        // Fill any unpopulated lattice holes with default neutral cells
        for (int r = 0; r < totalRows; r++) {
            for (int c = 0; c < totalCols; c++) {
                if (grid.getCell(r, c) == null) {
                    grid.setCell(r, c, new Cell(r, c, 0.5, 100.0, 0.0, Cell.FireState.UNBURNED));
                }
            }
        }

        // Build 8-neighbour adjacency graph
        grid.buildNeighbourGraph();

        return new LoadResult(grid, records.size(), ignitionCount, warnings);
    }

    /**
     * Resolves CSV headers and identifies aliases to canonical names.
     */
    private static Map<String, Integer> parseHeader(String headerLine) {
        Map<String, Integer> map = new HashMap<>();
        String[] headers = headerLine.split(",");

        for (int i = 0; i < headers.length; i++) {
            String h = headers[i].trim().toLowerCase().replaceAll("[\\s\\-\\.\\#]+", "_");

            if (h.equals("row") || h.equals("y") || h.equals("r") || h.equals("row_idx")) {
                map.put("row", i);
            } else if (h.equals("col") || h.equals("x") || h.equals("c") || h.equals("col_idx")) {
                map.put("col", i);
            } else if (h.equals("vegetation") || h.equals("ndvi") || h.equals("fuel") || h.equals("veg")) {
                map.put("vegetation", i);
            } else if (h.equals("elevation") || h.equals("altitude") || h.equals("elev") || h.equals("dem")) {
                map.put("elevation", i);
            } else if (h.equals("slope") || h.equals("gradient") || h.equals("incline")) {
                map.put("slope", i);
            } else if (h.equals("wind_dir") || h.equals("wind_direction") || h.equals("winddir")) {
                map.put("wind_dir", i);
            } else if (h.equals("wind_speed") || h.equals("wind") || h.equals("windspeed")) {
                map.put("wind_speed", i);
            } else if (h.equals("fire") || h.equals("ignition") || h.equals("state")) {
                map.put("fire", i);
            }
        }
        return map;
    }

    private static void validateHeaderMap(Map<String, Integer> map) throws IOException {
        if (!map.containsKey("row") || !map.containsKey("col")) {
            throw new IOException("Missing mandatory coordinate headers ('row'/'y' and 'col'/'x'). Found: " + map.keySet());
        }
    }
}
