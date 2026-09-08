import { useEffect, useId, useRef, useState, type ReactNode } from 'react';

interface Props {
  title: string;
  onClose: () => void;
  children: ReactNode;
}

const FOCUSABLE = 'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

/**
 * A modal panel over a backdrop. Escape and the backdrop close it; Tab
 * cycles inside it; whatever had focus before it opened gets it back
 * when it closes, so a keyboard user lands on the button they pressed.
 * The caller decides what to focus first (forms autofocus their first
 * field; otherwise the panel itself). Plain divs rather than <dialog> so
 * the same markup works in every browser and in the test DOM.
 */
export default function Dialog({ title, onClose, children }: Props) {
  const titleId = useId();
  const panel = useRef<HTMLDivElement>(null);

  // captured during the first render, before an autofocused field inside
  // the dialog takes focus, so it is the button that opened us
  const [opener] = useState(() => document.activeElement as HTMLElement | null);

  useEffect(() => {
    const el = panel.current;
    if (el && !el.contains(document.activeElement)) {
      el.focus();
    }
    return () => {
      if (opener && opener.isConnected) opener.focus();
    };
  }, [opener]);

  useEffect(() => {
    function onKey(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        // capture phase, so an assistant drawer behind us never sees it
        event.stopPropagation();
        onClose();
        return;
      }
      if (event.key !== 'Tab' || !panel.current) return;
      const focusable = Array.from(panel.current.querySelectorAll<HTMLElement>(FOCUSABLE));
      if (focusable.length === 0) {
        event.preventDefault();
        return;
      }
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      const active = document.activeElement;
      if (event.shiftKey && (active === first || !panel.current.contains(active))) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && active === last) {
        event.preventDefault();
        first.focus();
      }
    }
    document.addEventListener('keydown', onKey, true);
    return () => document.removeEventListener('keydown', onKey, true);
  }, [onClose]);

  return (
    <div className="backdrop" onClick={onClose}>
      <div
        ref={panel}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        className="dialog"
        tabIndex={-1}
        onClick={(e) => e.stopPropagation()}
      >
        <header className="dialog-header">
          <h2 id={titleId}>{title}</h2>
          <button type="button" className="btn btn-ghost" aria-label="Close" onClick={onClose}>
            ×
          </button>
        </header>
        {children}
      </div>
    </div>
  );
}
