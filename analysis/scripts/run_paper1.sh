#!/usr/bin/env bash
set -euo pipefail

# Run from the Maven/NetBeans project root.
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$PROJECT_ROOT"

MODE="${1:-publication}"
MAIN="org.iram.omega.iram_omega_q.simulation.RunFromConfig"
JAR="target/IRAM-Omega-Q-1.0-SNAPSHOT.jar"


# Prefer the project-local plotting virtual environment when present.
# This avoids using Homebrew's externally managed system Python.
if [[ -n "${PYTHON_BIN:-}" ]]; then
  PYTHON="$PYTHON_BIN"
elif [[ -x "$PROJECT_ROOT/.venv/bin/python" ]]; then
  PYTHON="$PROJECT_ROOT/.venv/bin/python"
else
  PYTHON="python3"
fi

plot_with_python () {
  if ! "$PYTHON" -c 'import numpy, matplotlib' >/dev/null 2>&1; then
    echo "Plotting dependencies are not available for: $PYTHON" >&2
    echo "Create/install the project virtual environment with:" >&2
    echo "  python3 -m venv .venv" >&2
    echo "  .venv/bin/python -m pip install matplotlib numpy" >&2
    exit 1
  fi
  "$PYTHON" "$@"
}

echo "Building IRAM-Omega-Q..."
mvn -q -DskipTests package

run_cfg () {
  echo "Running $1"
  java -cp "$JAR" "$MAIN" "$1"
}

run_cfg analysis/paper1/config/paper1_rf_averaged.properties
run_cfg analysis/paper1/config/paper1_df_averaged.properties

if [[ "$MODE" == "quick" ]]; then
  run_cfg analysis/paper1/config/paper1_rf_phase_quick.properties
  run_cfg analysis/paper1/config/paper1_df_phase_quick.properties
  plot_with_python analysis/scripts/plot_paper1.py quick
else
  run_cfg analysis/paper1/config/paper1_rf_phase.properties
  run_cfg analysis/paper1/config/paper1_df_phase.properties
  plot_with_python analysis/scripts/plot_paper1.py publication
fi

echo "Paper 1 analysis complete. Results are under analysis/results/paper1."
