/**
 * =============================================================================
 * 🔥 Forest Fire Spread Predictor — Problem Statement No. 86
 * Semester Project | RGMCET B.Tech CSE (Data Science)
 * File: frontend/js/canvas.js
 *
 * High-performance HTML5 Canvas Grid Renderer & Live Risk Heatmap Visualizer.
 * Dynamically scales to any grid dimensions (rows × cols), supports dual view
 * modes (Fire State vs Live Risk Heatmap), hover tooltips, and click interactions.
 * =============================================================================
 */

const FireCanvas = (() => {
  // DOM Elements
  const canvas = document.getElementById("fireCanvas");
  const ctx = canvas.getContext("2d");
  const container = document.getElementById("canvasContainer");
  const tooltip = document.getElementById("cellTooltip");

  // State
  let currentGridData = null; // Holds rows, cols, states, risk, veg, elev, slope
  let activeViewMode = "state"; // "state" | "heatmap"
  let showGridLines = true;
  let zoomLevel = 1.0;
  let cellSize = 20; // Calculated dynamically
  let flameFlickerPhase = 0; // For micro-animation flame shimmering

  // Risk Color Scale Palette
  const RISK_COLORS = {
    LOW: "#10b981",       // 🟢 Green
    MODERATE: "#f59e0b",  // 🟡 Yellow / Amber
    HIGH: "#f97316",      // 🟠 Orange
    VERY_HIGH: "#ef4444", // 🔴 Red
    BURNED: "#334155"     // Charcoal
  };

  /**
   * Initializes the canvas event listeners and animation loop.
   */
  function init() {
    setupCanvasListeners();

    // Start subtle flame flicker animation loop for burning cells
    function animationLoop() {
      flameFlickerPhase = (flameFlickerPhase + 0.08) % (Math.PI * 2);
      if (currentGridData && hasBurningCells()) {
        render();
      }
      requestAnimationFrame(animationLoop);
    }
    requestAnimationFrame(animationLoop);
  }

  function hasBurningCells() {
    if (!currentGridData || !currentGridData.states) return false;
    for (let r = 0; r < currentGridData.rows; r++) {
      for (let c = 0; c < currentGridData.cols; c++) {
        if (currentGridData.states[r][c] === 1) return true;
      }
    }
    return false;
  }

  /**
   * Sets up mouse movement and click event listeners for canvas interaction.
   */
  /**
   * Sets up mouse movement and click event listeners for canvas interaction.
   */
  function setupCanvasListeners() {
    // Hover inspection tooltip
    canvas.addEventListener("mousemove", (e) => {
      if (!currentGridData || !currentGridData.rows || !currentGridData.cols) return;

      const rect = canvas.getBoundingClientRect();
      const clickX = e.clientX - rect.left;
      const clickY = e.clientY - rect.top;

      if (clickX < 0 || clickX >= rect.width || clickY < 0 || clickY >= rect.height) {
        hideTooltip();
        return;
      }

      const col = Math.floor((clickX / rect.width) * currentGridData.cols);
      const row = Math.floor((clickY / rect.height) * currentGridData.rows);

      if (row >= 0 && row < currentGridData.rows && col >= 0 && col < currentGridData.cols) {
        showTooltip(row, col, e.clientX, e.clientY);
      } else {
        hideTooltip();
      }
    });

    canvas.addEventListener("mouseleave", () => {
      hideTooltip();
    });

    // Dynamic click listener on HTML5 canvas for manual cell ignition
    canvas.addEventListener("click", async (e) => {
      if (!currentGridData || !currentGridData.rows || !currentGridData.cols) return;

      // Calculate clicked cell's row and col based on mouse client coordinates
      // relative to canvas bounding box and total grid dimensions (rows × cols)
      const rect = canvas.getBoundingClientRect();
      const clickX = e.clientX - rect.left;
      const clickY = e.clientY - rect.top;

      if (clickX < 0 || clickX >= rect.width || clickY < 0 || clickY >= rect.height) {
        return;
      }

      const col = Math.floor((clickX / rect.width) * currentGridData.cols);
      const row = Math.floor((clickY / rect.height) * currentGridData.rows);

      if (row >= 0 && row < currentGridData.rows && col >= 0 && col < currentGridData.cols) {
        await handleCellClick(row, col);
      }
    });
  }

  /**
   * Dispatches cell click: triggers ignition or inspects cell depending on active mode.
   */
  async function handleCellClick(row, col) {
    const activeMode = (window.FireControls && typeof window.FireControls.getActiveInteractionMode === "function")
      ? window.FireControls.getActiveInteractionMode()
      : "ignite";

    if (activeMode === "inspect") {
      // Inspect mode - highlight tooltip border
      if (tooltip) {
        tooltip.style.border = "2px solid #3b82f6";
        setTimeout(() => { tooltip.style.border = "1px solid rgba(249, 115, 22, 0.4)"; }, 400);
      }
      return;
    }

    // Default & Ignite mode: trigger manual ignition
    await igniteCell(row, col);
  }

  /**
   * Sends POST request to /api/simulation/ignite and refreshes grid upon success.
   */
  async function igniteCell(row, col) {
    try {
      // 1. Optimistic visual feedback: immediately mark cell as BURNING and redraw
      if (currentGridData && currentGridData.states) {
        currentGridData.states[row][col] = 1; // 1 = BURNING
        render();
      }

      // 2. Send fetch POST request to /api/simulation/ignite with row and col
      let resultData;
      if (window.FireApi && typeof window.FireApi.igniteCell === "function") {
        resultData = await window.FireApi.igniteCell(row, col);
      } else {
        const isDirect = window.location.port === "7070";
        const endpoint = isDirect ? "/api/simulation/ignite" : "http://localhost:7070/api/simulation/ignite";
        const response = await fetch(endpoint, {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "Accept": "application/json"
          },
          body: JSON.stringify({ row: Number(row), col: Number(col) })
        });
        if (!response.ok) {
          const errBody = await response.json().catch(() => ({}));
          throw new Error(errBody.error || `Server responded with HTTP ${response.status}`);
        }
        resultData = await response.json();
      }

      // 3. Immediately trigger grid state refresh / redrawing function upon success
      if (window.FireControls && typeof window.FireControls.applyGridState === "function") {
        window.FireControls.applyGridState(resultData);
      } else {
        updateData(resultData);
      }

      if (window.FireControls && typeof window.FireControls.showToast === "function") {
        window.FireControls.showToast(`🔥 Ignited cell at [Row ${row}, Col ${col}]! Dynamic risk recalibrated.`, "warning");
      }
    } catch (err) {
      console.error(`[FireCanvas] Ignition failed at [${row}, ${col}]:`, err);
      if (window.FireControls && typeof window.FireControls.showToast === "function") {
        window.FireControls.showToast(`Ignition failed: ${err.message}`, "error");
      }
    }
  }

  function showTooltip(row, col, clientX, clientY) {
    if (!currentGridData) return;

    const stateCode = currentGridData.states ? currentGridData.states[row][col] : 0;
    const risk = currentGridData.risk ? currentGridData.risk[row][col] : 0;
    const veg = currentGridData.vegetation ? currentGridData.vegetation[row][col] : 0.5;
    const elev = currentGridData.elevation ? currentGridData.elevation[row][col] : 100;
    const slope = currentGridData.slope ? currentGridData.slope[row][col] : 0;

    let stateText = "UNBURNED";
    let stateColor = "#10b981";
    if (stateCode === 1) {
      stateText = "BURNING 🔥";
      stateColor = "#f97316";
    } else if (stateCode === 2) {
      stateText = "BURNED OUT";
      stateColor = "#64748b";
    }

    let riskLevel = "Low";
    if (stateCode === 2) riskLevel = "Burned (0.0)";
    else if (stateCode === 1 || risk >= 0.75) riskLevel = "Very High ⚠️";
    else if (risk >= 0.50) riskLevel = "High";
    else if (risk >= 0.25) riskLevel = "Moderate";

    document.getElementById("ttCoordinates").textContent = `Cell [Row: ${row}, Col: ${col}]`;
    const elState = document.getElementById("ttState");
    elState.textContent = stateText;
    elState.style.color = stateColor;

    document.getElementById("ttRisk").textContent = (risk * 100).toFixed(0) + "% (" + risk.toFixed(2) + ")";
    document.getElementById("ttRiskLevel").textContent = riskLevel;
    document.getElementById("ttVegetation").textContent = (veg * 100).toFixed(0) + "% fuel density";
    document.getElementById("ttElevation").textContent = elev.toFixed(1) + " m";
    document.getElementById("ttSlope").textContent = slope.toFixed(1) + "° incline";

    // Position tooltip relative to container
    const containerRect = container.getBoundingClientRect();
    let left = clientX - containerRect.left + 16;
    let top = clientY - containerRect.top + 16;

    // Prevent overflowing container bounds
    if (left + 190 > containerRect.width) left -= 210;
    if (top + 160 > containerRect.height) top -= 170;

    tooltip.style.left = `${Math.max(8, left)}px`;
    tooltip.style.top = `${Math.max(8, top)}px`;
    tooltip.style.display = "block";
  }

  function hideTooltip() {
    tooltip.style.display = "none";
  }

  /**
   * Updates grid state model and recalibrates canvas dimensions.
   */
  function updateData(data) {
    if (!data || !data.rows || !data.cols) return;
    currentGridData = data;
    resizeCanvas();
    render();
  }

  /**
   * Resizes canvas to fit container while maintaining crisp square cell aspect ratio.
   */
  function resizeCanvas() {
    if (!currentGridData) return;

    const availableWidth = container.clientWidth - 32;
    const availableHeight = container.clientHeight - 32;

    const targetSize = Math.max(300, Math.min(availableWidth, availableHeight, 680)) * zoomLevel;
    const maxDimension = Math.max(currentGridData.rows, currentGridData.cols);

    cellSize = Math.max(6, Math.floor(targetSize / maxDimension));

    const totalWidth = cellSize * currentGridData.cols;
    const totalHeight = cellSize * currentGridData.rows;

    canvas.width = totalWidth;
    canvas.height = totalHeight;
    canvas.style.width = `${totalWidth}px`;
    canvas.style.height = `${totalHeight}px`;
  }

  /**
   * Main rendering routine: draws cells, flame shimmer, heatmaps, and grid lines.
   */
  function render() {
    if (!currentGridData || !currentGridData.rows || !currentGridData.cols) return;

    const rows = currentGridData.rows;
    const cols = currentGridData.cols;
    const states = currentGridData.states || [];
    const risks = currentGridData.risk || [];
    const vegetation = currentGridData.vegetation || [];

    ctx.clearRect(0, 0, canvas.width, canvas.height);

    for (let r = 0; r < rows; r++) {
      for (let c = 0; c < cols; c++) {
        const x = c * cellSize;
        const y = r * cellSize;
        const state = (states[r] && states[r][c] !== undefined) ? states[r][c] : 0;
        const risk = (risks[r] && risks[r][c] !== undefined) ? risks[r][c] : 0.0;
        const veg = (vegetation[r] && vegetation[r][c] !== undefined) ? vegetation[r][c] : 0.5;

        if (activeViewMode === "heatmap") {
          renderHeatmapCell(r, c, x, y, state, risk);
        } else {
          renderFireStateCell(r, c, x, y, state, veg);
        }
      }
    }

    // Moore 8-Neighbour Grid Overlay Lines
    if (showGridLines && cellSize >= 8) {
      ctx.strokeStyle = "rgba(255, 255, 255, 0.08)";
      ctx.lineWidth = 1;
      ctx.beginPath();

      for (let r = 0; r <= rows; r++) {
        ctx.moveTo(0, r * cellSize);
        ctx.lineTo(cols * cellSize, r * cellSize);
      }
      for (let c = 0; c <= cols; c++) {
        ctx.moveTo(c * cellSize, 0);
        ctx.lineTo(c * cellSize, rows * cellSize);
      }
      ctx.stroke();
    }
  }

  /**
   * Renders an individual cell in Fire State View (Unburned, Burning, Burned).
   */
  function renderFireStateCell(r, c, x, y, state, veg) {
    if (state === 1) {
      // BURNING CELL: Blazing flame with animated pulse shimmer
      const flicker = Math.sin(flameFlickerPhase + (r * 3.1) + (c * 1.7)) * 0.15;
      const grad = ctx.createRadialGradient(
        x + cellSize / 2, y + cellSize / 2, cellSize * 0.1,
        x + cellSize / 2, y + cellSize / 2, cellSize * 0.7
      );

      grad.addColorStop(0, "#fef08a"); // Bright yellow core
      grad.addColorStop(0.4, "#f97316"); // Blazing orange
      grad.addColorStop(1, "#dc2626"); // Red rim

      ctx.fillStyle = grad;
      ctx.fillRect(x, y, cellSize, cellSize);

      // Flame core particle
      ctx.fillStyle = `rgba(255, 255, 255, ${0.7 + flicker})`;
      ctx.fillRect(x + cellSize * 0.35, y + cellSize * 0.35, cellSize * 0.3, cellSize * 0.3);

    } else if (state === 2) {
      // BURNED OUT CELL: Charcoal ash texture
      ctx.fillStyle = "#1e293b";
      ctx.fillRect(x, y, cellSize, cellSize);

      // Subtle ash embers
      if ((r + c) % 3 === 0) {
        ctx.fillStyle = "rgba(100, 116, 139, 0.4)";
        ctx.fillRect(x + cellSize * 0.2, y + cellSize * 0.2, cellSize * 0.25, cellSize * 0.25);
      }
    } else {
      // UNBURNED CELL: Healthy forest green modulated by vegetation fuel load
      const baseGreen = Math.floor(110 + veg * 65);
      const baseRed = Math.floor(10 + (1 - veg) * 30);
      const baseBlue = Math.floor(45 + veg * 25);

      ctx.fillStyle = `rgb(${baseRed}, ${baseGreen}, ${baseBlue})`;
      ctx.fillRect(x, y, cellSize, cellSize);
    }
  }

  /**
   * Renders an individual cell in Live Risk Heatmap View.
   */
  function renderHeatmapCell(r, c, x, y, state, risk) {
    if (state === 2) {
      // Fuel exhausted (BURNED): zero risk
      ctx.fillStyle = "#1e293b";
      ctx.fillRect(x, y, cellSize, cellSize);
      return;
    }

    if (state === 1) {
      // Active Fire: Maximum critical risk
      ctx.fillStyle = "#ef4444";
      ctx.fillRect(x, y, cellSize, cellSize);
      ctx.fillStyle = "rgba(255, 255, 255, 0.6)";
      ctx.fillRect(x + cellSize * 0.25, y + cellSize * 0.25, cellSize * 0.5, cellSize * 0.5);
      return;
    }

    // Dynamic continuous color interpolation across risk tiers
    let color;
    if (risk < 0.25) {
      // Low Risk (Emerald Green)
      color = interpolateColor("#10b981", "#34d399", risk / 0.25);
    } else if (risk < 0.50) {
      // Moderate Risk (Warm Amber)
      color = interpolateColor("#34d399", "#f59e0b", (risk - 0.25) / 0.25);
    } else if (risk < 0.75) {
      // High Risk (Vibrant Orange)
      color = interpolateColor("#f59e0b", "#f97316", (risk - 0.50) / 0.25);
    } else {
      // Very High / Imminent Danger (Blazing Red)
      color = interpolateColor("#f97316", "#ef4444", (risk - 0.75) / 0.25);
    }

    ctx.fillStyle = color;
    ctx.fillRect(x, y, cellSize, cellSize);
  }

  function interpolateColor(hexA, hexB, factor) {
    const t = Math.max(0, Math.min(1, factor));
    const cA = parseHex(hexA);
    const cB = parseHex(hexB);

    const r = Math.round(cA.r + (cB.r - cA.r) * t);
    const g = Math.round(cA.g + (cB.g - cA.g) * t);
    const b = Math.round(cA.b + (cB.b - cA.b) * t);

    return `rgb(${r}, ${g}, ${b})`;
  }

  function parseHex(hex) {
    const clean = hex.replace("#", "");
    return {
      r: parseInt(clean.substring(0, 2), 16),
      g: parseInt(clean.substring(2, 4), 16),
      b: parseInt(clean.substring(4, 6), 16)
    };
  }

  return {
    init,
    updateData,
    render,

    setViewMode(mode) {
      activeViewMode = (mode === "heatmap") ? "heatmap" : "state";
      render();
    },

    getViewMode() {
      return activeViewMode;
    },

    setGridLines(visible) {
      showGridLines = !!visible;
      render();
    },

    zoomIn() {
      zoomLevel = Math.min(2.5, zoomLevel + 0.2);
      resizeCanvas();
      render();
    },

    zoomOut() {
      zoomLevel = Math.max(0.5, zoomLevel - 0.2);
      resizeCanvas();
      render();
    },

    zoomFit() {
      zoomLevel = 1.0;
      resizeCanvas();
      render();
    },

    getCanvas() {
      return canvas;
    },

    getGridData() {
      return currentGridData;
    },

    igniteCell
  };
})();

// Attach to window for global access
window.FireCanvas = FireCanvas;
