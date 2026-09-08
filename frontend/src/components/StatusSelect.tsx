import { STATUSES, type ApplicationStatus } from '../api/types';

interface Props {
  id?: string;
  value: ApplicationStatus | string | null | undefined;
  onChange: (status: ApplicationStatus) => void;
  disabled?: boolean;
  'aria-label'?: string;
  className?: string;
}

/** The status dropdown, shared by the form and the inline edit in the list. */
export default function StatusSelect({ id, value, onChange, disabled, className, ...aria }: Props) {
  return (
    <select
      id={id}
      className={className ?? 'select'}
      value={value ?? 'APPLIED'}
      disabled={disabled}
      onChange={(e) => onChange(e.target.value as ApplicationStatus)}
      aria-label={aria['aria-label']}
    >
      {STATUSES.map((s) => (
        <option key={s} value={s}>
          {label(s)}
        </option>
      ))}
    </select>
  );
}

export function label(status: string | null | undefined): string {
  if (!status) return 'Unknown';
  return status.charAt(0) + status.slice(1).toLowerCase();
}
