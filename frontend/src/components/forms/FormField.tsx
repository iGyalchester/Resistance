import type { ReactNode } from 'react';

interface Props {
  id: string;
  label: string;
  error?: string | null;
  hint?: string;
  children: ReactNode;
}

/** Label + control + (optional) field error, wired together for screen readers. */
export default function FormField({ id, label, error, hint, children }: Props) {
  return (
    <div className={`field${error ? ' field-invalid' : ''}`}>
      <label htmlFor={id}>{label}</label>
      {children}
      {hint && !error && (
        <p className="muted small" id={`${id}-hint`}>
          {hint}
        </p>
      )}
      {error && (
        <p className="error small" id={`${id}-error`} role="alert">
          {error}
        </p>
      )}
    </div>
  );
}
