#!/usr/bin/env python3
"""Generate publication-quality charts for the peer review simulation paper."""

import altair as alt
import pandas as pd

# Disable max rows warning
alt.data_transformers.disable_max_rows()

# Quality x Noise data
quality_noise_data = pd.DataFrame([
    {"quality": 50, "noise": "Low (SD=15)", "probability": 0.2, "order": 1},
    {"quality": 60, "noise": "Low (SD=15)", "probability": 2.8, "order": 1},
    {"quality": 70, "noise": "Low (SD=15)", "probability": 26.8, "order": 1},
    {"quality": 80, "noise": "Low (SD=15)", "probability": 66.7, "order": 1},
    {"quality": 90, "noise": "Low (SD=15)", "probability": 93.9, "order": 1},
    {"quality": 50, "noise": "Medium (SD=30)", "probability": 3.4, "order": 2},
    {"quality": 60, "noise": "Medium (SD=30)", "probability": 11.0, "order": 2},
    {"quality": 70, "noise": "Medium (SD=30)", "probability": 24.3, "order": 2},
    {"quality": 80, "noise": "Medium (SD=30)", "probability": 46.1, "order": 2},
    {"quality": 90, "noise": "Medium (SD=30)", "probability": 67.7, "order": 2},
    {"quality": 50, "noise": "High (SD=45)", "probability": 7.0, "order": 3},
    {"quality": 60, "noise": "High (SD=45)", "probability": 13.6, "order": 3},
    {"quality": 70, "noise": "High (SD=45)", "probability": 23.8, "order": 3},
    {"quality": 80, "noise": "High (SD=45)", "probability": 37.6, "order": 3},
    {"quality": 90, "noise": "High (SD=45)", "probability": 51.6, "order": 3},
])

# Quality x Noise chart
threshold_line = alt.Chart(pd.DataFrame({'x': [70]})).mark_rule(
    strokeDash=[6, 4],
    color='#888888',
    strokeWidth=1.5
).encode(x='x:Q')

threshold_label = alt.Chart(pd.DataFrame({'x': [70], 'y': [92], 'text': ['Threshold']})).mark_text(
    align='left',
    dx=5,
    fontSize=11,
    color='#666666'
).encode(x='x:Q', y='y:Q', text='text:N')

quality_noise_chart = alt.Chart(quality_noise_data).mark_line(
    point=alt.OverlayMarkDef(size=80),
    strokeWidth=2.5
).encode(
    x=alt.X('quality:Q',
            title='True Paper Quality',
            scale=alt.Scale(domain=[45, 95])),
    y=alt.Y('probability:Q',
            title='Acceptance Probability (%)',
            scale=alt.Scale(domain=[0, 100])),
    color=alt.Color('noise:N',
                    title='Reviewer Noise',
                    sort=['Low (SD=15)', 'Medium (SD=30)', 'High (SD=45)'],
                    scale=alt.Scale(range=['#2563eb', '#9333ea', '#dc2626']))
).properties(
    width=450,
    height=300,
    title=alt.Title(
        text='How Noise Redistributes Acceptance',
        subtitle='Good papers lose acceptance probability, bad papers gain'
    )
)

fig1 = (quality_noise_chart + threshold_line + threshold_label).configure(
    font='Helvetica'
).configure_axis(
    labelFontSize=11,
    titleFontSize=12
).configure_legend(
    labelFontSize=11,
    titleFontSize=12
)

# Strategy comparison data
strategy_data = pd.DataFrame([
    {"strategy": "Two Very Good", "expected": 0.82, "papers": 2, "quality": 78},
    {"strategy": "Three Decent", "expected": 0.60, "papers": 3, "quality": 68},
    {"strategy": "Two Good", "expected": 0.57, "papers": 2, "quality": 72},
    {"strategy": "One Excellent", "expected": 0.56, "papers": 1, "quality": 85},
])

bars = alt.Chart(strategy_data).mark_bar(
    color='#2563eb',
    cornerRadiusTopLeft=3,
    cornerRadiusTopRight=3
).encode(
    x=alt.X('strategy:N',
            title='Research Strategy (Fixed Total Effort)',
            sort=alt.EncodingSortField(field='expected', order='descending'),
            axis=alt.Axis(labelAngle=0)),
    y=alt.Y('expected:Q',
            title='Expected Publications',
            scale=alt.Scale(domain=[0, 1]))
)

text_labels = alt.Chart(strategy_data).mark_text(
    dy=-8,
    fontSize=12,
    fontWeight='bold'
).encode(
    x=alt.X('strategy:N', sort=alt.EncodingSortField(field='expected', order='descending')),
    y=alt.Y('expected:Q'),
    text=alt.Text('expected:Q', format='.2f')
)

# Add quality info below bars
info_data = strategy_data.copy()
info_data['info'] = info_data.apply(lambda r: f"{r['papers']} paper{'s' if r['papers']>1 else ''}, Q={r['quality']}", axis=1)
info_data['y_pos'] = 0.08

info_labels = alt.Chart(info_data).mark_text(
    fontSize=10,
    color='white'
).encode(
    x=alt.X('strategy:N', sort=alt.EncodingSortField(field='expected', order='descending')),
    y=alt.Y('y_pos:Q'),
    text='info:N'
)

fig2 = (bars + text_labels + info_labels).properties(
    width=400,
    height=300,
    title=alt.Title(
        text='Perverse Incentives: Quantity Over Quality',
        subtitle='Lower quality strategy yields 46% more expected publications'
    )
).configure(
    font='Helvetica'
).configure_axis(
    labelFontSize=11,
    titleFontSize=12
).configure_view(
    stroke=None
)

# Save charts
print("Saving Quality x Noise chart...")
fig1.save('paper/figures/quality-x-noise.png', scale_factor=2.0)
fig1.save('paper/figures/quality-x-noise.pdf')

print("Saving Strategy Comparison chart...")
fig2.save('paper/figures/strategy-comparison.png', scale_factor=2.0)
fig2.save('paper/figures/strategy-comparison.pdf')

print("Done! Charts saved to paper/figures/")
