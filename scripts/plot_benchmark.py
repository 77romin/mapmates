"""Plot measured samples, verifying equivalent response payloads first. Requires matplotlib."""
import json
from pathlib import Path
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt

root = Path('docs/benchmarks')
before, after = [json.loads((root/(label+'.json')).read_text()) for label in ['before', 'after']]
assert before['fixture_plans'] == after['fixture_plans']
assert before['samples'] == after['samples']
for name in before['cases']:
    assert before['cases'][name]['response_sha256'] == after['cases'][name]['response_sha256'], name

plt.rcParams.update({'font.size': 11, 'axes.spines.top': False, 'axes.spines.right': False,
                     'svg.fonttype': 'none', 'svg.hashsalt': 'mapmates'})
fig, axes = plt.subplots(1, 2, figsize=(12, 5), gridspec_kw={'width_ratios': [1, 2.4]})
labels = {'plans': 'Plan list\n50 plans / 500 stops', 'area_type': 'Area + type',
          'nearby': 'Nearby (5 km)', 'keyword': 'Keyword'}
for ax, names in zip(axes, [['plans'], ['area_type', 'nearby', 'keyword']]):
    for shift, data, label, color in [(-.19, before, 'Before', '#9ba6b5'),
                                     (.19, after, 'After', '#db7552')]:
        medians = [data['cases'][n]['median_ms'] for n in names]
        p95 = [data['cases'][n]['p95_ms'] for n in names]
        x = [i+shift for i in range(len(names))]
        bars = ax.bar(x, medians, width=.34, color=color, label=label)
        ax.scatter(x, p95, color=color, edgecolor='#334155', marker='D', s=30, zorder=3)
        for bar, value, tail in zip(bars, medians, p95):
            ax.annotate(f'{value:.2f}', (bar.get_x()+bar.get_width()/2, max(value, tail)),
                        xytext=(0, 5), textcoords='offset points', ha='center', fontsize=10)
    maximum = max(before['cases'][n]['p95_ms'] for n in names)
    maximum = max(maximum, max(after['cases'][n]['p95_ms'] for n in names))
    ax.set_ylim(0, maximum*1.25)
    ax.set_xticks(range(len(names)), [labels[n] for n in names])
    ax.set_ylabel('HTTP response time (ms) - lower is better')
    ax.grid(axis='y', alpha=.2)
    ax.set_axisbelow(True)
    ax.legend(frameon=False)
axes[0].set_title('Batched plan-item reads')
axes[1].set_title('50,915 tourism rows: index + refreshed statistics')
fig.suptitle('MapMates: measured before / after', fontsize=18, fontweight='bold')
fig.text(.5, .025, 'Bars: median | diamonds: p95 | 10 warm-ups + 60 sequential requests per case\n'
         'Same local Docker DB and response SHA-256; separate y-axis scales; no load-test claim',
         ha='center', fontsize=10, color='#475569')
fig.tight_layout(rect=(0, .11, 1, .92))
for suffix in ['png', 'svg']:
    target = root/('comparison.'+suffix)
    fig.savefig(target, dpi=180, facecolor='white',
                metadata={'Date': None} if suffix == 'svg' else {})
    if suffix == 'svg':
        target.write_text('\n'.join(line.rstrip() for line in target.read_text().splitlines())+'\n')
print('WROTE benchmark charts; all four response hashes match')
