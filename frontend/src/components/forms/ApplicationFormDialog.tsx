import { useState, type FormEvent } from 'react';
import { ApiError, createApplication, updateApplication } from '../../api/client';
import type { ApplicationDetailView, ApplicationRequest, ApplicationStatus, ApplicationView, ContactView } from '../../api/types';
import Dialog from '../Dialog';
import StatusSelect from '../StatusSelect';
import FormField from './FormField';

interface Props {
  /** when present the dialog edits; otherwise it creates */
  initial?: ApplicationView | null;
  contacts: ContactView[];
  onClose: () => void;
  onSaved: (saved: ApplicationDetailView) => void;
}

/**
 * Create or edit an application. Required fields are checked here for a
 * fast answer; the server's field errors (same names) win if it disagrees.
 * The applied-on date can only be set when creating - edits keep the date
 * the email or the first entry established.
 */
export default function ApplicationFormDialog({ initial, contacts, onClose, onSaved }: Props) {
  const editing = !!initial;
  const [companyName, setCompanyName] = useState(initial?.companyName ?? '');
  const [positionTitle, setPositionTitle] = useState(initial?.positionTitle ?? '');
  const [status, setStatus] = useState<ApplicationStatus>((initial?.status as ApplicationStatus) ?? 'APPLIED');
  const [contactId, setContactId] = useState<number | null>(initial?.contactId ?? null);
  const [appliedOn, setAppliedOn] = useState('');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (!companyName.trim()) {
      setErrors({ companyName: 'required' });
      return;
    }
    setSubmitting(true);
    setErrors({});
    const body: ApplicationRequest = {
      companyName: companyName.trim(),
      positionTitle: positionTitle.trim() || null,
      status,
      contactId,
      appliedOn: !editing && appliedOn ? appliedOn : null,
    };
    try {
      const saved = editing ? await updateApplication(initial!.id, body) : await createApplication(body);
      onSaved(saved);
    } catch (e) {
      if (e instanceof ApiError && Object.keys(e.fields).length > 0) {
        setErrors(e.fields);
      } else {
        setErrors({ form: 'Could not save. Try again.' });
      }
      setSubmitting(false);
    }
  }

  return (
    <Dialog title={editing ? 'Edit application' : 'Add application'} onClose={onClose}>
      <form onSubmit={onSubmit} noValidate>
        <FormField id="companyName" label="Company" error={errors.companyName}>
          <input
            id="companyName"
            value={companyName}
            onChange={(e) => setCompanyName(e.target.value)}
            maxLength={90}
            autoFocus
            required
          />
        </FormField>
        <FormField id="positionTitle" label="Position" error={errors.positionTitle}>
          <input
            id="positionTitle"
            value={positionTitle}
            onChange={(e) => setPositionTitle(e.target.value)}
            maxLength={90}
          />
        </FormField>
        <FormField id="status" label="Status" error={errors.status}>
          <StatusSelect id="status" value={status} onChange={setStatus} />
        </FormField>
        <FormField id="contactId" label="Contact" error={errors.contactId}>
          <select
            id="contactId"
            className="select"
            value={contactId ?? ''}
            onChange={(e) => setContactId(e.target.value ? Number(e.target.value) : null)}
          >
            <option value="">— no contact —</option>
            {contacts.map((c) => (
              <option key={c.id} value={c.id}>
                {[c.firstName, c.lastName].filter(Boolean).join(' ')}
              </option>
            ))}
          </select>
        </FormField>
        {!editing && (
          <FormField id="appliedOn" label="Applied on" hint="Leave empty for today" error={errors.appliedOn}>
            <input id="appliedOn" type="date" value={appliedOn} onChange={(e) => setAppliedOn(e.target.value)} />
          </FormField>
        )}
        {errors.form && <p className="error">{errors.form}</p>}
        <div className="actions">
          <button type="button" className="btn" onClick={onClose} disabled={submitting}>
            Cancel
          </button>
          <button type="submit" className="btn btn-primary" disabled={submitting}>
            {submitting ? 'Saving…' : 'Save'}
          </button>
        </div>
      </form>
    </Dialog>
  );
}
