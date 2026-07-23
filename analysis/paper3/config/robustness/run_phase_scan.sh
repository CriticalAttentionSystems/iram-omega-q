#!/usr/bin/env bash
set -euo pipefail

echo "Running Paper 3 robustness scan from: $(pwd)"
mkdir -p /Users/veronique/BASE/Code/Repo/iram-omega-q/analysis/results/paper3/robustness/logs/phase

echo "=== Running phase_mu0p030_eta0p130 ==="
caffeinate -i mvn -q -DskipTests exec:java \
  -Dexec.mainClass=org.iram.omega.iram_omega_q.simulation.RunFromConfig \
  -Dexec.args=/Users/veronique/BASE/Code/Repo/iram-omega-q/analysis/paper3/config/robustness/phase/phase_mu0p030_eta0p130.properties \
  | tee /Users/veronique/BASE/Code/Repo/iram-omega-q/analysis/results/paper3/robustness/logs/phase/phase_mu0p030_eta0p130.log

echo "=== Running phase_mu0p030_eta0p150 ==="
caffeinate -i mvn -q -DskipTests exec:java \
  -Dexec.mainClass=org.iram.omega.iram_omega_q.simulation.RunFromConfig \
  -Dexec.args=/Users/veronique/BASE/Code/Repo/iram-omega-q/analysis/paper3/config/robustness/phase/phase_mu0p030_eta0p150.properties \
  | tee /Users/veronique/BASE/Code/Repo/iram-omega-q/analysis/results/paper3/robustness/logs/phase/phase_mu0p030_eta0p150.log

echo "=== Running phase_mu0p030_eta0p170 ==="
caffeinate -i mvn -q -DskipTests exec:java \
  -Dexec.mainClass=org.iram.omega.iram_omega_q.simulation.RunFromConfig \
  -Dexec.args=/Users/veronique/BASE/Code/Repo/iram-omega-q/analysis/paper3/config/robustness/phase/phase_mu0p030_eta0p170.properties \
  | tee /Users/veronique/BASE/Code/Repo/iram-omega-q/analysis/results/paper3/robustness/logs/phase/phase_mu0p030_eta0p170.log

echo "=== Running phase_mu0p030_eta0p190 ==="
caffeinate -i mvn -q -DskipTests exec:java \
  -Dexec.mainClass=org.iram.omega.iram_omega_q.simulation.RunFromConfig \
  -Dexec.args=/Users/veronique/BASE/Code/Repo/iram-omega-q/analysis/paper3/config/robustness/phase/phase_mu0p030_eta0p190.properties \
  | tee /Users/veronique/BASE/Code/Repo/iram-omega-q/analysis/results/paper3/robustness/logs/phase/phase_mu0p030_eta0p190.log

echo "=== Running phase_mu0p030_eta0p210 ==="
caffeinate -i mvn -q -DskipTests exec:java \
  -Dexec.mainClass=org.iram.omega.iram_omega_q.simulation.RunFromConfig \
  -Dexec.args=/Users/veronique/BASE/Code/Repo/iram-omega-q/analysis/paper3/config/robustness/phase/phase_mu0p030_eta0p210.properties \
  | tee /Users/veronique/BASE/Code/Repo/iram-omega-q/analysis/results/paper3/robustness/logs/phase/phase_mu0p030_eta0p210.log

echo "=== Running phase_mu0p040_eta0p130 ==="
caffeinate -i mvn -q -DskipTests exec:java \
  -Dexec.mainClass=org.iram.omega.iram_omega_q.simulation.RunFromConfig \
  -Dexec.args=/Users/veronique/BASE/Code/Repo/iram-omega-q/analysis/paper3/config/robustness/phase/phase_mu0p040_eta0p130.properties \
  | tee /Users/veronique/BASE/Code/Repo/iram-omega-q/analysis/results/paper3/robustness/logs/phase/phase_mu0p040_eta0p130.log

echo "=== Running phase_mu0p040_eta0p150 ==="
caffeinate -i mvn -q -DskipTests exec:java \
  -Dexec.mainClass=org.iram.omega.iram_omega_q.simulation.RunFromConfig \
  -Dexec.args=/Users/veronique/BASE/Code/Repo/iram-omega-q/analysis/paper3/config/robustness/phase/phase_mu0p040_eta0p150.properties \
  | tee /Users/veronique/BASE/Code/Repo/iram-omega-q/analysis/results/paper3/robustness/logs/phase/phase_mu0p040_eta0p150.log

echo "=== Running phase_mu0p040_eta0p170 ==="
caffeinate -i mvn -q -DskipTests exec:java \
  -Dexec.mainClass=org.iram.omega.iram_omega_q.simulation.RunFromConfig \
  -Dexec.args=/Users/veronique/BASE/Code/Repo/iram-omega-q/analysis/paper3/config/robustness/phase/phase_mu0p040_eta0p170.properties \
  | tee /Users/veronique/BASE/Code/Repo/iram-omega-q/analysis/results/paper3/robustness/logs/phase/phase_mu0p040_eta0p170.log

echo "=== Running phase_mu0p040_eta0p190 ==="
caffeinate -i mvn -q -DskipTests exec:java \
  -Dexec.mainClass=org.iram.omega.iram_omega_q.simulation.RunFromConfig \
  -Dexec.args=/Users/veronique/BASE/Code/Repo/iram-omega-q/analysis/paper3/config/robustness/phase/phase_mu0p040_eta0p190.properties \
  | tee /Users/veronique/BASE/Code/Repo/iram-omega-q/analysis/results/paper3/robustness/logs/phase/phase_mu0p040_eta0p190.log

echo "=== Running phase_mu0p040_eta0p210 ==="
caffeinate -i mvn -q -DskipTests exec:java \
  -Dexec.mainClass=org.iram.omega.iram_omega_q.simulation.RunFromConfig \
  -Dexec.args=/Users/veronique/BASE/Code/Repo/iram-omega-q/analysis/paper3/config/robustness/phase/phase_mu0p040_eta0p210.properties \
  | tee /Users/veronique/BASE/Code/Repo/iram-omega-q/analysis/results/paper3/robustness/logs/phase/phase_mu0p040_eta0p210.log

echo "=== Running phase_mu0p050_eta0p130 ==="
caffeinate -i mvn -q -DskipTests exec:java \
  -Dexec.mainClass=org.iram.omega.iram_omega_q.simulation.RunFromConfig \
  -Dexec.args=/Users/veronique/BASE/Code/Repo/iram-omega-q/analysis/paper3/config/robustness/phase/phase_mu0p050_eta0p130.properties \
  | tee /Users/veronique/BASE/Code/Repo/iram-omega-q/analysis/results/paper3/robustness/logs/phase/phase_mu0p050_eta0p130.log

echo "=== Running phase_mu0p050_eta0p150 ==="
caffeinate -i mvn -q -DskipTests exec:java \
  -Dexec.mainClass=org.iram.omega.iram_omega_q.simulation.RunFromConfig \
  -Dexec.args=/Users/veronique/BASE/Code/Repo/iram-omega-q/analysis/paper3/config/robustness/phase/phase_mu0p050_eta0p150.properties \
  | tee /Users/veronique/BASE/Code/Repo/iram-omega-q/analysis/results/paper3/robustness/logs/phase/phase_mu0p050_eta0p150.log

echo "=== Running phase_mu0p050_eta0p170 ==="
caffeinate -i mvn -q -DskipTests exec:java \
  -Dexec.mainClass=org.iram.omega.iram_omega_q.simulation.RunFromConfig \
  -Dexec.args=/Users/veronique/BASE/Code/Repo/iram-omega-q/analysis/paper3/config/robustness/phase/phase_mu0p050_eta0p170.properties \
  | tee /Users/veronique/BASE/Code/Repo/iram-omega-q/analysis/results/paper3/robustness/logs/phase/phase_mu0p050_eta0p170.log

echo "=== Running phase_mu0p050_eta0p190 ==="
caffeinate -i mvn -q -DskipTests exec:java \
  -Dexec.mainClass=org.iram.omega.iram_omega_q.simulation.RunFromConfig \
  -Dexec.args=/Users/veronique/BASE/Code/Repo/iram-omega-q/analysis/paper3/config/robustness/phase/phase_mu0p050_eta0p190.properties \
  | tee /Users/veronique/BASE/Code/Repo/iram-omega-q/analysis/results/paper3/robustness/logs/phase/phase_mu0p050_eta0p190.log

echo "=== Running phase_mu0p050_eta0p210 ==="
caffeinate -i mvn -q -DskipTests exec:java \
  -Dexec.mainClass=org.iram.omega.iram_omega_q.simulation.RunFromConfig \
  -Dexec.args=/Users/veronique/BASE/Code/Repo/iram-omega-q/analysis/paper3/config/robustness/phase/phase_mu0p050_eta0p210.properties \
  | tee /Users/veronique/BASE/Code/Repo/iram-omega-q/analysis/results/paper3/robustness/logs/phase/phase_mu0p050_eta0p210.log

echo "=== Running phase_mu0p060_eta0p130 ==="
caffeinate -i mvn -q -DskipTests exec:java \
  -Dexec.mainClass=org.iram.omega.iram_omega_q.simulation.RunFromConfig \
  -Dexec.args=/Users/veronique/BASE/Code/Repo/iram-omega-q/analysis/paper3/config/robustness/phase/phase_mu0p060_eta0p130.properties \
  | tee /Users/veronique/BASE/Code/Repo/iram-omega-q/analysis/results/paper3/robustness/logs/phase/phase_mu0p060_eta0p130.log

echo "=== Running phase_mu0p060_eta0p150 ==="
caffeinate -i mvn -q -DskipTests exec:java \
  -Dexec.mainClass=org.iram.omega.iram_omega_q.simulation.RunFromConfig \
  -Dexec.args=/Users/veronique/BASE/Code/Repo/iram-omega-q/analysis/paper3/config/robustness/phase/phase_mu0p060_eta0p150.properties \
  | tee /Users/veronique/BASE/Code/Repo/iram-omega-q/analysis/results/paper3/robustness/logs/phase/phase_mu0p060_eta0p150.log

echo "=== Running phase_mu0p060_eta0p170 ==="
caffeinate -i mvn -q -DskipTests exec:java \
  -Dexec.mainClass=org.iram.omega.iram_omega_q.simulation.RunFromConfig \
  -Dexec.args=/Users/veronique/BASE/Code/Repo/iram-omega-q/analysis/paper3/config/robustness/phase/phase_mu0p060_eta0p170.properties \
  | tee /Users/veronique/BASE/Code/Repo/iram-omega-q/analysis/results/paper3/robustness/logs/phase/phase_mu0p060_eta0p170.log

echo "=== Running phase_mu0p060_eta0p190 ==="
caffeinate -i mvn -q -DskipTests exec:java \
  -Dexec.mainClass=org.iram.omega.iram_omega_q.simulation.RunFromConfig \
  -Dexec.args=/Users/veronique/BASE/Code/Repo/iram-omega-q/analysis/paper3/config/robustness/phase/phase_mu0p060_eta0p190.properties \
  | tee /Users/veronique/BASE/Code/Repo/iram-omega-q/analysis/results/paper3/robustness/logs/phase/phase_mu0p060_eta0p190.log

echo "=== Running phase_mu0p060_eta0p210 ==="
caffeinate -i mvn -q -DskipTests exec:java \
  -Dexec.mainClass=org.iram.omega.iram_omega_q.simulation.RunFromConfig \
  -Dexec.args=/Users/veronique/BASE/Code/Repo/iram-omega-q/analysis/paper3/config/robustness/phase/phase_mu0p060_eta0p210.properties \
  | tee /Users/veronique/BASE/Code/Repo/iram-omega-q/analysis/results/paper3/robustness/logs/phase/phase_mu0p060_eta0p210.log

echo "=== Running phase_mu0p070_eta0p130 ==="
caffeinate -i mvn -q -DskipTests exec:java \
  -Dexec.mainClass=org.iram.omega.iram_omega_q.simulation.RunFromConfig \
  -Dexec.args=/Users/veronique/BASE/Code/Repo/iram-omega-q/analysis/paper3/config/robustness/phase/phase_mu0p070_eta0p130.properties \
  | tee /Users/veronique/BASE/Code/Repo/iram-omega-q/analysis/results/paper3/robustness/logs/phase/phase_mu0p070_eta0p130.log

echo "=== Running phase_mu0p070_eta0p150 ==="
caffeinate -i mvn -q -DskipTests exec:java \
  -Dexec.mainClass=org.iram.omega.iram_omega_q.simulation.RunFromConfig \
  -Dexec.args=/Users/veronique/BASE/Code/Repo/iram-omega-q/analysis/paper3/config/robustness/phase/phase_mu0p070_eta0p150.properties \
  | tee /Users/veronique/BASE/Code/Repo/iram-omega-q/analysis/results/paper3/robustness/logs/phase/phase_mu0p070_eta0p150.log

echo "=== Running phase_mu0p070_eta0p170 ==="
caffeinate -i mvn -q -DskipTests exec:java \
  -Dexec.mainClass=org.iram.omega.iram_omega_q.simulation.RunFromConfig \
  -Dexec.args=/Users/veronique/BASE/Code/Repo/iram-omega-q/analysis/paper3/config/robustness/phase/phase_mu0p070_eta0p170.properties \
  | tee /Users/veronique/BASE/Code/Repo/iram-omega-q/analysis/results/paper3/robustness/logs/phase/phase_mu0p070_eta0p170.log

echo "=== Running phase_mu0p070_eta0p190 ==="
caffeinate -i mvn -q -DskipTests exec:java \
  -Dexec.mainClass=org.iram.omega.iram_omega_q.simulation.RunFromConfig \
  -Dexec.args=/Users/veronique/BASE/Code/Repo/iram-omega-q/analysis/paper3/config/robustness/phase/phase_mu0p070_eta0p190.properties \
  | tee /Users/veronique/BASE/Code/Repo/iram-omega-q/analysis/results/paper3/robustness/logs/phase/phase_mu0p070_eta0p190.log

echo "=== Running phase_mu0p070_eta0p210 ==="
caffeinate -i mvn -q -DskipTests exec:java \
  -Dexec.mainClass=org.iram.omega.iram_omega_q.simulation.RunFromConfig \
  -Dexec.args=/Users/veronique/BASE/Code/Repo/iram-omega-q/analysis/paper3/config/robustness/phase/phase_mu0p070_eta0p210.properties \
  | tee /Users/veronique/BASE/Code/Repo/iram-omega-q/analysis/results/paper3/robustness/logs/phase/phase_mu0p070_eta0p210.log
