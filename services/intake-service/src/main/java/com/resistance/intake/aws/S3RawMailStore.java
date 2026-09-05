package com.resistance.intake.aws;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.util.Optional;

/**
 * Reads the archived MIME from the bucket SES writes to. Credentials come
 * from the default chain, which on ECS is the task role - no keys anywhere.
 */
public class S3RawMailStore implements RawMailStore {

    private static final Logger log = LoggerFactory.getLogger(S3RawMailStore.class);

    /**
     * SES's own maximum message size. Reading is bounded so a wrong or
     * hostile object key cannot pull an arbitrarily large object into the
     * task's heap; the fetch is into memory because the parser wants the
     * whole message anyway.
     */
    static final long MAX_BYTES = 40L * 1024 * 1024;

    private final S3Client s3Client;

    public S3RawMailStore(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    @Override
    public Optional<byte[]> fetch(String bucket, String key) {
        if (bucket == null || bucket.isBlank() || key == null || key.isBlank()) {
            return Optional.empty();
        }
        try {
            ResponseBytes<GetObjectResponse> object = s3Client.getObjectAsBytes(
                    GetObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            // bytes 0..MAX_BYTES-1; a larger object is
                            // truncated rather than refused, since a partial
                            // MIME still yields headers
                            .range("bytes=0-" + (MAX_BYTES - 1))
                            .build());
            return Optional.of(object.asByteArray());
        } catch (Exception e) {
            // The mail still gets filed from its headers. Losing the body is
            // bad; losing the application entirely would be worse.
            log.warn("Could not read raw mail s3://{}/{}: {}", bucket, key, e.toString());
            return Optional.empty();
        }
    }
}
