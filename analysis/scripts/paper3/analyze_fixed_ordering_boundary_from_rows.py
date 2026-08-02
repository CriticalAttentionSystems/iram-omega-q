#!/usr/bin/env python3
"""
Paper 3 boundary analysis using FIXED_RF and FIXED_DF rows.

Purpose
-------
The robustness scan already contains baseline rows for every replicate:

    protocol = FIXED_RF
    protocol = FIXED_DF

This script pairs those rows by replicate and computes:

    B_RFDF^(mu) = meanMu_DF - meanMu_RF

It then compares that fixed-ordering advantage with:

    C_switch^(mu)

for the representative periodic and Markov schedules already used in the
Paper 3 robustness section.

No Java modification and no simulation rerun are required.

Run from the IRAM-Omega-Q project root:

python3 analysis/scripts/analyze_fixed_ordering_boundary_from_rows.py \
  --phase-manifest analysis/paper3/config/robustness/phase_manifest.csv \
  --target-manifest analysis/paper3/config/robustness/target_manifest.csv \
  --out analysis/results/paper3/robustness/fixed_ordering_boundary \
  --periodic-dwell 1000 \
  --markov-p 0.001

Dependencies: numpy, pandas, matplotlib.  SciPy is not required.
"""
from __future__ import annotations

import argparse
import math
from pathlib import Path

import numpy as np
import pandas as pd
import matplotlib.pyplot as plt
from matplotlib.colors import BoundaryNorm, ListedColormap, TwoSlopeNorm
from matplotlib.patches import Patch


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--phase-manifest", type=Path, required=True)
    parser.add_argument("--target-manifest", type=Path)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--periodic-dwell", type=int, default=1000)
    parser.add_argument("--markov-p", type=float, default=0.001)
    parser.add_argument("--runs-filename", default="switching_runs.csv")
    return parser.parse_args()


def ci95(values: pd.Series | np.ndarray) -> dict[str, float | int | bool]:
    arr = np.asarray(values, dtype=float)
    arr = arr[np.isfinite(arr)]
    if arr.size == 0:
        raise ValueError("No finite values available for CI calculation.")
    mean = float(np.mean(arr))
    sd = float(np.std(arr, ddof=1)) if arr.size > 1 else float("nan")
    sem = sd / math.sqrt(arr.size) if arr.size > 1 else float("nan")
    half = 1.96 * sem if arr.size > 1 else float("nan")
    low = mean - half if arr.size > 1 else float("nan")
    high = mean + half if arr.size > 1 else float("nan")
    return {
        "n": int(arr.size),
        "mean": mean,
        "sd": sd,
        "sem": sem,
        "ci95_low": low,
        "ci95_high": high,
        "resolved_positive": bool(low > 0.0) if arr.size > 1 else False,
        "resolved_negative": bool(high < 0.0) if arr.size > 1 else False,
    }


def resolve_runs_file(output_dir: str | Path, filename: str) -> Path:
    directory = Path(str(output_dir))
    direct = directory / filename
    if direct.exists():
        return direct
    matches = list(directory.rglob(filename)) if directory.exists() else []
    if len(matches) == 1:
        return matches[0]
    raise FileNotFoundError(f"Could not locate {filename} under {directory}")


def validate_columns(df: pd.DataFrame, path: Path) -> None:
    required = {
        "protocol", "condition", "replicate", "periodicDwellSteps",
        "pLoss", "pReturn", "meanMu", "C_switch_mu",
    }
    missing = sorted(required.difference(df.columns))
    if missing:
        raise KeyError(f"Missing columns in {path}: {missing}\nAvailable: {list(df.columns)}")


def paired_baseline(df: pd.DataFrame, source_path: Path) -> pd.DataFrame:
    """Return one paired RF/DF baseline row per replicate."""
    rf = df[df["protocol"].astype(str).str.upper() == "FIXED_RF"][["replicate", "meanMu"]].copy()
    dfixed = df[df["protocol"].astype(str).str.upper() == "FIXED_DF"][["replicate", "meanMu"]].copy()
    rf = rf.rename(columns={"meanMu": "meanMu_RF"})
    dfixed = dfixed.rename(columns={"meanMu": "meanMu_DF"})
    paired = rf.merge(dfixed, on="replicate", how="inner", validate="one_to_one")
    if paired.empty:
        raise ValueError(f"No paired FIXED_RF / FIXED_DF rows found in {source_path}")
    n_rf = rf["replicate"].nunique()
    n_df = dfixed["replicate"].nunique()
    if len(paired) != n_rf or len(paired) != n_df:
        raise ValueError(
            f"Unmatched baseline replicate rows in {source_path}: "
            f"FIXED_RF={n_rf}, FIXED_DF={n_df}, paired={len(paired)}"
        )
    paired["B_RFDF_mu"] = paired["meanMu_DF"] - paired["meanMu_RF"]
    return paired


def selected_switching_rows(df: pd.DataFrame, schedule: str, dwell: int, markov_p: float) -> pd.DataFrame:
    protocol = df["protocol"].astype(str).str.upper()
    if schedule == "periodic":
        selected = df[
            (protocol == "PERIODIC") &
            (pd.to_numeric(df["periodicDwellSteps"], errors="coerce") == dwell)
        ].copy()
    elif schedule == "markov":
        selected = df[
            (protocol == "MARKOV") &
            np.isclose(pd.to_numeric(df["pLoss"], errors="coerce"), markov_p) &
            np.isclose(pd.to_numeric(df["pReturn"], errors="coerce"), markov_p)
        ].copy()
    else:
        raise ValueError(f"Unknown schedule: {schedule}")
    return selected


def collect_scan(manifest_path: Path, scan_name: str, args: argparse.Namespace) -> tuple[pd.DataFrame, pd.DataFrame]:
    manifest = pd.read_csv(manifest_path)
    if manifest.empty:
        raise ValueError(f"Manifest is empty: {manifest_path}")
    required_manifest = {"mu", "noise", "targetEntropy", "output_dir"}
    missing = sorted(required_manifest.difference(manifest.columns))
    if missing:
        raise KeyError(f"Missing manifest columns in {manifest_path}: {missing}")

    baseline_records: list[dict] = []
    schedule_records: list[dict] = []
    paired_records: list[pd.DataFrame] = []

    schedule_specs = [
        ("periodic", f"Periodic L={args.periodic_dwell}"),
        ("markov", f"Markov p={args.markov_p:g}"),
    ]

    for _, point in manifest.iterrows():
        runs_path = resolve_runs_file(point["output_dir"], args.runs_filename)
        runs = pd.read_csv(runs_path)
        validate_columns(runs, runs_path)

        baselines = paired_baseline(runs, runs_path)
        b_stats = ci95(baselines["B_RFDF_mu"])
        common = {
            "scan": scan_name,
            "mu": float(point["mu"]),
            "noise": float(point["noise"]),
            "targetEntropy": float(point["targetEntropy"]),
            "source_file": str(runs_path),
        }
        baseline_records.append({
            **common,
            "B_n": b_stats["n"],
            "B_mean": b_stats["mean"],
            "B_sd": b_stats["sd"],
            "B_sem": b_stats["sem"],
            "B_ci95_low": b_stats["ci95_low"],
            "B_ci95_high": b_stats["ci95_high"],
            "B_resolved_RF_advantage": b_stats["resolved_positive"],
        })

        for schedule, label in schedule_specs:
            switching = selected_switching_rows(
                runs, schedule, args.periodic_dwell, args.markov_p
            )
            if switching.empty:
                raise ValueError(f"No rows found for {label} in {runs_path}")

            merged = switching.merge(
                baselines[["replicate", "meanMu_RF", "meanMu_DF", "B_RFDF_mu"]],
                on="replicate", how="inner", validate="one_to_one"
            )
            if len(merged) != len(switching):
                raise ValueError(
                    f"Could not pair all {label} replicates with fixed baselines in {runs_path}: "
                    f"switching={len(switching)}, paired={len(merged)}"
                )
            c_stats = ci95(merged["C_switch_mu"])
            # Optional consistency check: C_switch_mu should equal meanMu - expectedMuMixture.
            if "expectedMuMixture" in merged.columns:
                recomputed = merged["meanMu"] - merged["expectedMuMixture"]
                max_abs_error = float(np.nanmax(np.abs(recomputed - merged["C_switch_mu"])))
            else:
                max_abs_error = float("nan")

            if b_stats["resolved_positive"] and c_stats["resolved_negative"]:
                category = "RF advantage + resolved switching relief"
                code = 1
            elif b_stats["resolved_positive"] and c_stats["resolved_positive"]:
                category = "RF advantage + resolved positive switching penalty"
                code = 3
            elif b_stats["resolved_positive"]:
                category = "RF advantage + attenuated/unresolved switching effect"
                code = 2
            else:
                category = "No resolved fixed RF advantage"
                code = 0

            schedule_records.append({
                **common,
                "schedule": schedule,
                "schedule_label": label,
                "C_n": c_stats["n"],
                "C_mean": c_stats["mean"],
                "C_sd": c_stats["sd"],
                "C_sem": c_stats["sem"],
                "C_ci95_low": c_stats["ci95_low"],
                "C_ci95_high": c_stats["ci95_high"],
                "C_resolved_relief": c_stats["resolved_negative"],
                "C_resolved_positive_penalty": c_stats["resolved_positive"],
                "B_mean": b_stats["mean"],
                "B_ci95_low": b_stats["ci95_low"],
                "B_ci95_high": b_stats["ci95_high"],
                "B_resolved_RF_advantage": b_stats["resolved_positive"],
                "classification": category,
                "classification_code": code,
                "C_consistency_max_abs_error": max_abs_error,
            })
            merged = merged.assign(
                scan=scan_name,
                mu=float(point["mu"]),
                noise=float(point["noise"]),
                targetEntropy=float(point["targetEntropy"]),
                schedule=schedule,
                schedule_label=label,
            )
            paired_records.append(merged)

    baseline_df = pd.DataFrame(baseline_records)
    schedule_df = pd.DataFrame(schedule_records)
    paired_df = pd.concat(paired_records, ignore_index=True) if paired_records else pd.DataFrame()
    return baseline_df, schedule_df, paired_df


def heatmap(ax: plt.Axes, data: pd.DataFrame, value: str, resolved_col: str,
            title: str, norm: TwoSlopeNorm, marker_meaning: str) -> object:
    mus = np.array(sorted(data["mu"].unique()), dtype=float)
    noises = np.array(sorted(data["noise"].unique()), dtype=float)
    values = data.pivot(index="noise", columns="mu", values=value).reindex(
        index=noises, columns=mus
    ).to_numpy()
    marks = data.pivot(index="noise", columns="mu", values=resolved_col).reindex(
        index=noises, columns=mus
    ).to_numpy()
    image = ax.imshow(
        values, origin="lower", aspect="auto", cmap="coolwarm", norm=norm,
        extent=[mus.min() - 0.005, mus.max() + 0.005,
                noises.min() - 0.01, noises.max() + 0.01],
    )
    for iy, eta in enumerate(noises):
        for ix, mu in enumerate(mus):
            if bool(marks[iy, ix]):
                ax.plot(mu, eta, "k.", ms=5)
    ax.set_title(title + "\n" + marker_meaning, fontsize=10)
    ax.set_xlabel(r"Initial regulation $\mu_0$")
    ax.set_ylabel(r"Disturbance $\eta$")
    return image


def plot_phase(baseline: pd.DataFrame, schedules: pd.DataFrame, out: Path) -> None:
    shown = [
        ("periodic", "Periodic $L=1000$"),
        ("markov", "Markov $p=0.001$"),
    ]
    vmax_b = max(float(np.max(np.abs(baseline["B_mean"]))), 1e-12)
    vmax_c = max(float(np.max(np.abs(schedules["C_mean"]))), 1e-12)
    b_norm = TwoSlopeNorm(vmin=-vmax_b, vcenter=0.0, vmax=vmax_b)
    c_norm = TwoSlopeNorm(vmin=-vmax_c, vcenter=0.0, vmax=vmax_c)

    fig, axes = plt.subplots(1, 3, figsize=(14.0, 4.35), constrained_layout=True)
    b_image = heatmap(
        axes[0], baseline, "B_mean", "B_resolved_RF_advantage",
        r"Fixed ordering: $B_{\rm RF/DF}^{(\mu)}$",
        b_norm, "dot: CI entirely above zero",
    )
    c_images = []
    for ax, (key, title) in zip(axes[1:], shown):
        data = schedules[schedules["schedule"] == key]
        c_images.append(heatmap(
            ax, data, "C_mean", "C_resolved_relief",
            title + r": $C_{\rm switch}^{(\mu)}$",
            c_norm, "dot: CI entirely below zero",
        ))
    fig.colorbar(b_image, ax=axes[0], shrink=0.86,
                 label=r"$B_{\mathrm{RF/DF}}^{(\mu)}=\overline{\mu}_{DF}-\overline{\mu}_{RF}$")
    fig.colorbar(c_images[-1], ax=axes[1:], shrink=0.86,
                 label=r"$C_{\mathrm{switch}}^{(\mu)}$")
    fig.suptitle("Boundary interpretation: fixed RF advantage versus switching relief", fontsize=13)
    for ext in ("pdf", "png"):
        fig.savefig(out / f"fig_phase_baseline_advantage_and_switching_penalty.{ext}",
                    dpi=300 if ext == "png" else None, bbox_inches="tight")
    plt.close(fig)

    cmap = ListedColormap(["#d9d9d9", "#2c7fb8", "#fdae61", "#d7191c"])
    norm = BoundaryNorm([-0.5, 0.5, 1.5, 2.5, 3.5], cmap.N)
    fig, axes = plt.subplots(1, 2, figsize=(10.2, 4.65), constrained_layout=True)
    for ax, (key, title) in zip(axes, shown):
        data = schedules[schedules["schedule"] == key]
        mus = np.array(sorted(data["mu"].unique()), dtype=float)
        noises = np.array(sorted(data["noise"].unique()), dtype=float)
        classes = data.pivot(index="noise", columns="mu", values="classification_code").reindex(
            index=noises, columns=mus
        ).to_numpy()
        ax.imshow(
            classes, origin="lower", aspect="auto", cmap=cmap, norm=norm,
            extent=[mus.min() - 0.005, mus.max() + 0.005,
                    noises.min() - 0.01, noises.max() + 0.01],
        )
        ax.set_title(title)
        ax.set_xlabel(r"Initial regulation $\mu_0$")
        ax.set_ylabel(r"Disturbance $\eta$")
    legend = [
        Patch(facecolor="#d9d9d9", label="No resolved fixed RF advantage"),
        Patch(facecolor="#2c7fb8", label="RF advantage + resolved relief"),
        Patch(facecolor="#fdae61", label="RF advantage + attenuated/unresolved relief"),
        Patch(facecolor="#d7191c", label="RF advantage + resolved positive penalty"),
    ]
    fig.legend(handles=legend, loc="outside lower center", ncol=2, frameon=False)
    fig.suptitle("Regime classification for attenuated switching-penalty cells", fontsize=13)
    for ext in ("pdf", "png"):
        fig.savefig(out / f"fig_phase_regime_classification.{ext}",
                    dpi=300 if ext == "png" else None, bbox_inches="tight")
    plt.close(fig)


def plot_target(baseline: pd.DataFrame, schedules: pd.DataFrame, out: Path) -> None:
    fig, axes = plt.subplots(2, 1, figsize=(7.8, 8.0), sharex=True, constrained_layout=True)

    b = baseline.sort_values("targetEntropy")
    x = b["targetEntropy"].to_numpy(float)
    y = b["B_mean"].to_numpy(float)
    yerr = np.vstack([y - b["B_ci95_low"].to_numpy(float),
                      b["B_ci95_high"].to_numpy(float) - y])
    axes[0].errorbar(x, y, yerr=yerr, marker="o", capsize=3, linewidth=1.25,
                     label=r"Fixed RF/DF separation")
    axes[0].axhline(0.0, linestyle="--", linewidth=1)
    axes[0].set_ylabel(r"$B_{\mathrm{RF/DF}}^{(\mu)}$")
    axes[0].set_title("Does fixed RF retain a regulatory advantage as target uncertainty changes?")
    axes[0].grid(True, alpha=0.25)
    axes[0].legend(frameon=False)

    for label, group in schedules.groupby("schedule_label", sort=False):
        d = group.sort_values("targetEntropy")
        x = d["targetEntropy"].to_numpy(float)
        y = d["C_mean"].to_numpy(float)
        yerr = np.vstack([y - d["C_ci95_low"].to_numpy(float),
                          d["C_ci95_high"].to_numpy(float) - y])
        axes[1].errorbar(x, y, yerr=yerr, marker="o", capsize=3,
                         linewidth=1.25, label=label)
    axes[1].axhline(0.0, linestyle="--", linewidth=1)
    axes[1].set_xlabel(r"Target uncertainty $S^*$")
    axes[1].set_ylabel(r"$C_{\mathrm{switch}}^{(\mu)}$")
    axes[1].set_title("Switching relief in the same target-uncertainty scan")
    axes[1].grid(True, alpha=0.25)
    axes[1].legend(frameon=False)
    for ext in ("pdf", "png"):
        fig.savefig(out / f"fig_target_baseline_advantage_and_switching_penalty.{ext}",
                    dpi=300 if ext == "png" else None, bbox_inches="tight")
    plt.close(fig)


def write_summary(phase_baseline: pd.DataFrame, phase_schedules: pd.DataFrame,
                  target_baseline: pd.DataFrame | None,
                  target_schedules: pd.DataFrame | None, out: Path) -> None:
    lines = [
        "Paper 3: Fixed-ordering boundary diagnostic summary",
        "====================================================",
        "",
        "B_RFDF^(mu) = meanMu_DF - meanMu_RF",
        "Positive, resolved B means fixed RF retains lower regulatory burden than fixed DF.",
        "",
        "Phase-map classification:",
    ]
    for label, data in phase_schedules.groupby("schedule_label", sort=False):
        lines += [
            f"  {label}:",
            f"    total cells: {len(data)}",
            f"    resolved RF advantage: {int(data['B_resolved_RF_advantage'].sum())}",
            f"    resolved switching relief: {int(data['C_resolved_relief'].sum())}",
            "    RF advantage + resolved relief: "
            f"{int((data['classification_code'] == 1).sum())}",
            "    RF advantage + attenuated/unresolved relief: "
            f"{int((data['classification_code'] == 2).sum())}",
            "    RF advantage + resolved positive switching penalty: "
            f"{int((data['classification_code'] == 3).sum())}",
            "    no resolved fixed RF advantage: "
            f"{int((data['classification_code'] == 0).sum())}",
            "",
        ]
    if target_baseline is not None and target_schedules is not None:
        lines += ["Target-uncertainty scan:", ""]
        for _, row in target_baseline.sort_values("targetEntropy").iterrows():
            lines.append(
                f"  S*={row.targetEntropy:.2f}: "
                f"B={row.B_mean:.8g} [{row.B_ci95_low:.8g}, {row.B_ci95_high:.8g}]"
            )
        lines.append("")
        for label, data in target_schedules.groupby("schedule_label", sort=False):
            lines.append(f"  {label}:")
            for _, row in data.sort_values("targetEntropy").iterrows():
                lines.append(
                    f"    S*={row.targetEntropy:.2f}: "
                    f"C={row.C_mean:.8g} [{row.C_ci95_low:.8g}, {row.C_ci95_high:.8g}], "
                    f"{row.classification}"
                )
            lines.append("")
    lines += [
        "Manuscript decision rule:",
        "  - If cells where C attenuates lack resolved positive B, write that boundary",
        "    attenuation coincides with loss of the fixed RF-advantage regime.",
        "  - If cells where C attenuates retain resolved positive B, write that switching",
        "    relief weakens within part of the fixed RF-advantage regime.",
        "  - Claim a positive switching penalty only where its 95% CI is entirely above zero.",
    ]
    (out / "paper3_boundary_diagnostic_summary.txt").write_text(
        "\n".join(lines) + "\n", encoding="utf-8"
    )


def main() -> None:
    args = parse_args()
    args.out.mkdir(parents=True, exist_ok=True)

    phase_baseline, phase_schedules, phase_paired = collect_scan(
        args.phase_manifest, "phase", args
    )
    phase_baseline.to_csv(args.out / "phase_fixed_ordering_baseline_statistics.csv", index=False)
    phase_schedules.to_csv(args.out / "phase_fixed_ordering_and_switching_statistics.csv", index=False)
    phase_paired.to_csv(args.out / "phase_fixed_ordering_paired_runs.csv", index=False)
    plot_phase(phase_baseline, phase_schedules, args.out)

    target_baseline = target_schedules = None
    if args.target_manifest is not None:
        target_baseline, target_schedules, target_paired = collect_scan(
            args.target_manifest, "target", args
        )
        target_baseline.to_csv(args.out / "target_fixed_ordering_baseline_statistics.csv", index=False)
        target_schedules.to_csv(args.out / "target_fixed_ordering_and_switching_statistics.csv", index=False)
        target_paired.to_csv(args.out / "target_fixed_ordering_paired_runs.csv", index=False)
        plot_target(target_baseline, target_schedules, args.out)

    write_summary(phase_baseline, phase_schedules, target_baseline, target_schedules, args.out)

    print(f"Completed fixed-ordering boundary analysis: {args.out}")
    for name in [
        "fig_phase_baseline_advantage_and_switching_penalty.pdf",
        "fig_phase_regime_classification.pdf",
        "paper3_boundary_diagnostic_summary.txt",
    ]:
        print(f"  {args.out / name}")
    if target_baseline is not None:
        print(f"  {args.out / 'fig_target_baseline_advantage_and_switching_penalty.pdf'}")


if __name__ == "__main__":
    main()
