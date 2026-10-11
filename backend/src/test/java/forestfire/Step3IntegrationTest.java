package forestfire;

import forestfire.engine.FireSimulator;
import forestfire.engine.RiskAssessment;
import forestfire.engine.SpreadCalculator;
import forestfire.model.Cell;
import forestfire.model.ForestGrid;
import forestfire.model.Wind;
import forestfire.report.ReportManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * =============================================================================
 * 🔥 Forest Fire Spread Predictor — Step 3 Unit & Integration Test Suite
 * Problem Statement No. 86 | RGMCET B.Tech CSE (Data Science)
 * =============================================================================
 * File: backend/src/test/java/forestfire/Step3IntegrationTest.java
 *
 * Validates Step 3 deliverables:
 *   1. RiskAssessment dynamic risk recalibration & tier classification.
 *   2. ReportManager JSON payload building & statistics aggregation.
 *   3. App.SimulationService lifecycle (init, step, state, wind, ignite, report).
 * =============================================================================
 */
public class Step3IntegrationTest {

    @Test
    @DisplayName("Verify Dynamic Live Risk Recalibration & State Transitions")
    void testRiskAssessmentDynamicRecalibration() {
        ForestGrid grid = new ForestGrid(10, 10);
        for (int r = 0; r < 10; r++) {
            for (int c = 0; c < 10; c++) {
                grid.setCell(r, c, new Cell(r, c, 0.70, 100.0, 5.0, Cell.FireState.UNBURNED));
            }
        }
        grid.setWind(new Wind(20.0, 90.0)); // Wind blowing East (90 deg)
        grid.buildNeighbourGraph();

        // Ignite cell (5, 5)
        grid.igniteCell(5, 5);

        RiskAssessment riskAssessment = new RiskAssessment();
        RiskAssessment.RiskStats stats = riskAssessment.recalibrate(grid);

        assertNotNull(stats, "RiskStats must not be null");

        // Burning cell (5, 5) must have maximum risk 1.0
        Cell burningCell = grid.getCell(5, 5);
        assertEquals(1.0, burningCell.getRiskScore(), 0.001, "Burning cell risk must be 1.0");
        assertEquals(Cell.RiskLevel.VERY_HIGH, burningCell.getRiskLevel());

        // Cell (5, 6) is directly East (downwind from fire) -> should have higher risk than (5, 4) upwind
        Cell downwindCell = grid.getCell(5, 6);
        Cell upwindCell = grid.getCell(5, 4);

        assertTrue(downwindCell.getRiskScore() > upwindCell.getRiskScore(),
                "Downwind cell should have strictly higher risk than upwind cell due to wind alignment");

        // Transition (5, 5) to BURNED
        burningCell.stageBurnout();
        burningCell.commitNextState();
        assertTrue(burningCell.isBurned(), "Cell must now be BURNED");

        // Recalibrate grid when fire has burned out
        RiskAssessment.RiskStats coolStats = riskAssessment.recalibrate(grid, Collections.emptyList());
        assertEquals(0.0, burningCell.getRiskScore(), 0.001, "Burned cell must have 0.0 risk");
        assertEquals("Burned", RiskAssessment.getEffectiveRiskCategory(burningCell));
    }

    @Test
    @DisplayName("Verify ReportManager Step & State Payload Generation")
    void testReportManagerPayloads() {
        ForestGrid grid = new ForestGrid(5, 5);
        for (int r = 0; r < 5; r++) {
            for (int c = 0; c < 5; c++) {
                grid.setCell(r, c, new Cell(r, c, 0.60, 100.0, 0.0, Cell.FireState.UNBURNED));
            }
        }
        grid.igniteCell(2, 2);
        grid.buildNeighbourGraph();

        FireSimulator simulator = new FireSimulator(grid, new SpreadCalculator(42L), 1);
        RiskAssessment riskAssessment = new RiskAssessment();
        ReportManager reportManager = new ReportManager();

        RiskAssessment.RiskStats riskStats = riskAssessment.recalibrate(grid, simulator.getActiveFrontier());
        FireSimulator.StepSnapshot snapshot = simulator.advanceStep();
        riskStats = riskAssessment.recalibrate(grid, simulator.getActiveFrontier());

        ReportManager.StepPayload stepPayload = reportManager.buildStepPayload(grid, snapshot, riskStats);
        assertNotNull(stepPayload);
        assertEquals(1, stepPayload.getStep());
        assertEquals(5, stepPayload.getStates().length);
        assertEquals(5, stepPayload.getStates()[0].length);
        assertEquals(5, stepPayload.getRisk().length);
        assertNotNull(stepPayload.getStats().get("burning"));
        assertNotNull(stepPayload.getStats().get("burned"));
        assertNotNull(stepPayload.getStats().get("unburned"));
        assertNotNull(stepPayload.getStats().get("affectedAreaHa"));

        ReportManager.StatePayload statePayload = reportManager.buildStatePayload(grid, 1, riskStats, simulator.isExtinguished());
        assertNotNull(statePayload);
        assertEquals(5, statePayload.getRows());
        assertEquals(5, statePayload.getCols());
        assertEquals(25, statePayload.getTotalCells());
        assertNotNull(statePayload.getWind());
        assertNotNull(statePayload.getRiskLevels());

        ReportManager.FinalReport finalReport = reportManager.buildFinalReport(grid, 1, "NE");
        assertNotNull(finalReport);
        assertEquals(25, finalReport.getTotalCells());
        assertEquals(1, finalReport.getStepHistory().size());
    }

    @Test
    @DisplayName("Verify App SimulationService Full Workflow Integration")
    void testSimulationServiceLifecycle() {
        App.SimulationService service = new App.SimulationService();

        // 1. Initialise simulation
        App.InitRequest initReq = new App.InitRequest();
        initReq.ignitionPoints = List.of(Map.of("row", 10, "col", 10));
        initReq.windSpeed = 16.0;
        initReq.windDirection = 45.0; // NE wind
        initReq.burnDuration = 1;
        initReq.seed = 99L;

        ReportManager.StatePayload initial = service.initSimulation(initReq);
        assertNotNull(initial);
        assertEquals(0, initial.getStep());
        assertEquals(1, ((Number) initial.getStats().get("burning")).intValue());

        // 2. Advance 1 step
        ReportManager.StepPayload step1 = service.advanceStep(1);
        assertNotNull(step1);
        assertEquals(1, step1.getStep());

        // 3. Update wind mid-simulation
        ReportManager.StatePayload windUpdate = service.updateWind(25.0, 180.0);
        assertNotNull(windUpdate);
        assertEquals(25.0, (Double) windUpdate.getWind().get("speed"), 0.001);

        // 4. Click-to-ignite
        ReportManager.StatePayload igniteUpdate = service.igniteCell(3, 3);
        assertNotNull(igniteUpdate);

        // 5. Query full state
        ReportManager.StatePayload state = service.getState();
        assertNotNull(state);

        // 6. Diagnostics
        Map<String, Object> health = service.getHealthDiagnostics();
        assertEquals("UP", health.get("status"));
        assertEquals(86, health.get("problemStatement"));

        // 7. Reset
        ReportManager.StatePayload resetState = service.resetSimulation();
        assertEquals(0, resetState.getStep());
    }

    @Test
    @DisplayName("Verify End-to-End Javalin HTTP REST API Endpoints")
    void testEndToEndHttpEndpoints() throws Exception {
        App.SimulationService service = new App.SimulationService();
        io.javalin.Javalin app = App.createApp(service, 0);
        app.start(0);
        int port = app.port();

        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        String baseUrl = "http://localhost:" + port;

        try {
            // 1. GET /api/health
            java.net.http.HttpRequest healthReq = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(baseUrl + "/api/health"))
                    .GET()
                    .build();
            java.net.http.HttpResponse<String> healthRes = client.send(healthReq, java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(200, healthRes.statusCode());
            assertTrue(healthRes.body().contains("\"status\":\"UP\""));

            // 2. GET /api/simulation/state
            java.net.http.HttpRequest stateReq = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(baseUrl + "/api/simulation/state"))
                    .GET()
                    .build();
            java.net.http.HttpResponse<String> stateRes = client.send(stateReq, java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(200, stateRes.statusCode());
            assertTrue(stateRes.body().contains("\"states\""));
            assertTrue(stateRes.body().contains("\"risk\""));

            // 3. POST /api/simulation/step
            java.net.http.HttpRequest stepReq = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(baseUrl + "/api/simulation/step"))
                    .POST(java.net.http.HttpRequest.BodyPublishers.noBody())
                    .build();
            java.net.http.HttpResponse<String> stepRes = client.send(stepReq, java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(200, stepRes.statusCode());
            assertTrue(stepRes.body().contains("\"step\":1"));

            // 4. POST /api/simulation/init
            String initJson = "{\"ignitionPoints\":[{\"row\":5,\"col\":5}],\"windSpeed\":12.0,\"windDirection\":90.0}";
            java.net.http.HttpRequest initReq = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(baseUrl + "/api/simulation/init"))
                    .header("Content-Type", "application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(initJson))
                    .build();
            java.net.http.HttpResponse<String> initRes = client.send(initReq, java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(200, initRes.statusCode());
            assertTrue(initRes.body().contains("\"step\":0"));

            // 5. POST /api/simulation/ignite
            String igniteJson = "{\"row\":2,\"col\":3}";
            java.net.http.HttpRequest igniteReq = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(baseUrl + "/api/simulation/ignite"))
                    .header("Content-Type", "application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(igniteJson))
                    .build();
            java.net.http.HttpResponse<String> igniteRes = client.send(igniteReq, java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(200, igniteRes.statusCode());
            assertTrue(igniteRes.body().contains("\"status\":\"success\""));
            assertTrue(igniteRes.body().contains("\"row\":2"));
            assertTrue(igniteRes.body().contains("\"col\":3"));
        } finally {
            app.stop();
        }
    }
}
