#!/usr/bin/env python3
"""Create paper-ready figures for the IRAM-Omega-Q RF/DF switching study.

Expected input files are produced by SwitchingStudyRunner:
    switching_summary.csv
    switching_runs.csv
    timeseries/*.csv

Usage:
    python analysis/plot_switching_study.py \
        --results sim/results/rf_df_switching_boundary_probe \
        --out analysis/results/paper3/figures
"""
from __future__ import annotations

import argparse
from pathlib import Path
import sys

import matplotlib.pyplot as plt
import numpy as np
import pandas as pd


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--results", type=Path, required=True,
                        help="Directory containing switching_summary.csv and timeseries/")
    parser.add_argument("--out", type=Path, required=True,
                        help="Directory where PNG/PDF figures will be written")
    return parser.parse_args()


def load_results(results: Path) -> tuple[pd.DataFrame, pd.DataFrame]:
    summary_file = results / "switching_summary.csv"
    runs_file = results / "switching_runs.csv"
    if not summary_file.exists() or not runs_file.exists():
        raise FileNotFoundError(
            f"Missing switching output. Expected {summary_file} and {runs_file}"
        )
    return pd.read_csv(summary_file), pd.read_csv(runs_file)


def save(fig: plt.Figure, out: Path, stem: str) -> None:
    fig.savefig(out / f"{stem}.pdf", bbox_inches="tight")
    fig.savefig(out / f"{stem}.png", dpi=300, bbox_inches="tight")
    plt.close(fig)


def plot_periodic_penalty(summary: pd.DataFrame, out: Path) -> None:
    d = summary[summary["protocol"] == "PERIODIC"].copy()
    if d.empty:
        return
    d = d.sort_values("periodicDwellSteps")
    fig, ax = plt.subplots(figsize=(6.6, 4.2))
    x = d["periodicDwellSteps"].to_numpy()
    y = d["C_switch_mu_mean"].to_numpy()
    err = d["C_switch_mu_std"].to_numpy()
    ax.errorbar(x, y, yerr=err, marker="o", capsize=3, linewidth=1.3)
    ax.axhline(0.0, linewidth=1.0, linestyle="--")
    ax.set_xscale("log")
    ax.set_xlabel(r"Dwell duration $L$ in each mode (steps)")
    ax.set_ylabel(r"Switching penalty $C_{\mathrm{switch}}$ in $\overline{\mu}$")
    ax.set_title("Periodic loss of anticipatory regulation")
    ax.grid(True, alpha=0.25)
    fig.tight_layout()
    save(fig, out, "fig_periodic_switching_penalty")


def plot_periodic_all_metrics(summary: pd.DataFrame, out: Path) -> None:
    d = summary[summary["protocol"] == "PERIODIC"].copy()
    if d.empty:
        return
    d = d.sort_values("periodicDwellSteps")
    x = d["periodicDwellSteps"].to_numpy()
    quantities = [
        ("meanMu_mean", r"$\overline{\mu}$", "Mean regulation demand"),
        ("meanDeltaC_mean", r"$\overline{\Delta C}$", "Mean coherence gap"),
        ("chiDeltaC_mean", r"$\chi=\mathrm{Var}_t[\Delta C]$", "Susceptibility"),
        ("A_mu_mean", r"$A_{\mu}$", "Regulation amplitude"),
        ("A_deltaC_mean", r"$A_{\Delta C}$", "Coherence-gap amplitude"),
    ]
    fig, axes = plt.subplots(len(quantities), 1, figsize=(7.0, 11.0), sharex=True)
    for ax, (col, ylabel, title) in zip(axes, quantities):
        error_col = col.replace("_mean", "_std")
        ax.errorbar(x, d[col], yerr=d[error_col], marker="o", capsize=3, linewidth=1.1)
        ax.set_ylabel(ylabel)
        ax.set_title(title, fontsize=10, loc="left")
        ax.grid(True, alpha=0.22)
    axes[-1].set_xscale("log")
    axes[-1].set_xlabel(r"Periodic RF/DF dwell duration $L$ (steps)")
    fig.suptitle("Periodic switching: regulatory and coherence consequences", y=0.995)
    fig.tight_layout()
    save(fig, out, "fig_periodic_metrics")


def plot_markov_penalty(summary: pd.DataFrame, out: Path) -> None:
    d = summary[summary["protocol"] == "MARKOV"].copy()
    if d.empty:
        return
    fig, ax = plt.subplots(figsize=(6.8, 4.5))
    sc = ax.scatter(
        d["meanDFEpisodeTime_mean"],
        d["C_switch_mu_mean"],
        s=65,
        c=d["fractionRF_mean"],
        edgecolors="black",
        linewidths=0.5,
    )
    for _, row in d.iterrows():
        label = f"{row['pLoss']:.2g}/{row['pReturn']:.2g}"
        ax.annotate(label,
                    (row["meanDFEpisodeTime_mean"], row["C_switch_mu_mean"]),
                    textcoords="offset points", xytext=(5, 4), fontsize=8)
    ax.axhline(0.0, linewidth=1.0, linestyle="--")
    ax.set_xlabel("Mean DF episode duration (model time)")
    ax.set_ylabel(r"Switching penalty $C_{\mathrm{switch}}$ in $\overline{\mu}$")
    ax.set_title("Stochastic switching: does a reactive episode leave a cost?")
    cb = fig.colorbar(sc, ax=ax)
    cb.set_label("Fraction of retained time in RF")
    ax.grid(True, alpha=0.25)
    fig.tight_layout()
    save(fig, out, "fig_markov_switching_penalty")


def read_timeseries(results: Path, filename: str) -> pd.DataFrame:
    file = results / "timeseries" / filename
    if not file.exists():
        raise FileNotFoundError(file)
    return pd.read_csv(file)


def shade_df_blocks(ax: plt.Axes, data: pd.DataFrame) -> None:
    is_df = data["orderingRF"].to_numpy() == 0
    t = data["t"].to_numpy()
    if len(t) < 2:
        return
    start = None
    for i, df in enumerate(is_df):
        if df and start is None:
            start = t[i]
        at_end = i == len(is_df) - 1
        if start is not None and ((not df) or at_end):
            end = t[i] if not df else t[i] + (t[-1] - t[-2])
            ax.axvspan(start, end, alpha=0.10)
            start = None


def plot_representative_trace(results: Path, out: Path, dwell: int = 50) -> None:
    filename = f"periodic_L{dwell}.csv"
    file = results / "timeseries" / filename
    if not file.exists():
        candidates = sorted((results / "timeseries").glob("periodic_L*.csv"))
        if not candidates:
            return
        file = candidates[0]
    data = pd.read_csv(file)
    fig, axes = plt.subplots(3, 1, figsize=(8.2, 7.0), sharex=True)
    axes[0].step(data["t"], data["orderingRF"], where="post", linewidth=1.1)
    axes[0].set_yticks([0, 1], labels=["DF", "RF"])
    axes[0].set_ylabel(r"$q(t)$")
    axes[0].set_title("Representative periodic switching trajectory", loc="left")
    axes[1].plot(data["t"], data["mu"], linewidth=1.0)
    axes[1].set_ylabel(r"Regulation $\mu$")
    axes[2].plot(data["t"], data["dC"], linewidth=1.0)
    axes[2].set_ylabel(r"Gap $\Delta C$")
    axes[2].set_xlabel("Model time")
    for ax in axes[1:]:
        shade_df_blocks(ax, data)
    for ax in axes:
        ax.grid(True, alpha=0.20)
    axes[2].text(0.01, 0.94, "Shaded windows: DF episodes", transform=axes[2].transAxes,
                 fontsize=9, va="top")
    fig.tight_layout()
    save(fig, out, "fig_representative_periodic_trace")


def plot_penalty_distribution(runs: pd.DataFrame, out: Path) -> None:
    d = runs[runs["protocol"].isin(["PERIODIC", "MARKOV"])].copy()
    if d.empty:
        return
    labels = []
    values = []
    for protocol, condition in d[["protocol", "condition"]].drop_duplicates().itertuples(index=False):
        subset = d[(d["protocol"] == protocol) & (d["condition"] == condition)]
        labels.append(condition.replace("pLoss=", "").replace("_pReturn=", "/"))
        values.append(subset["C_switch_mu"].to_numpy())
    fig, ax = plt.subplots(figsize=(9.0, 4.8))
    ax.boxplot(values, tick_labels=labels, showfliers=False)
    ax.axhline(0.0, linewidth=1.0, linestyle="--")
    ax.set_ylabel(r"$C_{\mathrm{switch}}$ in $\overline{\mu}$")
    ax.set_title("Run-to-run distribution of nonlinear switching penalty")
    ax.tick_params(axis="x", rotation=45)
    ax.grid(True, axis="y", alpha=0.22)
    fig.tight_layout()
    save(fig, out, "fig_switching_penalty_distribution")


def main() -> int:
    args = parse_args()
    args.out.mkdir(parents=True, exist_ok=True)
    summary, runs = load_results(args.results)
    plot_periodic_penalty(summary, args.out)
    plot_periodic_all_metrics(summary, args.out)
    plot_markov_penalty(summary, args.out)
    plot_representative_trace(args.results, args.out)
    plot_penalty_distribution(runs, args.out)
    print(f"Wrote figures to {args.out.resolve()}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
