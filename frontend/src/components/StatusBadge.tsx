/**
 * A tracked application's status as a colored pill. The class name is
 * derived from the enum value (see index.css for the palette); unknown
 * or missing statuses fall back to a neutral badge.
 */
export default function StatusBadge({ status }: { status: string | null }) {
  const value = status ?? 'UNKNOWN';
  return <span className={`badge badge-${value.toLowerCase()}`}>{value}</span>;
}
