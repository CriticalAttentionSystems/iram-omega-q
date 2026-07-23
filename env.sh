#!/usr/bin/env bash
# IRAM-Ω-Q analysis environment setup.
#
# Usage from the project root:
#   source env.sh
#
# Or from anywhere inside the repo:
#   source /path/to/IRAM-Omega-Q/env.sh

# Stop only this setup script on errors, not the user's shell.
set -e

# Resolve the directory containing this env.sh file.
# Works when sourced from bash or zsh.
if [[ -n "${BASH_SOURCE[0]:-}" ]]; then
  # bash
  _ENV_FILE="${BASH_SOURCE[0]}"
elif [[ -n "${ZSH_VERSION:-}" ]]; then
  # zsh
  _ENV_FILE="${(%):-%N}"
else
  echo "ERROR: env.sh must be sourced from bash or zsh."
  return 1 2>/dev/null || true
fi

IRAM_OMEGA_Q_ROOT="$(cd "$(dirname "${_ENV_FILE}")" && pwd)"
unset _ENV_FILE
export IRAM_OMEGA_Q_ROOT

# Standard project paths.
export IRAM_OMEGA_Q_ANALYSIS="${IRAM_OMEGA_Q_ROOT}/analysis"
export IRAM_OMEGA_Q_SCRIPTS="${IRAM_OMEGA_Q_ROOT}/analysis/scripts"
export IRAM_OMEGA_Q_RESULTS="${IRAM_OMEGA_Q_ROOT}/analysis/results"
export IRAM_OMEGA_Q_CONFIGS="${IRAM_OMEGA_Q_ROOT}/configs"

# Paper-specific paths.
export PAPER1_SCRIPTS="${IRAM_OMEGA_Q_SCRIPTS}/paper1"
export PAPER2_SCRIPTS="${IRAM_OMEGA_Q_SCRIPTS}/paper2"
export PAPER3_SCRIPTS="${IRAM_OMEGA_Q_SCRIPTS}/paper3"
export PAPER4_SCRIPTS="${IRAM_OMEGA_Q_SCRIPTS}/paper4"

export PAPER1_RESULTS="${IRAM_OMEGA_Q_RESULTS}/paper1"
export PAPER2_RESULTS="${IRAM_OMEGA_Q_RESULTS}/paper2"
export PAPER3_RESULTS="${IRAM_OMEGA_Q_RESULTS}/paper3"
export PAPER4_RESULTS="${IRAM_OMEGA_Q_RESULTS}/paper4"

# Prefer the local virtual environment if present.
if [[ -d "${IRAM_OMEGA_Q_ROOT}/.venv" ]]; then
  # shellcheck disable=SC1091
  source "${IRAM_OMEGA_Q_ROOT}/.venv/bin/activate"
else
  echo "WARNING: No .venv found at ${IRAM_OMEGA_Q_ROOT}/.venv"
  echo "Create one with:"
  echo "  python3 -m venv .venv"
  echo "  source .venv/bin/activate"
  echo "  python -m pip install --upgrade pip"
  echo "  pip install -r requirements-analysis.txt"
fi

# Make local Python imports work from any directory.
export PYTHONPATH="${IRAM_OMEGA_Q_ROOT}:${PYTHONPATH:-}"

# Make project scripts easier to call.
export PATH="${IRAM_OMEGA_Q_SCRIPTS}/paper1:${IRAM_OMEGA_Q_SCRIPTS}/paper2:${IRAM_OMEGA_Q_SCRIPTS}/paper3:${IRAM_OMEGA_Q_SCRIPTS}/paper4:${PATH}"

# Ensure result directories exist locally.
mkdir -p \
  "${PAPER1_RESULTS}" \
  "${PAPER2_RESULTS}" \
  "${PAPER3_RESULTS}" \
  "${PAPER4_RESULTS}"

echo "IRAM-Ω-Q environment set."
echo "Project root: ${IRAM_OMEGA_Q_ROOT}"
echo "Python: $(command -v python3)"
