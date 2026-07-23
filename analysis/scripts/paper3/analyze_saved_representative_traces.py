#!/usr/bin/env python3
"""Exploratory diagnostics from the representative (replicate-0) time series.

The current SwitchingStudyRunner writes one full time series per condition only
for replicate 0. This script lets you inspect late drift and transition-aligned
behavior immediately from an already completed run, but its results must not be
used as across-run statistical evidence.
"""
from __future__ import annotations
import argparse
from pathlib import Path
import numpy as np
import pandas as pd
import matplotlib.pyplot as plt


def args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--results', type=Path, required=True)
    p.add_argument('--out', type=Path, required=True)
    p.add_argument('--burn-in-samples', type=int, default=40000)
    p.add_argument('--max-lag-steps', type=int, default=2000)
    p.add_argument('--lag-stride-steps', type=int, default=10)
    return p.parse_args()


def identity(path: Path) -> tuple[str, str]:
    stem = path.stem
    if stem == 'fixed_rf': return 'FIXED_RF', 'FIXED_RF'
    if stem == 'fixed_df': return 'FIXED_DF', 'FIXED_DF'
    if stem.startswith('periodic_'): return 'PERIODIC', stem.replace('periodic_', '')
    if stem.startswith('markov_'): return 'MARKOV', stem.replace('markov_', '')
    return 'UNKNOWN', stem


def stats(protocol: str, condition: str, d: pd.DataFrame, burn: int) -> dict:
    post = d.iloc[burn:].reset_index(drop=True)
    quarters = np.array_split(post, 4)
    muw = [q['mu'].mean() for q in quarters]
    dcw = [q['dC'].mean() for q in quarters]
    last = quarters[-1]
    return {'protocol': protocol, 'condition': condition,
            **{f'muWindow{i+1}': v for i, v in enumerate(muw)},
            **{f'dCWindow{i+1}': v for i, v in enumerate(dcw)},
            'muLateDrift': muw[3]-muw[2], 'dCLateDrift': dcw[3]-dcw[2],
            'muFinalQuarterSlope': np.polyfit(last['t'], last['mu'], 1)[0],
            'dCFinalQuarterSlope': np.polyfit(last['t'], last['dC'], 1)[0]}


def event_rows(protocol: str, condition: str, d: pd.DataFrame, rf: pd.DataFrame,
               df: pd.DataFrame, burn: int, max_lag: int, stride: int) -> list[dict]:
    out = []
    for transition, after, baseline in [('DF_TO_RF', 1, rf), ('RF_TO_DF', 0, df)]:
        q = d['orderingRF'].to_numpy(int)
        onsets = [i for i in range(max(1, burn), len(d)) if q[i] != q[i-1] and q[i] == after]
        for lag in range(0, max_lag+1, stride):
            vals_mu, vals_dc = [], []
            for onset in onsets:
                idx = onset + lag
                if idx >= len(d) or np.any(q[onset:idx+1] != after):
                    continue
                vals_mu.append(d.iloc[idx]['mu'] - baseline.iloc[idx]['mu'])
                vals_dc.append(d.iloc[idx]['dC'] - baseline.iloc[idx]['dC'])
            if vals_mu:
                out.append({'protocol': protocol, 'condition': condition, 'transition': transition,
                            'lagSteps': lag, 'tau': d.iloc[lag]['t']-d.iloc[0]['t'],
                            'availableEvents': len(vals_mu), 'excessMuMean': np.mean(vals_mu),
                            'excessDeltaCMean': np.mean(vals_dc)})
    return out


def main() -> int:
    a = args(); a.out.mkdir(parents=True, exist_ok=True)
    tsdir = a.results / 'timeseries'
    files = sorted(tsdir.glob('*.csv'))
    if not files: raise FileNotFoundError(f'No representative time series found in {tsdir}')
    rf = pd.read_csv(tsdir/'fixed_rf.csv'); df = pd.read_csv(tsdir/'fixed_df.csv')
    srows, erows = [], []
    for f in files:
        protocol, condition = identity(f)
        d = pd.read_csv(f)
        srows.append(stats(protocol, condition, d, a.burn_in_samples))
        if protocol in ('PERIODIC', 'MARKOV'):
            erows.extend(event_rows(protocol, condition, d, rf, df, a.burn_in_samples,
                                    a.max_lag_steps, a.lag_stride_steps))
    st = pd.DataFrame(srows); ev = pd.DataFrame(erows)
    st.to_csv(a.out/'representative_stationarity.csv', index=False)
    ev.to_csv(a.out/'representative_event_aligned.csv', index=False)
    p = st[st['protocol']=='PERIODIC'].copy()
    p['L'] = p['condition'].str.extract(r'(\d+)').astype(float)
    p = p.sort_values('L')
    if not p.empty:
        fig, ax = plt.subplots(figsize=(6.8,4.4))
        ax.plot(p['L'], p['muLateDrift'], marker='o')
        ax.axhline(0, linestyle='--', linewidth=1)
        ax.set_xscale('log'); ax.set_xlabel('Periodic dwell duration L (steps)')
        ax.set_ylabel(r'Replicate-0 late drift $\overline{\mu}_4-\overline{\mu}_3$')
        ax.set_title('Exploratory only: representative late drift')
        ax.grid(True, alpha=.25); fig.tight_layout()
        fig.savefig(a.out/'fig_representative_late_drift.png', dpi=300, bbox_inches='tight')
        fig.savefig(a.out/'fig_representative_late_drift.pdf', bbox_inches='tight'); plt.close(fig)
    print('Wrote exploratory replicate-0 diagnostics; do not treat these as population statistics.')
    return 0

if __name__ == '__main__':
    raise SystemExit(main())
