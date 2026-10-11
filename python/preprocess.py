#!/usr/bin/env python3
"""
================================================================================
🔥 Forest Fire Spread Predictor — Data Preprocessing Backend
Problem Statement No. 86 | Semester Project
================================================================================
Architecture Layer: Part 1 — Python Data Preprocessing & CSV Mapping Engine
File: python/preprocess.py

Authors: RGMCET B.Tech CSE (Data Science)
Tech Stack: Python 3.9+, Pandas, NumPy

Core Functional Capabilities:
1. Robust Data Loading & Error Handling:
   - Resilient CSV loading with multiple encoding fallbacks, delimiter detection,
     and explicit exception handling (file not found, empty file, malformed rows).
2. Advanced Header Alias Mapping Engine:
   - Canonical column resolution (row, col, vegetation, elevation, slope, wind_dir,
     wind_speed, fire) via fuzzy/alias pattern matching.
   - Graceful dropping of unmapped auxiliary columns.
3. Data Cleaning, De-duplication & Imputation:
   - Coordinate type enforcement (0-indexed integer grid positions).
   - Strict de-duplication on (row, col) coordinates, preserving first occurrence.
   - Outlier detection, clamping, and intelligent imputation (median / default fallbacks).
4. Feature Normalisation & Grid Inference:
   - Strict normalisation of vegetation density to [0.0, 1.0] using NumPy.
   - Automatic grid dimension inference (total_rows × total_cols).
   - Dynamic slope derivation using 2D elevation gradients (central differences)
     when slope data is missing or incomplete.
   - Optional grid densification for missing coordinate cells.
5. Export & Comprehensive Metadata Summary:
   - Standardised CSV export ready for the Java OOP simulation core.
   - JSON metadata generation and rich console summary verification.
================================================================================
"""

import os
import sys
import json
import argparse
import logging
from pathlib import Path
from typing import Dict, List, Tuple, Optional, Any
from dataclasses import dataclass, asdict

# Ensure cross-platform UTF-8 terminal encoding
if sys.platform.startswith("win"):
    try:
        if hasattr(sys.stdout, "reconfigure"):
            sys.stdout.reconfigure(encoding="utf-8", errors="replace")
        if hasattr(sys.stderr, "reconfigure"):
            sys.stderr.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass

import numpy as np
import pandas as pd

# Import the modular ColumnMapper
try:
    from python.column_mapper import ColumnMapper, ColumnMappingError
except ImportError:
    try:
        from column_mapper import ColumnMapper, ColumnMappingError
    except ImportError:
        # Fallback inline ColumnMapper if imported outside standard sys.path
        import re

        class ColumnMappingError(Exception):
            pass

        class ColumnMapper:
            CANONICAL_COLUMNS = [
                "row", "col", "vegetation", "elevation",
                "slope", "wind_dir", "wind_speed", "fire"
            ]
            MANDATORY_COORDINATES = ["row", "col"]
            ALIAS_MAP = {
                "row": ["row", "y", "r", "row_idx", "grid_row", "lat_idx"],
                "col": ["col", "x", "c", "col_idx", "grid_col", "lon_idx"],
                "vegetation": ["vegetation", "ndvi", "fuel", "veg", "veg_density"],
                "elevation": ["elevation", "altitude", "elev", "height", "dem", "alt"],
                "slope": ["slope", "gradient", "terrain_slope", "incline"],
                "wind_dir": ["wind_dir", "wind_direction", "winddir", "wind_deg"],
                "wind_speed": ["wind_speed", "wind", "windspeed", "speed", "wind_velocity"],
                "fire": ["fire", "ignition", "state", "fire_state", "is_burning"],
            }

            def __init__(self, alias_map=None):
                self.alias_map = alias_map or self.ALIAS_MAP

            @staticmethod
            def clean_header_string(header: str) -> str:
                cleaned = str(header).strip().lower()
                cleaned = re.sub(r"[\s\-\.\/\\\(\)\[\]\{\}\#\:\,\;]+", "_", cleaned)
                return re.sub(r"_+", "_", cleaned).strip("_")

            def map_columns(self, raw_columns):
                cleaned_to_raw = {self.clean_header_string(c): c for c in raw_columns}
                mapping, matched = {}, set()
                for canonical, aliases in self.alias_map.items():
                    for alias in aliases:
                        ca = self.clean_header_string(alias)
                        if ca in cleaned_to_raw:
                            mapping[cleaned_to_raw[ca]] = canonical
                            matched.add(canonical)
                            break
                for canonical, aliases in self.alias_map.items():
                    if canonical in matched:
                        continue
                    for ch, raw in cleaned_to_raw.items():
                        if raw in mapping:
                            continue
                        for alias in aliases:
                            ca = self.clean_header_string(alias)
                            if ca in ch:
                                mapping[raw] = canonical
                                matched.add(canonical)
                                break
                        if canonical in matched:
                            break
                unmapped = [c for c in raw_columns if c not in mapping]
                return mapping, unmapped

            def validate_and_apply(self, df):
                mapping, unmapped = self.map_columns(list(df.columns))
                missing = [m for m in self.MANDATORY_COORDINATES if m not in mapping.values()]
                if missing:
                    raise ColumnMappingError(f"Missing mandatory coordinates: {missing}")
                renamed = df.rename(columns=mapping)
                keep = [c for c in self.CANONICAL_COLUMNS if c in renamed.columns]
                return renamed[keep].copy(), mapping, unmapped


# Configure structured logging
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    datefmt="%H:%M:%S"
)
logger = logging.getLogger("ForestFirePreprocessor")


# ==============================================================================
# Custom Domain Exceptions
# ==============================================================================

class PreprocessingError(Exception):
    """Base exception for preprocessing failures."""
    pass


class DatasetLoadError(PreprocessingError):
    """Raised when the CSV file cannot be opened, read, or parsed."""
    pass


class GridDimensionError(PreprocessingError):
    """Raised when spatial grid dimensions cannot be inferred or are invalid."""
    pass


# ==============================================================================
# Preprocessing Statistics Container
# ==============================================================================

@dataclass
class PreprocessingSummary:
    """Holds audit metrics for the data cleaning and transformation pipeline."""
    input_file: str
    output_file: str
    initial_rows: int = 0
    final_rows: int = 0
    duplicate_rows_dropped: int = 0
    invalid_coord_rows_dropped: int = 0
    grid_rows: int = 0
    grid_cols: int = 0
    total_grid_cells: int = 0
    sparse_cells_filled: int = 0
    columns_mapped: Dict[str, str] = None
    columns_dropped: List[str] = None
    missing_values_imputed: Dict[str, int] = None
    derived_slope_from_elevation: bool = False
    vegetation_normalised: bool = False
    ignition_cells_count: int = 0

    def to_dict(self) -> Dict[str, Any]:
        data = asdict(self)
        return data


# ==============================================================================
# Core Preprocessor Engine
# ==============================================================================

class ForestDataPreprocessor:
    """
    Production-grade Data Preprocessing Engine for Forest Fire Simulation.

    Transforms raw, uncleaned public or satellite datasets into a uniform,
    strictly validated CSV grid conforming to Java OOP simulation requirements.
    """

    # Canonical columns and expected output order for the Java engine
    OUTPUT_SCHEMA: List[str] = [
        "row",
        "col",
        "vegetation",
        "elevation",
        "slope",
        "wind_dir",
        "wind_speed",
        "fire"
    ]

    # Default fallback values for environmental attributes
    DEFAULTS: Dict[str, float] = {
        "vegetation": 0.50,    # Medium fuel load
        "elevation": 100.0,    # 100 meters above sea level
        "slope": 0.0,          # Flat ground
        "wind_dir": 0.0,       # North (0 degrees)
        "wind_speed": 0.0,     # Calm conditions (0 km/h)
        "fire": 0.0            # UNBURNED state
    }

    def __init__(
        self,
        cell_size_meters: float = 10.0,
        fill_missing_cells: bool = False,
        alias_map: Optional[Dict[str, List[str]]] = None
    ):
        """
        Initialise the preprocessor.

        Args:
            cell_size_meters: Physical dimension of a grid cell in meters (for slope calculation).
            fill_missing_cells: Whether to synthesise missing (row, col) cells for an N x M lattice.
            alias_map: Optional custom alias mapping dictionary for ColumnMapper.
        """
        self.cell_size_meters = max(1.0, float(cell_size_meters))
        self.fill_missing_cells = fill_missing_cells
        self.mapper = ColumnMapper(alias_map)
        self.summary: Optional[PreprocessingSummary] = None

    # --------------------------------------------------------------------------
    # 1. Robust Data Loading
    # --------------------------------------------------------------------------
    def load_dataset(self, file_path: Path) -> pd.DataFrame:
        """
        Load any raw CSV file with comprehensive error handling and encoding fallbacks.

        Args:
            file_path: Path to the target CSV file.

        Returns:
            pd.DataFrame: Raw parsed DataFrame.

        Raises:
            DatasetLoadError: If file is missing, empty, or unparseable.
        """
        if not file_path.exists():
            raise DatasetLoadError(f"Input file not found: '{file_path.resolve()}'")

        if not file_path.is_file():
            raise DatasetLoadError(f"Specified path is a directory, not a file: '{file_path}'")

        if file_path.stat().st_size == 0:
            raise DatasetLoadError(f"Input CSV file is empty (0 bytes): '{file_path}'")

        # Try multiple standard encodings
        encodings = ["utf-8", "utf-8-sig", "latin1", "cp1252", "iso-8859-1"]
        last_exception = None

        for enc in encodings:
            try:
                # Use python engine with sep=None to sniff delimiters (comma, semicolon, tab)
                df = pd.read_csv(
                    file_path,
                    sep=None,
                    engine="python",
                    encoding=enc,
                    skipinitialspace=True
                )
                if df.empty:
                    raise DatasetLoadError(f"CSV file contains headers but no data rows: '{file_path}'")
                logger.info(f"Loaded '{file_path.name}' successfully using encoding '{enc}'. Shape: {df.shape}")
                return df
            except pd.errors.EmptyDataError:
                raise DatasetLoadError(f"CSV file contains no readable data: '{file_path}'")
            except (UnicodeDecodeError, pd.errors.ParserError) as ex:
                last_exception = ex
                continue

        # If sniffing failed, try fallback comma-separated
        try:
            df = pd.read_csv(file_path, sep=",", encoding="utf-8", on_bad_lines="skip")
            if not df.empty:
                logger.warning(f"Fell back to standard comma delimiter with skipped bad lines. Shape: {df.shape}")
                return df
        except Exception as ex:
            last_exception = ex

        raise DatasetLoadError(
            f"Failed to parse CSV dataset '{file_path}'. Error details: {last_exception}"
        )

    # --------------------------------------------------------------------------
    # 2. Header Mapping & Auxiliary Column Pruning
    # --------------------------------------------------------------------------
    def apply_header_mapping(
        self, df: pd.DataFrame
    ) -> Tuple[pd.DataFrame, Dict[str, str], List[str]]:
        """
        Map input headers to canonical simulation fields and drop unmapped columns.
        """
        try:
            mapped_df, mapping, unmapped = self.mapper.validate_and_apply(df)
            logger.info(f"Header mapping complete: {len(mapping)} columns mapped to canonical fields.")
            if unmapped:
                logger.info(f"Safely ignored unmapped columns ({len(unmapped)}): {unmapped}")
            return mapped_df, mapping, unmapped
        except ColumnMappingError as cme:
            raise PreprocessingError(f"Header mapping validation failed: {cme}")

    # --------------------------------------------------------------------------
    # 3. Coordinate Cleaning & De-duplication
    # --------------------------------------------------------------------------
    def clean_coordinates(
        self, df: pd.DataFrame
    ) -> Tuple[pd.DataFrame, int, int]:
        """
        Clean, validate, and de-duplicate grid coordinates (row, col).

        Returns:
            Tuple of:
                - cleaned DataFrame
                - count of duplicate rows dropped
                - count of invalid coordinate rows dropped
        """
        initial_count = len(df)

        # 1. Force row and col to numeric, coercing non-numeric values to NaN
        df["row"] = pd.to_numeric(df["row"], errors="coerce")
        df["col"] = pd.to_numeric(df["col"], errors="coerce")

        # 2. Filter out NaN, non-finite, or negative coordinates
        valid_coords = df["row"].notna() & df["col"].notna() & (df["row"] >= 0) & (df["col"] >= 0)
        invalid_coord_count = int((~valid_coords).sum())
        df = df[valid_coords].copy()

        if df.empty:
            raise PreprocessingError("All rows contained invalid or negative coordinates.")

        # Cast coordinates to integer indices
        df["row"] = df["row"].astype(np.int64)
        df["col"] = df["col"].astype(np.int64)

        # 3. Check for 1-based indexing (common in some GIS systems)
        min_r, min_c = df["row"].min(), df["col"].min()
        if min_r == 1 and min_c == 1:
            logger.info("Detected 1-based coordinate indexing; re-indexing to 0-based grid positions.")
            df["row"] = df["row"] - 1
            df["col"] = df["col"] - 1

        # 4. Strict De-duplication on (row, col) coordinate pairs, preserving first
        pre_dedup_count = len(df)
        df = df.drop_duplicates(subset=["row", "col"], keep="first").copy()
        duplicate_count = pre_dedup_count - len(df)

        if duplicate_count > 0:
            logger.info(f"De-duplication: Removed {duplicate_count} duplicate coordinate rows.")

        return df, duplicate_count, invalid_coord_count

    # --------------------------------------------------------------------------
    # 4. Feature Imputation & Normalisation
    # --------------------------------------------------------------------------
    def clean_and_impute_features(
        self, df: pd.DataFrame
    ) -> Tuple[pd.DataFrame, Dict[str, int]]:
        """
        Clean numeric features, handle missing/invalid values, and apply domain rules.
        """
        imputation_counts: Dict[str, int] = {}

        # Coerce all environmental features to numeric
        feature_cols = ["vegetation", "elevation", "slope", "wind_dir", "wind_speed", "fire"]
        for col in feature_cols:
            if col not in df.columns:
                df[col] = np.nan
            else:
                df[col] = pd.to_numeric(df[col], errors="coerce")

        # --- A. VEGETATION DENSITY ---
        veg_missing = int(df["vegetation"].isna().sum())
        # Impute missing vegetation with median of valid records, or default 0.50
        valid_veg = df["vegetation"].dropna()
        veg_median = float(valid_veg.median()) if not valid_veg.empty else self.DEFAULTS["vegetation"]
        df["vegetation"] = df["vegetation"].fillna(veg_median)
        imputation_counts["vegetation"] = veg_missing

        # Normalise vegetation: handle NDVI ranges [-1.0, 1.0] or percentages [0, 100]
        veg_min = float(df["vegetation"].min())
        veg_max = float(df["vegetation"].max())

        if veg_max > 1.0:
            logger.info(f"Vegetation max ({veg_max:.2f}) > 1.0; applying Min-Max normalisation.")
            if veg_max != veg_min:
                df["vegetation"] = (df["vegetation"] - veg_min) / (veg_max - veg_min)
            else:
                df["vegetation"] = self.DEFAULTS["vegetation"]
        elif veg_min < 0.0:
            # In NDVI, negative values represent water/rock/snow (zero fuel)
            logger.info("Detected negative vegetation values (e.g. NDVI water/barren); clipping <0 to 0.0.")
            df["vegetation"] = df["vegetation"].clip(lower=0.0)

        # Strictly clamp into [0.0, 1.0]
        df["vegetation"] = np.clip(df["vegetation"].values, 0.0, 1.0).round(4)

        # --- B. ELEVATION ---
        elev_missing = int(df["elevation"].isna().sum())
        valid_elev = df["elevation"].dropna()
        elev_median = float(valid_elev.median()) if not valid_elev.empty else self.DEFAULTS["elevation"]
        df["elevation"] = df["elevation"].fillna(elev_median).round(2)
        imputation_counts["elevation"] = elev_missing

        # --- C. WIND DIRECTION & SPEED ---
        wind_dir_missing = int(df["wind_dir"].isna().sum())
        df["wind_dir"] = df["wind_dir"].fillna(self.DEFAULTS["wind_dir"])
        # Wind direction modulo 360 to ensure strictly [0.0, 360.0)
        df["wind_dir"] = (df["wind_dir"] % 360.0).round(2)
        imputation_counts["wind_dir"] = wind_dir_missing

        wind_spd_missing = int(df["wind_speed"].isna().sum())
        df["wind_speed"] = df["wind_speed"].fillna(self.DEFAULTS["wind_speed"])
        df["wind_speed"] = df["wind_speed"].clip(lower=0.0).round(2)
        imputation_counts["wind_speed"] = wind_spd_missing

        # --- D. INITIAL FIRE STATE ---
        fire_missing = int(df["fire"].isna().sum())
        df["fire"] = df["fire"].fillna(self.DEFAULTS["fire"])
        # States: 0 = UNBURNED, 1 = BURNING, 2 = BURNED
        df["fire"] = df["fire"].clip(lower=0, upper=2).astype(np.int32)
        imputation_counts["fire"] = fire_missing

        return df, imputation_counts

    # --------------------------------------------------------------------------
    # 5. Grid Inference & Terrain Slope Derivation
    # --------------------------------------------------------------------------
    def derive_slope_from_elevation(
        self, df: pd.DataFrame, total_rows: int, total_cols: int
    ) -> Tuple[pd.DataFrame, bool]:
        """
        Derive slope (degrees) using 2D elevation gradient central difference
        if slope column is absent, empty, or largely unpopulated.

        Formula (Horn's Method / 2D central gradient):
            dz_dy, dz_dx = np.gradient(elev_grid, cell_size_meters)
            gradient_magnitude = sqrt(dz_dy^2 + dz_dx^2)
            slope_degrees = arctan(gradient_magnitude) * (180 / pi)
        """
        slope_missing_count = int(df["slope"].isna().sum()) if "slope" in df.columns else len(df)
        total_cells = len(df)

        # If slope is mostly populated (>80%), just impute missing slope with median
        if slope_missing_count < (0.2 * total_cells):
            valid_slope = df["slope"].dropna()
            slope_median = float(valid_slope.median()) if not valid_slope.empty else 0.0
            df["slope"] = df["slope"].fillna(slope_median).clip(0.0, 89.0).round(2)
            return df, False

        logger.info(
            f"Deriving terrain slope from 2D elevation gradient "
            f"(missing in {slope_missing_count}/{total_cells} cells)..."
        )

        # Build 2D elevation lattice
        median_elev = float(df["elevation"].median()) if not df["elevation"].empty else 100.0
        elev_grid = np.full((total_rows, total_cols), fill_value=median_elev, dtype=np.float64)

        for _, row_data in df.iterrows():
            r = int(row_data["row"])
            c = int(row_data["col"])
            if 0 <= r < total_rows and 0 <= c < total_cols:
                elev_grid[r, c] = float(row_data["elevation"])

        # Compute numerical gradient along axis 0 (rows/y) and axis 1 (cols/x)
        if total_rows > 1 and total_cols > 1:
            grad_y, grad_x = np.gradient(elev_grid, self.cell_size_meters)
            grad_mag = np.sqrt(grad_y**2 + grad_x**2)
            slope_grid_deg = np.degrees(np.arctan(grad_mag))
        elif total_rows > 1:
            grad_y = np.gradient(elev_grid, self.cell_size_meters, axis=0)
            slope_grid_deg = np.degrees(np.arctan(np.abs(grad_y)))
        elif total_cols > 1:
            grad_x = np.gradient(elev_grid, self.cell_size_meters, axis=1)
            slope_grid_deg = np.degrees(np.arctan(np.abs(grad_x)))
        else:
            slope_grid_deg = np.zeros((total_rows, total_cols), dtype=np.float64)

        # Apply derived slope values where slope was NaN or absent
        derived_slopes = []
        for _, row_data in df.iterrows():
            curr_slope = row_data.get("slope", np.nan)
            if pd.isna(curr_slope):
                r = int(row_data["row"])
                c = int(row_data["col"])
                derived = float(slope_grid_deg[r, c])
                derived_slopes.append(round(np.clip(derived, 0.0, 85.0), 2))
            else:
                derived_slopes.append(round(np.clip(float(curr_slope), 0.0, 85.0), 2))

        df["slope"] = derived_slopes
        return df, True

    # --------------------------------------------------------------------------
    # 6. Optional Grid Densification (Fill Missing Cells)
    # --------------------------------------------------------------------------
    def densify_grid(
        self, df: pd.DataFrame, total_rows: int, total_cols: int
    ) -> Tuple[pd.DataFrame, int]:
        """
        Fill unrepresented coordinate cells in the N x M lattice with
        appropriate default environmental properties.
        """
        existing_coords = set(zip(df["row"].values, df["col"].values))
        all_coords = set((r, c) for r in range(total_rows) for c in range(total_cols))
        missing_coords = all_coords - existing_coords

        if not missing_coords:
            return df, 0

        logger.info(f"Densifying grid: Synthesising {len(missing_coords)} unpopulated grid cells.")

        # Compute medians from existing data for consistent fallback
        median_veg = float(df["vegetation"].median())
        median_elev = float(df["elevation"].median())
        median_slope = float(df["slope"].median())
        median_wdir = float(df["wind_dir"].median())
        median_wspd = float(df["wind_speed"].median())

        synthetic_rows = []
        for r, c in missing_coords:
            synthetic_rows.append({
                "row": r,
                "col": c,
                "vegetation": median_veg,
                "elevation": median_elev,
                "slope": median_slope,
                "wind_dir": median_wdir,
                "wind_speed": median_wspd,
                "fire": 0  # Missing cells start UNBURNED
            })

        synthetic_df = pd.DataFrame(synthetic_rows)
        full_df = pd.concat([df, synthetic_df], ignore_index=True)
        return full_df, len(missing_coords)

    # --------------------------------------------------------------------------
    # 7. End-to-End Preprocessing Pipeline
    # --------------------------------------------------------------------------
    def process(self, input_path: Path, output_path: Path) -> pd.DataFrame:
        """
        Execute the full data cleaning, normalisation, and inference pipeline.

        Args:
            input_path: Source CSV file.
            output_path: Destination path for cleaned, mapped dataset.

        Returns:
            pd.DataFrame: Ready-to-simulate cleaned DataFrame.
        """
        logger.info(f"Starting Forest Fire Data Preprocessing for '{input_path}'...")

        # 1. Load CSV
        raw_df = self.load_dataset(input_path)
        initial_rows = len(raw_df)

        # 2. Map Column Aliases & Drop Unmapped
        mapped_df, mapping, unmapped = self.apply_header_mapping(raw_df)

        # 3. Clean Coordinates & De-duplicate
        cleaned_df, dup_dropped, invalid_dropped = self.clean_coordinates(mapped_df)

        # 4. Infer Grid Dimensions from maximum coordinates
        total_rows = int(cleaned_df["row"].max() + 1)
        total_cols = int(cleaned_df["col"].max() + 1)
        if total_rows <= 0 or total_cols <= 0:
            raise GridDimensionError(f"Inferred invalid dimensions: {total_rows}x{total_cols}")

        logger.info(f"Inferred Grid Dimensions: {total_rows} rows × {total_cols} cols "
                    f"({total_rows * total_cols} total lattice cells).")

        # 5. Clean Features & Normalise Vegetation
        featured_df, imputations = self.clean_and_impute_features(cleaned_df)

        # 6. Derive Slope from Elevation Gradients
        final_df, derived_slope = self.derive_slope_from_elevation(featured_df, total_rows, total_cols)

        # 7. Optional Densification
        filled_count = 0
        if self.fill_missing_cells:
            final_df, filled_count = self.densify_grid(final_df, total_rows, total_cols)

        # 8. Sort and Reorder Columns to Canonical Output Schema
        final_df = final_df.sort_values(by=["row", "col"], ascending=[True, True]).reset_index(drop=True)
        final_df = final_df[self.OUTPUT_SCHEMA].copy()

        # Count active fires / ignition points
        ignition_count = int((final_df["fire"] == 1).sum())

        # Compile Summary
        self.summary = PreprocessingSummary(
            input_file=str(input_path.resolve()),
            output_file=str(output_path.resolve()),
            initial_rows=initial_rows,
            final_rows=len(final_df),
            duplicate_rows_dropped=dup_dropped,
            invalid_coord_rows_dropped=invalid_dropped,
            grid_rows=total_rows,
            grid_cols=total_cols,
            total_grid_cells=total_rows * total_cols,
            sparse_cells_filled=filled_count,
            columns_mapped=mapping,
            columns_dropped=unmapped,
            missing_values_imputed=imputations,
            derived_slope_from_elevation=derived_slope,
            vegetation_normalised=True,
            ignition_cells_count=ignition_count
        )

        return final_df

    # --------------------------------------------------------------------------
    # 8. Exporting & Metadata Reporting
    # --------------------------------------------------------------------------
    def export(self, df: pd.DataFrame, output_path: Path, meta_path: Optional[Path] = None) -> None:
        """
        Export cleaned CSV and companion metadata JSON file.
        """
        output_path.parent.mkdir(parents=True, exist_ok=True)
        df.to_csv(output_path, index=False)
        logger.info(f"Exported clean grid dataset ({len(df)} rows) to '{output_path}'.")

        # Export metadata JSON
        if meta_path is None:
            meta_path = output_path.with_name(f"{output_path.stem}_metadata.json")

        if self.summary:
            meta_path.parent.mkdir(parents=True, exist_ok=True)
            with open(meta_path, "w", encoding="utf-8") as f:
                json.dump(self.summary.to_dict(), f, indent=2)
            logger.info(f"Exported metadata summary to '{meta_path}'.")

    def print_summary_report(self, df: pd.DataFrame) -> None:
        """
        Print a clear, professional console verification report for viva and debugging.
        """
        if not self.summary:
            print("No summary metrics available.")
            return

        s = self.summary
        separator = "=" * 78
        subsep = "-" * 78

        print("\n" + separator)
        print("  [AUDIT] FOREST FIRE SPREAD PREDICTOR -- PREPROCESSING VERIFICATION AUDIT")
        print(separator)
        print(f"  Input Source       : {Path(s.input_file).name}")
        print(f"  Output Target      : {Path(s.output_file).name}")
        print(f"  Grid Dimensions    : {s.grid_rows} Rows x {s.grid_cols} Columns ({s.total_grid_cells} Cells)")
        print(f"  Lattice Density    : {(s.final_rows / max(1, s.total_grid_cells)) * 100:.1f}%")
        print(subsep)
        print("  [METRICS] DATA CLEANING & PURIFICATION:")
        print(f"    * Initial Raw Records            : {s.initial_rows}")
        print(f"    * Duplicate Coordinates Removed  : {s.duplicate_rows_dropped}")
        print(f"    * Invalid/NaN Coordinates Dropped: {s.invalid_coord_rows_dropped}")
        print(f"    * Missing Cells Synthesised      : {s.sparse_cells_filled}")
        print(f"    * Final Clean Records Exported   : {s.final_rows}")
        print(subsep)
        print("  [COLUMNS] COLUMN ALIAS MAPPING AUDIT:")
        for raw, canonical in (s.columns_mapped or {}).items():
            print(f"    * [Mapped]   '{raw}'  --->  '{canonical}'")
        if s.columns_dropped:
            for unmapped in s.columns_dropped:
                print(f"    * [Dropped]  '{unmapped}' (auxiliary/unrecognised)")
        print(subsep)
        print("  [ENGINEERING] FEATURE ENGINEERING & IMPUTATION:")
        for feat, count in (s.missing_values_imputed or {}).items():
            print(f"    * {feat:<12}: {count} missing values imputed")
        print(f"    * Terrain Slope Derived from Elevation Gradients : {'YES' if s.derived_slope_from_elevation else 'NO (Pre-existing)'}")
        print(f"    * Vegetation Density Normalised strictly [0, 1]  : {'YES' if s.vegetation_normalised else 'NO'}")
        print(f"    * Active Ignition Cells (fire = 1)               : {s.ignition_cells_count}")
        print(subsep)
        print("  [DISTRIBUTIONS] FEATURE DISTRIBUTIONS (OUTPUT DATASET):")
        for col in ["vegetation", "elevation", "slope", "wind_dir", "wind_speed"]:
            vals = df[col]
            print(f"    * {col:<12}: min={vals.min():.2f}, mean={vals.mean():.2f}, max={vals.max():.2f}")
        fire_counts = df["fire"].value_counts().to_dict()
        print(f"    * Fire States : UNBURNED(0)={fire_counts.get(0, 0)}, BURNING(1)={fire_counts.get(1, 0)}, BURNED(2)={fire_counts.get(2, 0)}")
        print(separator + "\n")


# ==============================================================================
# Sample Dataset Generator for Turnkey Testing & Viva Demonstration
# ==============================================================================

def generate_synthetic_messy_dataset(output_path: Path, rows: int = 20, cols: int = 20) -> Path:
    """
    Generates a realistic raw dataset containing common real-world flaws:
      - Aliased headers ('y', 'x', 'NDVI', 'altitude_m', 'wind_direction', 'wind_velocity', 'ignition_point')
      - Auxiliary unmapped columns ('sensor_id', 'ambient_temp_c', 'humidity_pct')
      - Missing values (NaN in NDVI, altitude, wind)
      - Duplicate coordinate rows
      - Out-of-range values (negative NDVI for water bodies, wind direction > 360)
      - Omitted slope column (forcing gradient derivation from DEM)
    """
    output_path.parent.mkdir(parents=True, exist_ok=True)
    np.random.seed(42)

    records = []
    # Create terrain topography: a gentle hill in the center
    center_r, center_c = rows / 2.0, cols / 2.0

    for r in range(rows):
        for c in range(cols):
            # Elevation with a Gaussian hill
            dist = np.sqrt((r - center_r) ** 2 + (c - center_c) ** 2)
            elevation = 200.0 + 150.0 * np.exp(- (dist ** 2) / (2 * (rows / 4.0) ** 2))
            elevation += np.random.normal(0, 2.0)  # slight noise

            # Vegetation: healthy forest with a small barren/lake patch
            if dist < 2.0:
                ndvi = -0.15  # Water body / lake (negative NDVI test case)
            else:
                ndvi = np.clip(np.random.normal(0.75, 0.15), 0.1, 0.98)

            # Wind: general Southwest wind (approx 225 deg) with gust variations
            wind_dir = 225.0 + np.random.normal(0, 15)
            wind_speed = max(0.0, np.random.normal(14.0, 3.0))

            # Initial fire at coordinate (15, 5)
            ignition = 1 if (r == 15 and c == 5) else 0

            records.append({
                "y": r,                             # Alias for 'row'
                "x": c,                             # Alias for 'col'
                "NDVI": round(ndvi, 3),             # Alias for 'vegetation'
                "altitude_m": round(elevation, 1),  # Alias for 'elevation'
                # Note: 'slope' is deliberately omitted to test gradient derivation!
                "wind_direction": round(wind_dir, 1), # Alias for 'wind_dir'
                "wind_velocity": round(wind_speed, 1),# Alias for 'wind_speed'
                "ignition_point": ignition,         # Alias for 'fire'
                "sensor_id": f"SN_{r}_{c}",         # Unmapped column 1
                "ambient_temp_c": round(np.random.uniform(28.0, 38.0), 1), # Unmapped column 2
                "humidity_pct": round(np.random.uniform(15.0, 35.0), 1)    # Unmapped column 3
            })

    # Intentionally insert duplicate coordinate records
    records.append(records[10].copy())
    records.append(records[25].copy())

    # Intentionally insert some NaNs and out-of-range values
    records[5]["NDVI"] = np.nan
    records[12]["altitude_m"] = np.nan
    records[18]["wind_direction"] = 410.0  # Out of range (>360) test case
    records[30]["wind_velocity"] = -5.0    # Negative wind speed test case

    df = pd.DataFrame(records)
    # Shuffle slightly to emulate disordered CSV dumps
    df = df.sample(frac=1.0, random_state=42).reset_index(drop=True)
    df.to_csv(output_path, index=False)
    logger.info(f"Generated realistic messy sample CSV ({len(df)} rows) at '{output_path}'.")
    return output_path


# ==============================================================================
# CLI Entrypoint
# ==============================================================================

def parse_arguments() -> argparse.Namespace:
    """Parse command line arguments."""
    parser = argparse.ArgumentParser(
        description="🔥 Forest Fire Spread Predictor — CSV Preprocessing & Alias Mapping Backend",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter
    )
    parser.add_argument(
        "-i", "--input",
        type=str,
        default="data/raw/forest_raw.csv",
        help="Path to the input raw CSV dataset."
    )
    parser.add_argument(
        "-o", "--output",
        type=str,
        default="data/processed/processed_grid.csv",
        help="Path to export the clean, normalised grid dataset."
    )
    parser.add_argument(
        "-m", "--meta",
        type=str,
        default=None,
        help="Optional path to export the companion metadata JSON file."
    )
    parser.add_argument(
        "--cell-size",
        type=float,
        default=10.0,
        help="Physical grid cell resolution in meters (used for elevation slope derivation)."
    )
    parser.add_argument(
        "--fill-missing-cells",
        action="store_true",
        help="Automatically synthesise missing (row, col) cells to guarantee an unbroken N x M lattice."
    )
    parser.add_argument(
        "--generate-sample",
        action="store_true",
        help="Generate a synthetic messy raw dataset with intentional real-world flaws for immediate testing."
    )
    parser.add_argument(
        "--sample-grid-size",
        type=int,
        default=20,
        help="Grid dimension (N x N) for the synthetic sample generator."
    )
    return parser.parse_args()


def main() -> int:
    """Main CLI execution routine."""
    args = parse_arguments()

    input_path = Path(args.input)
    output_path = Path(args.output)
    meta_path = Path(args.meta) if args.meta else None

    # Handle synthetic sample dataset generation
    if args.generate_sample or not input_path.exists():
        if not input_path.exists() and not args.generate_sample:
            logger.warning(
                f"Input file '{input_path}' does not exist. "
                f"Generating a synthetic test dataset automatically to proceed..."
            )
            input_path = Path("data/raw/sample_forest_raw.csv")
        else:
            input_path = Path("data/raw/sample_forest_raw.csv")

        generate_synthetic_messy_dataset(
            input_path, rows=args.sample_grid_size, cols=args.sample_grid_size
        )

    try:
        preprocessor = ForestDataPreprocessor(
            cell_size_meters=args.cell_size,
            fill_missing_cells=args.fill_missing_cells
        )

        cleaned_df = preprocessor.process(input_path, output_path)
        preprocessor.export(cleaned_df, output_path, meta_path)
        preprocessor.print_summary_report(cleaned_df)

        logger.info("Preprocessing completed successfully. Data is ready for Java simulation core.")
        return 0

    except PreprocessingError as pe:
        logger.error(f"Preprocessing aborted: {pe}")
        return 1
    except Exception as ex:
        logger.exception(f"Unexpected fatal error during preprocessing: {ex}")
        return 2


if __name__ == "__main__":
    sys.exit(main())
