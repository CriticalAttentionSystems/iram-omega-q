#!/usr/bin/env python3
"""Statistical analysis of the unexpected negative RF/DF switching penalty.

Runs immediately on an existing SwitchingStudyRunner output directory; no rerun
of the simulation is required.

Input:
    switching_runs.csv

Output:
    negative_penalty_statistics.csv
    negative_penalty_report.md
    fig_negative_penalty_ci95_periodic.{png,pdf}
    fig_negative_penalty_ci95_markov.{png,pdf}
    fig_negative_penalty_forest.{png,pdf}

Example:
    python analysis/analyze_negative_penalty.py \
      --results sim/results/rf_df_switching_long_production \
      --out analysis/results/paper3/negative_penalty
"""
from __future__ import annotations

import argparse
from pathlib import Path
import math

import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
from scipy import stats


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--results", required=True, type=Path,
                   help="Directory containing switching_runs.csv")
    p.add_argument("--out", required=True, type=Path,
                   help="Output directory for statistics and figures")
    p.add_argument("--alpha", default=0.05, type=float,
                   help="Confidence/test alpha; default 0.05")
    return p.parse_args()


def label_for_row(protocol: str, condition: str) -> str:
    if protocol == "PERIODIC":
        return condition
    return condition.replace("pLoss=", "").replace("_pReturn=", "/").replace("p", ".")


def bh_adjust(p_values: np.ndarray) -> np.ndarray:
    """Benjamini-Hochberg false-discovery-rate adjusted p values."""
    p = np.asarray(p_values, dtype=float)
    n = len(p)
    order = np.argsort(p)
    ranked = p[order]
    adjusted = ranked * n / np.arange(1, n + 1)
    adjusted = np.minimum.accumulate(adjusted[::-1])[::-1]
    adjusted = np.minimum(adjusted, 1.0)
    out = np.empty_like(adjusted)
    out[order] = adjusted
    return out


def summarize_penalty(runs: pd.DataFrame, metric: str, alpha: float) -> pd.DataFrame:
    d = runs[runs["protocol"].isin(["PERIODIC", "MARKOV"])].copy()
    rows: list[dict[str, float | int | str]] = []
    for (protocol, condition), g in d.groupby(["protocol", "condition"], sort=False):
        values = g[metric].dropna().to_numpy(float)
        n = len(values)
        mean = float(np.mean(values))
        sd = float(np.std(values, ddof=1)) if n > 1 else float("nan")
        sem = sd / math.sqrt(n) if n > 1 else float("nan")
        tcrit = float(stats.t.ppf(1.0 - alpha / 2.0, n - 1)) if n > 1 else float("nan")
        ci_low = mean - tcrit * sem if n > 1 else float("nan")
        ci_high = mean + tcrit * sem if n > 1 else float("nan")
        t_stat, p_two = stats.ttest_1samp(values, popmean=0.0) if n > 1 else (float("nan"), float("nan"))
        # The hypothesized unexpected effect is negative: H1 mean < 0.
        p_negative = float(p_two / 2.0) if t_stat < 0 else float(1.0 - p_two / 2.0)
        cohen_d = mean / sd if n > 1 and sd > 0 else float("nan")
        rows.append({
            "protocol": protocol,
            "condition": condition,
            "label": label_for_row(protocol, condition),
            "n": n,
            "mean": mean,
            "sd": sd,
            "sem": sem,
            "ci95_low": ci_low,
            "ci95_high": ci_high,
            "t_stat": float(t_stat),
            "p_two_sided": float(p_two),
            "p_one_sided_negative": p_negative,
            "cohen_d": cohen_d,
            "fraction_negative": float(np.mean(values < 0.0)),
            "periodicDwellSteps": float(g["periodicDwellSteps"].dropna().iloc[0]) if protocol == "PERIODIC" else np.nan,
            "pLoss": float(g["pLoss"].dropna().iloc[0]) if protocol == "MARKOV" else np.nan,
            "pReturn": float(g["pReturn"].dropna().iloc[0]) if protocol == "MARKOV" else np.nan,
            "meanDFEpisodeTime": float(g["meanDFEpisodeTime"].mean()) if protocol == "MARKOV" else np.nan,
        })
    out = pd.DataFrame(rows)
    out["p_negative_fdr_bh"] = bh_adjust(out["p_one_sided_negative"].to_numpy())
    out["negative_ci95"] = out["ci95_high"] < 0.0
    out["negative_fdr_0p05"] = out["p_negative_fdr_bh"] < alpha
    return out


def save(fig: plt.Figure, out: Path, stem: str) -> None:
    fig.savefig(out / f"{stem}.png", dpi=300, bbox_inches="tight")
    fig.savefig(out / f"{stem}.pdf", bbox_inches="tight")
    plt.close(fig)


def plot_periodic_ci(stats_df: pd.DataFrame, out: Path) -> None:
    d = stats_df[stats_df["protocol"] == "PERIODIC"].sort_values("periodicDwellSteps")
    if d.empty:
        return
    x = d["periodicDwellSteps"].to_numpy()
    y = d["mean"].to_numpy()
    low = y - d["ci95_low"].to_numpy()
    high = d["ci95_high"].to_numpy() - y
    fig, ax = plt.subplots(figsize=(7.0, 4.6))
    ax.errorbar(x, y, yerr=[low, high], marker="o", capsize=4, linewidth=1.4)
    ax.axhline(0.0, linestyle="--", linewidth=1.0)
    ax.set_xscale("log")
    ax.set_xlabel(r"Periodic RF/DF dwell duration $L$ (steps)")
    ax.set_ylabel(r"$C_{\mathrm{switch}}^{(\mu)}$ in $\overline{\mu}$")
    ax.set_title("Periodic switching penalty: mean and 95% confidence interval")
    ax.grid(True, alpha=0.25)
    fig.tight_layout()
    save(fig, out, "fig_negative_penalty_ci95_periodic")


def plot_markov_ci(stats_df: pd.DataFrame, out: Path) -> None:
    d = stats_df[stats_df["protocol"] == "MARKOV"].sort_values("meanDFEpisodeTime")
    if d.empty:
        return
    x = d["meanDFEpisodeTime"].to_numpy()
    y = d["mean"].to_numpy()
    low = y - d["ci95_low"].to_numpy()
    high = d["ci95_high"].to_numpy() - y
    fig, ax = plt.subplots(figsize=(7.0, 4.6))
    ax.errorbar(x, y, yerr=[low, high], marker="o", capsize=4, linewidth=1.4)
    ax.axhline(0.0, linestyle="--", linewidth=1.0)
    for _, r in d.iterrows():
        ax.annotate(r["label"], (r["meanDFEpisodeTime"], r["mean"]),
                    textcoords="offset points", xytext=(4, 6), fontsize=8)
    ax.set_xscale("log")
    ax.set_xlabel("Mean DF episode duration (model time)")
    ax.set_ylabel(r"$C_{\mathrm{switch}}^{(\mu)}$ in $\overline{\mu}$")
    ax.set_title("Stochastic switching penalty: mean and 95% confidence interval")
    ax.grid(True, alpha=0.25)
    fig.tight_layout()
    save(fig, out, "fig_negative_penalty_ci95_markov")


def plot_forest(stats_df: pd.DataFrame, out: Path) -> None:
    d = stats_df.copy()
    d["display"] = d.apply(lambda r: f"{r['protocol'].title()}: {r['label']}", axis=1)
    d = d.iloc[::-1].reset_index(drop=True)
    yloc = np.arange(len(d))
    y = d["mean"].to_numpy()
    low = y - d["ci95_low"].to_numpy()
    high = d["ci95_high"].to_numpy() - y
    fig, ax = plt.subplots(figsize=(8.0, 6.0))
    ax.errorbar(y, yloc, xerr=[low, high], fmt="o", capsize=3, linewidth=1.2)
    ax.axvline(0.0, linestyle="--", linewidth=1.0)
    ax.set_yticks(yloc, d["display"])
    ax.set_xlabel(r"Mean switching penalty $C_{\mathrm{switch}}^{(\mu)}$ (95% CI)")
    ax.set_title("Is the unexpected negative penalty resolved across conditions?")
    ax.grid(True, axis="x", alpha=0.25)
    fig.tight_layout()
    save(fig, out, "fig_negative_penalty_forest")


def write_report(statistics: pd.DataFrame, out: Path, metric: str) -> None:
    sig = statistics[statistics["negative_fdr_0p05"]]
    lines = [
        "# Negative switching penalty: immediate statistical analysis",
        "",
        f"Metric tested: `{metric}`.",
        "",
        "For each switching condition, the analysis tests the one-sided hypothesis ",
        "that the mean nonlinear switching penalty is less than zero. Confidence ",
        "intervals use Student's t distribution; multiplicity across tested ",
        "conditions is controlled with the Benjamini-Hochberg FDR procedure.",
        "",
        f"Number of switching conditions analyzed: {len(statistics)}.",
        f"Conditions with FDR-adjusted one-sided p < 0.05: {len(sig)}.",
        "",
        "## Condition results",
        "",
        "| Protocol | Condition | n | Mean penalty | 95% CI | Fraction negative | FDR-adjusted p |",
        "|---|---:|---:|---:|---:|---:|---:|",
    ]
    for _, r in statistics.iterrows():
        lines.append(
            f"| {r['protocol']} | {r['label']} | {int(r['n'])} | "
            f"{r['mean']:.6g} | [{r['ci95_low']:.6g}, {r['ci95_high']:.6g}] | "
            f"{r['fraction_negative']:.3f} | {r['p_negative_fdr_bh']:.4g} |"
        )
    lines += [
        "",
        "## Interpretation guardrail",
        "",
        "A statistically resolved negative mean penalty would establish that, for the tested finite-duration protocol and operating point, switching trajectories have lower mean regulation demand than the matched occupancy-weighted fixed RF/DF reference. It does not by itself establish a lower asymptotic steady state; that requires the accompanying late-drift/stationarity analysis.",
        "",
    ]
    (out / "negative_penalty_report.md").write_text("\n".join(lines), encoding="utf-8")


def main() -> int:
    args = parse_args()
    args.out.mkdir(parents=True, exist_ok=True)
    source = args.results / "switching_runs.csv"
    if not source.exists():
        raise FileNotFoundError(f"Expected {source}")
    runs = pd.read_csv(source)
    metric = "C_switch_mu"
    statistics = summarize_penalty(runs, metric, args.alpha)
    statistics.to_csv(args.out / "negative_penalty_statistics.csv", index=False)
    plot_periodic_ci(statistics, args.out)
    plot_markov_ci(statistics, args.out)
    plot_forest(statistics, args.out)
    write_report(statistics, args.out, metric)
    print(f"Wrote negative-penalty statistics and figures to {args.out.resolve()}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
