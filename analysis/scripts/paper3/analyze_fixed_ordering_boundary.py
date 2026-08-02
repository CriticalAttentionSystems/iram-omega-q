#!/usr/bin/env python3
"""
Paper 3 missing analysis: determine whether switching-penalty attenuation occurs
inside or outside the fixed RF-advantage regime.

Scientific question
-------------------
The completed robustness scan mapped:

    C_switch^(mu) =
        meanMu_switching
        - [p_RF * meanMu_RF + (1-p_RF) * meanMu_DF].

At low initial regulation mu0 and high disturbance eta, C_switch^(mu)
attenuates and may approach/cross zero.  To interpret this boundary, compute:

    B_RFDF^(mu) = meanMu_DF - meanMu_RF.

If B_RFDF^(mu) remains resolved positive while C_switch is no longer negative,
switching relief has weakened inside an RF-advantage regime.  If B_RFDF^(mu)
becomes unresolved or non-positive, the boundary may instead be outside the
regime in which fixed RF itself retains a regulatory advantage.

This script:
  1. Reads the existing phase and target manifests.
  2. Reads switching_runs.csv in each completed output directory.
  3. Detects exported matched RF and DF mean-mu columns.
  4. Computes paired B_RFDF^(mu), C_switch^(mu), means, and 95% CIs.
  5. Produces:
       - phase_fixed_ordering_statistics.csv
       - target_fixed_ordering_statistics.csv
       - fig_phase_baseline_advantage_and_switching_penalty.{png,pdf}
       - fig_phase_regime_classification.{png,pdf}
       - fig_target_baseline_advantage_and_switching_penalty.{png,pdf}
       - paper3_boundary_diagnostic_summary.txt

Dependencies: numpy, pandas, matplotlib only.  SciPy is not required.

Example from the IRAM-Omega-Q project root
------------------------------------------
python analysis/scripts/analyze_fixed_ordering_boundary.py \
  --phase-manifest analysis/paper3/config/robustness/phase_manifest.csv \
  --target-manifest analysis/paper3/config/robustness/target_manifest.csv \
  --out analysis/results/paper3/robustness/fixed_ordering_boundary \
  --periodic-dwell 1000 \
  --markov-p 0.001
"""
from __future__ import annotations

import argparse
import math
import re
from pathlib import Path
from typing import Iterable

import numpy as np
import pandas as pd
import matplotlib.pyplot as plt
from matplotlib.colors import BoundaryNorm, ListedColormap, TwoSlopeNorm
from matplotlib.patches import Patch


# Candidate names cover common Java/Python-export conventions.  If your export
# uses a different name, pass --rf-column and --df-column explicitly.
RF_COLUMN_ALIASES = [
    "meanMu_RF", "meanMuRF", "mean_mu_rf", "rf_mean_mu", "rfMeanMu",
    "muRF", "mu_RF", "mu_rf", "meanMuFixedRF", "mean_mu_fixed_rf",
    "fixedRF_meanMu", "fixed_rf_mean_mu", "baselineRF_meanMu",
    "baseline_rf_mean_mu", "meanMuBaselineRF", "baselineMeanMuRF",
    "baseline_mu_rf", "rfBaselineMeanMu",
]
DF_COLUMN_ALIASES = [
    "meanMu_DF", "meanMuDF", "mean_mu_df", "df_mean_mu", "dfMeanMu",
    "muDF", "mu_DF", "mu_df", "meanMuFixedDF", "mean_mu_fixed_df",
    "fixedDF_meanMu", "fixed_df_mean_mu", "baselineDF_meanMu",
    "baseline_df_mean_mu", "meanMuBaselineDF", "baselineMeanMuDF",
    "baseline_mu_df", "dfBaselineMeanMu",
]
C_COLUMN_ALIASES = [
    "C_switch_mu", "Cswitch_mu", "C_switch^(mu)", "C_switch", "switchPenaltyMu",
    "switchingPenaltyMu", "switch_penalty_mu", "penalty_mu",
]
PROTOCOL_ALIASES = ["protocol", "switchingProtocol", "scheduleProtocol", "schedule"]
DWELL_ALIASES = ["periodicDwellSteps", "dwellSteps", "dwell", "periodic_dwell_steps", "L"]
PLOSS_ALIASES = ["pLoss", "p_loss", "ploss"]
PRETURN_ALIASES = ["pReturn", "p_return", "preturn"]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--phase-manifest", type=Path, required=True,
                        help="CSV generated for the completed (mu0, eta) scan.")
    parser.add_argument("--target-manifest", type=Path,
                        help="CSV generated for the completed S* scan. Optional.")
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--periodic-dwell", type=int, default=1000,
                        help="Representative periodic schedule used in manuscript robustness plot.")
    parser.add_argument("--markov-p", type=float, default=0.001,
                        help="Representative symmetric Markov transition probability.")
    parser.add_argument("--rf-column", default=None,
                        help="Explicit fixed-RF mean-mu column name if auto-detection fails.")
    parser.add_argument("--df-column", default=None,
                        help="Explicit fixed-DF mean-mu column name if auto-detection fails.")
    parser.add_argument("--c-column", default=None,
                        help="Explicit switching-penalty column name if auto-detection fails.")
    parser.add_argument("--runs-filename", default="switching_runs.csv",
                        help="Run-level CSV name inside each output_dir.")
    return parser.parse_args()


def normalize(name: str) -> str:
    return re.sub(r"[^a-z0-9]", "", str(name).lower())


def exact_alias(columns: Iterable[str], aliases: Iterable[str]) -> str | None:
    by_norm = {normalize(col): col for col in columns}
    for alias in aliases:
        if normalize(alias) in by_norm:
            return by_norm[normalize(alias)]
    return None


def fuzzy_baseline_column(columns: list[str], mode: str) -> list[str]:
    """Return plausible baseline mean-mu columns containing RF or DF."""
    other = "df" if mode == "rf" else "rf"
    candidates = []
    for col in columns:
        n = normalize(col)
        if mode not in n or other in n:
            continue
        if "mu" not in n:
            continue
        if any(word in n for word in ("mean", "avg", "bar", "baseline", "fixed")):
            candidates.append(col)
    return candidates


def required_column(df: pd.DataFrame, explicit: str | None, aliases: list[str],
                    role: str, diagnostic_file: Path | None = None) -> str:
    if explicit:
        if explicit not in df.columns:
            raise KeyError(f"Requested {role} column '{explicit}' is not present. Columns: {list(df.columns)}")
        return explicit
    found = exact_alias(list(df.columns), aliases)
    if found:
        return found
    if role in ("fixed RF mean mu", "fixed DF mean mu"):
        mode = "rf" if role.startswith("fixed RF") else "df"
        candidates = fuzzy_baseline_column(list(df.columns), mode)
        if len(candidates) == 1:
            return candidates[0]
    raise KeyError(
        f"Could not auto-detect {role} column.\n"
        f"Available columns: {list(df.columns)}\n"
        "Re-run with the explicit --rf-column, --df-column, or --c-column argument "
        "if the corresponding value is present under a different name."
    )


def find_general_column(df: pd.DataFrame, aliases: list[str], role: str) -> str:
    col = exact_alias(list(df.columns), aliases)
    if not col:
        raise KeyError(f"Could not detect {role} column. Available columns: {list(df.columns)}")
    return col


def ci95(values: np.ndarray) -> dict[str, float | int | bool]:
    values = np.asarray(values, dtype=float)
    values = values[np.isfinite(values)]
    if values.size == 0:
        raise ValueError("No finite values available for statistics.")
    mean = float(np.mean(values))
    sd = float(np.std(values, ddof=1)) if values.size > 1 else float("nan")
    se = sd / math.sqrt(values.size) if values.size > 1 else float("nan")
    half = 1.96 * se if values.size > 1 else float("nan")
    low = mean - half if values.size > 1 else float("nan")
    high = mean + half if values.size > 1 else float("nan")
    return {
        "n": int(values.size), "mean": mean, "sd": sd, "se": se,
        "ci95_low": low, "ci95_high": high,
        "resolved_positive": bool(low > 0) if values.size > 1 else False,
        "resolved_negative": bool(high < 0) if values.size > 1 else False,
    }


def select_schedule(df: pd.DataFrame, kind: str, periodic_dwell: int, markov_p: float) -> pd.DataFrame:
    protocol_col = find_general_column(df, PROTOCOL_ALIASES, "protocol")
    protocol = df[protocol_col].astype(str).str.upper()
    if kind == "periodic":
        dwell_col = find_general_column(df, DWELL_ALIASES, "periodic dwell")
        return df[(protocol == "PERIODIC") &
                  (pd.to_numeric(df[dwell_col], errors="coerce") == periodic_dwell)].copy()
    ploss_col = find_general_column(df, PLOSS_ALIASES, "pLoss")
    preturn_col = find_general_column(df, PRETURN_ALIASES, "pReturn")
    return df[(protocol == "MARKOV") &
              np.isclose(pd.to_numeric(df[ploss_col], errors="coerce"), markov_p) &
              np.isclose(pd.to_numeric(df[preturn_col], errors="coerce"), markov_p)].copy()


def resolve_output_csv(output_dir: Path, filename: str) -> Path:
    direct = output_dir / filename
    if direct.exists():
        return direct
    matches = list(output_dir.rglob(filename)) if output_dir.exists() else []
    if len(matches) == 1:
        return matches[0]
    raise FileNotFoundError(f"Cannot locate {filename} under output directory: {output_dir}")


def inspect_first_output(manifest: pd.DataFrame, runs_filename: str, out: Path) -> pd.DataFrame:
    if "output_dir" not in manifest.columns:
        raise KeyError(f"Manifest has no 'output_dir' column. Columns: {list(manifest.columns)}")
    first = resolve_output_csv(Path(str(manifest.iloc[0]["output_dir"])), runs_filename)
    df = pd.read_csv(first)
    lines = [
        "Paper 3 fixed-ordering boundary analysis: export inspection",
        "==========================================================",
        f"Inspected run file: {first}",
        "",
        "Available columns:",
        *[f"  - {col}" for col in df.columns],
        "",
        "Plausible fixed-RF mean-mu columns:",
        *[f"  - {col}" for col in fuzzy_baseline_column(list(df.columns), 'rf')],
        "",
        "Plausible fixed-DF mean-mu columns:",
        *[f"  - {col}" for col in fuzzy_baseline_column(list(df.columns), 'df')],
        "",
        "If the script stops because auto-detection failed, inspect this list and rerun with:",
        "  --rf-column <actual_R​​F_column> --df-column <actual_DF_column> --c-column <actual_penalty_column>",
        "",
        "If no RF/DF baseline columns are present, the Java exporter did not write the values",
        "needed for B_RFDF^(mu); in that case, a small Java output modification or a rerun",
        "that exports matched RF and DF mean-mu values is required.",
    ]
    (out / "export_column_inspection.txt").write_text("\n".join(lines) + "\n", encoding="utf-8")
    return df


def collect_statistics(manifest_path: Path, scan_name: str, args: argparse.Namespace) -> pd.DataFrame:
    manifest = pd.read_csv(manifest_path)
    if manifest.empty:
        raise ValueError(f"Manifest is empty: {manifest_path}")
    inspect_first_output(manifest, args.runs_filename, args.out)

    records: list[dict] = []
    for _, point in manifest.iterrows():
        run_file = resolve_output_csv(Path(str(point["output_dir"])), args.runs_filename)
        full = pd.read_csv(run_file)
        rf_col = required_column(full, args.rf_column, RF_COLUMN_ALIASES, "fixed RF mean mu")
        df_col = required_column(full, args.df_column, DF_COLUMN_ALIASES, "fixed DF mean mu")
        c_col = required_column(full, args.c_column, C_COLUMN_ALIASES, "switching penalty mu")

        for kind, label in [
            ("periodic", rf"Periodic $L={args.periodic_dwell}$"),
            ("markov", rf"Markov $p={args.markov_p:g}$"),
        ]:
            selected = select_schedule(full, kind, args.periodic_dwell, args.markov_p)
            if selected.empty:
                raise ValueError(f"No {label} rows found in {run_file}")

            b_values = (pd.to_numeric(selected[df_col], errors="coerce") -
                        pd.to_numeric(selected[rf_col], errors="coerce")).to_numpy()
            c_values = pd.to_numeric(selected[c_col], errors="coerce").to_numpy()
            b = ci95(b_values)
            c = ci95(c_values)

            record = {
                "scan": scan_name,
                "schedule": kind,
                "schedule_label": label,
                "rf_column": rf_col,
                "df_column": df_col,
                "c_column": c_col,
                "mu": float(point["mu"]),
                "noise": float(point["noise"]),
                "targetEntropy": float(point["targetEntropy"]),
                "B_n": b["n"], "B_mean": b["mean"], "B_sd": b["sd"],
                "B_ci95_low": b["ci95_low"], "B_ci95_high": b["ci95_high"],
                "B_resolved_RF_advantage": b["resolved_positive"],
                "C_n": c["n"], "C_mean": c["mean"], "C_sd": c["sd"],
                "C_ci95_low": c["ci95_low"], "C_ci95_high": c["ci95_high"],
                "C_resolved_relief": c["resolved_negative"],
                "C_resolved_positive_penalty": c["resolved_positive"],
            }
            if b["resolved_positive"] and c["resolved_negative"]:
                category = "RF advantage + switching relief"
                category_code = 1
            elif b["resolved_positive"] and c["resolved_positive"]:
                category = "RF advantage + resolved positive switching penalty"
                category_code = 3
            elif b["resolved_positive"]:
                category = "RF advantage + unresolved/attenuated switching effect"
                category_code = 2
            else:
                category = "No resolved fixed RF advantage"
                category_code = 0
            record["classification"] = category
            record["classification_code"] = category_code
            records.append(record)
    return pd.DataFrame(records)


def draw_heatmap(ax: plt.Axes, data: pd.DataFrame, value: str, title: str,
                 mark_col: str, norm: TwoSlopeNorm):
    mus = np.array(sorted(data["mu"].unique()), dtype=float)
    noises = np.array(sorted(data["noise"].unique()), dtype=float)
    matrix = data.pivot(index="noise", columns="mu", values=value).reindex(index=noises, columns=mus).to_numpy()
    marked = data.pivot(index="noise", columns="mu", values=mark_col).reindex(index=noises, columns=mus).to_numpy()
    im = ax.imshow(
        matrix, origin="lower", aspect="auto", cmap="coolwarm", norm=norm,
        extent=[mus.min() - 0.005, mus.max() + 0.005, noises.min() - 0.01, noises.max() + 0.01],
    )
    for iy, eta in enumerate(noises):
        for ix, mu in enumerate(mus):
            if bool(marked[iy, ix]):
                ax.plot(mu, eta, "k.", markersize=5)
    ax.set_title(title)
    ax.set_xlabel(r"Initial regulation $\mu_0$")
    ax.set_ylabel(r"Disturbance $\eta$")
    return im


def plot_phase(stats: pd.DataFrame, out: Path) -> None:
    schedules = list(stats["schedule_label"].drop_duplicates())
    vmax_b = max(float(np.nanmax(np.abs(stats["B_mean"]))), 1e-12)
    vmax_c = max(float(np.nanmax(np.abs(stats["C_mean"]))), 1e-12)
    b_norm = TwoSlopeNorm(vmin=-vmax_b, vcenter=0, vmax=vmax_b)
    c_norm = TwoSlopeNorm(vmin=-vmax_c, vcenter=0, vmax=vmax_c)

    fig, axes = plt.subplots(len(schedules), 2, figsize=(10.2, 4.1 * len(schedules)), constrained_layout=True)
    axes = np.atleast_2d(axes)
    b_im = c_im = None
    for row, schedule in enumerate(schedules):
        d = stats[stats["schedule_label"] == schedule]
        b_im = draw_heatmap(
            axes[row, 0], d, "B_mean",
            schedule + "\n" + r"$B_{\mathrm{RF/DF}}^{(\mu)}=\overline{\mu}_{\mathrm{DF}}-\overline{\mu}_{\mathrm{RF}}$",
            "B_resolved_RF_advantage", b_norm,
        )
        c_im = draw_heatmap(
            axes[row, 1], d, "C_mean",
            schedule + "\n" + r"$C_{\mathrm{switch}}^{(\mu)}$",
            "C_resolved_relief", c_norm,
        )
    fig.colorbar(b_im, ax=axes[:, 0], shrink=0.82,
                 label=r"$B_{\mathrm{RF/DF}}^{(\mu)}$; dot = 95% CI above zero")
    fig.colorbar(c_im, ax=axes[:, 1], shrink=0.82,
                 label=r"$C_{\mathrm{switch}}^{(\mu)}$; dot = 95% CI below zero")
    fig.suptitle("Is boundary attenuation outside the fixed RF-advantage regime?", fontsize=13)
    for ext in ("png", "pdf"):
        fig.savefig(out / f"fig_phase_baseline_advantage_and_switching_penalty.{ext}",
                    dpi=300 if ext == "png" else None, bbox_inches="tight")
    plt.close(fig)

    # Classification map focused directly on the interpretive question.
    cmap = ListedColormap(["0.82", "#2c7fb8", "#fdae61", "#d7191c"])
    norm = BoundaryNorm([-0.5, 0.5, 1.5, 2.5, 3.5], cmap.N)
    fig, axes = plt.subplots(1, len(schedules), figsize=(5.0 * len(schedules), 4.35), constrained_layout=True)
    axes = np.atleast_1d(axes)
    for ax, schedule in zip(axes, schedules):
        d = stats[stats["schedule_label"] == schedule]
        mus = np.array(sorted(d["mu"].unique()), dtype=float)
        noises = np.array(sorted(d["noise"].unique()), dtype=float)
        matrix = d.pivot(index="noise", columns="mu", values="classification_code").reindex(
            index=noises, columns=mus).to_numpy()
        ax.imshow(matrix, origin="lower", aspect="auto", cmap=cmap, norm=norm,
                  extent=[mus.min() - 0.005, mus.max() + 0.005, noises.min() - 0.01, noises.max() + 0.01])
        ax.set_title(schedule)
        ax.set_xlabel(r"Initial regulation $\mu_0$")
        ax.set_ylabel(r"Disturbance $\eta$")
    handles = [
        Patch(facecolor="0.82", label="No resolved RF advantage"),
        Patch(facecolor="#2c7fb8", label="RF advantage + relief"),
        Patch(facecolor="#fdae61", label="RF advantage + attenuated/unresolved C"),
        Patch(facecolor="#d7191c", label="RF advantage + resolved positive C"),
    ]
    fig.legend(handles=handles, loc="outside lower center", ncol=2, frameon=False)
    fig.suptitle("Classification of switching-penalty boundary cells", fontsize=13)
    for ext in ("png", "pdf"):
        fig.savefig(out / f"fig_phase_regime_classification.{ext}",
                    dpi=300 if ext == "png" else None, bbox_inches="tight")
    plt.close(fig)


def plot_target(stats: pd.DataFrame, out: Path) -> None:
    schedules = list(stats["schedule_label"].drop_duplicates())
    fig, axes = plt.subplots(2, 1, figsize=(7.8, 8.0), sharex=True, constrained_layout=True)
    for schedule in schedules:
        d = stats[stats["schedule_label"] == schedule].sort_values("targetEntropy")
        x = d["targetEntropy"].to_numpy(float)
        b = d["B_mean"].to_numpy(float)
        b_err = np.vstack([b - d["B_ci95_low"].to_numpy(float), d["B_ci95_high"].to_numpy(float) - b])
        c = d["C_mean"].to_numpy(float)
        c_err = np.vstack([c - d["C_ci95_low"].to_numpy(float), d["C_ci95_high"].to_numpy(float) - c])
        axes[0].errorbar(x, b, yerr=b_err, marker="o", capsize=3, label=schedule)
        axes[1].errorbar(x, c, yerr=c_err, marker="o", capsize=3, label=schedule)
    axes[0].axhline(0, linestyle="--", linewidth=1)
    axes[1].axhline(0, linestyle="--", linewidth=1)
    axes[0].set_ylabel(r"$B_{\mathrm{RF/DF}}^{(\mu)}$")
    axes[0].set_title("Fixed RF-versus-DF regulatory-burden separation\n95% confidence intervals")
    axes[1].set_ylabel(r"$C_{\mathrm{switch}}^{(\mu)}$")
    axes[1].set_xlabel(r"Target uncertainty $S^*$")
    axes[1].set_title("Switching relief in the same target-uncertainty scan\n95% confidence intervals")
    axes[0].legend(frameon=False)
    for ax in axes:
        ax.grid(True, alpha=0.25)
    for ext in ("png", "pdf"):
        fig.savefig(out / f"fig_target_baseline_advantage_and_switching_penalty.{ext}",
                    dpi=300 if ext == "png" else None, bbox_inches="tight")
    plt.close(fig)


def write_summary(phase: pd.DataFrame, target: pd.DataFrame | None, out: Path) -> None:
    lines = [
        "Paper 3 boundary diagnostic summary",
        "===================================",
        "",
        "Definition:",
        "  B_RFDF^(mu) = meanMu_DF - meanMu_RF",
        "  Positive B means fixed RF requires less regulation than fixed DF.",
        "",
    ]
    for schedule in phase["schedule_label"].drop_duplicates():
        d = phase[phase["schedule_label"] == schedule]
        rf_adv = int(d["B_resolved_RF_advantage"].sum())
        relief = int(d["C_resolved_relief"].sum())
        true_counter = int(((d["B_resolved_RF_advantage"]) & (d["C_resolved_positive_penalty"])).sum())
        attenuation_inside = int(((d["B_resolved_RF_advantage"]) & (~d["C_resolved_relief"]) &
                                  (~d["C_resolved_positive_penalty"])).sum())
        lines.extend([
            f"Phase scan: {schedule}",
            f"  Resolved RF-advantage cells: {rf_adv}/{len(d)}",
            f"  Resolved switching-relief cells: {relief}/{len(d)}",
            f"  RF advantage + unresolved/attenuated switching effect: {attenuation_inside}",
            f"  RF advantage + resolved positive switching penalty: {true_counter}",
            "",
        ])
    if target is not None:
        lines.append("Target-uncertainty scan:")
        for schedule in target["schedule_label"].drop_duplicates():
            d = target[target["schedule_label"] == schedule].sort_values("targetEntropy")
            lines.append(f"  {schedule}:")
            for _, row in d.iterrows():
                lines.append(
                    f"    S*={row.targetEntropy:.2f}: "
                    f"B={row.B_mean:.6g} [{row.B_ci95_low:.6g}, {row.B_ci95_high:.6g}], "
                    f"C={row.C_mean:.6g} [{row.C_ci95_low:.6g}, {row.C_ci95_high:.6g}]"
                )
        lines.append("")
    lines.extend([
        "Interpretation rule for the manuscript:",
        "  - If attenuated/positive C cells lack resolved positive B, state that attenuation",
        "    occurs near or outside the fixed RF-advantage boundary.",
        "  - If attenuated/positive C cells retain resolved positive B, state that switching",
        "    relief weakens within part of the RF-advantage regime.",
        "  - Only call a boundary a resolved positive switching penalty when the C 95% CI",
        "    lies entirely above zero.",
    ])
    (out / "paper3_boundary_diagnostic_summary.txt").write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> None:
    args = parse_args()
    args.out.mkdir(parents=True, exist_ok=True)
    try:
        phase = collect_statistics(args.phase_manifest, "phase", args)
        phase.to_csv(args.out / "phase_fixed_ordering_statistics.csv", index=False)
        plot_phase(phase, args.out)

        target = None
        if args.target_manifest:
            target = collect_statistics(args.target_manifest, "target", args)
            target.to_csv(args.out / "target_fixed_ordering_statistics.csv", index=False)
            plot_target(target, args.out)
        write_summary(phase, target, args.out)
    except Exception as error:
        report = args.out / "boundary_analysis_error.txt"
        report.write_text(
            "Paper 3 boundary-analysis script could not complete.\n"
            "=====================================================\n\n"
            f"{type(error).__name__}: {error}\n\n"
            "An export_column_inspection.txt file may have been written. If the required "
            "fixed-RF and fixed-DF baseline mean-mu quantities are absent from switching_runs.csv, "
            "the simulation exporter must be updated to write them before this analysis can be computed.\n",
            encoding="utf-8",
        )
        raise
    print(f"Wrote Paper 3 fixed-ordering boundary analysis to {args.out}")
    print("Core outputs:")
    for name in [
        "phase_fixed_ordering_statistics.csv",
        "fig_phase_baseline_advantage_and_switching_penalty.pdf",
        "fig_phase_regime_classification.pdf",
        "paper3_boundary_diagnostic_summary.txt",
    ]:
        print(f"  {args.out / name}")
    if args.target_manifest:
        print(f"  {args.out / 'target_fixed_ordering_statistics.csv'}")
        print(f"  {args.out / 'fig_target_baseline_advantage_and_switching_penalty.pdf'}")


if __name__ == "__main__":
    main()
