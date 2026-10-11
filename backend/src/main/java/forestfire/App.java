package forestfire;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import forestfire.engine.FireSimulator;
import forestfire.engine.RiskAssessment;
import forestfire.engine.SpreadCalculator;
import forestfire.io.CsvLoader;
import forestfire.model.Cell;
import forestfire.model.ForestGrid;
import forestfire.model.Wind;
import forestfire.report.ReportManager;
import io.javalin.Javalin;
import io.javalin.http.ContentType;
import io.javalin.http.HttpStatus;
import io.javalin.http.UploadedFile;
import io.javalin.http.staticfiles.Location;
import io.javalin.json.JavalinJackson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.util.*;

/**
 * =============================================================================
 * 🔥 Forest Fire Spread Predictor — Problem Statement No. 86
 * Semester Project | RGMCET B.Tech CSE (Data Science)
 * =============================================================================
 * File: backend/src/main/java/forestfire/App.java
 *
 * Main Application Entry Point & Javalin REST API Controller.
 *
 * Responsibilities:
 * 1. Initializes and configures lightweight Javalin web server (default port
 * 7070).
 * 2. Configures CORS headers and static file serving for web frontend
 * dashboard.
 * 3. Exposes REST API endpoints matching Problem Statement No. 86
 * specification:
 * • POST /api/simulation/init — Initializes grid, wind, ignition points
 * • POST /api/simulation/step — Advances cellular automaton 1..N steps
 * • GET /api/simulation/state — Returns complete grid, states & risk heatmap
 * • POST /api/simulation/wind — Dynamically updates wind speed & direction
 * • POST /api/simulation/ignite — Interactive click-to-ignite cell placement
 * • POST /api/simulation/reset — Resets simulation back to step 0
 * • POST /api/upload — Dynamic multipart CSV dataset upload
 * • GET /api/report — Comprehensive final post-simulation audit
 * • GET /api/health — Backend runtime health and diagnostics
 * =============================================================================
 */
public class App {

    private static final Logger logger = LoggerFactory.getLogger(App.class);
    private static final int DEFAULT_PORT = 7070;

    // =========================================================================
    // Request DTOs
    // =========================================================================

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class InitRequest {
        public List<Map<String, Integer>> ignitionPoints;
        public List<List<Integer>> ignitionCells;
        public Double windSpeed;
        public Double windDirection;
        public Integer burnDuration;
        public Long seed;
        public String csvPath;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StepRequest {
        public Integer steps;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WindRequest {
        public Double speed;
        public Double direction;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class IgniteRequest {
        public Integer row;
        public Integer col;
    }

    // =========================================================================
    // Thread-Safe Simulation Service State
    // =========================================================================

    public static class SimulationService {
        private ForestGrid grid;
        private FireSimulator simulator;
        private final RiskAssessment riskAssessment;
        private final ReportManager reportManager;
        private RiskAssessment.RiskStats latestRiskStats;
        private String currentDatasetName;
        private final Object lock = new Object();

        public SimulationService() {
            this.riskAssessment = new RiskAssessment();
            this.reportManager = new ReportManager();
            this.currentDatasetName = "processed_grid.csv";
            loadDefaultGrid();
        }

        private void loadDefaultGrid() {
            synchronized (lock) {
                String candidatePath = resolveDefaultCsvPath();
                File csvFile = new File(candidatePath);

                if (csvFile.exists()) {
                    try {
                        logger.info("Loading default dataset from: {}", csvFile.getAbsolutePath());
                        CsvLoader.LoadResult result = CsvLoader.loadFromFile(csvFile.getAbsolutePath());
                        this.grid = result.getGrid();
                        this.currentDatasetName = csvFile.getName();
                        logger.info("Loaded grid: {}x{} ({} cells)", grid.getRows(), grid.getCols(),
                                grid.getTotalCells());
                    } catch (Exception ex) {
                        logger.warn("Failed to load CSV at {}: {}. Checking classpath resource.",
                                candidatePath, ex.getMessage());
                    }
                }

                // Fallback to bundled classpath dataset if filesystem path does not exist
                if (this.grid == null) {
                    try (InputStream is = App.class.getResourceAsStream("/data/processed/processed_grid.csv")) {
                        if (is != null) {
                            logger.info("Loading default dataset from CLASSPATH: /data/processed/processed_grid.csv");
                            CsvLoader.LoadResult result = CsvLoader.loadFromStream(is);
                            this.grid = result.getGrid();
                            this.currentDatasetName = "processed_grid.csv";
                            logger.info("Loaded grid from classpath: {}x{} ({} cells)", grid.getRows(), grid.getCols(),
                                    grid.getTotalCells());
                        }
                    } catch (Exception ex) {
                        logger.warn("Failed to load CSV from classpath resource: {}", ex.getMessage());
                    }
                }

                if (this.grid == null) {
                    logger.warn("No processed CSV found at {} or on classpath. Generating procedural grid fallback.",
                            candidatePath);
                    this.grid = createProceduralGrid(30, 30);
                }

                // Ensure at least one ignition point exists
                if (grid.getBurningCells().isEmpty()) {
                    int midR = grid.getRows() / 2;
                    int midC = grid.getCols() / 2;
                    grid.igniteCell(midR, midC);
                }

                this.simulator = new FireSimulator(grid, new SpreadCalculator(42L), 1);
                this.reportManager.reset();
                this.latestRiskStats = riskAssessment.recalibrate(grid, simulator.getActiveFrontier());
            }
        }

        private ForestGrid createProceduralGrid(int rows, int cols) {
            ForestGrid g = new ForestGrid(rows, cols);
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    double veg = 0.65;
                    double elev = 100.0 + (r * 1.5) + (c * 1.0);
                    double slope = 5.0;
                    g.setCell(r, c, new Cell(r, c, veg, elev, slope, Cell.FireState.UNBURNED));
                }
            }
            g.setWind(new Wind(15.0, 225.0));
            g.buildNeighbourGraph();
            return g;
        }

        public ReportManager.StatePayload initSimulation(InitRequest req) {
            synchronized (lock) {
                // 1. Reload grid dataset
                String path = (req != null && req.csvPath != null && !req.csvPath.trim().isEmpty())
                        ? req.csvPath
                        : resolveDefaultCsvPath();

                File file = new File(path);
                if (file.exists()) {
                    try {
                        CsvLoader.LoadResult res = CsvLoader.loadFromFile(file.getAbsolutePath());
                        this.grid = res.getGrid();
                        this.currentDatasetName = file.getName();
                    } catch (Exception ex) {
                        logger.error("Error reloading dataset from disk: {}", ex.getMessage());
                    }
                }

                if (this.grid == null) {
                    try (InputStream is = App.class.getResourceAsStream("/data/processed/processed_grid.csv")) {
                        if (is != null) {
                            CsvLoader.LoadResult res = CsvLoader.loadFromStream(is);
                            this.grid = res.getGrid();
                            this.currentDatasetName = "processed_grid.csv";
                        }
                    } catch (Exception ex) {
                        logger.error("Error reloading dataset from classpath: {}", ex.getMessage());
                    }
                }

                if (this.grid == null) {
                    this.grid = createProceduralGrid(30, 30);
                }

                // 2. Configure Wind
                if (req != null) {
                    double speed = (req.windSpeed != null) ? req.windSpeed : grid.getWind().getSpeed();
                    double dir = (req.windDirection != null) ? req.windDirection : grid.getWind().getDirection();
                    grid.setWind(new Wind(speed, dir));
                }

                // 3. Clear existing fires and assign new ignition cells
                boolean customIgnitionProvided = false;
                if (req != null) {
                    if (req.ignitionPoints != null && !req.ignitionPoints.isEmpty()) {
                        grid.clearAllFires();
                        for (Map<String, Integer> pt : req.ignitionPoints) {
                            Integer r = pt.get("row");
                            Integer c = pt.get("col");
                            if (r != null && c != null && grid.isValidCoordinate(r, c)) {
                                grid.igniteCell(r, c);
                                customIgnitionProvided = true;
                            }
                        }
                    } else if (req.ignitionCells != null && !req.ignitionCells.isEmpty()) {
                        grid.clearAllFires();
                        for (List<Integer> pt : req.ignitionCells) {
                            if (pt != null && pt.size() >= 2) {
                                int r = pt.get(0);
                                int c = pt.get(1);
                                if (grid.isValidCoordinate(r, c)) {
                                    grid.igniteCell(r, c);
                                    customIgnitionProvided = true;
                                }
                            }
                        }
                    }
                }

                // If no fires active, ignite center
                if (!customIgnitionProvided && grid.getBurningCells().isEmpty()) {
                    int midR = grid.getRows() / 2;
                    int midC = grid.getCols() / 2;
                    grid.igniteCell(midR, midC);
                }

                // 4. Instantiate Simulator
                Long seed = (req != null && req.seed != null) ? req.seed : 42L;
                int duration = (req != null && req.burnDuration != null) ? req.burnDuration : 1;
                this.simulator = new FireSimulator(grid, new SpreadCalculator(seed), duration);
                this.reportManager.reset();

                // 5. Recalibrate Live Risk Heatmap
                this.latestRiskStats = riskAssessment.recalibrate(grid, simulator.getActiveFrontier());

                return reportManager.buildStatePayload(grid, 0, latestRiskStats, simulator.isExtinguished());
            }
        }

        public ReportManager.StepPayload advanceStep(int steps) {
            synchronized (lock) {
                int count = Math.max(1, steps);
                FireSimulator.StepSnapshot snapshot = null;

                for (int i = 0; i < count; i++) {
                    snapshot = simulator.advanceStep();
                    this.latestRiskStats = riskAssessment.recalibrate(grid, simulator.getActiveFrontier());
                    if (snapshot.isExtinguished()) {
                        break;
                    }
                }

                if (snapshot == null) {
                    this.latestRiskStats = riskAssessment.recalibrate(grid, simulator.getActiveFrontier());
                    return reportManager.buildStepPayload(grid,
                            new FireSimulator.StepSnapshot(simulator.getCurrentStep(),
                                    grid.getBurningCells().size(),
                                    grid.getBurnedCells().size(),
                                    grid.getUnburnedCells().size(),
                                    0, 0, 0),
                            latestRiskStats);
                }

                return reportManager.buildStepPayload(grid, snapshot, latestRiskStats);
            }
        }

        public ReportManager.StatePayload getState() {
            synchronized (lock) {
                if (latestRiskStats == null) {
                    this.latestRiskStats = riskAssessment.recalibrate(grid, simulator.getActiveFrontier());
                }
                return reportManager.buildStatePayload(grid, simulator.getCurrentStep(), latestRiskStats,
                        simulator.isExtinguished());
            }
        }

        public ReportManager.StatePayload updateWind(double speed, double direction) {
            synchronized (lock) {
                Wind newWind = new Wind(speed, direction);
                grid.setWind(newWind);
                this.latestRiskStats = riskAssessment.recalibrate(grid, simulator.getActiveFrontier());
                return reportManager.buildStatePayload(grid, simulator.getCurrentStep(), latestRiskStats,
                        simulator.isExtinguished());
            }
        }

        public ForestGrid getGrid() {
            synchronized (lock) {
                return grid;
            }
        }

        public ReportManager.StatePayload igniteCell(int row, int col) {
            synchronized (lock) {
                if (grid.isValidCoordinate(row, col)) {
                    simulator.ignite(row, col);
                    // Recalculate dynamic risk heatmap using RiskAssessment.updateRiskHeatmap(grid)
                    this.latestRiskStats = riskAssessment.updateRiskHeatmap(grid);
                }
                return reportManager.buildStatePayload(grid, simulator.getCurrentStep(), latestRiskStats,
                        simulator.isExtinguished());
            }
        }

        public ReportManager.StatePayload resetSimulation() {
            synchronized (lock) {
                simulator.reset();
                reportManager.reset();
                this.latestRiskStats = riskAssessment.recalibrate(grid, simulator.getActiveFrontier());
                return reportManager.buildStatePayload(grid, 0, latestRiskStats, simulator.isExtinguished());
            }
        }

        public Map<String, Object> loadUploadedCsv(InputStream inputStream, String filename) throws Exception {
            synchronized (lock) {
                CsvLoader.LoadResult result = CsvLoader.loadFromStream(inputStream);
                this.grid = result.getGrid();
                this.currentDatasetName = (filename != null) ? filename : "uploaded_grid.csv";

                if (grid.getBurningCells().isEmpty()) {
                    grid.igniteCell(grid.getRows() / 2, grid.getCols() / 2);
                }

                this.simulator = new FireSimulator(grid, new SpreadCalculator(42L), 1);
                this.reportManager.reset();
                this.latestRiskStats = riskAssessment.recalibrate(grid, simulator.getActiveFrontier());

                Map<String, Object> response = new LinkedHashMap<>();
                response.put("status", "success");
                response.put("dataset", currentDatasetName);
                response.put("rows", grid.getRows());
                response.put("cols", grid.getCols());
                response.put("totalCells", grid.getTotalCells());
                response.put("edges", grid.getGraph().getEdgeCount());
                response.put("ignitionPoints", result.getIgnitionPointsCount());
                response.put("warnings", result.getWarnings());
                return response;
            }
        }

        public ReportManager.FinalReport getFinalReport() {
            synchronized (lock) {
                String dominantDir = (latestRiskStats != null) ? latestRiskStats.getDominantThreatDirection() : "NE";
                return reportManager.buildFinalReport(grid, simulator.getCurrentStep(), dominantDir);
            }
        }

        public Map<String, Object> getHealthDiagnostics() {
            synchronized (lock) {
                Map<String, Object> health = new LinkedHashMap<>();
                health.put("status", "UP");
                health.put("service", "Forest Fire Spread Predictor API");
                health.put("problemStatement", 86);
                health.put("dataset", currentDatasetName);
                health.put("dimensions", String.format("%dx%d", grid.getRows(), grid.getCols()));
                health.put("activeStep", simulator.getCurrentStep());
                health.put("burningCells", grid.getBurningCells().size());
                health.put("burnedCells", grid.getBurnedCells().size());
                health.put("unburnedCells", grid.getUnburnedCells().size());
                health.put("isExtinguished", simulator.isExtinguished());
                return health;
            }
        }
    }

    // =========================================================================
    // Helper Path Resolvers
    // =========================================================================

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

    private static File resolveFrontendDirectory() {
        String[] candidates = {
                "frontend",
                "../frontend",
                "../../frontend"
        };
        for (String c : candidates) {
            File f = new File(c);
            if (f.exists() && f.isDirectory()) {
                return f;
            }
        }
        return new File("frontend");
    }

    // =========================================================================
    // Javalin Application Bootstrapper
    // =========================================================================

    public static Javalin createApp(SimulationService service, int port) {
        File frontendDir = resolveFrontendDirectory();
        ObjectMapper mapper = new ObjectMapper();

        Javalin app = Javalin.create(config -> {
            // Configure Jackson JsonMapper
            config.jsonMapper(new JavalinJackson(mapper, true));

            // Enable CORS across all routes for smooth client communication
            config.bundledPlugins.enableCors(cors -> cors.addRule(it -> it.anyHost()));

            // Static file serving for web frontend
            if (frontendDir.exists()) {
                logger.info("Serving frontend static assets from: {}", frontendDir.getAbsolutePath());
                config.staticFiles.add(staticFile -> {
                    staticFile.hostedPath = "/";
                    staticFile.directory = frontendDir.getAbsolutePath();
                    staticFile.location = Location.EXTERNAL;
                });
            } else {
                logger.info("Serving frontend static assets from CLASSPATH: /frontend");
                config.staticFiles.add(staticFile -> {
                    staticFile.hostedPath = "/";
                    staticFile.directory = "/frontend";
                    staticFile.location = Location.CLASSPATH;
                });
            }

            // Clean request logging
            config.requestLogger.http((ctx, ms) -> {
                if (ctx.path().startsWith("/api")) {
                    logger.debug("[REST] {} {} - Status: {} ({}ms)", ctx.method(), ctx.path(), ctx.status(), ms);
                }
            });
        });

        // =====================================================================
        // REST API Endpoints
        // =====================================================================

        // 1. POST /api/simulation/init (initializes the grid, wind, and state)
        app.post("/api/simulation/init", ctx -> {
            InitRequest req = null;
            try {
                if (ctx.body() != null && !ctx.body().trim().isEmpty()) {
                    req = ctx.bodyAsClass(InitRequest.class);
                }
            } catch (Exception ex) {
                logger.warn("Failed to parse /api/simulation/init JSON body: {}. Using defaults.", ex.getMessage());
            }
            ReportManager.StatePayload payload = service.initSimulation(req);
            ctx.status(HttpStatus.OK).json(payload);
        });

        // 2. POST /api/simulation/step (advances cellular automaton by 1..N steps)
        app.post("/api/simulation/step", ctx -> {
            int steps = 1;
            String countParam = ctx.queryParam("count");
            if (countParam != null && !countParam.trim().isEmpty()) {
                try {
                    steps = Integer.parseInt(countParam);
                } catch (NumberFormatException ignored) {
                }
            } else if (ctx.body() != null && !ctx.body().trim().isEmpty()) {
                try {
                    StepRequest req = ctx.bodyAsClass(StepRequest.class);
                    if (req.steps != null && req.steps > 0) {
                        steps = req.steps;
                    }
                } catch (Exception ignored) {
                }
            }
            ReportManager.StepPayload stepPayload = service.advanceStep(steps);
            ctx.status(HttpStatus.OK).json(stepPayload);
        });

        // 3. GET /api/simulation/state (returns full grid state, attributes & risk)
        app.get("/api/simulation/state", ctx -> {
            ReportManager.StatePayload state = service.getState();
            ctx.status(HttpStatus.OK).json(state);
        });

        // 4. POST /api/simulation/wind (dynamically updates wind speed & direction)
        app.post("/api/simulation/wind", ctx -> {
            WindRequest req = ctx.bodyAsClass(WindRequest.class);
            if (req.speed == null || req.direction == null) {
                ctx.status(HttpStatus.BAD_REQUEST).json(Map.of("error", "Missing 'speed' or 'direction' field"));
                return;
            }
            ReportManager.StatePayload updatedState = service.updateWind(req.speed, req.direction);
            ctx.status(HttpStatus.OK).json(updatedState);
        });

        // 5. POST /api/simulation/ignite (interactive click-to-ignite point)
        app.post("/api/simulation/ignite", ctx -> {
            IgniteRequest req = ctx.bodyAsClass(IgniteRequest.class);
            if (req.row == null || req.col == null) {
                ctx.status(HttpStatus.BAD_REQUEST).json(Map.of(
                        "status", "error",
                        "error", "Missing 'row' or 'col' field"));
                return;
            }
            if (!service.getGrid().isValidCoordinate(req.row, req.col)) {
                ctx.status(HttpStatus.BAD_REQUEST).json(Map.of(
                        "status", "error",
                        "error", String.format("Coordinate [%d, %d] is out of grid bounds", req.row, req.col)));
                return;
            }

            ReportManager.StatePayload updatedState = service.igniteCell(req.row, req.col);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", "success");
            response.put("message", String.format("Cell ignited successfully at [%d, %d]", req.row, req.col));
            response.put("row", req.row);
            response.put("col", req.col);
            response.put("step", updatedState.getStep());
            response.put("rows", updatedState.getRows());
            response.put("cols", updatedState.getCols());
            response.put("totalCells", updatedState.getTotalCells());
            response.put("states", updatedState.getStates());
            response.put("risk", updatedState.getRisk());
            response.put("riskLevels", updatedState.getRiskLevels());
            response.put("vegetation", updatedState.getVegetation());
            response.put("elevation", updatedState.getElevation());
            response.put("slope", updatedState.getSlope());
            response.put("wind", updatedState.getWind());
            response.put("stats", updatedState.getStats());

            ctx.status(HttpStatus.OK).json(response);
        });

        // 6. POST /api/simulation/reset (resets simulation to step 0)
        app.post("/api/simulation/reset", ctx -> {
            ReportManager.StatePayload resetState = service.resetSimulation();
            ctx.status(HttpStatus.OK).json(resetState);
        });

        // 7. POST /api/upload (multipart CSV dataset upload)
        app.post("/api/upload", ctx -> {
            UploadedFile uploadedFile = ctx.uploadedFile("file");
            if (uploadedFile == null && !ctx.uploadedFiles().isEmpty()) {
                uploadedFile = ctx.uploadedFiles().get(0);
            }

            if (uploadedFile == null) {
                ctx.status(HttpStatus.BAD_REQUEST)
                        .json(Map.of("error", "No file uploaded. Expected multipart 'file'."));
                return;
            }

            try (InputStream is = uploadedFile.content()) {
                Map<String, Object> uploadResult = service.loadUploadedCsv(is, uploadedFile.filename());
                ctx.status(HttpStatus.OK).json(uploadResult);
            } catch (Exception ex) {
                logger.error("Failed to parse uploaded CSV: {}", ex.getMessage());
                ctx.status(HttpStatus.UNPROCESSABLE_CONTENT).json(Map.of(
                        "error", "CSV parsing failed: " + ex.getMessage(),
                        "hint", "Ensure CSV contains row, col, vegetation, elevation columns."));
            }
        });

        // 8. GET /api/report (final simulation statistics & step history audit)
        app.get("/api/report", ctx -> {
            ReportManager.FinalReport report = service.getFinalReport();
            ctx.status(HttpStatus.OK).json(report);
        });

        // 9. GET /api/health (backend system health & diagnostics)
        app.get("/api/health", ctx -> {
            ctx.status(HttpStatus.OK).json(service.getHealthDiagnostics());
        });

        // Entrypoint on GET /: serve frontend dashboard index.html if present, else fallback
        app.get("/", ctx -> {
            File indexFile = new File(frontendDir, "index.html");
            if (indexFile.exists()) {
                ctx.contentType(ContentType.TEXT_HTML);
                ctx.result(java.nio.file.Files.readString(indexFile.toPath()));
            } else {
                try (InputStream is = App.class.getResourceAsStream("/frontend/index.html")) {
                    if (is != null) {
                        ctx.contentType(ContentType.TEXT_HTML);
                        ctx.result(new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
                        return;
                    }
                } catch (Exception ignored) {
                }
                ctx.contentType(ContentType.TEXT_HTML);
                ctx.result(renderWelcomeHtml(service));
            }
        });

        // Global Exception Handler
        app.exception(Exception.class, (e, ctx) -> {
            logger.error("Internal Server Error on {}: {}", ctx.path(), e.getMessage(), e);
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("error", true);
            err.put("status", 500);
            err.put("message", e.getMessage());
            err.put("path", ctx.path());
            err.put("timestamp", System.currentTimeMillis());
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(err);
        });

        return app;
    }

    private static String renderWelcomeHtml(SimulationService service) {
        Map<String, Object> h = service.getHealthDiagnostics();
        return "<!DOCTYPE html>\n" +
                "<html lang=\"en\">\n" +
                "<head>\n" +
                "  <meta charset=\"UTF-8\">\n" +
                "  <title>🔥 Forest Fire Spread Predictor — Backend Ready</title>\n" +
                "  <style>\n" +
                "    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;\n"
                +
                "           background: #121820; color: #ecf0f1; margin: 0; padding: 40px; display: flex; justify-content: center; }\n"
                +
                "    .container { max-width: 820px; width: 100%; background: #1b2430; border-radius: 12px; padding: 32px;\n"
                +
                "                 box-shadow: 0 10px 30px rgba(0,0,0,0.5); border: 1px solid #2c3e50; }\n" +
                "    h1 { color: #e67e22; margin-top: 0; font-size: 26px; border-bottom: 2px solid #e67e22; padding-bottom: 12px; }\n"
                +
                "    .badge { display: inline-block; background: #27ae60; color: white; padding: 4px 10px; border-radius: 6px; font-weight: bold; font-size: 13px; }\n"
                +
                "    pre { background: #0f141c; padding: 16px; border-radius: 8px; color: #2ecc71; overflow-x: auto; font-family: Consolas, monospace; }\n"
                +
                "    table { width: 100%; border-collapse: collapse; margin: 20px 0; }\n" +
                "    th, td { text-align: left; padding: 10px; border-bottom: 1px solid #2c3e50; font-size: 14px; }\n" +
                "    th { color: #f39c12; }\n" +
                "    .code { font-family: monospace; color: #3498db; background: #16202c; padding: 2px 6px; border-radius: 4px; }\n"
                +
                "  </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "  <div class=\"container\">\n" +
                "    <h1>🔥 Forest Fire Spread Predictor — Javalin REST API</h1>\n" +
                "    <p><span class=\"badge\">API ONLINE</span> &nbsp; Problem Statement No. 86 | RGMCET B.Tech CSE (Data Science)</p>\n"
                +
                "    <p>The backend simulation server is active and responding on port <strong>7070</strong>. Connect your HTML5/JS frontend to the endpoints below:</p>\n"
                +
                "    <table>\n" +
                "      <tr><th>Method</th><th>Endpoint</th><th>Description</th></tr>\n" +
                "      <tr><td><span class=\"code\">POST</span></td><td><span class=\"code\">/api/simulation/init</span></td><td>Initializes grid, wind, & ignition cells</td></tr>\n"
                +
                "      <tr><td><span class=\"code\">POST</span></td><td><span class=\"code\">/api/simulation/step</span></td><td>Advances cellular automaton by 1 step</td></tr>\n"
                +
                "      <tr><td><span class=\"code\">GET</span></td><td><span class=\"code\">/api/simulation/state</span></td><td>Returns full 2-D grid & live risk heatmap</td></tr>\n"
                +
                "      <tr><td><span class=\"code\">POST</span></td><td><span class=\"code\">/api/simulation/wind</span></td><td>Updates wind speed & direction dynamically</td></tr>\n"
                +
                "      <tr><td><span class=\"code\">POST</span></td><td><span class=\"code\">/api/simulation/ignite</span></td><td>Interactive canvas click-to-ignite</td></tr>\n"
                +
                "      <tr><td><span class=\"code\">POST</span></td><td><span class=\"code\">/api/simulation/reset</span></td><td>Resets simulation to initial state</td></tr>\n"
                +
                "      <tr><td><span class=\"code\">POST</span></td><td><span class=\"code\">/api/upload</span></td><td>Multipart CSV file upload & grid auto-detect</td></tr>\n"
                +
                "      <tr><td><span class=\"code\">GET</span></td><td><span class=\"code\">/api/report</span></td><td>Final simulation stats & step history</td></tr>\n"
                +
                "      <tr><td><span class=\"code\">GET</span></td><td><span class=\"code\">/api/health</span></td><td>Diagnostics & system health</td></tr>\n"
                +
                "    </table>\n" +
                "    <h3>Live Engine Diagnostics</h3>\n" +
                "    <pre>" + h.toString() + "</pre>\n" +
                "  </div>\n" +
                "</body>\n" +
                "</html>";
    }

    // =========================================================================
    // Main Entry Point
    // =========================================================================

    public static void main(String[] args) {
        int port = DEFAULT_PORT;

        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {
            }
        } else if (System.getenv("PORT") != null) {
            try {
                port = Integer.parseInt(System.getenv("PORT"));
            } catch (NumberFormatException ignored) {
            }
        }

        System.out.println("==============================================================================");
        System.out.println("  [JAVALIN REST API] FOREST FIRE SPREAD PREDICTOR (PROBLEM STATEMENT NO. 86)");
        System.out.println("==============================================================================");
        System.out.println("  * Web Server Port  : " + port);
        System.out.println("  * Backend Engine   : Java 17 Cellular Automaton + Graph BFS");
        System.out.println("  * Dynamic Risk     : Live Multi-Source Proximity Heatmap Recalibration");
        System.out.println("==============================================================================");

        try {
            SimulationService service = new SimulationService();
            Javalin app = createApp(service, port);
            app.start(port);

            System.out.println("\n[SERVER STARTED SUCCESSFULLY]");
            System.out.printf("  >> REST Endpoints : http://localhost:%d/api/simulation/state\n", port);
            System.out.printf("  >> Health Check   : http://localhost:%d/api/health\n", port);
            System.out.printf("  >> Web Dashboard  : http://localhost:%d/\n", port);
            System.out.println("==============================================================================\n");

        } catch (Exception ex) {
            logger.error("Failed to start Javalin server: {}", ex.getMessage(), ex);
            System.err.println("[FATAL] Could not bind server to port " + port + ": " + ex.getMessage());
            System.exit(1);
        }
    }
}
