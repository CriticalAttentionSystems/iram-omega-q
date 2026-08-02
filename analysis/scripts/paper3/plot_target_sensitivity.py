#!/usr/bin/env python3
"""Plot target-uncertainty sensitivity for Paper 3.

Requires completed runs listed in target_manifest.csv, produced by
generate_paper3_robustness_configs.py.

Output:
  fig_switching_penalty_target_sensitivity.{png,pdf}
  target_sensitivity_statistics.csv
"""
from __future__ import annotations

import argparse
from pathlib import Path
import numpy as np
import pandas as pd
import matplotlib.pyplot as plt

CONDITIONS = [
    ("PERIODIC", 500, None, r"Periodic $L=500$"),
    ("PERIODIC", 1000, None, r"Periodic $L=1000$"),
    ("MARKOV", None, 0.002, r"Markov $p=0.002$"),
    ("MARKOV", None, 0.001, r"Markov $p=0.001$"),
]


def select_rows(df: pd.DataFrame, protocol: str, dwell: int | None, prob: float | None) -> pd.DataFrame:
    proto = df["protocol"].astype(str).str.upper()
    if protocol == "PERIODIC":
        return df[(proto == protocol) &
                  (pd.to_numeric(df["periodicDwellSteps"], errors="coerce") == dwell)]
    return df[(proto == protocol) &
              np.isclose(pd.to_numeric(df["pLoss"], errors="coerce"), prob) &
              np.isclose(pd.to_numeric(df["pReturn"], errors="coerce"), prob)]


def main() -> None:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--manifest", required=True, type=Path)
    p.add_argument("--out", required=True, type=Path)
    a = p.parse_args()
    a.out.mkdir(parents=True, exist_ok=True)
    manifest = pd.read_csv(a.manifest)
    records = []

    for _, point in manifest.iterrows():
        file = Path(point["output_dir"]) / "switching_runs.csv"
        if not file.exists():
            raise FileNotFoundError(f"Missing completed output: {file}")
        runs = pd.read_csv(file)
        for protocol, dwell, prob, label in CONDITIONS:
            sample = select_rows(runs, protocol, dwell, prob)
            if sample.empty:
                raise ValueError(f"Missing condition {label} in {file}")
            values = sample["C_switch_mu"].dropna().to_numpy(float)
            mean = float(values.mean())
            sd = float(values.std(ddof=1))
            half_width = 1.96 * sd / np.sqrt(values.size)
            records.append({
                "targetEntropy": float(point["targetEntropy"]),
                "protocol": protocol, "label": label, "n": values.size,
                "mean": mean, "sd": sd,
                "ci95_low": mean - half_width, "ci95_high": mean + half_width,
                "negative_ci95": mean + half_width < 0.0,
            })

    stats = pd.DataFrame(records).sort_values(["label", "targetEntropy"])
    stats.to_csv(a.out / "target_sensitivity_statistics.csv", index=False)

    fig, ax = plt.subplots(figsize=(7.4, 4.8))
    for label, d in stats.groupby("label", sort=False):
        lower = d["mean"] - d["ci95_low"]
        upper = d["ci95_high"] - d["mean"]
        ax.errorbar(d["targetEntropy"], d["mean"], yerr=np.vstack([lower, upper]),
                    marker="o", capsize=3, linewidth=1.25, label=label)
    ax.axhline(0.0, linestyle="--", linewidth=1.1)
    ax.set_xlabel(r"Target uncertainty $S^*$")
    ax.set_ylabel(r"Mean $C_{\mathrm{switch}}^{(\mu)}$")
    ax.set_title("Sensitivity of regulatory relief to target uncertainty\n"
                 "Means and 95% confidence intervals")
    ax.grid(True, alpha=0.25)
    ax.legend(frameon=False, fontsize=9)
    fig.tight_layout()
    for ext in ("png", "pdf"):
        path = a.out / f"fig_switching_penalty_target_sensitivity.{ext}"
        fig.savefig(path, dpi=300 if ext == "png" else None, bbox_inches="tight")
        print(f"Wrote {path}")
    plt.close(fig)


if __name__ == "__main__":
    main()
