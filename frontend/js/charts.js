/**
 * =============================================================================
 * 🔥 Forest Fire Spread Predictor — Problem Statement No. 86
 * Semester Project | RGMCET B.Tech CSE (Data Science)
 * File: frontend/js/charts.js
 *
 * Real-Time Analytics, Chart.js Visualizations & Audit Report Generator.
 * Tracks fire progression over time, monitors risk class distributions,
 * updates KPI cards, and exports post-simulation audit documents (JSON/PNG/CSV).
 * =============================================================================
 */

const FireCharts = (() => {
  let timelineChart = null;
  let riskDonutChart = null;

  // Historical Progression Tracking
  const history = {
    steps: [],
    burning: [],
    burned: [],
    unburned: []
  };

  /**
   * Initializes Chart.js line and doughnut charts.
   */
  function init() {
    initTimelineChart();
    initRiskDonutChart();
  }

  function initTimelineChart() {
    const ctx = document.getElementById("timelineChart")?.getContext("2d");
    if (!ctx || typeof Chart === "undefined") return;

    timelineChart = new Chart(ctx, {
      type: "line",
      data: {
        labels: [],
        datasets: [
          {
            label: "Burning",
            data: [],
            borderColor: "#f97316",
            backgroundColor: "rgba(249, 115, 22, 0.2)",
            borderWidth: 2,
            tension: 0.3,
            fill: true,
            pointRadius: 2
          },
          {
            label: "Burned",
            data: [],
            borderColor: "#64748b",
            backgroundColor: "rgba(100, 116, 139, 0.15)",
            borderWidth: 2,
            tension: 0.3,
            fill: true,
            pointRadius: 2
          },
          {
            label: "Intact",
            data: [],
            borderColor: "#10b981",
            backgroundColor: "transparent",
            borderWidth: 1.5,
            borderDash: [4, 4],
            tension: 0.3,
            pointRadius: 0
          }
        ]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        animation: { duration: 250 },
        plugins: {
          legend: {
            position: "top",
            labels: {
              boxWidth: 10,
              boxHeight: 10,
              font: { size: 10, family: "'Inter', sans-serif" },
              color: "#94a3b8"
            }
          },
          tooltip: {
            mode: "index",
            intersect: false
          }
        },
        scales: {
          x: {
            grid: { color: "rgba(255, 255, 255, 0.05)" },
            ticks: { font: { size: 9 }, color: "#64748b", maxTicksLimit: 8 }
          },
          y: {
            grid: { color: "rgba(255, 255, 255, 0.05)" },
            ticks: { font: { size: 9 }, color: "#64748b" },
            beginAtZero: true
          }
        }
      }
    });
  }

  function initRiskDonutChart() {
    const ctx = document.getElementById("riskDistributionChart")?.getContext("2d");
    if (!ctx || typeof Chart === "undefined") return;

    riskDonutChart = new Chart(ctx, {
      type: "doughnut",
      data: {
        labels: ["Low", "Moderate", "High", "Very High"],
        datasets: [{
          data: [100, 0, 0, 0],
          backgroundColor: ["#10b981", "#f59e0b", "#f97316", "#ef4444"],
          borderColor: "rgba(15, 23, 42, 0.8)",
          borderWidth: 2
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        cutout: "68%",
        plugins: {
          legend: {
            position: "right",
            labels: {
              boxWidth: 10,
              boxHeight: 10,
              font: { size: 10, family: "'Inter', sans-serif" },
              color: "#94a3b8"
            }
          }
        }
      }
    });
  }

  /**
   * Updates KPI dashboard cards and charts with fresh tick statistics.
   */
  function updateStats(stats, step = 0, totalCells = 400) {
    if (!stats) return;

    const burning = stats.burning || 0;
    const burned = stats.burned || 0;
    const unburned = (stats.unburned !== undefined) ? stats.unburned : (totalCells - burning - burned);
    const newFire = stats.newlyIgnited || 0;
    const areaHa = (stats.affectedAreaHa !== undefined) ? stats.affectedAreaHa : 0.0;
    const threat = stats.spreadDirection || "NE";

    const total = Math.max(1, totalCells);
    const pctBurned = ((burned / total) * 100).toFixed(1);
    const pctUnburned = ((unburned / total) * 100).toFixed(1);

    // Update KPI Card Numbers
    document.getElementById("kpiBurningCount").textContent = burning;
    document.getElementById("kpiBurningRate").textContent = `+${newFire} newly ignited`;

    document.getElementById("kpiBurnedCount").textContent = burned;
    document.getElementById("kpiBurnedPercent").textContent = `${pctBurned}% of forest`;

    document.getElementById("kpiUnburnedCount").textContent = unburned;
    document.getElementById("kpiUnburnedPercent").textContent = `${pctUnburned}% preserved`;

    document.getElementById("kpiAreaHectares").textContent = `${areaHa} ha`;
    document.getElementById("kpiThreatDirection").textContent = threat;

    document.getElementById("stepCounterBadge").textContent = `Tick: ${step}`;

    // Update Risk Count Badges in Legend
    if (stats.riskCounts) {
      const rc = stats.riskCounts;
      document.getElementById("legendCountLow").textContent = rc.low || 0;
      document.getElementById("legendCountMod").textContent = rc.moderate || 0;
      document.getElementById("legendCountHigh").textContent = rc.high || 0;
      document.getElementById("legendCountVHigh").textContent = rc.veryHigh || 0;

      // Update Donut Chart
      if (riskDonutChart) {
        riskDonutChart.data.datasets[0].data = [
          rc.low || 0,
          rc.moderate || 0,
          rc.high || 0,
          rc.veryHigh || 0
        ];
        riskDonutChart.update();
      }
    }

    // Append to progression timeline history
    if (!history.steps.includes(step)) {
      history.steps.push(step);
      history.burning.push(burning);
      history.burned.push(burned);
      history.unburned.push(unburned);

      if (timelineChart) {
        timelineChart.data.labels = [...history.steps];
        timelineChart.data.datasets[0].data = [...history.burning];
        timelineChart.data.datasets[1].data = [...history.burned];
        timelineChart.data.datasets[2].data = [...history.unburned];
        timelineChart.update("none"); // Update without heavy layout reflow
      }
    }
  }

  /**
   * Resets chart data series upon simulation reset.
   */
  function reset() {
    history.steps = [];
    history.burning = [];
    history.burned = [];
    history.unburned = [];

    if (timelineChart) {
      timelineChart.data.labels = [];
      timelineChart.data.datasets.forEach(ds => ds.data = []);
      timelineChart.update();
    }
    if (riskDonutChart) {
      riskDonutChart.data.datasets[0].data = [100, 0, 0, 0];
      riskDonutChart.update();
    }

    document.getElementById("stepCounterBadge").textContent = "Tick: 0";
  }

  /**
   * Populates and opens the post-simulation audit modal.
   */
  function showReportModal(report) {
    if (!report) return;

    document.getElementById("repTotalSteps").textContent = report.totalSteps || 0;
    document.getElementById("repTotalBurned").textContent = `${report.totalBurned || 0} (${report.burnedPercentage || 0}%)`;
    document.getElementById("repTotalArea").textContent = `${report.affectedAreaHectares || 0} ha`;
    document.getElementById("repPeakBurning").textContent = report.peakActiveBurning || 0;
    document.getElementById("repSpreadDir").textContent = report.dominantSpreadDirection || "NE";

    const tbody = document.getElementById("reportHistoryBody");
    tbody.innerHTML = "";

    const steps = report.stepHistory || [];
    steps.forEach(s => {
      const tr = document.createElement("tr");
      tr.innerHTML = `
        <td><strong>#${s.step}</strong></td>
        <td style="color:#f97316">${s.burning}</td>
        <td style="color:#94a3b8">${s.burned}</td>
        <td style="color:#10b981">${s.unburned}</td>
        <td>+${s.newlyIgnited}</td>
        <td>${s.affectedAreaHa} ha</td>
        <td><strong>${s.spreadDirection || "NE"}</strong></td>
        <td>${s.isExtinguished ? '<span class="badge" style="background:#334155;color:#fff">Extinguished</span>' : '<span class="badge badge-primary">Active</span>'}</td>
      `;
      tbody.appendChild(tr);
    });

    document.getElementById("reportModalOverlay").classList.remove("hidden");
  }

  function hideReportModal() {
    document.getElementById("reportModalOverlay").classList.add("hidden");
  }

  /**
   * Exports full simulation report state as a downloaded JSON file.
   */
  function downloadJson(data, filename = "forest_fire_simulation_report.json") {
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: "application/json" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = filename;
    a.click();
    URL.revokeObjectURL(url);
  }

  /**
   * Exports history as CSV file.
   */
  function downloadCsvHistory(report, filename = "fire_spread_history.csv") {
    const steps = (report && report.stepHistory) ? report.stepHistory : [];
    let csv = "Step,Burning,Burned,Unburned,NewlyIgnited,AffectedAreaHa,SpreadDirection\n";
    steps.forEach(s => {
      csv += `${s.step},${s.burning},${s.burned},${s.unburned},${s.newlyIgnited},${s.affectedAreaHa},${s.spreadDirection || "NE"}\n`;
    });

    const blob = new Blob([csv], { type: "text/csv;charset=utf-8;" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = filename;
    a.click();
    URL.revokeObjectURL(url);
  }

  /**
   * Exports current HTML5 canvas view as a PNG image file.
   */
  function downloadCanvasPng(canvas, filename = "forest_fire_heatmap_snapshot.png") {
    if (!canvas) return;
    const url = canvas.toDataURL("image/png");
    const a = document.createElement("a");
    a.href = url;
    a.download = filename;
    a.click();
  }

  return {
    init,
    updateStats,
    reset,
    showReportModal,
    hideReportModal,
    downloadJson,
    downloadCsvHistory,
    downloadCanvasPng,
    getHistory: () => history
  };
})();

// Attach to window for global access
window.FireCharts = FireCharts;
