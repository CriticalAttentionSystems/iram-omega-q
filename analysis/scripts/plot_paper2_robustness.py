#!/usr/bin/env python3
"""
Plot the Paper 2 parameter-neighborhood robustness study.

This script does not run the Java model. It reads continuous-ramp HYSTERESIS
outputs created by run_paper2_robustness.sh:

    analysis/results/paper2/robustness_hysteresis/
        eta_0p080_mu0_0p040/{rf,df}/rep_*/hysteresis_loop.csv
        ...
        eta_0p180_mu0_0p120/{rf,df}/rep_*/hysteresis_loop.csv

For each eta/mu0 cell and each matched RF/DF pair, it computes:

    A_y^(o) = integral [y_down(S*) - y_up(S*)] dS*
              for y in {mu, dC}, o in {RF, DF}

    B_y = mean over both ramp branches and S* bins of
          [y_DF(S*) - y_RF(S*)]

Outputs
-------
Figure 6:
    paper2_robustness_loop_areas_RF_DF.png/pdf
    RF/DF signed hysteresis-loop areas for mu and Delta C across the grid.
    RF and DF panels use the same color scale for each observable.

Figure 7:
    paper2_robustness_regulatory_burden_Bmu.png/pdf
    Mean paired B_mu and fraction of pairs with B_mu > 0.

Figure 8:
    paper2_robustness_state_displacement_BdC.png/pdf
    Mean paired B_DeltaC and fraction of pairs with B_DeltaC > 0.

CSV summaries:
    paper2_robustness_per_replicate.csv
    paper2_robustness_cell_summary.csv
"""
from __future__ import annotations

import argparse
import csv
from pathlib import Path
from typing import Iterable

import matplotlib.pyplot as plt
from matplotlib.colors import TwoSlopeNorm
import numpy as np

ETA_VALUES = np.array([0.08, 0.13, 0.18], dtype=float)
MU0_VALUES = np.array([0.04, 0.08, 0.12], dtype=float)
ORDERINGS = ("rf", "df")
OBSERVABLES = ("mu", "dC")
BRANCHES = ("up", "down")
DEFAULT_BINS = 34


def value_tag(x: float) -> str:
    return f"{x:.3f}".replace(".", "p")


def cell_name(eta: float, mu0: float) -> str:
    return f"eta_{value_tag(eta)}_mu0_{value_tag(mu0)}"


def load_loop(path: Path) -> np.ndarray:
    if not path.exists():
        raise FileNotFoundError(f"Missing result file: {path}")
    data = np.genfromtxt(path, delimiter=",", names=True, dtype=None, encoding="utf-8")
    required = {"direction", "targetEntropy", "mu", "dC"}
    missing = required - set(data.dtype.names or ())
    if missing:
        raise ValueError(f"{path} missing columns: {sorted(missing)}")
    return data


def write_rows(path: Path, header: Iterable[str], rows: Iterable[Iterable[object]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="") as stream:
        writer = csv.writer(stream)
        writer.writerow(list(header))
        writer.writerows(rows)


def common_replicates(cell_root: Path) -> list[str]:
    rf = {p.name for p in (cell_root / "rf").glob("rep_*")
          if (p / "hysteresis_loop.csv").exists()}
    df = {p.name for p in (cell_root / "df").glob("rep_*")
          if (p / "hysteresis_loop.csv").exists()}
    common = sorted(rf & df)
    if not common:
        raise FileNotFoundError(
            f"No matched RF/DF replicate pairs found under {cell_root}.\n"
            "Run: bash analysis/scripts/run_paper2_robustness.sh publication"
        )
    if rf != df:
        print(f"Warning: unmatched replicate directories ignored in {cell_root.name}: "
              f"RF-only={sorted(rf-df)} DF-only={sorted(df-rf)}")
    return common


def binned(data: np.ndarray, observable: str, branch: str, edges: np.ndarray) -> np.ndarray:
    mask = np.asarray(data["direction"]) == branch
    x = np.asarray(data["targetEntropy"][mask], dtype=float)
    y = np.asarray(data[observable][mask], dtype=float)
    out = np.full(len(edges) - 1, np.nan, dtype=float)
    for k in range(len(out)):
        in_bin = ((x >= edges[k]) & (x <= edges[k + 1])
                  if k == len(out) - 1
                  else (x >= edges[k]) & (x < edges[k + 1]))
        if np.any(in_bin):
            out[k] = np.mean(y[in_bin])
    return out


def integrate(y: np.ndarray, x: np.ndarray) -> float:
    ok = np.isfinite(y) & np.isfinite(x)
    if np.sum(ok) < 2:
        return np.nan
    if hasattr(np, "trapezoid"):
        return float(np.trapezoid(y[ok], x[ok]))
    return float(np.trapz(y[ok], x[ok]))


def mean_ci(x: np.ndarray) -> tuple[float, float, int]:
    x = np.asarray(x, dtype=float)
    x = x[np.isfinite(x)]
    n = int(x.size)
    if n == 0:
        return np.nan, np.nan, 0
    m = float(np.mean(x))
    if n < 2:
        return m, np.nan, n
    return m, float(1.96 * np.std(x, ddof=1) / np.sqrt(n)), n


def symmetric_norm(arrays: list[np.ndarray]) -> TwoSlopeNorm:
    finite = np.concatenate([a[np.isfinite(a)] for a in arrays if np.any(np.isfinite(a))])
    vmax = max(float(np.max(np.abs(finite))), 1.0e-12)
    return TwoSlopeNorm(vmin=-vmax, vcenter=0.0, vmax=vmax)


def annotate(ax: plt.Axes, values: np.ndarray, fmt: str = ".3g") -> None:
    for i in range(values.shape[0]):
        for j in range(values.shape[1]):
            if np.isfinite(values[i, j]):
                ax.text(j, i, format(values[i, j], fmt),
                        ha="center", va="center", fontsize=8, color="black")


def heat(ax: plt.Axes, values: np.ndarray, title: str,
         cmap: str, norm=None, vmin=None, vmax=None):
    image = ax.imshow(values, origin="lower", aspect="auto", cmap=cmap,
                      norm=norm, vmin=vmin, vmax=vmax)
    ax.set_xticks(range(len(ETA_VALUES)),
                  [f"{v:.2f}" for v in ETA_VALUES])
    ax.set_yticks(range(len(MU0_VALUES)),
                  [f"{v:.2f}" for v in MU0_VALUES])
    ax.set_xlabel(r"Incoming disturbance amplitude $\eta$")
    ax.set_ylabel(r"Initial regulation gain $\mu_0$")
    ax.set_title(title)
    annotate(ax, values)
    return image


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path("analysis/results/paper2"))
    parser.add_argument("--bins", type=int, default=DEFAULT_BINS)
    args = parser.parse_args()

    root = args.root
    data_root = root / "robustness_hysteresis"
    fig_dir = root / "figures"
    summary_dir = root / "summary"
    fig_dir.mkdir(parents=True, exist_ok=True)
    summary_dir.mkdir(parents=True, exist_ok=True)

    areas = {obs: {ordering: np.full((len(MU0_VALUES), len(ETA_VALUES)), np.nan)
                   for ordering in ORDERINGS} for obs in OBSERVABLES}
    burden = {obs: np.full((len(MU0_VALUES), len(ETA_VALUES)), np.nan)
              for obs in OBSERVABLES}
    burden_fraction_positive = {
        obs: np.full((len(MU0_VALUES), len(ETA_VALUES)), np.nan)
        for obs in OBSERVABLES
    }

    per_rep_rows = []
    summary_rows = []

    for j, eta in enumerate(ETA_VALUES):
        for i, mu0 in enumerate(MU0_VALUES):
            cell = cell_name(eta, mu0)
            cell_root = data_root / cell
            reps = common_replicates(cell_root)

            first = load_loop(cell_root / "rf" / reps[0] / "hysteresis_loop.csv")
            target = np.asarray(first["targetEntropy"], dtype=float)
            edges = np.linspace(np.nanmin(target), np.nanmax(target), args.bins + 1)
            centers = 0.5 * (edges[:-1] + edges[1:])

            values = {obs: {ordering: [] for ordering in ORDERINGS}
                      for obs in OBSERVABLES}
            b_values = {obs: [] for obs in OBSERVABLES}

            for rep in reps:
                rf = load_loop(cell_root / "rf" / rep / "hysteresis_loop.csv")
                df = load_loop(cell_root / "df" / rep / "hysteresis_loop.csv")

                for obs in OBSERVABLES:
                    curves = {}
                    for ordering, data in (("rf", rf), ("df", df)):
                        up = binned(data, obs, "up", edges)
                        down = binned(data, obs, "down", edges)
                        area = integrate(down - up, centers)
                        values[obs][ordering].append(area)
                        curves[ordering] = (up, down)

                    paired_curve = np.concatenate([
                        curves["df"][0] - curves["rf"][0],
                        curves["df"][1] - curves["rf"][1],
                    ])
                    B = float(np.nanmean(paired_curve))
                    b_values[obs].append(B)

                    per_rep_rows.append([
                        cell, eta, mu0, rep, obs,
                        values[obs]["rf"][-1],
                        values[obs]["df"][-1],
                        values[obs]["df"][-1] - values[obs]["rf"][-1],
                        B,
                    ])

            for obs in OBSERVABLES:
                rf_m, rf_ci, n = mean_ci(np.asarray(values[obs]["rf"]))
                df_m, df_ci, _ = mean_ci(np.asarray(values[obs]["df"]))
                B_m, B_ci, _ = mean_ci(np.asarray(b_values[obs]))
                frac = float(np.mean(np.asarray(b_values[obs]) > 0.0))

                areas[obs]["rf"][i, j] = rf_m
                areas[obs]["df"][i, j] = df_m
                burden[obs][i, j] = B_m
                burden_fraction_positive[obs][i, j] = frac

                summary_rows.append([
                    cell, eta, mu0, obs, n,
                    rf_m, rf_ci, df_m, df_ci,
                    df_m - rf_m, B_m, B_ci, frac,
                ])

    write_rows(
        summary_dir / "paper2_robustness_per_replicate.csv",
        ["cell", "eta", "mu0", "replicate", "observable",
         "area_rf", "area_df", "area_df_minus_rf", "B_df_minus_rf"],
        per_rep_rows,
    )
    write_rows(
        summary_dir / "paper2_robustness_cell_summary.csv",
        ["cell", "eta", "mu0", "observable", "n_pairs",
         "mean_area_rf", "ci95_area_rf", "mean_area_df", "ci95_area_df",
         "mean_area_df_minus_rf", "mean_B_df_minus_rf", "ci95_B_df_minus_rf",
         "fraction_pairs_B_positive"],
        summary_rows,
    )

    # Figure 6: signed hysteresis-loop areas.
    #
    # RF and DF must share one color scale for each observable.  Put the two
    # shared color bars in dedicated GridSpec columns rather than attaching
    # them to the DF axes.  This prevents the color-bar labels and tick labels
    # from covering the right-hand heatmaps.
    fig = plt.figure(figsize=(12.6, 8.5), constrained_layout=True)
    gs = fig.add_gridspec(
        2, 3,
        width_ratios=[1.0, 1.0, 0.065],
        height_ratios=[1.0, 1.0],
        wspace=0.18,
        hspace=0.24,
    )

    ax_mu_rf = fig.add_subplot(gs[0, 0])
    ax_mu_df = fig.add_subplot(gs[0, 1])
    cax_mu = fig.add_subplot(gs[0, 2])

    ax_dc_rf = fig.add_subplot(gs[1, 0])
    ax_dc_df = fig.add_subplot(gs[1, 1])
    cax_dc = fig.add_subplot(gs[1, 2])

    norm_mu = symmetric_norm([areas["mu"]["rf"], areas["mu"]["df"]])
    norm_dc = symmetric_norm([areas["dC"]["rf"], areas["dC"]["df"]])

    im_mu = heat(ax_mu_rf, areas["mu"]["rf"],
                 r"RF: $A_{\mu}$", "coolwarm", norm=norm_mu)
    heat(ax_mu_df, areas["mu"]["df"],
         r"DF: $A_{\mu}$", "coolwarm", norm=norm_mu)

    im_dc = heat(ax_dc_rf, areas["dC"]["rf"],
                 r"RF: $A_{\Delta C}$", "coolwarm", norm=norm_dc)
    heat(ax_dc_df, areas["dC"]["df"],
         r"DF: $A_{\Delta C}$", "coolwarm", norm=norm_dc)

    cb_mu = fig.colorbar(im_mu, cax=cax_mu)
    cb_mu.set_label(
        r"$A_{\mu}=\int(\mu_{\downarrow}-\mu_{\uparrow})\,dS^*$",
        rotation=270,
        labelpad=18,
    )

    cb_dc = fig.colorbar(im_dc, cax=cax_dc)
    cb_dc.set_label(
        r"$A_{\Delta C}=\int(\Delta C_{\downarrow}-\Delta C_{\uparrow})\,dS^*$",
        rotation=270,
        labelpad=18,
    )

    fig.suptitle(
        "Robustness of continuous-ramp hysteresis across parameter conditions",
        fontsize=14,
    )

    for ext in ("png", "pdf"):
        fig.savefig(
            fig_dir / f"paper2_robustness_loop_areas_RF_DF.{ext}",
            dpi=300 if ext == "png" else None,
            bbox_inches="tight",
        )
    plt.close(fig)

    # Figure 7: controller-level ordering burden.
    fig, axes = plt.subplots(1, 2, figsize=(10.7, 4.5))
    norm_bmu = symmetric_norm([burden["mu"]])
    im = heat(axes[0], burden["mu"],
              r"Mean $B_{\mu}=\langle\mu_{\rm DF}-\mu_{\rm RF}\rangle$",
              "coolwarm", norm=norm_bmu)
    im2 = heat(axes[1], burden_fraction_positive["mu"],
               r"Fraction of pairs with $B_{\mu}>0$",
               "viridis", vmin=0.0, vmax=1.0)
    fig.colorbar(im, ax=axes[0], shrink=0.90, label=r"$B_{\mu}$")
    fig.colorbar(im2, ax=axes[1], shrink=0.90, label="Fraction")
    fig.suptitle("Ordering-dependent adaptive regulatory burden")
    fig.tight_layout()
    for ext in ("png", "pdf"):
        fig.savefig(fig_dir / f"paper2_robustness_regulatory_burden_Bmu.{ext}",
                    dpi=300 if ext == "png" else None, bbox_inches="tight")
    plt.close(fig)

    # Figure 8: state-observable displacement.
    fig, axes = plt.subplots(1, 2, figsize=(10.7, 4.5))
    norm_bdc = symmetric_norm([burden["dC"]])
    im = heat(axes[0], burden["dC"],
              r"Mean $B_{\Delta C}=\langle\Delta C_{\rm DF}-\Delta C_{\rm RF}\rangle$",
              "coolwarm", norm=norm_bdc)
    im2 = heat(axes[1], burden_fraction_positive["dC"],
               r"Fraction of pairs with $B_{\Delta C}>0$",
               "viridis", vmin=0.0, vmax=1.0)
    fig.colorbar(im, ax=axes[0], shrink=0.90, label=r"$B_{\Delta C}$")
    fig.colorbar(im2, ax=axes[1], shrink=0.90, label="Fraction")
    fig.suptitle("Ordering-dependent coherence-gap displacement")
    fig.tight_layout()
    for ext in ("png", "pdf"):
        fig.savefig(fig_dir / f"paper2_robustness_state_displacement_BdC.{ext}",
                    dpi=300 if ext == "png" else None, bbox_inches="tight")
    plt.close(fig)

    print("Wrote Paper 2 robustness figures:")
    print(" ", fig_dir / "paper2_robustness_loop_areas_RF_DF.png")
    print(" ", fig_dir / "paper2_robustness_regulatory_burden_Bmu.png")
    print(" ", fig_dir / "paper2_robustness_state_displacement_BdC.png")
    print("Wrote numerical summary:", summary_dir / "paper2_robustness_cell_summary.csv")


if __name__ == "__main__":
    main()
