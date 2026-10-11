/**
 * =============================================================================
 * 🔥 Forest Fire Spread Predictor — Problem Statement No. 86
 * Semester Project | RGMCET B.Tech CSE (Data Science)
 * File: frontend/js/controls.js
 *
 * Master Controller & User Interaction Orchestrator.
 * Binds UI buttons, playback timers, wind compass dials, CSV drag-and-drop,
 * view toggling, and coordinates between FireApi, FireCanvas, and FireCharts.
 * =============================================================================
 */

const FireControls = (() => {
  // Playback State
  let isPlaying = false;
  let playIntervalId = null;
  let tickIntervalMs = 300;
  let activeInteractionMode = "ignite"; // "ignite" | "inspect"

  /**
   * Application Bootstrapper on DOM Ready.
   */
  async function init() {
    setupPlaybackControls();
    setupWindControls();
    setupViewSwitchers();
    setupCanvasTools();
    setupCsvUploader();
    setupThemeToggle();
    setupModalAndExportButtons();
    setupStatusPillClick();

    // Initialize sub-modules
    FireCanvas.init();
    FireCharts.init();

    // Verify backend connectivity and load initial state
    await checkConnectionAndLoadInitialState();
  }

  function setupStatusPillClick() {
    const statusPill = document.getElementById("serverStatusPill");
    if (!statusPill) return;
    statusPill.style.cursor = "pointer";
    statusPill.addEventListener("click", () => {
      const current = FireApi.getBaseUrl() || (window.location.hostname === "localhost" ? "http://localhost:7070" : "");
      const input = prompt(
        "Configure Backend API Server URL:\n" +
        "• Leave empty to use local backend (http://localhost:7070)\n" +
        "• Or enter your hosted backend (e.g., https://your-backend.onrender.com)",
        current
      );
      if (input !== null) {
        FireApi.setBaseUrl(input);
        checkConnectionAndLoadInitialState();
      }
    });
  }

  /**
   * Pings the Javalin backend and pulls the initial forest grid.
   */
  async function checkConnectionAndLoadInitialState() {
    const statusPill = document.getElementById("serverStatusPill");
    const statusLabel = document.getElementById("serverStatusText");
    const statusDot = statusPill.querySelector(".status-dot");

    try {
      const health = await FireApi.checkHealth();
      statusDot.className = "status-dot online";
      const targetName = FireApi.getBaseUrl() ? "Render" : "Local:7070";
      statusLabel.textContent = `Backend Online (${targetName})`;
      showToast("Connected to Java Cellular Automaton Engine", "success");

      // Load initial grid state from real Java backend
      const state = await FireApi.getState();
      applyGridState(state);

    } catch (err) {
      statusDot.className = "status-dot";
      statusLabel.textContent = "Backend Offline (Click to configure)";
      const isCloud = window.location.hostname !== "localhost" && window.location.hostname !== "127.0.0.1";
      if (isCloud && !FireApi.getBaseUrl()) {
        showToast("Backend not configured. Click the Server Status pill to enter your Render backend URL, or use ?backend=https://...", "warning");
      } else {
        showToast(`Backend connection failed: ${err.message}. Click status pill to verify API URL.`, "error");
      }
      console.warn("[FireControls] Health check failed:", err.message);
    }
  }

  /**
   * Ingests full grid state from the backend and updates Canvas, KPIs, and Wind.
   */
  function applyGridState(state) {
    if (!state) return;
    cachedLatestState = state;

    // Update Grid dimension badge
    const dimEl = document.getElementById("gridDimensionsText");
    if (dimEl) dimEl.textContent = `${state.rows} × ${state.cols} (${state.totalCells} cells)`;

    // Sync Wind Controls if present
    if (state.wind) {
      syncWindUi(state.wind.speed, state.wind.direction, state.wind.compass);
    }

    // Update Canvas
    FireCanvas.updateData(state);

    // Update KPIs and Charts
    FireCharts.updateStats(state.stats, state.step, state.totalCells);

    // If fire is extinguished, pause automatically
    if (state.stats && state.stats.isExtinguished && isPlaying) {
      pauseSimulation();
      showToast(`Simulation complete: Fire extinguished at Step ${state.step}!`, "info");
    }
  }

  /**
   * Playback Controls: Start, Pause, Step, Reset.
   */
  function setupPlaybackControls() {
    const startBtn = document.getElementById("startBtn");
    const pauseBtn = document.getElementById("pauseBtn");
    const stepBtn = document.getElementById("stepBtn");
    const resetBtn = document.getElementById("resetBtn");
    const speedSlider = document.getElementById("speedSlider");
    const speedText = document.getElementById("speedValueText");

    startBtn.addEventListener("click", () => startSimulation());
    pauseBtn.addEventListener("click", () => pauseSimulation());

    stepBtn.addEventListener("click", async () => {
      if (isPlaying) pauseSimulation();
      await advanceSingleStep();
    });

    resetBtn.addEventListener("click", async () => {
      pauseSimulation();
      try {
        const resetState = await FireApi.resetSimulation();
        FireCharts.reset();
        applyGridState(resetState);
        showToast("Simulation reset to initial generation Step 0", "info");
      } catch (err) {
        showToast("Reset failed: " + err.message, "error");
      }
    });

    // Speed Slider
    speedSlider.addEventListener("input", (e) => {
      tickIntervalMs = Number(e.target.value);
      speedText.textContent = `${tickIntervalMs} ms`;
      if (isPlaying) {
        // Restart interval with new rate
        clearInterval(playIntervalId);
        playIntervalId = setInterval(advanceSingleStep, tickIntervalMs);
      }
    });

    // Keyboard Shortcuts
    document.addEventListener("keydown", (e) => {
      if (e.target.tagName === "INPUT" || e.target.tagName === "TEXTAREA") return;
      if (e.code === "Space") {
        e.preventDefault();
        isPlaying ? pauseSimulation() : startSimulation();
      } else if (e.code === "KeyS") {
        advanceSingleStep();
      } else if (e.code === "KeyR") {
        resetBtn.click();
      } else if (e.code === "KeyV") {
        // Toggle view
        const isHeatmap = FireCanvas.getViewMode() === "heatmap";
        setViewMode(isHeatmap ? "state" : "heatmap");
      }
    });
  }

  function startSimulation() {
    if (isPlaying) return;
    isPlaying = true;

    document.getElementById("startBtn").disabled = true;
    document.getElementById("pauseBtn").disabled = false;

    playIntervalId = setInterval(advanceSingleStep, tickIntervalMs);
    showToast("Simulation running...", "info");
  }

  function pauseSimulation() {
    if (!isPlaying) return;
    isPlaying = false;

    document.getElementById("startBtn").disabled = false;
    document.getElementById("pauseBtn").disabled = true;

    if (playIntervalId) {
      clearInterval(playIntervalId);
      playIntervalId = null;
    }
  }

  async function advanceSingleStep() {
    try {
      const stepPayload = await FireApi.advanceStep(1);

      if (cachedLatestState) {
        // Merge latest step matrix and stats from Java Cellular Automaton
        cachedLatestState.step = stepPayload.step;
        cachedLatestState.states = stepPayload.states;
        cachedLatestState.risk = stepPayload.risk;
        cachedLatestState.stats = stepPayload.stats;

        FireCanvas.updateData(cachedLatestState);
        FireCharts.updateStats(stepPayload.stats, stepPayload.step, cachedLatestState.totalCells);

        if (stepPayload.stats && stepPayload.stats.isExtinguished && isPlaying) {
          pauseSimulation();
          showToast(`Simulation complete: Fire extinguished at Step ${stepPayload.step}!`, "info");
        }
      } else {
        const fullState = await FireApi.getState();
        applyGridState(fullState);
      }
    } catch (err) {
      pauseSimulation();
      showToast("Simulation tick failed: " + err.message, "error");
    }
  }

  /**
   * Wind Controls: Compass Dial, Sliders, and Quick Presets.
   */
  function setupWindControls() {
    const dirSlider = document.getElementById("windDirSlider");
    const speedSlider = document.getElementById("windSpeedSlider");
    const dirText = document.getElementById("windDirText");
    const speedText = document.getElementById("windSpeedText");
    const applyBtn = document.getElementById("applyWindBtn");

    function updateWindSliders(speed, dir) {
      dirSlider.value = dir;
      speedSlider.value = speed;
      dirText.textContent = `${dir}°`;
      speedText.textContent = `${speed} km/h`;
      rotateCompass(dir);
    }

    dirSlider.addEventListener("input", (e) => {
      const deg = Number(e.target.value);
      dirText.textContent = `${deg}°`;
      rotateCompass(deg);
    });

    speedSlider.addEventListener("input", (e) => {
      const spd = Number(e.target.value);
      speedText.textContent = `${spd} km/h`;
    });

    // Preset buttons
    document.querySelectorAll(".wind-preset").forEach(btn => {
      btn.addEventListener("click", () => {
        const spd = Number(btn.getAttribute("data-speed"));
        const dir = Number(btn.getAttribute("data-dir"));
        updateWindSliders(spd, dir);
      });
    });

    // Apply wind mid-simulation
    applyBtn.addEventListener("click", async () => {
      const speed = Number(speedSlider.value);
      const dir = Number(dirSlider.value);
      try {
        const updatedState = await FireApi.updateWind(speed, dir);
        applyGridState(updatedState);
        showToast(`Wind updated to ${speed} km/h at ${dir}°! Heatmap recalibrated.`, "success");
      } catch (err) {
        showToast("Failed to update wind: " + err.message, "error");
      }
    });
  }

  function rotateCompass(degrees) {
    const needle = document.getElementById("compassNeedle");
    const degText = document.getElementById("compassDegreeText");
    const bearingText = document.getElementById("compassBearingText");

    if (needle) needle.style.transform = `translate(-50%, -50%) rotate(${degrees}deg)`;
    if (degText) degText.textContent = `${degrees}°`;

    const bearings = ["N", "NE", "E", "SE", "S", "SW", "W", "NW", "N"];
    const idx = Math.round(degrees / 45) % 8;
    if (bearingText) bearingText.textContent = bearings[idx];
  }

  function syncWindUi(speed, direction, compass) {
    const dirSlider = document.getElementById("windDirSlider");
    const speedSlider = document.getElementById("windSpeedSlider");
    const dirText = document.getElementById("windDirText");
    const speedText = document.getElementById("windSpeedText");
    const readoutTag = document.getElementById("windReadoutTag");

    if (dirSlider) dirSlider.value = direction;
    if (speedSlider) speedSlider.value = speed;
    if (dirText) dirText.textContent = `${direction}°`;
    if (speedText) speedText.textContent = `${speed} km/h`;
    if (readoutTag) readoutTag.textContent = `${speed} km/h • ${direction}° ${compass || ""}`;

    rotateCompass(direction);
  }

  /**
   * View Switcher: Fire State View vs Live Risk Heatmap.
   */
  function setupViewSwitchers() {
    const stateBtn = document.getElementById("viewStateBtn");
    const heatmapBtn = document.getElementById("viewHeatmapBtn");
    const legendState = document.getElementById("legendFireState");
    const legendHeatmap = document.getElementById("legendHeatmap");

    stateBtn.addEventListener("click", () => setViewMode("state"));
    heatmapBtn.addEventListener("click", () => setViewMode("heatmap"));

    // Interaction tool modes
    const igniteBtn = document.getElementById("modeIgniteBtn");
    const inspectBtn = document.getElementById("modeInspectBtn");
    const modeHelp = document.getElementById("modeHelpText");

    igniteBtn.addEventListener("click", () => {
      activeInteractionMode = "ignite";
      igniteBtn.classList.add("active");
      inspectBtn.classList.remove("active");
      modeHelp.textContent = "Click on any canvas cell to spawn a new ignition point.";
    });

    inspectBtn.addEventListener("click", () => {
      activeInteractionMode = "inspect";
      inspectBtn.classList.add("active");
      igniteBtn.classList.remove("active");
      modeHelp.textContent = "Hover or click on any cell to inspect terrain, fuel, and risk metrics.";
    });
  }

  function setViewMode(mode) {
    const stateBtn = document.getElementById("viewStateBtn");
    const heatmapBtn = document.getElementById("viewHeatmapBtn");
    const legendState = document.getElementById("legendFireState");
    const legendHeatmap = document.getElementById("legendHeatmap");

    if (mode === "heatmap") {
      FireCanvas.setViewMode("heatmap");
      heatmapBtn.classList.add("active");
      stateBtn.classList.remove("active");
      legendHeatmap.classList.remove("hidden");
      legendState.classList.add("hidden");
      showToast("Switched to Live Risk Heatmap View", "info");
    } else {
      FireCanvas.setViewMode("state");
      stateBtn.classList.add("active");
      heatmapBtn.classList.remove("active");
      legendState.classList.remove("hidden");
      legendHeatmap.classList.add("hidden");
      showToast("Switched to Fire State View", "info");
    }
  }

  /**
   * Canvas Tooling: Zoom (+ / - / Fit) and Grid toggle.
   */
  function setupCanvasTools() {
    document.getElementById("zoomInBtn").addEventListener("click", () => FireCanvas.zoomIn());
    document.getElementById("zoomOutBtn").addEventListener("click", () => FireCanvas.zoomOut());
    document.getElementById("zoomFitBtn").addEventListener("click", () => FireCanvas.zoomFit());

    document.getElementById("toggleGridLines").addEventListener("change", (e) => {
      FireCanvas.setGridLines(e.target.checked);
    });
  }

  /**
   * CSV Dataset Drag-and-Drop & File Picker Uploader.
   */
  function setupCsvUploader() {
    const dropZone = document.getElementById("dropZone");
    const fileInput = document.getElementById("csvFileInput");
    const loadDefaultBtn = document.getElementById("loadDefaultBtn");
    const datasetTag = document.getElementById("datasetNameTag");

    dropZone.addEventListener("click", () => fileInput.click());

    dropZone.addEventListener("dragover", (e) => {
      e.preventDefault();
      dropZone.classList.add("dragover");
    });

    dropZone.addEventListener("dragleave", () => {
      dropZone.classList.remove("dragover");
    });

    dropZone.addEventListener("drop", async (e) => {
      e.preventDefault();
      dropZone.classList.remove("dragover");
      if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
        await handleCsvUpload(e.dataTransfer.files[0]);
      }
    });

    fileInput.addEventListener("change", async (e) => {
      if (e.target.files && e.target.files.length > 0) {
        await handleCsvUpload(e.target.files[0]);
      }
    });

    loadDefaultBtn.addEventListener("click", async () => {
      pauseSimulation();
      try {
        const initResp = await FireApi.initSimulation();
        FireCharts.reset();
        applyGridState(initResp);
        if (datasetTag) datasetTag.textContent = "processed_grid.csv";
        showToast("Default grid restored successfully!", "success");
      } catch (err) {
        showToast("Failed to reload default grid: " + err.message, "error");
      }
    });
  }

  async function handleCsvUpload(file) {
    if (!file.name.toLowerCase().endsWith(".csv")) {
      showToast("Invalid file format. Please upload a .csv file.", "error");
      return;
    }

    pauseSimulation();
    showToast(`Uploading ${file.name}...`, "info");

    try {
      const uploadResult = await FireApi.uploadCsv(file);
      document.getElementById("datasetNameTag").textContent = file.name;

      // Fetch fresh state after upload
      const freshState = await FireApi.getState();
      FireCharts.reset();
      applyGridState(freshState);

      showToast(`Dataset loaded: ${uploadResult.rows}x${uploadResult.cols} (${uploadResult.totalCells} cells)!`, "success");
    } catch (err) {
      showToast("Upload failed: " + err.message, "error");
    }
  }

  /**
   * Interactive Click-to-Ignite Handler called by canvas.js.
   */
  async function triggerIgnition(row, col) {
    try {
      const updatedState = await FireApi.igniteCell(row, col);
      applyGridState(updatedState);
      showToast(`Ignited cell at (${row}, ${col})! Risk recalibrated.`, "warning");
    } catch (err) {
      showToast("Ignition failed: " + err.message, "error");
    }
  }

  /**
   * Theme Switcher (Dark / Light) with localStorage persistence.
   */
  function setupThemeToggle() {
    const toggleBtn = document.getElementById("themeToggleBtn");
    const themeIcon = document.getElementById("themeIcon");

    const savedTheme = localStorage.getItem("ff_theme") || "dark";
    applyTheme(savedTheme);

    toggleBtn.addEventListener("click", () => {
      const current = document.documentElement.getAttribute("data-theme") || "dark";
      const next = (current === "dark") ? "light" : "dark";
      applyTheme(next);
      localStorage.setItem("ff_theme", next);
    });

    function applyTheme(theme) {
      document.documentElement.setAttribute("data-theme", theme);
      if (theme === "light") {
        themeIcon.className = "fa-solid fa-sun";
      } else {
        themeIcon.className = "fa-solid fa-moon";
      }
      FireCanvas.render();
    }
  }

  /**
   * Simulation Audit Modal & Exports (JSON, PNG, CSV).
   */
  function setupModalAndExportButtons() {
    const viewReportBtn = document.getElementById("viewReportBtn");
    const closeBtn = document.getElementById("closeReportModalBtn");
    const dismissBtn = document.getElementById("dismissModalBtn");
    const downloadJsonBtn = document.getElementById("downloadReportJsonBtn");

    const exportJsonBtn = document.getElementById("exportJsonBtn");
    const exportPngBtn = document.getElementById("exportPngBtn");
    const exportCsvBtn = document.getElementById("exportCsvBtn");

    viewReportBtn.addEventListener("click", async () => {
      try {
        const report = await FireApi.getReport();
        FireCharts.showReportModal(report);
      } catch (err) {
        showToast("Failed to fetch report: " + err.message, "error");
      }
    });

    closeBtn.addEventListener("click", () => FireCharts.hideReportModal());
    dismissBtn.addEventListener("click", () => FireCharts.hideReportModal());

    downloadJsonBtn.addEventListener("click", async () => {
      try {
        const report = await FireApi.getReport();
        FireCharts.downloadJson(report);
      } catch (err) {
        showToast("Export failed: " + err.message, "error");
      }
    });

    exportJsonBtn.addEventListener("click", async () => {
      try {
        const state = await FireApi.getState();
        FireCharts.downloadJson(state, "forest_fire_state.json");
        showToast("Exported simulation state JSON", "success");
      } catch (err) {
        showToast("Export failed: " + err.message, "error");
      }
    });

    exportPngBtn.addEventListener("click", () => {
      FireCharts.downloadCanvasPng(FireCanvas.getCanvas());
      showToast("Canvas image snapshot saved as PNG", "success");
    });

    exportCsvBtn.addEventListener("click", async () => {
      try {
        const report = await FireApi.getReport();
        FireCharts.downloadCsvHistory(report);
        showToast("Exported simulation history as CSV", "success");
      } catch (err) {
        showToast("CSV export failed: " + err.message, "error");
      }
    });
  }

  /**
   * Lightweight toast notification manager.
   */
  function showToast(message, type = "info") {
    const container = document.getElementById("toastContainer");
    if (!container) return;

    const toast = document.createElement("div");
    toast.className = `toast toast-${type}`;

    let icon = "fa-circle-info";
    if (type === "success") icon = "fa-circle-check";
    else if (type === "warning") icon = "fa-triangle-exclamation";
    else if (type === "error") icon = "fa-circle-exclamation";

    toast.innerHTML = `<i class="fa-solid ${icon}"></i> <span>${message}</span>`;
    container.appendChild(toast);

    setTimeout(() => {
      toast.style.opacity = "0";
      toast.style.transform = "translateY(10px)";
      toast.style.transition = "all 0.3s ease";
      setTimeout(() => toast.remove(), 300);
    }, 3500);
  }

  // Initialize on DOMContentLoaded
  document.addEventListener("DOMContentLoaded", init);

  return {
    getActiveInteractionMode: () => activeInteractionMode,
    triggerIgnition,
    applyGridState,
    showToast
  };
})();

// Attach to window for global access
window.FireControls = FireControls;
