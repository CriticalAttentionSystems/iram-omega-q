#!/usr/bin/env bash
set -euo pipefail

# Paper 2 analysis runner
#
# Existing modes:
#   run                  Run the original Paper 2 protocols and produce the
#                        descriptive figures from plot_paper2.py.
#   plot-only            Replot existing original-protocol CSV outputs.
#
# New replicate-hysteresis modes:
#   hysteresis-ensemble [N]
#                        Run N matched-seed RF/DF continuous hysteresis pairs
#                        and generate ensemble-averaged hysteresis figures.
#                        Default N = 30.
#   hysteresis-plot-only Replot an already generated hysteresis ensemble without
#                        rerunning Java simulations.
#
# The ensemble extension does not modify the computational model. It creates
# per-replicate configuration files from the two existing HYSTERESIS templates,
# changes only seed/runName/experimentTag, and runs the same protocol repeatedly.

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
cd "$PROJECT_ROOT"

MAIN="org.iram.omega.iram_omega_q.simulation.RunFromConfig"
JAR="target/IRAM-Omega-Q-1.0-SNAPSHOT.jar"

RF_HYST_TEMPLATE="analysis/paper2/config/paper2_rf_hysteresis.properties"
DF_HYST_TEMPLATE="analysis/paper2/config/paper2_df_hysteresis.properties"
GENERATED_CFG_DIR="analysis/paper2/generated_hysteresis_ensemble"

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
  echo "Running $1"
  java -cp "$JAR" "$MAIN" "$1"
}

build_model () {
  echo "Building IRAM-Omega-Q..."
  mvn -q -DskipTests package
  require_file "$JAR"
}

# Generate one replicate configuration from an existing hysteresis template.
#
# Only these properties are changed:
#   seed          - matched RF/DF replicate seed
#   runName       - unique replicate output directory
#   experimentTag - provenance label
#   mode          - forced to HYSTERESIS defensively
#
# Model parameters and the triangular target schedule remain those specified
# by the existing RF/DF template files.
write_rep_config () {
  local template="$1"
  local destination="$2"
  local seed="$3"
  local run_name="$4"
  local tag="$5"

  mkdir -p "$(dirname "$destination")"

  awk -v seed="$seed" -v run_name="$run_name" -v tag="$tag" '
    BEGIN { seen_seed=0; seen_run=0; seen_tag=0; seen_mode=0 }
    /^[[:space:]]*seed[[:space:]]*=/ {
      print "seed=" seed; seen_seed=1; next
    }
    /^[[:space:]]*runName[[:space:]]*=/ {
      print "runName=" run_name; seen_run=1; next
    }
    /^[[:space:]]*experimentTag[[:space:]]*=/ {
      print "experimentTag=" tag; seen_tag=1; next
    }
    /^[[:space:]]*mode[[:space:]]*=/ {
      print "mode=HYSTERESIS"; seen_mode=1; next
    }
    { print }
    END {
      if (!seen_seed) print "seed=" seed
      if (!seen_run) print "runName=" run_name
      if (!seen_tag) print "experimentTag=" tag
      if (!seen_mode) print "mode=HYSTERESIS"
    }
  ' "$template" > "$destination"
}

run_original_protocols () {
  require_file "analysis/scripts/paper2/plot_paper2.py"
  build_model

  run_cfg analysis/paper2/config/paper2_rf_hysteresis.properties
  run_cfg analysis/paper2/config/paper2_df_hysteresis.properties
  run_cfg analysis/paper2/config/paper2_rf_dwell.properties
  run_cfg analysis/paper2/config/paper2_df_dwell.properties
  run_cfg analysis/paper2/config/paper2_rf_intervention.properties
  run_cfg analysis/paper2/config/paper2_df_intervention.properties

  plot_with_python analysis/scripts/paper2/plot_paper2.py
}

run_hysteresis_ensemble () {
  local reps="${1:-30}"
  if ! [[ "$reps" =~ ^[1-9][0-9]*$ ]]; then
    echo "Number of ensemble replicates must be a positive integer; got: $reps" >&2
    exit 2
  fi

  require_file "$RF_HYST_TEMPLATE"
  require_file "$DF_HYST_TEMPLATE"
  require_file "analysis/scripts/paper2/plot_paper2_hysteresis_ensemble.py"
  build_model

  mkdir -p "$GENERATED_CFG_DIR"

  # Matched-seed rule:
  # RF and DF use exactly the same per-replicate seed. This makes ordering the
  # intended causal comparison while retaining stochastic replicate variation.
  local base_seed="${PAPER2_ENSEMBLE_BASE_SEED:-123456789}"
  local stride="${PAPER2_ENSEMBLE_SEED_STRIDE:-1000003}"

  echo "Running matched-seed hysteresis ensemble: $reps RF/DF pairs"
  echo "Base seed: $base_seed ; seed stride: $stride"

  for ((r=0; r<reps; r++)); do
    local rep
    rep=$(printf "%03d" "$r")
    local seed=$((base_seed + stride * r))

    local rf_cfg="$GENERATED_CFG_DIR/paper2_rf_hysteresis_rep_${rep}.properties"
    local df_cfg="$GENERATED_CFG_DIR/paper2_df_hysteresis_rep_${rep}.properties"

    write_rep_config \
      "$RF_HYST_TEMPLATE" "$rf_cfg" "$seed" \
      "rf_hysteresis_ensemble/rep_${rep}" \
      "paper2_rf_hysteresis_ensemble_rep_${rep}"

    write_rep_config \
      "$DF_HYST_TEMPLATE" "$df_cfg" "$seed" \
      "df_hysteresis_ensemble/rep_${rep}" \
      "paper2_df_hysteresis_ensemble_rep_${rep}"

    echo "Replicate $rep / seed $seed"
    run_cfg "$rf_cfg"
    run_cfg "$df_cfg"
  done

  plot_with_python analysis/scripts/paper2/plot_paper2_hysteresis_ensemble.py
}

MODE="${1:-run}"

case "$MODE" in
  run|publication)
    run_original_protocols
    ;;
  plot-only|replot)
    require_file "analysis/scripts/paper2/plot_paper2.py"
    echo "Replotting existing Paper 2 descriptive CSV outputs without rerunning simulations..."
    plot_with_python analysis/scripts/paper2/plot_paper2.py
    ;;
  hysteresis-ensemble)
    run_hysteresis_ensemble "${2:-30}"
    ;;
  hysteresis-plot-only)
    require_file "analysis/scripts/paper2/plot_paper2_hysteresis_ensemble.py"
    echo "Replotting existing replicate-averaged hysteresis outputs without rerunning simulations..."
    plot_with_python analysis/scripts/paper2/plot_paper2_hysteresis_ensemble.py
    ;;
  *)
    echo "Usage: bash analysis/scripts/run_paper2.sh [run|plot-only|hysteresis-ensemble [N]|hysteresis-plot-only]" >&2
    exit 2
    ;;
esac

echo "Paper 2 analysis complete."
echo "Figures are under:   analysis/results/paper2/figures/"
echo "Summaries are under: analysis/results/paper2/summary/"
