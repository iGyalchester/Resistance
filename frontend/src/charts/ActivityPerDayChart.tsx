import { Bar, BarChart, CartesianGrid, Legend, Tooltip, XAxis, YAxis } from 'recharts';
import type { DayCount } from '../api/types';
import ChartCard from './ChartCard';
import { ORDINAL, seriesColor } from './palette';

function shortDay(iso: string): string {
  const d = new Date(`${iso}T00:00:00Z`);
  return d.toLocaleDateString(undefined, { month: 'short', day: 'numeric', timeZone: 'UTC' });
}

/**
 * Status changes per day across every account, split by where they came
 * from: emails the intake filed versus edits people made by hand. Two
 * series stacked, so the bar height is the day's total; both use the one
 * validated hue (full strength for intake, the light ramp step for
 * manual) and the legend says which is which.
 */
export default function ActivityPerDayChart({ intake, manual }: { intake: DayCount[]; manual: DayCount[] }) {
  const byDay = new Map(manual.map((d) => [d.day, d.count]));
  const data = intake.map((d) => ({ day: d.day, label: shortDay(d.day), intake: d.count, manual: byDay.get(d.day) ?? 0 }));
  const intakeTotal = data.reduce((n, d) => n + d.intake, 0);
  const manualTotal = data.reduce((n, d) => n + d.manual, 0);

  return (
    <ChartCard
      title="Activity per day"
      description="Status changes across all accounts in the last 30 days: filed from email versus edited by hand."
      table={
        <table>
          <thead>
            <tr>
              <th>Day</th>
              <th>From email</th>
              <th>By hand</th>
            </tr>
          </thead>
          <tbody>
            {data.map((d) => (
              <tr key={d.day}>
                <td>{d.label}</td>
                <td>{d.intake}</td>
                <td>{d.manual}</td>
              </tr>
            ))}
          </tbody>
        </table>
      }
    >
      {(width) => (
        <div role="img" aria-label={`Activity per day over 30 days: ${intakeTotal} changes from email, ${manualTotal} by hand`}>
          <BarChart width={width} height={240} data={data} margin={{ left: 0, right: 8, top: 8, bottom: 4 }}>
            <CartesianGrid vertical={false} stroke="var(--border)" />
            <XAxis dataKey="label" tick={{ fill: 'var(--muted)', fontSize: 11 }} axisLine={false} tickLine={false} interval={4} />
            <YAxis allowDecimals={false} width={28} tick={{ fill: 'var(--muted)', fontSize: 11 }} axisLine={false} tickLine={false} />
            <Tooltip cursor={{ fill: 'var(--bg)' }} contentStyle={{ background: 'var(--card)', border: '1px solid var(--border)', color: 'var(--text)' }} />
            <Legend wrapperStyle={{ fontSize: 12, color: 'var(--muted)' }} />
            <Bar dataKey="intake" name="From email" stackId="a" fill={seriesColor()} isAnimationActive={false} />
            <Bar dataKey="manual" name="By hand" stackId="a" fill={ORDINAL[0]} radius={[4, 4, 0, 0]} isAnimationActive={false} />
          </BarChart>
        </div>
      )}
    </ChartCard>
  );
}
