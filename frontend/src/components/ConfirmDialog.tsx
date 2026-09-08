import { useState } from 'react';
import Dialog from './Dialog';

interface Props {
  title: string;
  message: string;
  confirmLabel?: string;
  onConfirm: () => Promise<void>;
  onClose: () => void;
}

/** "Are you sure?" for destructive actions; the confirm button runs the action and reports failure inline. */
export default function ConfirmDialog({ title, message, confirmLabel = 'Delete', onConfirm, onClose }: Props) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function confirm() {
    setBusy(true);
    setError(null);
    try {
      await onConfirm();
    } catch {
      setError('That did not work. Try again.');
      setBusy(false);
    }
  }

  return (
    <Dialog title={title} onClose={onClose}>
      <p>{message}</p>
      {error && <p className="error">{error}</p>}
      <div className="actions">
        <button type="button" className="btn" onClick={onClose} disabled={busy}>
          Cancel
        </button>
        <button type="button" className="btn btn-danger" onClick={confirm} disabled={busy} autoFocus>
          {busy ? 'Working…' : confirmLabel}
        </button>
      </div>
    </Dialog>
  );
}
