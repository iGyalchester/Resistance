/** One headline number with a label and a one-line explanation. */
export default function StatCard({ label, value, hint }: { label: string; value: string; hint: string }) {
  return (
    <div className="stat">
      <div className="stat-value">{value}</div>
      <div className="stat-label">{label}</div>
      <div className="muted small">{hint}</div>
    </div>
  );
}
