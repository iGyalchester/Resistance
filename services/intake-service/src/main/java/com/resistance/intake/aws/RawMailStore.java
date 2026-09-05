package com.resistance.intake.aws;

import java.util.Optional;

/**
 * Fetches the raw MIME message SES archived in S3.
 *
 * <p>Exists because the notification SES publishes to SNS cannot always
 * carry the message itself. An {@code sns_action} embeds the base64 MIME in
 * the notification, but SNS refuses anything over 150 KB and SES bounces
 * the mail - so a confirmation email with a logo attached is rejected
 * outright. The {@code s3_action} archives every message regardless of size
 * (SES's own limit is 40 MB) and tells us where it put it, so reading it
 * back lifts the ceiling and leaves exactly one notification per mail.
 *
 * <p>An interface rather than the S3 client directly so the controller's
 * parsing can be tested without AWS, and so an unconfigured deployment gets
 * a no-op rather than a startup failure.
 */
public interface RawMailStore {

    /**
     * @return the raw MIME bytes, or empty when the object cannot be read -
     *         the caller then degrades to header-only parsing rather than
     *         dropping the mail.
     */
    Optional<byte[]> fetch(String bucket, String key);
}
