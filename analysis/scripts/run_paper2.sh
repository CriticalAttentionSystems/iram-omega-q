#!/usr/bin/env bash
set -euo pipefail

# Paper 2 analysis runner
#
# Modes:
#   run         Run the Paper 2 protocols and generate figures.
#   plot-only   Replot existing Paper 2 CSV outputs without rerunning simulations.
#
# This public-release runner is intentionally limited to the Paper 2 protocols
# included in the v0.1.0 repository. Replicate-ensemble and future-extension
# analyses are not included in this release.

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$PROJECT_ROOT"

MAIN="org.iram.omega.iram_omega_q.simulation.RunFromConfig"
JAR="target/IRAM-Omega-Q-1.0-SNAPSHOT.jar"

# Prefer the project-local plotting virtual environment when present.
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

require_file () {
  if [[ ! -f "$1" ]]; then
    echo "Required file not found: $1" >&2
    exit 1
  fi
}

run_cfg () {
  local cfg="$1"
  echo "Running $cfg"
  java -cp "$JAR" "$MAIN" "$cfg"
}

build_model () {
  echo "Building IRAM-Omega-Q..."
  mvn -q -DskipTests package
  require_file "$JAR"
}

run_paper2_protocols () {
  require_file "analysis/scripts/plot_paper2.py"

  require_file "analysis/paper2/config/paper2_rf_hysteresis.properties"
  require_file "analysis/paper2/config/paper2_df_hysteresis.properties"
  require_file "analysis/paper2/config/paper2_rf_dwell.properties"
  require_file "analysis/paper2/config/paper2_df_dwell.properties"
  require_file "analysis/paper2/config/paper2_rf_intervention.properties"
  require_file "analysis/paper2/config/paper2_df_intervention.properties"

  build_model

  run_cfg "analysis/paper2/config/paper2_rf_hysteresis.properties"
  run_cfg "analysis/paper2/config/paper2_df_hysteresis.properties"
  run_cfg "analysis/paper2/config/paper2_rf_dwell.properties"
  run_cfg "analysis/paper2/config/paper2_df_dwell.properties"
  run_cfg "analysis/paper2/config/paper2_rf_intervention.properties"
  run_cfg "analysis/paper2/config/paper2_df_intervention.properties"

  plot_with_python "analysis/scripts/plot_paper2.py"
}

MODE="${1:-run}"

case "$MODE" in
  run|publication)
    run_paper2_protocols
    ;;
  plot-only|replot)
    require_file "analysis/scripts/plot_paper2.py"
    echo "Replotting existing Paper 2 CSV outputs without rerunning simulations..."
    plot_with_python "analysis/scripts/plot_paper2.py"
    ;;
  *)
    echo "Usage: bash analysis/scripts/run_paper2.sh [run|plot-only]" >&2
    exit 2
    ;;
esac

echo "Paper 2 analysis complete."
echo "Figures are under:   analysis/results/paper2/figures/"
echo "Summaries are under: analysis/results/paper2/summary/"
