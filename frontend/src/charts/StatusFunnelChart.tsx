import { Bar, BarChart, CartesianGrid, LabelList, Tooltip, XAxis, YAxis } from 'recharts';
import { STATUSES } from '../api/types';
import { label } from '../components/StatusSelect';
import ChartCard from './ChartCard';
import { seriesColor } from './palette';

/**
 * How many applications sit at each stage right now. Horizontal bars in
 * pipeline order, one hue (it is one measure), value labels on the bars.
 */
export default function StatusFunnelChart({ counts }: { counts: Record<string, number> }) {
  const data = STATUSES.map((s) => ({ status: label(s), count: counts[s] ?? 0 }));
  const summary = data.map((d) => `${d.status} ${d.count}`).join(', ');

  return (
    <ChartCard
      title="Pipeline"
      description="Where your applications are right now, by stage."
      table={
        <table>
          <thead>
            <tr>
              <th>Stage</th>
              <th>Applications</th>
            </tr>
          </thead>
          <tbody>
            {data.map((d) => (
              <tr key={d.status}>
                <td>{d.status}</td>
                <td>{d.count}</td>
              </tr>
            ))}
          </tbody>
        </table>
      }
    >
      {(width) => (
        <div role="img" aria-label={`Applications by stage: ${summary}`}>
          <BarChart width={width} height={240} data={data} layout="vertical" margin={{ left: 8, right: 32, top: 4, bottom: 4 }}>
            <CartesianGrid horizontal={false} stroke="var(--border)" />
            <XAxis type="number" allowDecimals={false} hide />
            <YAxis type="category" dataKey="status" width={88} tick={{ fill: 'var(--muted)', fontSize: 12 }} axisLine={false} tickLine={false} />
            <Tooltip cursor={{ fill: 'var(--bg)' }} contentStyle={{ background: 'var(--card)', border: '1px solid var(--border)', color: 'var(--text)' }} />
            <Bar dataKey="count" name="Applications" fill={seriesColor()} radius={[0, 4, 4, 0]} barSize={16} isAnimationActive={false}>
              <LabelList dataKey="count" position="right" style={{ fill: 'var(--text)', fontSize: 12 }} />
            </Bar>
          </BarChart>
        </div>
      )}
    </ChartCard>
  );
}
