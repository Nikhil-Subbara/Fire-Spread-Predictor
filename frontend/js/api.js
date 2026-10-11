/**
 * =============================================================================
 * 🔥 Forest Fire Spread Predictor — Problem Statement No. 86
 * Semester Project | RGMCET B.Tech CSE (Data Science)
 * File: frontend/js/api.js
 *
 * REST API Client for communicating with the Javalin Java 17 backend engine.
 * Supports simulation initialization, single/batch stepping, dynamic wind updates,
 * interactive canvas ignition, CSV dataset uploads, and post-simulation reports.
 * =============================================================================
 */

const FireApi = (() => {
  // Determine backend base URL:
  // 1. Query parameter: ?backend=https://... or ?api=https://...
  // 2. localStorage saved API URL: "ff_api_url"
  // 3. Direct server (served from Javalin port 7070): relative ""
  // 4. Local development (localhost / 127.0.0.1 on port 5500/3000): http://localhost:7070
  // 5. Cloud deployment (Vercel / Netlify): saved URL or relative ""
  try {
    const urlParam = (typeof window !== "undefined" && window.location)
      ? (new URLSearchParams(window.location.search).get("backend") || new URLSearchParams(window.location.search).get("api"))
      : null;
    if (urlParam) {
      localStorage.setItem("ff_api_url", urlParam.trim().replace(/\/$/, ""));
    }
  } catch (_) {}

  function getBaseUrl() {
    // 1. Runtime configuration from config.js (preferred for static Vercel deployments)
    if (typeof window !== "undefined" && window.FIRE_PREDICTOR_CONFIG && window.FIRE_PREDICTOR_CONFIG.BACKEND_URL) {
      const configUrl = window.FIRE_PREDICTOR_CONFIG.BACKEND_URL.trim();
      if (configUrl.length > 0) return configUrl.replace(/\/$/, "");
    }

    // 2. Persisted custom backend URL in localStorage (set via prompt or query param)
    try {
      const stored = localStorage.getItem("ff_api_url");
      if (stored && stored.trim().length > 0) return stored.trim().replace(/\/$/, "");
    } catch (_) {}

    // 3. Local direct execution or development server detection
    if (typeof window !== "undefined" && window.location) {
      if (window.location.port === "7070") return ""; // Direct server execution
      if (window.location.hostname === "localhost" || window.location.hostname === "127.0.0.1") {
        return "http://localhost:7070";
      }
    }
    // On a hosted static frontend (for example Vercel), a blank base URL
    // would send /api requests to the frontend host, where the Java API does
    // not exist. Fail with a clear setup message instead of a misleading 404.
    if (typeof window !== "undefined" && window.location) {
      const hostname = window.location.hostname;
      const isLocalHost = hostname === "localhost" || hostname === "127.0.0.1";
      const isLocalFile = window.location.protocol === "file:";
      if (!isLocalHost && !isLocalFile) {
        throw new Error(
          "Backend URL is not configured. Click the Backend Status badge and enter your deployed Render service URL."
        );
      }
    }

    return "";
  }

  function setBaseUrl(url) {
    try {
      if (url && url.trim().length > 0) {
        localStorage.setItem("ff_api_url", url.trim().replace(/\/$/, ""));
      } else {
        localStorage.removeItem("ff_api_url");
      }
    } catch (_) {}
  }

  /**
   * Internal helper for executing HTTP requests with error interceptors.
   */
  async function request(endpoint, options = {}) {
    const baseUrl = getBaseUrl();
    const url = `${baseUrl}${endpoint}`;
    const defaultHeaders = {
      "Accept": "application/json"
    };

    if (!(options.body instanceof FormData)) {
      defaultHeaders["Content-Type"] = "application/json";
    }

    const config = {
      ...options,
      headers: {
        ...defaultHeaders,
        ...(options.headers || {})
      }
    };

    try {
      const response = await fetch(url, config);

      if (!response.ok) {
        let errorMsg = `Server error ${response.status}: ${response.statusText}`;
        try {
          const errJson = await response.json();
          if (errJson && errJson.message) errorMsg = errJson.message;
          if (errJson && errJson.error) errorMsg = errJson.error;
        } catch (_) {}
        throw new Error(errorMsg);
      }

      return await response.json();
    } catch (error) {
      console.error(`[FireApi] Request failed on ${endpoint}:`, error.message);
      throw error;
    }
  }

  return {
    /**
     * Checks backend system health and connectivity status.
     * GET /api/health
     */
    async checkHealth() {
      return await request("/api/health");
    },

    /**
     * Retrieves the complete 2-D grid state, cellular automaton values,
     * recalibrated risk scores, and aggregate statistics.
     * GET /api/simulation/state
     */
    async getState() {
      return await request("/api/simulation/state");
    },

    /**
     * Initializes or restarts the simulation with optional ignition coordinates,
     * wind conditions, and random seed.
     * POST /api/simulation/init
     */
    async initSimulation(params = {}) {
      return await request("/api/simulation/init", {
        method: "POST",
        body: JSON.stringify(params)
      });
    },

    /**
     * Advances the cellular automaton engine by one (or N) synchronous generation steps.
     * POST /api/simulation/step
     */
    async advanceStep(count = 1) {
      return await request(`/api/simulation/step?count=${encodeURIComponent(count)}`, {
        method: "POST"
      });
    },

    /**
     * Dynamically alters wind speed and direction mid-simulation and triggers
     * immediate live risk heatmap recalibration.
     * POST /api/simulation/wind
     */
    async updateWind(speed, direction) {
      return await request("/api/simulation/wind", {
        method: "POST",
        body: JSON.stringify({ speed: Number(speed), direction: Number(direction) })
      });
    },

    /**
     * Ignites an unburned cell at the specified coordinates on the canvas.
     * POST /api/simulation/ignite
     */
    async igniteCell(row, col) {
      return await request("/api/simulation/ignite", {
        method: "POST",
        body: JSON.stringify({ row: Number(row), col: Number(col) })
      });
    },

    /**
     * Resets the entire grid and cellular automaton back to generation Step 0.
     * POST /api/simulation/reset
     */
    async resetSimulation() {
      return await request("/api/simulation/reset", {
        method: "POST"
      });
    },

    /**
     * Uploads a raw or processed CSV dataset via multipart form-data.
     * Auto-detects dimensions and builds a fresh forest grid in Java.
     * POST /api/upload
     */
    async uploadCsv(file) {
      const formData = new FormData();
      formData.append("file", file);

      return await request("/api/upload", {
        method: "POST",
        body: formData
      });
    },

    /**
     * Fetches the comprehensive final simulation audit report and per-step history.
     * GET /api/report
     */
    async getReport() {
      return await request("/api/report");
    },

    getBaseUrl,
    setBaseUrl
  };
})();

// Attach to window for global access
window.FireApi = FireApi;
