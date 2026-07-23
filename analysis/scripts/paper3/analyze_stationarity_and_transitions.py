#!/usr/bin/env python3
"""Analyze compact all-replicate stationarity and transition-response outputs.

This script requires a rerun made with the supplied SwitchingDiagnostics.java
and replacement SwitchingStudyRunner.java. It does not require saving full
trajectories for every replicate.

Expected inputs:
    switching_runs.csv
    switching_stationarity_runs.csv
    switching_event_aligned.csv
"""
from __future__ import annotations
import argparse
from pathlib import Path
import math
import numpy as np
import pandas as pd
import matplotlib.pyplot as plt
from scipy import stats


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--results', type=Path, required=True)
    p.add_argument('--out', type=Path, required=True)
    p.add_argument('--bootstrap', type=int, default=2000)
    p.add_argument('--seed', type=int, default=11783)
    return p.parse_args()


def condition_label(protocol: str, condition: str) -> str:
    if protocol == 'PERIODIC': return condition
    if protocol == 'MARKOV': return condition.replace('pLoss=', '').replace('_pReturn=', '/').replace('p','.')
    return protocol


def group_ci(data: pd.DataFrame, col: str) -> pd.DataFrame:
    rows=[]
    for (protocol, condition), g in data.groupby(['protocol','condition'], sort=False):
        x=g[col].dropna().to_numpy(float); n=len(x); mean=x.mean()
        sd=x.std(ddof=1) if n>1 else np.nan; sem=sd/np.sqrt(n) if n>1 else np.nan
        tcrit=stats.t.ppf(.975,n-1) if n>1 else np.nan
        lo=mean-tcrit*sem if n>1 else np.nan; hi=mean+tcrit*sem if n>1 else np.nan
        tst,pv=stats.ttest_1samp(x,0) if n>1 else (np.nan,np.nan)
        rows.append({'protocol':protocol,'condition':condition,'label':condition_label(protocol,condition),
                     'metric':col,'n':n,'mean':mean,'sd':sd,'sem':sem,'ci95_low':lo,'ci95_high':hi,
                     't_stat':tst,'p_two_sided':pv})
    return pd.DataFrame(rows)


def save(fig: plt.Figure, out: Path, stem: str) -> None:
    fig.savefig(out/f'{stem}.png', dpi=300, bbox_inches='tight')
    fig.savefig(out/f'{stem}.pdf', bbox_inches='tight')
    plt.close(fig)


def plot_stationarity(ci: pd.DataFrame, protocol: str, metric: str, title: str, ylabel: str, out: Path, stem: str) -> None:
    d=ci[(ci.protocol==protocol)&(ci.metric==metric)].copy()
    if d.empty: return
    if protocol=='PERIODIC':
        d['x']=d['condition'].str.extract(r'(\d+)').astype(float); d=d.sort_values('x')
        xlabel='Periodic dwell duration L (steps)'; x=d['x'].to_numpy(); xscale='log'
    else:
        d['x']=np.arange(len(d)); x=d['x'].to_numpy(); xlabel='Markov transition pair pLoss / pReturn'; xscale=None
    y=d['mean'].to_numpy(); err=np.vstack([y-d['ci95_low'].to_numpy(),d['ci95_high'].to_numpy()-y])
    fig,ax=plt.subplots(figsize=(7.0,4.6)); ax.errorbar(x,y,yerr=err,marker='o',capsize=4,linewidth=1.3)
    ax.axhline(0,linestyle='--',linewidth=1); ax.set_title(title); ax.set_xlabel(xlabel); ax.set_ylabel(ylabel)
    if xscale: ax.set_xscale(xscale)
    else: ax.set_xticks(x,d['label'],rotation=35,ha='right')
    ax.grid(True,alpha=.25); fig.tight_layout(); save(fig,out,stem)


def weighted_mean(group: pd.DataFrame, value: str) -> float:
    w=group['availableEvents'].to_numpy(float); x=group[value].to_numpy(float)
    return float(np.sum(w*x)/np.sum(w))


def bootstrap_event_curve(ev: pd.DataFrame, protocol: str, condition: str, transition: str,
                          value: str, b: int, rng: np.random.Generator) -> pd.DataFrame:
    d=ev[(ev.protocol==protocol)&(ev.condition==condition)&(ev.transition==transition)].copy()
    if d.empty: return pd.DataFrame()
    reps=np.sort(d.replicate.unique())
    rows=[]
    for (lag,tau), g in d.groupby(['lagSteps','tau']):
        g=g.set_index('replicate').reindex(reps).dropna(subset=[value,'availableEvents']).reset_index()
        if g.empty: continue
        estimate=weighted_mean(g,value)
        boot=[]
        arr=g.to_dict('records')
        for _ in range(b):
            sampled=[arr[i] for i in rng.integers(0,len(arr),len(arr))]
            sb=pd.DataFrame(sampled)
            boot.append(weighted_mean(sb,value))
        lo,hi=np.percentile(boot,[2.5,97.5])
        rows.append({'protocol':protocol,'condition':condition,'transition':transition,'lagSteps':lag,'tau':tau,
                     'mean':estimate,'ci95_low':lo,'ci95_high':hi,'replicates':len(g),
                     'events':int(g.availableEvents.sum())})
    return pd.DataFrame(rows).sort_values('tau')


def select_conditions(ev: pd.DataFrame, protocol: str) -> list[str]:
    options=list(ev[ev.protocol==protocol].condition.drop_duplicates())
    if protocol=='PERIODIC':
        target=['L=100','L=500','L=2000']
    else:
        target=['pLoss=0p01_pReturn=0p01','pLoss=0p002_pReturn=0p002','pLoss=0p001_pReturn=0p001']
    selected=[c for c in target if c in options]
    return selected or options[:3]


def plot_event_curves(ev: pd.DataFrame, protocol: str, transition: str, value: str,
                      b: int, rng: np.random.Generator, out: Path, stem: str) -> pd.DataFrame:
    all_summary=[]; fig,ax=plt.subplots(figsize=(7.2,4.8))
    for condition in select_conditions(ev,protocol):
        curve=bootstrap_event_curve(ev,protocol,condition,transition,value,b,rng)
        if curve.empty: continue
        all_summary.append(curve)
        label=condition_label(protocol,condition)
        ax.plot(curve.tau,curve['mean'],label=label,linewidth=1.4)
        ax.fill_between(curve.tau,curve.ci95_low,curve.ci95_high,alpha=.13)
    ax.axhline(0,linestyle='--',linewidth=1)
    meaning='relative to matched fixed RF' if transition=='DF_TO_RF' else 'relative to matched fixed DF'
    ax.set_xlabel('Time since transition (model time)')
    ax.set_ylabel(r'Excess $\mu$ ' + meaning if value=='excessMuMean' else r'Excess $\Delta C$ ' + meaning)
    ax.set_title(f'{protocol.title()} {transition.replace("_", "→")}: event-aligned response')
    ax.grid(True,alpha=.25); ax.legend(title='Condition',fontsize=8); fig.tight_layout(); save(fig,out,stem)
    return pd.concat(all_summary,ignore_index=True) if all_summary else pd.DataFrame()


def plot_penalty_vs_slope(runs: pd.DataFrame, stat: pd.DataFrame, out: Path) -> None:
    merged=runs.merge(stat[['protocol','condition','replicate','muFinalQuarterSlope']],on=['protocol','condition','replicate'])
    d=merged[merged.protocol.isin(['PERIODIC','MARKOV'])]
    fig,ax=plt.subplots(figsize=(6.5,4.8))
    for protocol,g in d.groupby('protocol'):
        ax.scatter(g.muFinalQuarterSlope,g.C_switch_mu,alpha=.35,s=18,label=protocol)
    ax.axhline(0,linestyle='--',linewidth=1); ax.axvline(0,linestyle='--',linewidth=1)
    ax.set_xlabel(r'Final-quarter slope $d\mu/dt$')
    ax.set_ylabel(r'$C_{\mathrm{switch}}^{(\mu)}$')
    ax.set_title('Does negative penalty accompany continuing regulatory drift?')
    ax.grid(True,alpha=.25); ax.legend(); fig.tight_layout(); save(fig,out,'fig_penalty_vs_late_slope')
    d.to_csv(out/'penalty_with_stationarity.csv',index=False)


def main() -> int:
    a=parse_args(); a.out.mkdir(parents=True,exist_ok=True)
    statfile=a.results/'switching_stationarity_runs.csv'; evfile=a.results/'switching_event_aligned.csv'; runfile=a.results/'switching_runs.csv'
    for f in [statfile,evfile,runfile]:
        if not f.exists(): raise FileNotFoundError(f'Missing {f}. Rerun after installing the Java diagnostics patch.')
    stat=pd.read_csv(statfile); ev=pd.read_csv(evfile); runs=pd.read_csv(runfile)
    ci=pd.concat([group_ci(stat,'muLateDrift'),group_ci(stat,'muFinalQuarterSlope'),
                  group_ci(stat,'dCLateDrift'),group_ci(stat,'dCFinalQuarterSlope')],ignore_index=True)
    ci.to_csv(a.out/'stationarity_statistics.csv',index=False)
    plot_stationarity(ci,'PERIODIC','muLateDrift','Periodic switching: late regulatory drift',r'$\overline{\mu}_4-\overline{\mu}_3$',a.out,'fig_periodic_mu_late_drift_ci95')
    plot_stationarity(ci,'MARKOV','muLateDrift','Stochastic switching: late regulatory drift',r'$\overline{\mu}_4-\overline{\mu}_3$',a.out,'fig_markov_mu_late_drift_ci95')
    plot_stationarity(ci,'PERIODIC','muFinalQuarterSlope','Periodic switching: final-quarter slope',r'$d\mu/dt$',a.out,'fig_periodic_mu_final_slope_ci95')
    plot_stationarity(ci,'MARKOV','muFinalQuarterSlope','Stochastic switching: final-quarter slope',r'$d\mu/dt$',a.out,'fig_markov_mu_final_slope_ci95')
    rng=np.random.default_rng(a.seed); summaries=[]
    for protocol in ['PERIODIC','MARKOV']:
        for transition in ['DF_TO_RF','RF_TO_DF']:
            c=plot_event_curves(ev,protocol,transition,'excessMuMean',a.bootstrap,rng,a.out,
                                f'fig_{protocol.lower()}_{transition.lower()}_mu_response')
            if not c.empty: summaries.append(c.assign(metric='excessMuMean'))
    if summaries: pd.concat(summaries,ignore_index=True).to_csv(a.out/'event_aligned_mu_summary.csv',index=False)
    plot_penalty_vs_slope(runs,stat,a.out)
    print(f'Wrote stationarity and transition-response analysis to {a.out.resolve()}')
    return 0

if __name__=='__main__':
    raise SystemExit(main())
