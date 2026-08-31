// Mirrors the records in services/mvc-service/.../api - the wire contract.

export interface Me {
  fullName: string;
  email: string;
  /** track+<alias>@domain, or null until intake has assigned an alias. */
  intakeAddress: string | null;
}

export interface ApplicationView {
  id: number;
  companyName: string;
  positionTitle: string | null;
  status: string | null;
  /** ISO date (yyyy-mm-dd) of the first confirmation, or null. */
  appliedOn: string | null;
  contactName: string | null;
}
