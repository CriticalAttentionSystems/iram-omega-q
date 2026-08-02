#!/usr/bin/env python3
"""Plot aggregate periodic-switching observables with 95% confidence intervals.

Requires switching_summary.csv from the completed N=1000 detailed run.

Example:
python analysis/scripts/plot_periodic_metrics_ci95.py \
  --results analysis/results/paper3/rf_df_switching_long_production_diagnostics_1000runs \
  --out analysis/results/paper3/switching_figures_1000runs
"""
from __future__ import annotations

import argparse
from pathlib import Path
import numpy as np
import pandas as pd
import matplotlib.pyplot as plt


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--results", required=True, type=Path)
    parser.add_argument("--out", required=True, type=Path)
    a = parser.parse_args()

    source = a.results / "switching_summary.csv"
    if not source.exists():
        raise FileNotFoundError(f"Missing {source}")
    a.out.mkdir(parents=True, exist_ok=True)

    summary = pd.read_csv(source)
    d = summary[summary["protocol"].astype(str).str.upper() == "PERIODIC"].copy()
    if d.empty:
        raise ValueError("No PERIODIC rows found in switching_summary.csv")
    d = d.sort_values("periodicDwellSteps")
    n_col = "runs" if "runs" in d.columns else "n"
    if n_col not in d.columns:
        raise KeyError("Expected 'runs' or 'n' column in switching_summary.csv")

    x = d["periodicDwellSteps"].to_numpy(float)
    n = d[n_col].to_numpy(float)
    quantities = [
        ("meanMu_mean", "meanMu_std", r"$\overline{\mu}$", "Mean regulation demand"),
        ("meanDeltaC_mean", "meanDeltaC_std", r"$\overline{\Delta C}$", "Mean coherence gap"),
        ("chiDeltaC_mean", "chiDeltaC_std", r"$\chi=\mathrm{Var}_t[\Delta C]$", "Susceptibility"),
        ("A_mu_mean", "A_mu_std", r"$A_{\mu}$", "Regulation amplitude"),
        ("A_deltaC_mean", "A_deltaC_std", r"$A_{\Delta C}$", "Coherence-gap amplitude"),
    ]

    fig, axes = plt.subplots(5, 1, figsize=(7.4, 11.2), sharex=True)
    for ax, (mean_col, std_col, ylabel, title) in zip(axes, quantities):
        mean = d[mean_col].to_numpy(float)
        ci = 1.96 * d[std_col].to_numpy(float) / np.sqrt(n)
        ax.errorbar(x, mean, yerr=ci, marker="o", capsize=3, linewidth=1.25)
        ax.set_ylabel(ylabel)
        ax.set_title(title, fontsize=10, loc="left")
        ax.grid(True, alpha=0.24)
    axes[-1].set_xscale("log")
    axes[-1].set_xlabel(r"Periodic RF/DF dwell duration $L$ (steps)")
    fig.suptitle("Periodic switching: aggregate observables (mean and 95% CI)", y=0.997)
    fig.tight_layout()
    for ext in ("png", "pdf"):
        path = a.out / f"fig_periodic_metrics_ci95.{ext}"
        fig.savefig(path, dpi=300 if ext == "png" else None, bbox_inches="tight")
        print(f"Wrote {path}")
    plt.close(fig)


if __name__ == "__main__":
    main()
