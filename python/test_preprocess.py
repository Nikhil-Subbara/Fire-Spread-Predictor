#!/usr/bin/env python3
"""
Unit and Integration Test Suite for Forest Fire Preprocessing Backend
Problem Statement No. 86 | Part 1: python/preprocess.py
"""

import unittest
import tempfile
from pathlib import Path
import pandas as pd
import numpy as np

from python.column_mapper import ColumnMapper, ColumnMappingError
from python.preprocess import (
    ForestDataPreprocessor,
    DatasetLoadError,
    PreprocessingError,
    generate_synthetic_messy_dataset
)


class TestForestDataPreprocessor(unittest.TestCase):

    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.dir_path = Path(self.temp_dir.name)
        self.preprocessor = ForestDataPreprocessor(cell_size_meters=10.0)

    def tearDown(self):
        self.temp_dir.cleanup()

    def test_missing_file_raises_dataset_load_error(self):
        missing_path = self.dir_path / "non_existent.csv"
        with self.assertRaises(DatasetLoadError):
            self.preprocessor.load_dataset(missing_path)

    def test_empty_file_raises_dataset_load_error(self):
        empty_path = self.dir_path / "empty.csv"
        empty_path.touch()
        with self.assertRaises(DatasetLoadError):
            self.preprocessor.load_dataset(empty_path)

    def test_missing_mandatory_coordinates(self):
        csv_path = self.dir_path / "missing_coords.csv"
        df = pd.DataFrame({
            "vegetation": [0.5, 0.6],
            "elevation": [120, 130]
        })
        df.to_csv(csv_path, index=False)
        with self.assertRaises(PreprocessingError):
            self.preprocessor.process(csv_path, self.dir_path / "out.csv")

    def test_alias_mapping_and_dropping_unmapped(self):
        raw_df = pd.DataFrame({
            "R": [0, 1],
            "C": [0, 1],
            "veg_density": [0.4, 0.8],
            "altitude": [200, 220],
            "incline": [5.0, 8.0],
            "wind_direction": [90, 180],
            "wind": [10, 15],
            "ignition": [0, 1],
            "unmapped_col_1": ["foo", "bar"],
            "unmapped_col_2": [999, 888]
        })
        csv_path = self.dir_path / "aliased.csv"
        out_path = self.dir_path / "aliased_out.csv"
        raw_df.to_csv(csv_path, index=False)

        cleaned_df = self.preprocessor.process(csv_path, out_path)
        expected_cols = ["row", "col", "vegetation", "elevation", "slope", "wind_dir", "wind_speed", "fire"]
        self.assertListEqual(list(cleaned_df.columns), expected_cols)
        self.assertEqual(len(cleaned_df), 2)
        self.assertNotIn("unmapped_col_1", cleaned_df.columns)
        self.assertNotIn("unmapped_col_2", cleaned_df.columns)

    def test_deduplication(self):
        raw_df = pd.DataFrame({
            "row": [0, 0, 1],
            "col": [0, 0, 1],
            "vegetation": [0.5, 0.9, 0.7],  # duplicate at (0,0) with different veg
            "elevation": [100, 110, 120]
        })
        csv_path = self.dir_path / "dups.csv"
        out_path = self.dir_path / "dups_out.csv"
        raw_df.to_csv(csv_path, index=False)

        cleaned_df = self.preprocessor.process(csv_path, out_path)
        self.assertEqual(len(cleaned_df), 2)
        # Verify first occurrence was kept
        self.assertAlmostEqual(cleaned_df.loc[0, "vegetation"], 0.5)

    def test_vegetation_percentage_normalisation(self):
        raw_df = pd.DataFrame({
            "row": [0, 0, 1, 1],
            "col": [0, 1, 0, 1],
            "vegetation": [0.0, 50.0, 75.0, 100.0],  # 0-100 percentage fuel
            "elevation": [100, 100, 100, 100]
        })
        csv_path = self.dir_path / "veg_pct.csv"
        out_path = self.dir_path / "veg_pct_out.csv"
        raw_df.to_csv(csv_path, index=False)

        cleaned_df = self.preprocessor.process(csv_path, out_path)
        self.assertTrue((cleaned_df["vegetation"] >= 0.0).all())
        self.assertTrue((cleaned_df["vegetation"] <= 1.0).all())
        self.assertAlmostEqual(cleaned_df["vegetation"].max(), 1.0)
        self.assertAlmostEqual(cleaned_df["vegetation"].min(), 0.0)

    def test_slope_derivation_from_elevation_gradient(self):
        # 3x3 grid with an uphill ramp from west to east
        rows, cols = [], []
        elevations = []
        for r in range(3):
            for c in range(3):
                rows.append(r)
                cols.append(c)
                elevations.append(100.0 + c * 20.0)  # 20m elevation rise per 10m cell -> steep slope

        raw_df = pd.DataFrame({
            "row": rows,
            "col": cols,
            "elevation": elevations
            # 'slope' column omitted intentionally
        })
        csv_path = self.dir_path / "elev_slope.csv"
        out_path = self.dir_path / "elev_slope_out.csv"
        raw_df.to_csv(csv_path, index=False)

        cleaned_df = self.preprocessor.process(csv_path, out_path)
        self.assertIn("slope", cleaned_df.columns)
        self.assertTrue((cleaned_df["slope"] > 0.0).any())
        self.assertTrue(self.preprocessor.summary.derived_slope_from_elevation)

    def test_1_based_indexing_shift_to_0_based(self):
        raw_df = pd.DataFrame({
            "row": [1, 1, 2, 2],
            "col": [1, 2, 1, 2],
            "vegetation": [0.6, 0.7, 0.8, 0.9]
        })
        csv_path = self.dir_path / "one_indexed.csv"
        out_path = self.dir_path / "one_indexed_out.csv"
        raw_df.to_csv(csv_path, index=False)

        cleaned_df = self.preprocessor.process(csv_path, out_path)
        self.assertEqual(cleaned_df["row"].min(), 0)
        self.assertEqual(cleaned_df["col"].min(), 0)
        self.assertEqual(self.preprocessor.summary.grid_rows, 2)
        self.assertEqual(self.preprocessor.summary.grid_cols, 2)

    def test_grid_densification(self):
        # 3x3 grid with center cell (1, 1) missing
        rows = [0, 0, 0, 1, 1, 2, 2, 2]
        cols = [0, 1, 2, 0, 2, 0, 1, 2]
        raw_df = pd.DataFrame({
            "row": rows,
            "col": cols,
            "vegetation": [0.5] * len(rows),
            "elevation": [100.0] * len(rows)
        })
        csv_path = self.dir_path / "sparse.csv"
        out_path = self.dir_path / "sparse_out.csv"
        raw_df.to_csv(csv_path, index=False)

        dense_preprocessor = ForestDataPreprocessor(fill_missing_cells=True)
        cleaned_df = dense_preprocessor.process(csv_path, out_path)
        # Should now have 3x3 = 9 cells
        self.assertEqual(len(cleaned_df), 9)
        self.assertEqual(dense_preprocessor.summary.sparse_cells_filled, 1)


if __name__ == "__main__":
    unittest.main()
