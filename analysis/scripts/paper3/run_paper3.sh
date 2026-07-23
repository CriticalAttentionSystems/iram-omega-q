#!/usr/bin/env bash
set -euo pipefail

# Paper 3 analysis runner.
#
# Usage:
#   bash analysis/scripts/paper3/run_paper3.sh
#   bash analysis/scripts/paper3/run_paper3.sh --smoke
#   bash analysis/scripts/paper3/run_paper3.sh --plots-only
#   bash analysis/scripts/paper3/run_paper3.sh --full
#
# Modes:
#   smoke/default:
#     - check Python syntax
#     - remake periodic CI plot if the switching results exist
#     - generate robustness configs/manifests
#     - do NOT run robustness simulations
#     - do NOT attempt robustness plots, because they require completed outputs
#
#   plots-only:
#     - check Python syntax
#     - remake periodic CI plot if the switching results exist
#     - remake robustness plots from existing completed outputs
#     - do NOT regenerate configs
#     - do NOT run simulations
#
#   full:
#     - check Python syntax
#     - remake periodic CI plot if the switching results exist
#     - generate robustness configs/manifests
#     - run full robustness simulations
#     - remake robustness plots
#
# WARNING:
#   --full can require a very large amount of CPU time.

MODE="smoke"

case "${1:-}" in
  "")
    MODE="smoke"
    ;;
  "--smoke")
    MODE="smoke"
    ;;
  "--plots-only")
    MODE="plots-only"
    ;;
  "--full")
    MODE="full"
    ;;
  *)
    echo "Unknown option: ${1:-}"
    echo
    echo "Usage:"
    echo "  bash analysis/scripts/paper3/run_paper3.sh"
    echo "  bash analysis/scripts/paper3/run_paper3.sh --smoke"
    echo "  bash analysis/scripts/paper3/run_paper3.sh --plots-only"
    echo "  bash analysis/scripts/paper3/run_paper3.sh --full"
    exit 1
    ;;
esac

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"

PAPER3_DIR="${REPO_ROOT}/analysis/scripts/paper3"
RESULTS_DIR="${REPO_ROOT}/analysis/results/paper3"

SWITCHING_RESULTS="${RESULTS_DIR}/rf_df_switching_long_production_diagnostics_1000runs"
SWITCHING_FIGURES="${RESULTS_DIR}/switching_figures_1000runs"

CONFIG_ROOT="${REPO_ROOT}/analysis/paper3/config"
ROBUSTNESS_CONFIG_ROOT="${CONFIG_ROOT}/robustness"
ROBUSTNESS_RESULTS_ROOT="${RESULTS_DIR}/robustness"
ROBUSTNESS_FIGURES="${ROBUSTNESS_RESULTS_ROOT}/figures"
ROBUSTNESS_BASE_CONFIG="${CONFIG_ROOT}/paper3_robustness_base.properties"

cd "${REPO_ROOT}"

echo "============================================================"
echo "IRAM-Ω-Q Paper 3 runner"
echo "Mode: ${MODE}"
echo "Repository root: ${REPO_ROOT}"
echo "Paper 3 scripts: ${PAPER3_DIR}"
echo "Paper 3 results: ${RESULTS_DIR}"
echo "============================================================"
echo

echo "[1/6] Checking Python syntax..."
python3 -m compileall "${PAPER3_DIR}"
echo

echo "[2/6] Plotting periodic metrics with CI95..."
if [[ -d "${SWITCHING_RESULTS}" ]]; then
  mkdir -p "${SWITCHING_FIGURES}"
  python3 "${PAPER3_DIR}/plot_periodic_metrics_ci95.py" \
    --results "${SWITCHING_RESULTS}" \
    --out "${SWITCHING_FIGURES}"
else
  echo "SKIP: missing switching results directory:"
  echo "  ${SWITCHING_RESULTS}"
fi
echo

echo "[3/6] Robustness config generation..."
if [[ "${MODE}" == "plots-only" ]]; then
  echo "Skipping config generation in plots-only mode."
else
  if [[ -f "${ROBUSTNESS_BASE_CONFIG}" ]]; then
    python3 "${PAPER3_DIR}/generate_robustness_configs.py" \
      --base-config "${ROBUSTNESS_BASE_CONFIG}" \
      --config-root "${ROBUSTNESS_CONFIG_ROOT}" \
      --results-root "${ROBUSTNESS_RESULTS_ROOT}" \
      --runs 200
  else
    echo "ERROR: missing robustness base config:"
    echo "  ${ROBUSTNESS_BASE_CONFIG}"
    echo
    echo "This file is required for smoke and full modes."
    exit 1
  fi
fi
echo

echo "[4/6] Robustness simulations..."
if [[ "${MODE}" == "full" ]]; then
  echo "FULL MODE ENABLED."
  echo "This will run the full Paper 3 robustness simulations."

  if [[ -f "${ROBUSTNESS_CONFIG_ROOT}/run_phase_scan.sh" ]]; then
    bash "${ROBUSTNESS_CONFIG_ROOT}/run_phase_scan.sh"
  else
    echo "ERROR: missing launch script:"
    echo "  ${ROBUSTNESS_CONFIG_ROOT}/run_phase_scan.sh"
    exit 1
  fi

  if [[ -f "${ROBUSTNESS_CONFIG_ROOT}/run_all_robustness.sh" ]]; then
    bash "${ROBUSTNESS_CONFIG_ROOT}/run_all_robustness.sh"
  else
    echo "ERROR: missing launch script:"
    echo "  ${ROBUSTNESS_CONFIG_ROOT}/run_all_robustness.sh"
    exit 1
  fi
else
  echo "Not running full robustness simulations in ${MODE} mode."
  echo "To regenerate the full analysis, run:"
  echo "  bash analysis/scripts/paper3/run_paper3.sh --full"
fi
echo

echo "[5/6] Plotting robustness figures..."
mkdir -p "${ROBUSTNESS_FIGURES}"

if [[ "${MODE}" == "smoke" ]]; then
  echo "Skipping robustness plots in smoke mode."
  echo "Those require completed robustness outputs under:"
  echo "  ${ROBUSTNESS_RESULTS_ROOT}/phase_runs/"
  echo "  ${ROBUSTNESS_RESULTS_ROOT}/target_runs/"
  echo
  echo "To remake robustness plots from completed outputs, run:"
  echo "  bash analysis/scripts/paper3/run_paper3.sh --plots-only"
  echo
  echo "To regenerate full robustness outputs and plots, run:"
  echo "  bash analysis/scripts/paper3/run_paper3.sh --full"
else
  if [[ -f "${ROBUSTNESS_CONFIG_ROOT}/phase_manifest.csv" ]]; then
    python3 "${PAPER3_DIR}/plot_phase_robustness.py" \
      --manifest "${ROBUSTNESS_CONFIG_ROOT}/phase_manifest.csv" \
      --out "${ROBUSTNESS_FIGURES}"
    echo "OK: phase robustness plot"
  else
    echo "ERROR: missing phase manifest:"
    echo "  ${ROBUSTNESS_CONFIG_ROOT}/phase_manifest.csv"
    exit 1
  fi

  if [[ -f "${ROBUSTNESS_CONFIG_ROOT}/target_manifest.csv" ]]; then
    python3 "${PAPER3_DIR}/plot_target_sensitivity.py" \
      --manifest "${ROBUSTNESS_CONFIG_ROOT}/target_manifest.csv" \
      --out "${ROBUSTNESS_FIGURES}"
    echo "OK: target sensitivity plot"
  else
    echo "ERROR: missing target manifest:"
    echo "  ${ROBUSTNESS_CONFIG_ROOT}/target_manifest.csv"
    exit 1
  fi
fi
echo

echo "[6/6] Done."
echo "Paper 3 runner complete."
echo
echo "Modes:"
echo "  smoke/default : safe check, periodic replotting, config generation"
echo "  plots-only    : remake plots from existing completed outputs"
echo "  full          : regenerate full Paper 3 robustness analysis"
