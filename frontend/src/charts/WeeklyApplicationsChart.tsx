import { Bar, BarChart, CartesianGrid, Tooltip, XAxis, YAxis } from 'recharts';
import type { WeekBucket } from '../api/types';
import ChartCard from './ChartCard';
import { seriesColor } from './palette';

function shortWeek(iso: string): string {
  const d = new Date(`${iso}T00:00:00Z`);
  return d.toLocaleDateString(undefined, { month: 'short', day: 'numeric', timeZone: 'UTC' });
}

/** New applications per week for the last twelve weeks - your own pace. */
export default function WeeklyApplicationsChart({ weeks }: { weeks: WeekBucket[] }) {
  const data = weeks.map((w) => ({ week: shortWeek(w.weekStart), weekStart: w.weekStart, created: w.created }));
  const total = data.reduce((n, d) => n + d.created, 0);

  return (
    <ChartCard
      title="Applications per week"
      description="How many you sent each week, last twelve weeks."
      table={
        <table>
          <thead>
            <tr>
              <th>Week of</th>
              <th>Applications</th>
            </tr>
          </thead>
          <tbody>
            {data.map((d) => (
              <tr key={d.weekStart}>
                <td>{d.week}</td>
                <td>{d.created}</td>
              </tr>
            ))}
          </tbody>
        </table>
      }
    >
      {(width) => (
        <div role="img" aria-label={`Applications per week over twelve weeks, ${total} in total`}>
          <BarChart width={width} height={220} data={data} margin={{ left: 0, right: 8, top: 8, bottom: 4 }}>
            <CartesianGrid vertical={false} stroke="var(--border)" />
            <XAxis dataKey="week" tick={{ fill: 'var(--muted)', fontSize: 11 }} axisLine={false} tickLine={false} interval={1} />
            <YAxis allowDecimals={false} width={28} tick={{ fill: 'var(--muted)', fontSize: 11 }} axisLine={false} tickLine={false} />
            <Tooltip cursor={{ fill: 'var(--bg)' }} contentStyle={{ background: 'var(--card)', border: '1px solid var(--border)', color: 'var(--text)' }} />
            <Bar dataKey="created" name="Applications" fill={seriesColor()} radius={[4, 4, 0, 0]} isAnimationActive={false} />
          </BarChart>
        </div>
      )}
    </ChartCard>
  );
}
