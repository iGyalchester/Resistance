import { useEffect } from 'react';
import { Link } from 'react-router-dom';
import AssistantConversation from './AssistantConversation';

interface Props {
  open: boolean;
  prefill?: string;
  onClose: () => void;
}

/**
 * The slide-over version of the chat. It stays mounted while closed
 * (hidden, not unmounted) so the conversation survives closing and
 * reopening within the visit.
 */
export default function AssistantDrawer({ open, prefill, onClose }: Props) {
  useEffect(() => {
    if (!open) return;
    function onKey(event: KeyboardEvent) {
      if (event.key === 'Escape') onClose();
    }
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [open, onClose]);

  return (
    <aside className="drawer" hidden={!open} aria-label="Assistant">
      <header className="drawer-header">
        <h2>Assistant</h2>
        <div>
          <Link to="/assistant" className="btn btn-ghost" onClick={onClose}>
            Full page
          </Link>
          <button type="button" className="btn btn-ghost" aria-label="Close assistant" onClick={onClose}>
            ×
          </button>
        </div>
      </header>
      <AssistantConversation compact initialMessage={prefill} />
    </aside>
  );
}
