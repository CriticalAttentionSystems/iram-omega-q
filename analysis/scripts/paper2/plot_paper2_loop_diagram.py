#!/usr/bin/env python3
"""
Generate the Paper-2-specific IRAM-Omega-Q causal/protocol diagram.

This version is specific to the continuous target-ramp hysteresis study:
    - regulation-first versus disturbance-first causal ordering,
    - exact coherent evolution shared by both paths,
    - triangular S* schedule without reset at the turning point,
    - 30 matched-seed RF/DF replicate pairs,
    - ensemble hysteresis observables <mu>(S*) and <Delta C>(S*).

Usage from the project root:
    python3 analysis/scripts/plot_paper2_loop_diagram.py

Optional:
    python3 analysis/scripts/plot_paper2_loop_diagram.py --out-dir some/output/folder
"""

from __future__ import annotations

import argparse
from pathlib import Path

import matplotlib.pyplot as plt
from matplotlib.patches import FancyArrowPatch, FancyBboxPatch


DARK = "#17253f"
LINE = "#4c617f"
SETUP = "#e7f0fb"
STATE = "#e8f4ee"
METRIC = "#eee8fa"
CONTROL = "#fff2d8"
RF = "#e2ecfa"
DF = "#fdecec"
OUTPUT = "#e4f2f3"
RULE = "#ccd5e2"


def box(ax, x, y, w, h, text, face, fontsize=10.2, weight="normal"):
    patch = FancyBboxPatch(
        (x, y), w, h,
        boxstyle="round,pad=0.008,rounding_size=0.012",
        linewidth=1.35, edgecolor=DARK, facecolor=face
    )
    ax.add_patch(patch)
    ax.text(
        x + w / 2, y + h / 2, text,
        ha="center", va="center", fontsize=fontsize,
        color=DARK, weight=weight, linespacing=1.18
    )


def arrow(ax, x1, y1, x2, y2):
    ax.add_patch(FancyArrowPatch(
        (x1, y1), (x2, y2),
        arrowstyle="-|>", mutation_scale=13,
        linewidth=1.25, color=LINE
    ))


def section(ax, y, text, color):
    ax.text(0.02, y, text, fontsize=11.0, weight="bold", color=color, va="bottom")
    ax.plot([0.02, 0.98], [y - 0.01, y - 0.01], color=RULE, lw=1.0)


def build_figure(out_dir: Path) -> None:
    out_dir.mkdir(parents=True, exist_ok=True)

    fig, ax = plt.subplots(figsize=(15.5, 9.2))
    ax.set_xlim(0, 1)
    ax.set_ylim(0, 1)
    ax.axis("off")

    ax.text(
        0.5, 0.965,
        r"IRAM-$\Omega$-Q continuous-ramp hysteresis protocol",
        ha="center", va="center", fontsize=26, weight="bold", color=DARK
    )
    ax.text(
        0.5, 0.925,
        "Anticipatory protection versus reactive recovery under retained regulatory history",
        ha="center", va="center", fontsize=13, color=LINE
    )

    # Setup row
    section(ax, 0.868, "SETUP AND HISTORY-DEPENDENT DRIVE", "#487058")
    y = 0.774
    h = 0.071
    positions = [(0.025, 0.195), (0.255, 0.195), (0.485, 0.195), (0.715, 0.255)]
    texts = [
        "Initialize matched pair\nseed, state and controller",
        r"Initialize $\psi(0)$ from" + "\n" + "salience profile",
        r"Set $H,\ \eta,\ \mu_0$" + "\n" + "and focus index",
        r"Triangular target schedule" + "\n" + r"$S^*_{\min}\rightarrow S^*_{\max}\rightarrow S^*_{\min}$" + "\n(no reset at turn)"
    ]
    faces = [SETUP, STATE, SETUP, CONTROL]
    for (x, w), t, f in zip(positions, texts, faces):
        box(ax, x, y, w, h, t, f, fontsize=10)
    arrow(ax, 0.220, y+h/2, 0.250, y+h/2)
    arrow(ax, 0.450, y+h/2, 0.480, y+h/2)
    arrow(ax, 0.680, y+h/2, 0.710, y+h/2)

    # Current state and observation row
    section(ax, 0.704, "EACH COGNITIVE CYCLE", "#487058")
    y = 0.616
    h = 0.069
    xs = [(0.025, 0.195), (0.260, 0.200), (0.500, 0.205), (0.745, 0.230)]
    state_texts = [
        r"Primary state $\psi(t)$" + "\nnormalized amplitudes",
        r"Derived state $\rho(t)$" + "\nfor metrics",
        r"$S_{\mathrm{vN}},\ S_{\mathrm{diag}}$" + "\n" + r"$\Delta C=S_{\mathrm{diag}}-S_{\mathrm{vN}}$",
        r"Entropy error" + "\n" + r"$S_{\mathrm{vN}}-S^*(t)$"
    ]
    for (x, w), t, f in zip(xs, state_texts, [STATE, STATE, METRIC, CONTROL]):
        box(ax, x, y, w, h, t, f, fontsize=10)
    arrow(ax, 0.220, y+h/2, 0.255, y+h/2)
    arrow(ax, 0.460, y+h/2, 0.495, y+h/2)
    arrow(ax, 0.705, y+h/2, 0.740, y+h/2)

    # Ordering comparison
    section(ax, 0.550, "CAUSAL ORDERING MANIPULATION", "#89621b")
    y = 0.412
    h = 0.100
    box(
        ax, 0.025, y, 0.455, h,
        r"REGULATION-FIRST (RF): anticipatory protection"
        "\n" r"observe pre-exposure state $\rightarrow$ update $\mu^+$ $\rightarrow$ stabilize"
        "\n" r"$\eta_{\mathrm{eff}}=\eta(1-\mu^+)$ $\rightarrow$ attenuated exposure",
        RF, fontsize=10.0, weight="bold"
    )
    box(
        ax, 0.520, y, 0.455, h,
        r"DISTURBANCE-FIRST (DF): reactive recovery"
        "\n" r"incoming disturbance $\eta$ $\rightarrow$ observe disturbed state"
        "\n" r"update $\mu^+$ $\rightarrow$ stabilize after exposure",
        DF, fontsize=10.0, weight="bold"
    )

    # Shared exact evolution
    y2 = 0.307
    box(
        ax, 0.276, y2, 0.448, 0.066,
        r"Both paths: exact coherent evolution   "
        r"$\psi(t+\Delta t)=e^{-iH\Delta t}\psi_{\mathrm{nc}}(t)$",
        CONTROL, fontsize=11.0, weight="bold"
    )
    arrow(ax, 0.255, y, 0.385, y2 + 0.066)
    arrow(ax, 0.745, y, 0.615, y2 + 0.066)

    # Paper 2 analysis row
    section(ax, 0.258, "RECORDED OUTPUTS AND PAPER 2 ANALYSIS", "#625889")
    y = 0.160
    h = 0.072
    bottom = [(0.025, 0.245), (0.300, 0.292), (0.623, 0.352)]
    bottom_text = [
        r"Continuous target ramp" + "\n" r"retain state and controller history",
        "30 matched-seed RF/DF pairs\nbranch binning and 95% CIs",
        r"Hysteresis observables:" + "\n"
        r"$\langle\mu\rangle(S^*)$ regulatory burden;  "
        r"$\langle\Delta C\rangle(S^*)$ state response"
    ]
    for (x, w), t, f in zip(bottom, bottom_text, [METRIC, METRIC, OUTPUT]):
        box(ax, x, y, w, h, t, f, fontsize=9.8)
    arrow(ax, 0.270, y+h/2, 0.295, y+h/2)
    arrow(ax, 0.592, y+h/2, 0.618, y+h/2)

    # Bottom research question
    box(
        ax, 0.045, 0.055, 0.910, 0.052,
        "Question: does anticipatory regulation reduce adaptive control demand "
        "under continuous history-dependent target modulation?",
        "#f6f8fc", fontsize=11.0
    )

    png = out_dir / "paper2_causal_loop_diagram.png"
    pdf = out_dir / "paper2_causal_loop_diagram.pdf"
    fig.savefig(png, dpi=300, bbox_inches="tight")
    fig.savefig(pdf, bbox_inches="tight")
    plt.close(fig)
    print(f"Wrote {png}")
    print(f"Wrote {pdf}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--out-dir",
        type=Path,
        default=Path("analysis/results/paper2/figures"),
        help="Directory receiving paper2_causal_loop_diagram.png and .pdf"
    )
    args = parser.parse_args()
    build_figure(args.out_dir)


if __name__ == "__main__":
    main()
