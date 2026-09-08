import { useState, type FormEvent } from 'react';
import { ApiError, createContact, updateContact } from '../../api/client';
import type { ContactRequest, ContactView } from '../../api/types';
import Dialog from '../Dialog';
import FormField from './FormField';

interface Props {
  initial?: ContactView | null;
  onClose: () => void;
  onSaved: (saved: ContactView) => void;
}

/** Create or edit a contact; first name is the only required field. */
export default function ContactFormDialog({ initial, onClose, onSaved }: Props) {
  const editing = !!initial;
  const [firstName, setFirstName] = useState(initial?.firstName ?? '');
  const [lastName, setLastName] = useState(initial?.lastName ?? '');
  const [email, setEmail] = useState(initial?.email ?? '');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (!firstName.trim()) {
      setErrors({ firstName: 'required' });
      return;
    }
    setSubmitting(true);
    setErrors({});
    const body: ContactRequest = {
      firstName: firstName.trim(),
      lastName: lastName.trim() || null,
      email: email.trim() || null,
    };
    try {
      const saved = editing ? await updateContact(initial!.id, body) : await createContact(body);
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
    <Dialog title={editing ? 'Edit contact' : 'Add contact'} onClose={onClose}>
      <form onSubmit={onSubmit} noValidate>
        <FormField id="firstName" label="First name" error={errors.firstName}>
          <input id="firstName" value={firstName} onChange={(e) => setFirstName(e.target.value)} maxLength={45} autoFocus />
        </FormField>
        <FormField id="lastName" label="Last name" error={errors.lastName}>
          <input id="lastName" value={lastName} onChange={(e) => setLastName(e.target.value)} maxLength={45} />
        </FormField>
        <FormField id="email" label="Email" error={errors.email}>
          <input id="email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} maxLength={45} />
        </FormField>
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
