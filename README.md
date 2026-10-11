<div align="center">

# 🔥 Forest Fire Spread Predictor

### Grid-Based Cellular Automaton Simulation with Live Risk Heatmap
**Semester Project — Problem Statement No. 86**

![Java](https://img.shields.io/badge/Backend-Java%20OOP-orange?logo=openjdk)
![HTML5](https://img.shields.io/badge/Frontend-HTML5%20%7C%20CSS3%20%7C%20JavaScript-blue?logo=html5)
![Python](https://img.shields.io/badge/Preprocessing-Python%20%7C%20Pandas%20%7C%20NumPy-yellow?logo=python)
![Storage](https://img.shields.io/badge/Storage-CSV%20%7C%20MySQL-lightgrey)
![Status](https://img.shields.io/badge/Status-Semester%20Submission-success)

*Rajeev Gandhi Memorial College of Engineering & Technology (RGMCET), Nandyal*
*B.Tech — Computer Science Engineering (Data Science)*

</div>

---

## 📑 Table of Contents

1. [Project Overview & Problem Statement](#1-project-overview--problem-statement)
2. [Advanced Core Features](#2-advanced-core-features)
3. [Comprehensive Technology Stack](#3-comprehensive-technology-stack)
4. [System Architecture & 4-Part Implementation Plan](#4-system-architecture--4-part-implementation-plan)
5. [REST API Reference](#5-rest-api-reference)
6. [Step-by-Step System Workflow](#6-step-by-step-system-workflow)
7. [Expected Results](#7-expected-results)
8. [Viva Highlights](#8-viva-highlights)
9. [Project Structure](#9-project-structure)
10. [Installation & Usage](#10-installation--usage)
11. [Future Scope](#11-future-scope)
12. [Authors & License](#12-authors--license)

---

## 1. Project Overview & Problem Statement

### 1.1 Problem Statement

Forest fires can spread quickly across large areas. Their **direction** and **speed** are strongly influenced by three environmental factors: **wind**, **vegetation (fuel)** and **terrain slope**. Responders need a way to anticipate where a fire is heading and which areas are most endangered, so that resources can be planned *before* the fire arrives.

This project proposes a system that:

- represents a forest as a **grid of cells**,
- simulates fire movement from each cell to its neighbours using a **cellular automaton**,
- accounts for **wind, vegetation density and terrain slope** in every spread decision,
- and presents the **predicted fire-spread pattern and risk areas** through a user-friendly **web dashboard** (HTML, CSS, JavaScript) powered by a **Java OOP simulation backend**.

### 1.2 Grid-Based Forest Representation

The forest region is discretised into a 2-D grid of `rows × cols` cells. Each `Cell` stores:

| Attribute | Description |
|-----------|-------------|
| `row`, `col` | Position in the grid |
| `vegetationDensity` | Normalised fuel load (0.0 – 1.0) |
| `elevation` | Height above sea level |
| `slope` | Terrain slope derived from elevation differences with neighbouring cells |
| `state` | Fire state — `UNBURNED`, `BURNING` or `BURNED` |
| `riskScore` / `riskLevel` | Live risk value and class (Low / Moderate / High / Very High) |

Cells are also nodes of an **undirected graph** (8-neighbour / Moore neighbourhood), so propagation can be expressed as a graph traversal.

### 1.3 Cellular Automata Fire Propagation

Every cell follows a simple one-way life-cycle, updated synchronously at each simulation step:

```
        ┌────────────┐   spread rule met    ┌───────────┐   burn-out    ┌──────────┐
        │  UNBURNED  │ ───────────────────► │  BURNING  │ ────────────► │  BURNED  │
        └────────────┘                      └───────────┘               └──────────┘
```

- **UNBURNED → BURNING**: an unburned cell ignites if at least one burning neighbour succeeds in spreading to it (based on the spread score/probability).
- **BURNING → BURNED**: a burning cell burns out after its burn duration (or after one step in the base model) and becomes fuel-exhausted.
- **BURNED** is a terminal state.

Updates are computed from a snapshot of the previous generation, so the order in which cells are processed never biases the result (classic CA double-buffering).

### 1.4 Environmental Factor Analysis

For each (burning cell → unburned neighbour) pair, the `SpreadCalculator` evaluates:

| Factor | Effect on spread |
|--------|------------------|
| **Wind** (direction + speed) | Spread is boosted when the fire-to-neighbour direction aligns with the wind, and suppressed when it opposes it. |
| **Vegetation density** | Denser vegetation = more available fuel = higher ignition likelihood. |
| **Terrain slope** | Fire travels faster **uphill** (pre-heating of fuel above) and slower **downhill**. |

> **Core idea:** *Wind helps or opposes the direction of spread* **+** *dense vegetation provides more fuel* **+** *terrain slope changes the spread tendency.*

A configurable model is used (coefficients are tunable constants in `SpreadCalculator`):

```
P(spread) = clamp( P_base × f_vegetation × f_wind × f_slope , 0 , 1 )

f_vegetation = vegetationDensity of the target cell
f_wind       = 1 + k_w · windSpeed · cos(θ)        θ = angle between wind and spread direction
f_slope      = exp( k_s · tan(slope_uphill) )      > 1 uphill, < 1 downhill
```

---

## 2. Advanced Core Features

### 2.1 📂 Dynamic CSV Dataset Upload

The application is **not locked to one hard-coded map**. Any public or custom CSV can be loaded at runtime:

- **Browser file upload** (drag-and-drop or file picker) on the web dashboard; the file is sent to the Java backend.
- **Automatic grid dimension detection** — `rows` and `cols` are inferred from the data (max row/col index, or from unique coordinates), so no manual sizing is needed.
- **Automatic column mapping** — recognised headers (e.g. `row`/`y`, `col`/`x`, `vegetation`/`ndvi`/`fuel`, `elevation`/`altitude`, `slope`, `wind_dir`, `wind_speed`, `fire`/`ignition`) are mapped to grid fields; unmapped columns are ignored gracefully.
- **Fallbacks** — missing slope is computed from elevation; missing wind uses dashboard-supplied defaults.
- **Validation feedback** — invalid rows are reported in the UI instead of crashing the simulation.

Example accepted CSV:

```csv
row,col,vegetation,elevation,slope,wind_dir,wind_speed,fire
0,0,0.72,310,4.5,45,12,0
0,1,0.80,314,6.1,45,12,0
1,1,0.65,321,9.0,45,12,1
...
```

### 2.2 🎞️ Real-Time Animated Fire Spread Simulation

- An HTML5 **`<canvas>` grid** is redrawn every simulation tick using `requestAnimationFrame` / timed `fetch` calls to the Java engine.
- Cell colours reflect the live state: green (unburned) → orange/red (burning) → charcoal (burned).
- **Playback controls**: Start, Pause, Resume, Step, Reset, and a speed slider.
- **Interactive ignition**: click any cell on the canvas to place the initial fire; multiple ignition points are supported.
- **Live wind control**: change wind direction and speed from the dashboard and observe the effect.
- **Responsive CSS layout** that works on laptop and projector screens.

### 2.3 🌡️ Dynamic Live Risk Heatmap

The risk heatmap is **not a static pre-computed overlay**. It **recalibrates in real time**, on every simulation step, as cells change state:

- When cells become **BURNING**, the risk of their *unburned neighbours* (and, through propagation, the downwind/uphill region ahead of the fire) **rises immediately**.
- When cells become **BURNED**, their fuel is exhausted — their risk **drops to zero** (displayed as burned), and areas shielded by burned-out zones **cool down**.
- **Heatmap colours shift continuously** across the Low → Moderate → High → Very High scale, and summary statistics (counts per risk class, affected area) update with every tick.

| Risk Level | Colour | Meaning |
|------------|--------|---------|
| 🟢 Low | Green | Minimal near-term danger |
| 🟡 Moderate | Yellow | Watch — conditions could escalate |
| 🟠 High | Orange | Likely to be reached soon |
| 🔴 Very High | Red | Imminent / adjacent to active fire in a favourable direction |

---

## 3. Comprehensive Technology Stack

| Layer | Technology | Purpose |
|-------|------------|---------|
| **Frontend** | **HTML5 + CSS3 + JavaScript (ES6)** | Dashboard UI, CSV uploader, dynamic `<canvas>` grid, simulation controls, live heatmap view, charts, results panel |
| **Charts** | **Chart.js** *(or plain canvas)* | Fire-progression and risk-distribution graphs |
| **Backend & OOP** | **Java 17+** | Core classes — `Cell`, `ForestGrid`, `Wind`, `FireSimulator`, `Graph` (BFS propagation), `SpreadCalculator`, `RiskAssessment`, `ReportManager` |
| **REST layer** | **Javalin** *(lightweight Java web framework; Spring Boot also works)* | Exposes the Java engine to the browser as JSON APIs and serves the static frontend |
| **Data Preprocessing** | **Python + Pandas / NumPy** | Cleaning raw CSVs, handling missing/invalid records, removing duplicates, normalising features |
| **Analysis (optional)** | **Python + Matplotlib** | Offline graphs of fire progression and statistics |
| **Storage / Data** | **CSV / MySQL** | Public datasets, processed grids and saved simulation results |

### Java Class Responsibilities

| Class | Responsibility |
|-------|----------------|
| `Cell` | Stores position, vegetation, elevation, slope, fire state and risk |
| `ForestGrid` | Builds and manages the 2-D grid of cells from the dataset |
| `Wind` | Holds wind direction and speed |
| `Graph` | Stores neighbour connections; provides BFS traversal for propagation |
| `SpreadCalculator` | Computes the spread score/probability from wind, vegetation and slope |
| `FireSimulator` | Runs the cellular automaton step by step |
| `RiskAssessment` | Computes and classifies risk levels; recalibrates live each step |
| `ReportManager` | Prepares simulation statistics and results as JSON for the web interface |

---

## 4. System Architecture & 4-Part Implementation Plan

### 4.1 High-Level Architecture

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                               PART 1 — PYTHON                                │
│   Raw CSV ──► Pandas/NumPy cleaning ──► Normalisation ──► Column mapping     │
│                              │                                               │
│                              ▼  clean, mapped CSV  (or MySQL)                │
├──────────────────────────────────────────────────────────────────────────────┤
│                          PART 2 — JAVA OOP CORE (BACKEND)                    │
│   CSV Loader ──► ForestGrid ──► Cell[][] ──► Graph (neighbours)              │
│                         │                                                    │
│                         ▼                                                    │
│        FireSimulator (CA)  ◄──►  SpreadCalculator (wind·veg·slope)           │
│                         │  BFS propagation over Graph                        │
├──────────────────────────────────────────────────────────────────────────────┤
│                           PART 3 — RISK & HEATMAP ENGINE                     │
│   Cell states (every step) ──► RiskAssessment ──► Risk grid + class counts   │
│                         │                                                    │
│                         ▼        JSON over REST (Javalin)                    │
├──────────────────────────────────────────────────────────────────────────────┤
│                     PART 4 — WEB FRONTEND (HTML / CSS / JS)                  │
│   Uploader ─ Controls ─ Animated Canvas ─ Live Heatmap ─ Charts ─ Reports    │
└──────────────────────────────────────────────────────────────────────────────┘
```

**Request flow:** the browser uploads a CSV → Java builds the grid → the browser repeatedly asks Java to advance one step → Java returns the new cell states, risk values and statistics as JSON → JavaScript redraws the canvas.

---

### Part 1 — Python Data Preprocessing & CSV Mapping Engine

**Goal:** turn any messy public/custom CSV into a clean, normalised, grid-ready dataset.

**Responsibilities**
- Load raw CSV with **Pandas**; detect **missing / invalid** values (NaN, negative vegetation, out-of-range slope or wind).
- **Remove duplicate** records (e.g. repeated `row,col` pairs).
- **Impute** missing values (median / neighbour interpolation) or drop unrecoverable rows.
- **Normalise** features with **NumPy** (vegetation → `[0,1]`, wind direction → degrees `[0,360)`, elevation scaling).
- **Derive slope** from elevation using gradients when slope is not supplied.
- **Header mapping engine**: match known aliases to canonical fields (`row, col, vegetation, elevation, slope, wind_dir, wind_speed, fire`) and infer grid `rows × cols`.
- Export the **clean CSV** (or load into **MySQL**) for the Java application.

**Output:** `processed_grid.csv` + a small metadata summary (rows, cols, rows dropped, columns mapped).

---

### Part 2 — Java OOP Core, Forest Grid & Cellular Automaton Engine

**Goal:** model the forest and simulate fire propagation (the heart of the project).

**Responsibilities**
- Parse the processed CSV and **auto-detect grid dimensions**; build `ForestGrid` → `Cell[][]`.
- Build the **`Graph`** of 8-neighbour connections between cells.
- Implement **`FireSimulator`**, the cellular automaton: `UNBURNED → BURNING → BURNED`, double-buffered for synchronous updates.
- Use **BFS-based propagation** to visit only the frontier (burning cells and their neighbours) rather than scanning the whole grid each step.
- Implement **`SpreadCalculator`** to compute spread probability from wind alignment, vegetation density and slope; support seeded random numbers for reproducible runs.
- Support multiple ignition points and an adjustable burn duration.
- Expose the engine through a thin REST controller (see [Section 5](#5-rest-api-reference)).

**Core step logic (pseudo-code)**

```text
for each BURNING cell c in frontier (BFS queue):
    for each UNBURNED neighbour n of c in Graph:
        p = SpreadCalculator.compute(c, n, wind)
        if random() < p: mark n as NEXT_BURNING
    mark c as NEXT_BURNED
apply all NEXT_* states (synchronous update)
```

---

### Part 3 — Dynamic Risk Assessment & Live Heatmap Engine

**Goal:** compute risk per cell and keep it **continuously recalibrated** as the fire evolves.

**Responsibilities**
- `RiskAssessment` computes a **riskScore** for every non-burned cell from:
  - **proximity** to burning cells (BFS distance on the `Graph`),
  - **wind alignment** towards the cell,
  - **vegetation density** (fuel),
  - **slope** relative to the nearest fire front.
- Scores are **re-evaluated after every simulation step**, triggered by state changes to `BURNING` / `BURNED`:
  - cells ahead of the fire in a favourable wind/slope direction → risk **increases**,
  - `BURNED` cells → fuel exhausted, risk **zeroed**, and cells behind them → risk **decreases**.
- Scores are **mapped to classes** — Low, Moderate, High, Very High — and returned with every step so the frontend can colour the heatmap.
- Maintains **live statistics**: burning / burned / unburned counts, cells per risk class, estimated affected area, and the **dominant direction of spread** (centroid drift of the fire front).

---

### Part 4 — Web Frontend: Dashboard, Uploader & Real-Time Animated Visualization

**Goal:** give users a clean, interactive, browser-based dashboard.

**Responsibilities**
- **HTML**: semantic page structure — header, control panel, canvas area, statistics panel, legend.
- **CSS**: modern responsive layout (Flexbox/Grid), dark/light theme, colour-coded legends.
- **JavaScript (ES6)**:
  - **CSV Uploader** with drag-and-drop, file preview and validation messages (`fetch` + `FormData`).
  - **Dynamic canvas renderer** that scales to any `rows × cols` and redraws every tick.
  - **Simulation controls**: Start / Pause / Step / Reset, speed slider, wind direction + speed inputs, click-to-ignite.
  - **View toggle**: *Fire State view* ⇄ *Live Risk Heatmap view*.
  - **Live charts**: fire progression (burning vs burned over time) and risk-class distribution.
  - **Report panel**: final results (burned area, direction of spread, risk summary) with option to export as JSON/CSV/PNG.

---

## 5. REST API Reference

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/upload` | Upload CSV (multipart). Returns detected `rows`, `cols`, column mapping and validation warnings. |
| `POST` | `/api/simulation/init` | Body: ignition cells, wind direction/speed, burn duration, seed. Creates a fresh simulation. |
| `POST` | `/api/simulation/step` | Advances one CA step. Returns cell states, risk scores/levels and live statistics. |
| `POST` | `/api/simulation/wind` | Updates wind direction/speed mid-simulation. |
| `POST` | `/api/simulation/reset` | Resets the grid to its initial state. |
| `GET`  | `/api/report` | Final report: burned area, spread direction, risk summary, per-step history. |

Sample `step` response (abbreviated):

```json
{
  "step": 12,
  "states": [[0,0,1,2], [0,1,1,2], "..."],
  "risk":   [[0.12,0.44,0.0,0.0], "..."],
  "stats":  { "burning": 38, "burned": 61, "unburned": 901,
              "riskCounts": {"low": 640, "moderate": 190, "high": 55, "veryHigh": 16},
              "spreadDirection": "NE", "affectedAreaHa": 12.4 }
}
```

---

## 6. Step-by-Step System Workflow

```
Public/Custom Dataset
        │
        ▼
 1. Data Collection ────────── Upload CSV from the web dashboard
        │
        ▼
 2. Data Preprocessing ─────── Pandas/NumPy: clean, de-duplicate, normalise, map columns
        │
        ▼
 3. Forest Grid Creation ───── Java detects rows × cols; builds Cell[][] and neighbour Graph
        │
        ▼
 4. Fire Initialisation ────── Click ignition cell(s) on canvas; configure wind
        │
        ▼
 5. Neighbour Detection ────── BFS over Graph to find frontier neighbours
        │
        ▼
 6. Environmental Analysis ─── Wind + Vegetation + Slope → spread probability
        │
        ▼
 7. Fire Spread (CA step) ──── UNBURNED → BURNING → BURNED
        │
        ▼
 8. Live Risk Assessment ───── Recalibrate risk scores & heatmap colours every step
        │
        ▼
 9. Real-Time Visualisation ── JSON → JavaScript redraws canvas, heatmap, charts
        │  (repeat 5 → 9 for each simulation step)
        ▼
10. Final Results ──────────── Burned area, direction of spread, risk summary
```

---

## 7. Expected Results

- ✅ Animated visual simulation showing **how the fire spreads over time**.
- ✅ Grid/map displaying **burning, burned, unburned and high-risk** cells.
- ✅ **Live heatmap** whose colours shift as the fire advances.
- ✅ **Predicted direction** of fire spread.
- ✅ **Estimated affected / burned area**.
- ✅ **Risk levels** (Low / Moderate / High / Very High) for different forest regions.
- ✅ **Graphs** of fire progression and simulation statistics.
- ✅ Ability to **load any compatible CSV** and re-run scenarios with different wind and ignition settings.

---

## 8. Viva Highlights

### 🎤 One-Minute Explanation

> Our project is a **Forest Fire Spread Predictor**. We divide a forest into small cells and start a fire at a selected location. The system checks neighbouring cells and decides whether the fire can spread to them, mainly based on **wind direction, vegetation density and terrain slope**. **Cellular automata** update the state of each cell (UNBURNED → BURNING → BURNED), while **graph-based BFS propagation** identifies connected neighbouring cells. The simulation engine is written in **Java using OOP**, and the user interface is a **web dashboard built with HTML, CSS and JavaScript**. Users can **upload any CSV dataset**, and the system detects the grid size automatically. During the simulation, a **live risk heatmap** recalibrates at every step, showing which areas are becoming more or less dangerous. After several steps the system shows how the fire may spread, which areas are at higher risk, and how much area could be affected.

### ❓ Likely Viva Questions & Short Answers

| Question | Answer |
|----------|--------|
| *Why a grid / cellular automaton?* | Simple local rules produce realistic emergent spread; easy to scale and visualise. |
| *Where is the graph used?* | Cells are nodes, neighbours are edges; BFS gives frontier-based propagation and distance-to-fire for risk. |
| *Why BFS?* | Fire spreads outward in layers — exactly BFS's level-by-level traversal; also avoids scanning the full grid each step. |
| *Why synchronous (double-buffered) updates?* | So processing order doesn't bias results; every cell sees the same previous generation. |
| *How is wind modelled?* | Via cosine of the angle between wind direction and the fire-to-neighbour vector, scaled by wind speed. |
| *How does slope affect spread?* | Uphill increases probability (exponential in slope), downhill decreases it. |
| *What is "dynamic" about the heatmap?* | Risk is recomputed every step from the current states; burning cells raise nearby risk, burned cells remove fuel and lower it. |
| *How does CSV upload handle any dataset?* | Header alias mapping + dimension inference; Python preprocessing cleans and normalises before Java builds the grid. |
| *Why Java for the engine and JS for the UI?* | Java gives a strong OOP structure for the simulation logic; the browser gives a lightweight, installation-free, interactive UI. |
| *How do frontend and backend communicate?* | REST APIs exchanging JSON: the browser requests a step, Java returns states, risk values and stats. |
| *What are limitations?* | Simplified physics, uniform cell size, stochastic results; no real-time satellite feed yet. |

### 🧠 Concepts Demonstrated

`Object-Oriented Programming` · `Graph Theory & BFS` · `Cellular Automata` · `Probabilistic Modelling` · `REST APIs & JSON` · `Data Cleaning & Normalisation` · `HTML5 Canvas & DOM Events` · `Data Visualisation`

---

## 9. Project Structure

```
forest-fire-spread-predictor/
├── python/
│   ├── preprocess.py                # Pandas/NumPy cleaning & normalisation
│   ├── column_mapper.py             # Header alias mapping & grid dimension inference
│   └── analysis.py                  # Matplotlib progression graphs (optional)
├── backend/                         # Java OOP simulation engine + REST layer
│   ├── pom.xml
│   └── src/main/java/forestfire/
│       ├── model/
│       │   ├── Cell.java
│       │   ├── ForestGrid.java
│       │   ├── Wind.java
│       │   └── Graph.java
│       ├── engine/
│       │   ├── FireSimulator.java
│       │   ├── SpreadCalculator.java
│       │   └── RiskAssessment.java
│       ├── io/
│       │   └── CsvLoader.java
│       ├── report/
│       │   └── ReportManager.java
│       └── App.java                 # Starts the server, defines REST routes
├── frontend/                        # Served as static files by the backend
│   ├── index.html
│   ├── css/
│   │   └── style.css
│   └── js/
│       ├── api.js                   # fetch() calls to the REST API
│       ├── canvas.js                # Grid + heatmap rendering
│       ├── controls.js              # Upload, play/pause, wind, ignition
│       └── charts.js                # Live charts & report panel
├── data/
│   ├── raw/                         # Original public/custom CSVs
│   └── processed/                   # Cleaned, mapped CSVs
├── docs/                            # Project document, screenshots
└── README.md
```

> *Folder and file names are indicative — adjust to match your repository.*

---

## 10. Installation & Usage

### Prerequisites

- **JDK 17+** and **Maven** (or Gradle)
- **Python 3.9+** with `pandas`, `numpy`, `matplotlib`
- A modern web browser (Chrome, Edge, Firefox)
- *(Optional)* **MySQL 8+** for database storage

### Setup

```bash
# 1. Clone the repository
git clone https://github.com/<your-username>/forest-fire-spread-predictor.git
cd forest-fire-spread-predictor

# 2. Install Python dependencies
pip install pandas numpy matplotlib

# 3. (Optional) Preprocess a raw dataset from the command line
python python/preprocess.py --input data/raw/my_forest.csv --output data/processed/my_forest.csv

# 4. Build and start the Java backend (also serves the frontend)
cd backend
mvn clean package
java -jar target/forest-fire-predictor.jar

# 5. Open the dashboard in your browser
#    http://localhost:7070
```

### Using the Application

1. Open the dashboard and **upload a CSV** (raw or processed file).
2. Review the detected **grid size and column mapping**.
3. Set **wind direction and speed**; **click the canvas** to choose the ignition cell(s).
4. Press **Start** to animate the simulation; use **Pause / Step / Reset** and the **speed slider** as needed.
5. Toggle between **Fire State** and **Live Risk Heatmap** views.
6. Review the **final report** (burned area, spread direction, risk summary).

---

## 11. Future Scope

- Integration with **live weather APIs** and **satellite fire data** (e.g. NASA FIRMS).
- **Machine-learning** calibration of spread coefficients from historical fires.
- Support for **fire breaks, water bodies and suppression resources**.
- **Time-varying wind** and humidity models.
- **WebSocket streaming** for smoother real-time updates.
- Overlay on **real maps** (Leaflet/OpenStreetMap) and export of **GIS layers / PDF reports**.

---

## 12. Authors & License

**Team:** *\<add team member names and roll numbers\>*
**Institution:** Rajeev Gandhi Memorial College of Engineering & Technology (RGMCET), Nandyal, Andhra Pradesh, India
**Guide:** *\<add faculty guide name\>*

This project is developed for academic purposes as part of a semester project. Released under the **MIT License** *(change if required by your institution)*.

<div align="center">

*Built with ☕ Java, 🌐 HTML/CSS/JS, 🐍 Python and a healthy respect for wildfires.*

</div>
