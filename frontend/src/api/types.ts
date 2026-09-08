// Mirrors the records in services/mvc-service/.../api - the wire contract.
// The server omits null fields from JSON, so nullable fields may also be
// absent; read them with `?? null`.

export interface Me {
  fullName: string;
  email: string;
  /** track+<alias>@domain, or null until intake has assigned an alias. */
  intakeAddress?: string | null;
  /** ROLE_* authorities without the prefix; always at least USER. */
  roles: string[];
  /** Optional capabilities this deployment has switched on. */
  features: { assistant: boolean };
}

export const STATUSES = [
  'APPLIED',
  'SCREENING',
  'INTERVIEW',
  'OFFER',
  'ACCEPTED',
  'REJECTED',
  'WITHDRAWN',
] as const;

export type ApplicationStatus = (typeof STATUSES)[number];

/** Statuses that mean the application is still in play. */
export const ACTIVE_STATUSES: ApplicationStatus[] = ['APPLIED', 'SCREENING', 'INTERVIEW', 'OFFER'];

export interface ApplicationView {
  id: number;
  companyName: string;
  positionTitle?: string | null;
  status?: string | null;
  /** ISO date (yyyy-mm-dd) of the first confirmation, or null. */
  appliedOn?: string | null;
  contactId?: number | null;
  contactName?: string | null;
}

export interface StatusChangeView {
  /** null for the creation event */
  fromStatus?: string | null;
  toStatus: string;
  /** ISO instant */
  changedAt: string;
  source: 'INTAKE' | 'MANUAL' | string;
}

export interface ApplicationDetailView extends ApplicationView {
  updatedAt?: string | null;
  history: StatusChangeView[];
}

export interface ApplicationRequest {
  companyName: string;
  positionTitle: string | null;
  status: ApplicationStatus;
  contactId: number | null;
  /** honoured on create only */
  appliedOn?: string | null;
}

export interface ContactView {
  id: number;
  firstName: string;
  lastName?: string | null;
  email?: string | null;
  applicationCount: number;
}

export interface ContactRequest {
  firstName: string;
  lastName: string | null;
  email: string | null;
}

export interface ProfileView {
  fullName: string;
  email: string;
  phone?: string | null;
}

export interface ProfileRequest {
  fullName: string;
  phone: string | null;
}
