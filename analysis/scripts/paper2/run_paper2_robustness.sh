#!/usr/bin/env bash
set -euo pipefail

# Paper 2 local parameter-neighborhood robustness study
#
# Purpose:
#   Test whether continuous-ramp hysteresis and the RF/DF regulatory-burden
#   effect persist across nearby incoming disturbance amplitudes eta and
#   initial regulation gains mu0.
#
# Parameter grid:
#   eta in {0.08, 0.13, 0.18}
#   mu0 in {0.04, 0.08, 0.12}
#
# Protocol held fixed:
#   triangular target-entropy ramp S* in [0.15, 0.45]
#   HYSTERESIS mode, same Hamiltonian/controller/ramp settings as the existing
#   Paper 2 RF/DF hysteresis template files.
#
# Usage from the Maven project root:
#   bash analysis/scripts/run_paper2_robustness.sh publication
#   bash analysis/scripts/run_paper2_robustness.sh quick
#   bash analysis/scripts/run_paper2_robustness.sh plot-only
#
# publication : 30 matched-seed RF/DF replicate pairs per grid point
#               (3 x 3 x 2 x 30 = 540 runs)
# quick       : 3 matched-seed RF/DF replicate pairs per grid point
#               (for checking that generation and plotting work)
# plot-only   : do not rerun simulations; replot existing output CSV files.

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$PROJECT_ROOT"

MAIN="org.iram.omega.iram_omega_q.simulation.RunFromConfig"
JAR="target/IRAM-Omega-Q-1.0-SNAPSHOT.jar"

RF_TEMPLATE="analysis/paper2/config/paper2_rf_hysteresis.properties"
DF_TEMPLATE="analysis/paper2/config/paper2_df_hysteresis.properties"
GENERATED_CFG_ROOT="analysis/paper2/generated_robustness_hysteresis"
PLOTTER="analysis/scripts/plot_paper2_robustness.py"

ETA_VALUES=("0.08" "0.13" "0.18")
MU0_VALUES=("0.04" "0.08" "0.12")

if [[ -n "${PYTHON_BIN:-}" ]]; then
  PYTHON="$PYTHON_BIN"
elif [[ -x "$PROJECT_ROOT/.venv/bin/python" ]]; then
  PYTHON="$PROJECT_ROOT/.venv/bin/python"
else
  PYTHON="python3"
fi

require_file () {
  if [[ ! -f "$1" ]]; then
    echo "Required file not found: $1" >&2
    exit 1
  fi
}

plot_results () {
  require_file "$PLOTTER"
  if ! "$PYTHON" -c 'import numpy, matplotlib' >/dev/null 2>&1; then
    echo "Plotting dependencies are not available for: $PYTHON" >&2
    echo "Create the project plotting environment with:" >&2
    echo "  python3 -m venv .venv" >&2
    echo "  .venv/bin/python -m pip install numpy matplotlib" >&2
    exit 1
  fi
  "$PYTHON" "$PLOTTER"
}

tag_value () {
  # 0.08 -> 0p080 for stable, readable output folder names.
  "$PYTHON" - "$1" <<'PY'
from decimal import Decimal
import sys
x = Decimal(sys.argv[1])
print(f"{x:.3f}".replace(".", "p"))
PY
}

write_config () {
  local template="$1"
  local destination="$2"
  local seed="$3"
  local noise="$4"
  local mu="$5"
  local run_name="$6"
  local tag="$7"

  mkdir -p "$(dirname "$destination")"

  awk -v seed="$seed" -v noise="$noise" -v mu="$mu" \
      -v run_name="$run_name" -v tag="$tag" '
    BEGIN {
      seen_seed=0; seen_noise=0; seen_mu=0; seen_run=0;
      seen_tag=0; seen_mode=0;
    }
    /^[[:space:]]*seed[[:space:]]*=/ {
      print "seed=" seed; seen_seed=1; next
    }
    /^[[:space:]]*noise[[:space:]]*=/ {
      print "noise=" noise; seen_noise=1; next
    }
    /^[[:space:]]*mu[[:space:]]*=/ {
      print "mu=" mu; seen_mu=1; next
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
      if (!seen_noise) print "noise=" noise
      if (!seen_mu) print "mu=" mu
      if (!seen_run) print "runName=" run_name
      if (!seen_tag) print "experimentTag=" tag
      if (!seen_mode) print "mode=HYSTERESIS"
    }
  ' "$template" > "$destination"
}

run_config () {
  echo "Running $1"
  java -cp "$JAR" "$MAIN" "$1"
}

run_robustness () {
  local reps="$1"

  require_file "$RF_TEMPLATE"
  require_file "$DF_TEMPLATE"
  require_file "$PLOTTER"

  echo "Building IRAM-Omega-Q..."
  mvn -q -DskipTests package
  require_file "$JAR"

  mkdir -p "$GENERATED_CFG_ROOT"

  # Matched-seed design: within each eta/mu0 cell and replicate, RF and DF
  # receive the same seed. Seeds vary by cell and replicate.
  local base_seed="${PAPER2_ROBUSTNESS_BASE_SEED:-223456789}"
  local cell_stride="${PAPER2_ROBUSTNESS_CELL_STRIDE:-10000019}"
  local rep_stride="${PAPER2_ROBUSTNESS_REP_STRIDE:-100003}"

  echo "Paper 2 parameter-neighborhood robustness run"
  echo "  eta values: ${ETA_VALUES[*]}"
  echo "  mu0 values: ${MU0_VALUES[*]}"
  echo "  matched RF/DF pairs per grid cell: $reps"
  echo "  total simulations: $(( ${#ETA_VALUES[@]} * ${#MU0_VALUES[@]} * 2 * reps ))"

  local cell_index=0
  for eta in "${ETA_VALUES[@]}"; do
    local eta_tag
    eta_tag="$(tag_value "$eta")"

    for mu0 in "${MU0_VALUES[@]}"; do
      local mu_tag
      mu_tag="$(tag_value "$mu0")"
      local cell="eta_${eta_tag}_mu0_${mu_tag}"
      local cell_seed=$((base_seed + cell_stride * cell_index))

      echo "Grid cell: eta=$eta, mu0=$mu0 ($cell)"

      for ((r=0; r<reps; r++)); do
        local rep seed
        rep="$(printf "%03d" "$r")"
        seed=$((cell_seed + rep_stride * r))

        local rf_cfg="$GENERATED_CFG_ROOT/$cell/rf_rep_${rep}.properties"
        local df_cfg="$GENERATED_CFG_ROOT/$cell/df_rep_${rep}.properties"

        write_config "$RF_TEMPLATE" "$rf_cfg" "$seed" "$eta" "$mu0" \
          "robustness_hysteresis/$cell/rf/rep_${rep}" \
          "paper2_robustness_${cell}_rf_rep_${rep}"

        write_config "$DF_TEMPLATE" "$df_cfg" "$seed" "$eta" "$mu0" \
          "robustness_hysteresis/$cell/df/rep_${rep}" \
          "paper2_robustness_${cell}_df_rep_${rep}"

        run_config "$rf_cfg"
        run_config "$df_cfg"
      done

      cell_index=$((cell_index + 1))
    done
  done

  plot_results
}

MODE="${1:-publication}"

case "$MODE" in
  publication|run|full)
    run_robustness 30
    ;;
  quick)
    run_robustness 3
    ;;
  plot-only|replot)
    echo "Plotting existing Paper 2 robustness outputs without rerunning simulations..."
    plot_results
    ;;
  *)
    echo "Usage: bash analysis/scripts/run_paper2_robustness.sh [publication|quick|plot-only]" >&2
    exit 2
    ;;
esac

echo "Paper 2 robustness study complete."
echo "Figures:   analysis/results/paper2/figures/"
echo "Summaries: analysis/results/paper2/summary/"
