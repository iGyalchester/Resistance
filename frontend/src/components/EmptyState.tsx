import type { ReactNode } from 'react';

/** A quiet "nothing here yet" block with an optional call to action. */
export default function EmptyState({ children, action }: { children: ReactNode; action?: ReactNode }) {
  return (
    <div className="empty">
      <p className="muted">{children}</p>
      {action}
    </div>
  );
}
