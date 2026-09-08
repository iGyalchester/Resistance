import { useEffect, useState, type FormEvent } from 'react';
import { ApiError, fetchProfile, updateProfile } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import ErrorBanner from '../components/ErrorBanner';
import FormField from '../components/forms/FormField';
import { useToast } from '../components/Toast';
import { useAsync } from '../hooks/useAsync';

/**
 * The fields intake cannot learn from emails: your name and a phone
 * number (encrypted at rest). Email is your identity and is read-only.
 */
export default function ProfilePage() {
  const { data, error, loading, reload } = useAsync(fetchProfile);
  const { me, setMe } = useAuth();
  const { notify } = useToast();
  const [fullName, setFullName] = useState('');
  const [phone, setPhone] = useState('');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (data) {
      setFullName(data.fullName ?? '');
      setPhone(data.phone ?? '');
    }
  }, [data]);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (!fullName.trim()) {
      setErrors({ fullName: 'required' });
      return;
    }
    setSaving(true);
    setErrors({});
    try {
      const saved = await updateProfile({ fullName: fullName.trim(), phone: phone.trim() || null });
      if (me) setMe({ ...me, fullName: saved.fullName });
      notify('Profile saved');
    } catch (e) {
      if (e instanceof ApiError && Object.keys(e.fields).length > 0) {
        setErrors(e.fields);
      } else {
        setErrors({ form: 'Could not save. Try again.' });
      }
    } finally {
      setSaving(false);
    }
  }

  return (
    <>
      <h1>Profile</h1>
      <section className="card narrow-left">
        {error && <ErrorBanner message={error} onRetry={reload} />}
        {!error && loading && <p className="muted">Loading…</p>}
        {data && (
          <form onSubmit={onSubmit} noValidate>
            <FormField id="email" label="Email" hint="Your login and where codes are sent. Not editable.">
              <input id="email" value={data.email} readOnly />
            </FormField>
            <FormField id="fullName" label="Full name" error={errors.fullName}>
              <input id="fullName" value={fullName} onChange={(e) => setFullName(e.target.value)} maxLength={90} />
            </FormField>
            <FormField id="phone" label="Phone" hint="Stored encrypted." error={errors.phone}>
              <input id="phone" value={phone} onChange={(e) => setPhone(e.target.value)} maxLength={40} />
            </FormField>
            {errors.form && <p className="error">{errors.form}</p>}
            <div className="actions">
              <button type="submit" className="btn btn-primary" disabled={saving}>
                {saving ? 'Saving…' : 'Save'}
              </button>
            </div>
          </form>
        )}
      </section>
    </>
  );
}
