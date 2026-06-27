#!/usr/bin/env python3
"""Generate Paper 1 figures from IRAM-Omega-Q batch CSV output."""
from pathlib import Path
import sys
import numpy as np
import matplotlib.pyplot as plt

ROOT = Path(__file__).resolve().parents[1] / "results" / "paper1"
MODE = sys.argv[1].strip().lower() if len(sys.argv) > 1 else "publication"
SUFFIX = "_phase_quick" if MODE == "quick" else "_phase"
FIG = ROOT / ("figures_quick" if MODE == "quick" else "figures")
FIG.mkdir(parents=True, exist_ok=True)

def series(folder: str, filename: str):
    return np.genfromtxt(ROOT / folder / filename, delimiter=",", names=True)

def vector(folder: str, filename: str):
    return np.loadtxt(ROOT / folder / filename, delimiter=",")

def matrix(folder: str, filename: str):
    return np.loadtxt(ROOT / folder / filename, delimiter=",")

rf_mu = series("rf_averaged", "mu_ci.csv")
df_mu = series("df_averaged", "mu_ci.csv")
rf_dc = series("rf_averaged", "coherence_ci.csv")
df_dc = series("df_averaged", "coherence_ci.csv")

plt.figure(figsize=(8.0, 4.6))
for data, label in [(rf_mu, "Regulation first"), (df_mu, "Disturbance first")]:
    mean = data["mu_mean"]
    std = data["mu_std"]
    plt.plot(data["t"], mean, label=label)
    plt.fill_between(data["t"], mean - std, mean + std, alpha=0.20)
plt.xlabel("Time")
plt.ylabel(r"Regulation gain $\mu(t)$")
plt.title("Matched-seed averaged regulation trajectories")
plt.legend()
plt.tight_layout()
plt.savefig(FIG / "paper1_mu_RF_DF.png", dpi=300)
plt.close()

plt.figure(figsize=(8.0, 4.6))
for data, label in [(rf_dc, "Regulation first"), (df_dc, "Disturbance first")]:
    mean = data["dC_mean"]
    std = data["dC_std"]
    plt.plot(data["t"], mean, label=label)
    plt.fill_between(data["t"], mean - std, mean + std, alpha=0.20)
plt.xlabel("Time")
plt.ylabel(r"Coherence gap $\Delta C(t)$")
plt.title("Matched-seed averaged coherence-gap trajectories")
plt.legend()
plt.tight_layout()
plt.savefig(FIG / "paper1_coherence_RF_DF.png", dpi=300)
plt.close()

# Use one shared susceptibility color scale for RF and DF maps so that
# identical colors represent identical chi values in both panels.
rf_chi = matrix("rf" + SUFFIX, "susceptibility.csv")
df_chi = matrix("df" + SUFFIX, "susceptibility.csv")

chi_vmin = min(np.nanmin(rf_chi), np.nanmin(df_chi))
chi_vmax = max(np.nanmax(rf_chi), np.nanmax(df_chi))

for tag, label in [("rf", "Regulation first"), ("df", "Disturbance first")]:
    folder = tag + SUFFIX
    mu = vector(folder, "mu_grid.csv")
    eta = vector(folder, "noise_grid.csv")
    chi = matrix(folder, "susceptibility.csv")
    muc = vector(folder, "muCritical.csv")

    plt.figure(figsize=(7.2, 5.0))
    extent = [eta.min(), eta.max(), mu.min(), mu.max()]
    plt.imshow(chi, origin="lower", aspect="auto", extent=extent, vmin=chi_vmin, vmax=chi_vmax)
    cbar = plt.colorbar()
    cbar.set_label(r"$\chi = \langle \mathrm{Var}_{t}[\Delta C(t)] \rangle_{\mathrm{runs}}$")
    plt.plot(eta, muc, linewidth=2.0, label=r"$\hat{\mu}_{0,c}(\eta)$")
    plt.xlabel(r"Incoming disturbance amplitude $\eta$")
    plt.ylabel(r"Initial regulation gain $\mu_0$")
    plt.title(f"Susceptibility map: {label}")
    plt.legend()
    plt.tight_layout()
    plt.savefig(FIG / f"paper1_susceptibility_{tag}.png", dpi=300)
    plt.close()

rf_eta = vector("rf" + SUFFIX, "noise_grid.csv")
df_eta = vector("df" + SUFFIX, "noise_grid.csv")
rf_muc = vector("rf" + SUFFIX, "muCritical.csv")
df_muc = vector("df" + SUFFIX, "muCritical.csv")

plt.figure(figsize=(7.0, 4.6))
plt.plot(rf_eta, rf_muc, marker="o", markersize=3, label="Regulation first")
plt.plot(df_eta, df_muc, marker="o", markersize=3, label="Disturbance first")
plt.xlabel(r"Incoming disturbance amplitude $\eta$")
plt.ylabel(r"Critical initial regulation gain $\hat{\mu}_{0,c}(\eta)$")
plt.title("Critical-curve comparison")
plt.legend()
plt.tight_layout()
plt.savefig(FIG / "paper1_critical_curves_RF_DF.png", dpi=300)
plt.close()

print(f"Wrote Paper 1 figures to {FIG}")
