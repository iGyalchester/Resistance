import { Bar, BarChart, CartesianGrid, LabelList, Tooltip, XAxis, YAxis } from 'recharts';
import { STATUSES } from '../api/types';
import { label } from '../components/StatusSelect';
import ChartCard from './ChartCard';
import { seriesColor } from './palette';

/**
 * Median days an application spent in each stage before moving on. Only
 * stages somebody has actually left are shown - an open stay tells you
 * nothing about how long the stage takes.
 */
export default function TimeInStageChart({ medians }: { medians: Record<string, number> }) {
  const data = STATUSES.filter((s) => medians[s] !== undefined).map((s) => ({ stage: label(s), days: medians[s] }));

  return (
    <ChartCard
      title="Time in stage"
      description="Median days spent in a stage before moving to the next one."
      table={
        <table>
          <thead>
            <tr>
              <th>Stage</th>
              <th>Median days</th>
            </tr>
          </thead>
          <tbody>
            {data.map((d) => (
              <tr key={d.stage}>
                <td>{d.stage}</td>
                <td>{d.days}</td>
              </tr>
            ))}
          </tbody>
        </table>
      }
    >
      {(width) =>
        data.length === 0 ? (
          <p className="muted">Nothing has moved between stages yet.</p>
        ) : (
          <div role="img" aria-label={`Median days in stage: ${data.map((d) => `${d.stage} ${d.days}`).join(', ')}`}>
            <BarChart width={width} height={40 + data.length * 36} data={data} layout="vertical" margin={{ left: 8, right: 40, top: 4, bottom: 4 }}>
              <CartesianGrid horizontal={false} stroke="var(--border)" />
              <XAxis type="number" hide />
              <YAxis type="category" dataKey="stage" width={88} tick={{ fill: 'var(--muted)', fontSize: 12 }} axisLine={false} tickLine={false} />
              <Tooltip cursor={{ fill: 'var(--bg)' }} contentStyle={{ background: 'var(--card)', border: '1px solid var(--border)', color: 'var(--text)' }} />
              <Bar dataKey="days" name="Median days" fill={seriesColor()} radius={[0, 4, 4, 0]} barSize={16} isAnimationActive={false}>
                <LabelList dataKey="days" position="right" formatter={(v: number) => `${v} d`} style={{ fill: 'var(--text)', fontSize: 12 }} />
              </Bar>
            </BarChart>
          </div>
        )
      }
    </ChartCard>
  );
}
