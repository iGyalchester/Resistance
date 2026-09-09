import { Children, cloneElement, isValidElement, type ReactNode } from 'react';

interface Props {
  id: string;
  label: string;
  error?: string | null;
  hint?: string;
  children: ReactNode;
}

/**
 * Label + control + (optional) hint or field error, wired together for
 * screen readers: the control gets aria-describedby pointing at whichever
 * of the two is showing, and aria-invalid while there is an error.
 */
export default function FormField({ id, label, error, hint, children }: Props) {
  const describedBy = error ? `${id}-error` : hint ? `${id}-hint` : undefined;
  const control = Children.map(children, (child) =>
    isValidElement<Record<string, unknown>>(child)
      ? cloneElement(child, { 'aria-describedby': describedBy, 'aria-invalid': error ? true : undefined })
      : child,
  );
  return (
    <div className={`field${error ? ' field-invalid' : ''}`}>
      <label htmlFor={id}>{label}</label>
      {control}
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
