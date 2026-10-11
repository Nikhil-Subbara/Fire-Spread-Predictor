#!/usr/bin/env python3
"""
================================================================================
Forest Fire Spread Predictor — Header Alias Mapping & Grid Inference Engine
Problem Statement No. 86 | Semester Project
================================================================================
Module: python/column_mapper.py
Role: Advanced Header Alias Mapping Engine & Coordinate Grid Dimension Detection

This module automatically discovers, cleans, and normalises input CSV column
headers against canonical simulation schema fields. It tolerates messy naming
conventions, abbreviations, case variations, whitespace, and delimiter quirks
frequently found in public GIS, satellite (MODIS/VIIRS), and meteorological datasets.
================================================================================
"""

import re
from typing import Dict, List, Tuple, Set, Optional
import pandas as pd


class ColumnMappingError(Exception):
    """Raised when critical required columns (e.g., coordinates) cannot be mapped."""
    pass


class ColumnMapper:
    """
    Advanced Column Header Normalisation and Alias Resolution Engine.

    Maps diverse column aliases across public/custom forest datasets to the
    canonical schema expected by the Java simulation backend:
        - row        : Row index in the discretised grid (0-indexed integer)
        - col        : Column index in the discretised grid (0-indexed integer)
        - vegetation : Vegetation density / fuel load [0.0 - 1.0]
        - elevation  : Topographic elevation (meters above sea level)
        - slope      : Terrain incline / gradient (degrees or computed from DEM)
        - wind_dir   : Wind direction in degrees [0.0 - 360.0)
        - wind_speed : Wind speed (km/h or m/s)
        - fire       : Initial ignition state (0=UNBURNED, 1=BURNING, 2=BURNED)
    """

    # Canonical columns required or supported by the Java OOP simulation core
    CANONICAL_COLUMNS: List[str] = [
        "row",
        "col",
        "vegetation",
        "elevation",
        "slope",
        "wind_dir",
        "wind_speed",
        "fire",
    ]

    # Required coordinates without which spatial 2D grid simulation is impossible
    MANDATORY_COORDINATES: List[str] = ["row", "col"]

    # Comprehensive alias dictionary matching aliases and common abbreviations
    ALIAS_MAP: Dict[str, List[str]] = {
        "row": [
            "row", "y", "r", "row_idx", "row_index", "grid_row", "grid_y",
            "lat_idx", "latitude_idx", "y_coord", "y_pos", "cell_row"
        ],
        "col": [
            "col", "x", "c", "col_idx", "column", "col_index", "grid_col",
            "grid_x", "lon_idx", "longitude_idx", "x_coord", "x_pos", "cell_col"
        ],
        "vegetation": [
            "vegetation", "ndvi", "fuel", "veg", "vegetation_density",
            "fuel_density", "fuel_load", "biomass", "veg_density", "cover",
            "canopy_cover", "fuel_type"
        ],
        "elevation": [
            "elevation", "altitude", "elev", "height", "dem", "alt",
            "z", "terrain_height", "altitude_m", "elevation_m"
        ],
        "slope": [
            "slope", "gradient", "terrain_slope", "incline", "slope_deg",
            "slope_degrees", "slope_angle", "pitch"
        ],
        "wind_dir": [
            "wind_dir", "wind_direction", "winddir", "wind_deg", "wind_degrees",
            "wind_angle", "direction", "bearing", "wind_azimuth"
        ],
        "wind_speed": [
            "wind_speed", "wind", "windspeed", "speed", "wind_velocity",
            "velocity", "wind_kph", "wind_mps"
        ],
        "fire": [
            "fire", "ignition", "state", "fire_state", "is_burning",
            "burned", "status", "ignited", "active_fire", "ignition_point"
        ],
    }

    def __init__(self, alias_map: Optional[Dict[str, List[str]]] = None):
        """
        Initialise the ColumnMapper with optional custom alias overrides.

        Args:
            alias_map: Optional custom dictionary mapping canonical names to aliases.
        """
        self.alias_map = alias_map or self.ALIAS_MAP

    @staticmethod
    def clean_header_string(header: str) -> str:
        """
        Sanitise header strings into a canonical token format.
        Converts to lowercase, trims whitespace, removes non-alphanumeric symbols
        except underscores, and collapses multiple underscores.

        Examples:
            ' Wind-Direction (deg) ' -> 'wind_direction_deg'
            'NDVI_2024' -> 'ndvi_2024'
            'Row #' -> 'row'
        """
        if not isinstance(header, str):
            header = str(header)
        cleaned = header.strip().lower()
        # Replace hyphens, dots, spaces, slashes with underscores
        cleaned = re.sub(r"[\s\-\.\/\\\(\)\[\]\{\}\#\:\,\;]+", "_", cleaned)
        # Remove consecutive underscores and leading/trailing underscores
        cleaned = re.sub(r"_+", "_", cleaned).strip("_")
        return cleaned

    def map_columns(self, raw_columns: List[str]) -> Tuple[Dict[str, str], List[str]]:
        """
        Match input CSV columns to canonical fields.

        Returns:
            Tuple of:
                - mapping: Dict[raw_column_name, canonical_field_name]
                - unmapped_columns: List of columns not matching any canonical field
        """
        cleaned_to_raw: Dict[str, str] = {
            self.clean_header_string(col): col for col in raw_columns
        }

        mapping: Dict[str, str] = {}
        matched_canonical: Set[str] = set()

        # Step 1: Exact matches against cleaned header strings
        for canonical, aliases in self.alias_map.items():
            if canonical in matched_canonical:
                continue
            for alias in aliases:
                cleaned_alias = self.clean_header_string(alias)
                if cleaned_alias in cleaned_to_raw:
                    raw_col = cleaned_to_raw[cleaned_alias]
                    mapping[raw_col] = canonical
                    matched_canonical.add(canonical)
                    break

        # Step 2: Prefix / Token boundary matching for remaining canonical fields
        # Handles cases like 'avg_wind_speed', 'cell_elevation_dem', etc.
        for canonical, aliases in self.alias_map.items():
            if canonical in matched_canonical:
                continue
            for cleaned_hdr, raw_col in cleaned_to_raw.items():
                if raw_col in mapping:
                    continue  # Already assigned
                for alias in aliases:
                    cleaned_alias = self.clean_header_string(alias)
                    # Check token match (e.g. 'wind_speed_kmh' matches alias 'wind_speed')
                    if (
                        cleaned_alias == cleaned_hdr
                        or cleaned_hdr.startswith(f"{cleaned_alias}_")
                        or cleaned_hdr.endswith(f"_{cleaned_alias}")
                        or f"_{cleaned_alias}_" in cleaned_hdr
                    ):
                        mapping[raw_col] = canonical
                        matched_canonical.add(canonical)
                        break
                if canonical in matched_canonical:
                    break

        # Identify all columns that could not be mapped
        unmapped_columns = [col for col in raw_columns if col not in mapping]

        return mapping, unmapped_columns

    def validate_and_apply(
        self, df: pd.DataFrame
    ) -> Tuple[pd.DataFrame, Dict[str, str], List[str]]:
        """
        Validates presence of mandatory coordinates, renames columns to canonical
        names, and drops unmapped columns safely without crashing.

        Args:
            df: Raw input DataFrame

        Returns:
            Tuple of:
                - transformed DataFrame with canonical headers
                - mapping dictionary {raw_name: canonical_name}
                - list of dropped unmapped column names

        Raises:
            ColumnMappingError: If required coordinate fields ('row', 'col') are missing.
        """
        mapping, unmapped = self.map_columns(list(df.columns))

        mapped_canonical_values = set(mapping.values())
        missing_mandatory = [
            req for req in self.MANDATORY_COORDINATES if req not in mapped_canonical_values
        ]

        if missing_mandatory:
            raise ColumnMappingError(
                f"Missing critical spatial coordinates: {missing_mandatory}. "
                f"Available raw columns: {list(df.columns)}. "
                f"At least 'row' (or 'y') and 'col' (or 'x') must be provided."
            )

        # Rename mapped columns
        renamed_df = df.rename(columns=mapping)

        # Keep only canonical columns present in the renamed DataFrame
        cols_to_keep = [col for col in self.CANONICAL_COLUMNS if col in renamed_df.columns]
        pruned_df = renamed_df[cols_to_keep].copy()

        return pruned_df, mapping, unmapped
