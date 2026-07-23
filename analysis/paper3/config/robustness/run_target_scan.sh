#!/usr/bin/env bash
set -euo pipefail

echo "Running Paper 3 robustness scan from: $(pwd)"
mkdir -p /Users/veronique/BASE/Code/Repo/iram-omega-q/analysis/results/paper3/robustness/logs/target

echo "=== Running target_S0p200 ==="
caffeinate -i mvn -q -DskipTests exec:java \
  -Dexec.mainClass=org.iram.omega.iram_omega_q.simulation.RunFromConfig \
  -Dexec.args=/Users/veronique/BASE/Code/Repo/iram-omega-q/analysis/paper3/config/robustness/target/target_S0p200.properties \
  | tee /Users/veronique/BASE/Code/Repo/iram-omega-q/analysis/results/paper3/robustness/logs/target/target_S0p200.log

echo "=== Running target_S0p250 ==="
caffeinate -i mvn -q -DskipTests exec:java \
  -Dexec.mainClass=org.iram.omega.iram_omega_q.simulation.RunFromConfig \
  -Dexec.args=/Users/veronique/BASE/Code/Repo/iram-omega-q/analysis/paper3/config/robustness/target/target_S0p250.properties \
  | tee /Users/veronique/BASE/Code/Repo/iram-omega-q/analysis/results/paper3/robustness/logs/target/target_S0p250.log

echo "=== Running target_S0p300 ==="
caffeinate -i mvn -q -DskipTests exec:java \
  -Dexec.mainClass=org.iram.omega.iram_omega_q.simulation.RunFromConfig \
  -Dexec.args=/Users/veronique/BASE/Code/Repo/iram-omega-q/analysis/paper3/config/robustness/target/target_S0p300.properties \
  | tee /Users/veronique/BASE/Code/Repo/iram-omega-q/analysis/results/paper3/robustness/logs/target/target_S0p300.log

echo "=== Running target_S0p350 ==="
caffeinate -i mvn -q -DskipTests exec:java \
  -Dexec.mainClass=org.iram.omega.iram_omega_q.simulation.RunFromConfig \
  -Dexec.args=/Users/veronique/BASE/Code/Repo/iram-omega-q/analysis/paper3/config/robustness/target/target_S0p350.properties \
  | tee /Users/veronique/BASE/Code/Repo/iram-omega-q/analysis/results/paper3/robustness/logs/target/target_S0p350.log

echo "=== Running target_S0p400 ==="
caffeinate -i mvn -q -DskipTests exec:java \
  -Dexec.mainClass=org.iram.omega.iram_omega_q.simulation.RunFromConfig \
  -Dexec.args=/Users/veronique/BASE/Code/Repo/iram-omega-q/analysis/paper3/config/robustness/target/target_S0p400.properties \
  | tee /Users/veronique/BASE/Code/Repo/iram-omega-q/analysis/results/paper3/robustness/logs/target/target_S0p400.log
