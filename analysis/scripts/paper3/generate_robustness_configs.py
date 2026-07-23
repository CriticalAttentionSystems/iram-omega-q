#!/usr/bin/env python3
"""Generate Paper 3 bounded-domain robustness scan configurations.

This script uses the existing SWITCHING_STUDY Java runner. No further Java
changes are required. It creates:
  - a 5 x 5 scan in initial regulation mu and disturbance noise eta at S*=0.30;
  - a 5-point sensitivity scan in target uncertainty S* at mu=0.05, eta=0.17;
  - manifests and bash launch scripts.

Usage from the IRAM-Omega-Q project root:

python analysis/scripts/generate_paper3_robustness_configs.py \
  --base-config analysis/paper3/config/paper3_robustness_base.properties \
  --config-root analysis/paper3/config/robustness \
  --results-root analysis/results/paper3/robustness \
  --runs 200

bash analysis/paper3/config/robustness/run_phase_scan.sh
bash analysis/paper3/config/robustness/run_target_scan.sh
"""
from __future__ import annotations

import argparse
import csv
from itertools import product
from pathlib import Path
import shlex


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--base-config", type=Path, required=True)
    p.add_argument("--config-root", type=Path,
                   default=Path("analysis/paper3/config/robustness"))
    p.add_argument("--results-root", type=Path,
                   default=Path("analysis/results/paper3/robustness"))
    p.add_argument("--runs", type=int, default=200)
    p.add_argument("--steps", type=int, default=200000)
    p.add_argument("--burn-in", type=int, default=40000)
    p.add_argument("--mu-grid", default="0.03,0.04,0.05,0.06,0.07")
    p.add_argument("--noise-grid", default="0.13,0.15,0.17,0.19,0.21")
    p.add_argument("--target-grid", default="0.20,0.25,0.30,0.35,0.40")
    p.add_argument("--reference-mu", type=float, default=0.05)
    p.add_argument("--reference-noise", type=float, default=0.17)
    p.add_argument("--reference-target", type=float, default=0.30)
    return p.parse_args()


def read_properties(path: Path) -> dict[str, str]:
    if not path.exists():
        raise FileNotFoundError(f"Base configuration not found: {path}")
    values: dict[str, str] = {}
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip()
    return values


def floats(text: str) -> list[float]:
    return [float(item.strip()) for item in text.split(",") if item.strip()]


def token(prefix: str, value: float) -> str:
    return f"{prefix}{value:.3f}".replace(".", "p").replace("-", "m")


def write_properties(path: Path, base: dict[str, str], overrides: dict[str, str]) -> None:
    props = dict(base)
    props.update(overrides)
    ordered = [
        "mode", "outDir", "runName", "experimentTag",
        "steps", "dt", "dim", "seed", "baseSeed",
        "focusIndex", "salienceCenter", "salienceWidth", "phaseNoise",
        "energyScale", "coupling", "locality",
        "mu", "noise", "targetEntropy",
        "muDerivativeGain", "muTargetGain", "muMin", "muMax", "ordering",
        "switchingRuns", "switchingBurnInSamples", "switchingBaseSeed",
        "periodicDwellGrid", "markovPairGrid",
        "switchingDiagnosticMaxLagSteps", "switchingDiagnosticStrideSteps",
    ]
    lines = [
        "# Auto-generated Paper 3 bounded-domain robustness configuration",
        "# Conditions retained for compact scan: periodic L=500,1000; Markov p=0.002,0.001",
        "",
    ]
    written: set[str] = set()
    for key in ordered:
        if key in props:
            lines.append(f"{key}={props[key]}")
            written.add(key)
    for key in sorted(set(props) - written):
        lines.append(f"{key}={props[key]}")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def write_runner(path: Path, configs: list[Path], log_dir: Path) -> None:
    log_dir.mkdir(parents=True, exist_ok=True)
    lines = [
        "#!/usr/bin/env bash",
        "set -euo pipefail",
        "",
        'echo "Running Paper 3 robustness scan from: $(pwd)"',
        f"mkdir -p {shlex.quote(str(log_dir))}",
        "",
    ]
    for config in configs:
        name = config.stem
        lines.extend([
            f'echo "=== Running {name} ==="',
            "caffeinate -i mvn -q -DskipTests exec:java \\",
            "  -Dexec.mainClass=org.iram.omega.iram_omega_q.simulation.RunFromConfig \\",
            f"  -Dexec.args={shlex.quote(str(config))} \\",
            f"  | tee {shlex.quote(str(log_dir / (name + '.log')))}",
            "",
        ])
    path.write_text("\n".join(lines), encoding="utf-8")
    path.chmod(0o755)


def main() -> None:
    a = parse_args()
    base = read_properties(a.base_config)
    mus, noises, targets = floats(a.mu_grid), floats(a.noise_grid), floats(a.target_grid)

    common = {
        "mode": "SWITCHING_STUDY",
        "steps": str(a.steps),
        "switchingRuns": str(a.runs),
        "switchingBurnInSamples": str(a.burn_in),
        "periodicDwellGrid": "500,1000",
        "markovPairGrid": "0.002:0.002,0.001:0.001",
        "switchingDiagnosticMaxLagSteps": base.get("switchingDiagnosticMaxLagSteps", "2000"),
        "switchingDiagnosticStrideSteps": base.get("switchingDiagnosticStrideSteps", "10"),
    }

    phase_configs: list[Path] = []
    phase_results = a.results_root / "phase_runs"
    phase_manifest = a.config_root / "phase_manifest.csv"
    phase_manifest.parent.mkdir(parents=True, exist_ok=True)
    with phase_manifest.open("w", newline="", encoding="utf-8") as stream:
        writer = csv.writer(stream)
        writer.writerow(["scan", "mu", "noise", "targetEntropy", "config_path", "output_dir"])
        for mu, noise in product(mus, noises):
            name = f"phase_{token('mu', mu)}_{token('eta', noise)}"
            config = a.config_root / "phase" / f"{name}.properties"
            overrides = dict(common)
            overrides.update({
                "outDir": str(phase_results),
                "runName": name,
                "experimentTag": f"paper3_robustness_{name}",
                "mu": f"{mu:.8g}",
                "noise": f"{noise:.8g}",
                "targetEntropy": f"{a.reference_target:.8g}",
            })
            write_properties(config, base, overrides)
            phase_configs.append(config)
            writer.writerow(["phase", mu, noise, a.reference_target, config, phase_results / name])

    target_configs: list[Path] = []
    target_results = a.results_root / "target_runs"
    target_manifest = a.config_root / "target_manifest.csv"
    with target_manifest.open("w", newline="", encoding="utf-8") as stream:
        writer = csv.writer(stream)
        writer.writerow(["scan", "mu", "noise", "targetEntropy", "config_path", "output_dir"])
        for target in targets:
            name = f"target_{token('S', target)}"
            config = a.config_root / "target" / f"{name}.properties"
            overrides = dict(common)
            overrides.update({
                "outDir": str(target_results),
                "runName": name,
                "experimentTag": f"paper3_robustness_{name}",
                "mu": f"{a.reference_mu:.8g}",
                "noise": f"{a.reference_noise:.8g}",
                "targetEntropy": f"{target:.8g}",
            })
            write_properties(config, base, overrides)
            target_configs.append(config)
            writer.writerow(["target", a.reference_mu, a.reference_noise, target, config, target_results / name])

    write_runner(a.config_root / "run_phase_scan.sh", phase_configs, a.results_root / "logs" / "phase")
    write_runner(a.config_root / "run_target_scan.sh", target_configs, a.results_root / "logs" / "target")
    all_runner = a.config_root / "run_all_robustness.sh"
    all_runner.write_text(
        "#!/usr/bin/env bash\nset -euo pipefail\n"
        f"bash {shlex.quote(str(a.config_root / 'run_phase_scan.sh'))}\n"
        f"bash {shlex.quote(str(a.config_root / 'run_target_scan.sh'))}\n",
        encoding="utf-8",
    )
    all_runner.chmod(0o755)

    n_points = len(phase_configs) + len(target_configs)
    trajectories = n_points * a.runs * 6
    total_steps = trajectories * a.steps
    print(f"Generated {len(phase_configs)} phase-map configurations.")
    print(f"Generated {len(target_configs)} target-uncertainty configurations.")
    print(f"Manifests: {phase_manifest} and {target_manifest}")
    print(f"Planned workload: {trajectories:,} trajectories; {total_steps:,} simulation steps.")


if __name__ == "__main__":
    main()
