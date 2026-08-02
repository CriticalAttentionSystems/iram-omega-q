#!/usr/bin/env python3
"""Plot the bounded-domain (mu, eta) robustness maps for Paper 3.

Requires completed robustness runs listed in phase_manifest.csv. The manifest
is produced by generate_paper3_robustness_configs.py.

Main-text figure:
  fig_switching_penalty_phase_robustness.{png,pdf}
Context figure:
  fig_switching_penalty_phase_robustness_all_protocols.{png,pdf}
Table:
  phase_robustness_statistics.csv

A black dot marks a point whose approximate 95% confidence interval is entirely
below zero. The plot therefore distinguishes a negative mean from a locally
resolved negative penalty.
"""
from __future__ import annotations

import argparse
from pathlib import Path
import numpy as np
import pandas as pd
import matplotlib.pyplot as plt
from matplotlib.colors import TwoSlopeNorm

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


def collect(manifest: pd.DataFrame) -> pd.DataFrame:
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
                "mu": float(point["mu"]),
                "noise": float(point["noise"]),
                "targetEntropy": float(point["targetEntropy"]),
                "protocol": protocol,
                "label": label,
                "n": values.size,
                "mean": mean,
                "sd": sd,
                "ci95_low": mean - half_width,
                "ci95_high": mean + half_width,
                "negative_ci95": mean + half_width < 0.0,
            })
    return pd.DataFrame(records)


def draw_map(ax, stats: pd.DataFrame, label: str, norm: TwoSlopeNorm):
    d = stats[stats["label"] == label]
    mus = np.array(sorted(d["mu"].unique()), float)
    noises = np.array(sorted(d["noise"].unique()), float)
    means = d.pivot(index="noise", columns="mu", values="mean").reindex(index=noises, columns=mus).to_numpy()
    resolved = d.pivot(index="noise", columns="mu", values="negative_ci95").reindex(index=noises, columns=mus).to_numpy()
    image = ax.imshow(means, origin="lower", aspect="auto", cmap="coolwarm", norm=norm,
                      extent=[mus[0] - .005, mus[-1] + .005, noises[0] - .01, noises[-1] + .01])
    for iy, eta in enumerate(noises):
        for ix, mu in enumerate(mus):
            if bool(resolved[iy, ix]):
                ax.plot(mu, eta, "k.", ms=5)
    ax.set_title(label)
    ax.set_xlabel(r"Initial regulation $\mu_0$")
    ax.set_ylabel(r"Disturbance noise $\eta$")
    return image


def main() -> None:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--manifest", required=True, type=Path)
    p.add_argument("--out", required=True, type=Path)
    a = p.parse_args()
    a.out.mkdir(parents=True, exist_ok=True)

    stats = collect(pd.read_csv(a.manifest))
    stats.to_csv(a.out / "phase_robustness_statistics.csv", index=False)
    vmax = max(float(np.abs(stats["mean"]).max()), 1e-12)
    norm = TwoSlopeNorm(vmin=-vmax, vcenter=0.0, vmax=vmax)

    fig, axes = plt.subplots(1, 2, figsize=(10.4, 4.3), constrained_layout=True)
    for ax, label in zip(axes, [r"Periodic $L=1000$", r"Markov $p=0.001$"]):
        image = draw_map(ax, stats, label, norm)
    colorbar = fig.colorbar(image, ax=axes, shrink=0.92)
    colorbar.set_label(r"Mean $C_{\mathrm{switch}}^{(\mu)}$")
    fig.suptitle("Robustness of regulatory relief across operating conditions\n"
                 "Black dot: 95% CI entirely below zero", fontsize=12)
    for ext in ("png", "pdf"):
        path = a.out / f"fig_switching_penalty_phase_robustness.{ext}"
        fig.savefig(path, dpi=300 if ext == "png" else None, bbox_inches="tight")
        print(f"Wrote {path}")
    plt.close(fig)

    fig, axes = plt.subplots(2, 2, figsize=(10.3, 8.0), constrained_layout=True)
    for ax, (_, _, _, label) in zip(axes.flat, CONDITIONS):
        image = draw_map(ax, stats, label, norm)
    colorbar = fig.colorbar(image, ax=axes, shrink=0.92)
    colorbar.set_label(r"Mean $C_{\mathrm{switch}}^{(\mu)}$")
    fig.suptitle("All representative robustness protocols\n"
                 "Black dot: 95% CI entirely below zero", fontsize=12)
    for ext in ("png", "pdf"):
        path = a.out / f"fig_switching_penalty_phase_robustness_all_protocols.{ext}"
        fig.savefig(path, dpi=300 if ext == "png" else None, bbox_inches="tight")
        print(f"Wrote {path}")
    plt.close(fig)


if __name__ == "__main__":
    main()
