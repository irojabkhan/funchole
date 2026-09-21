package com.funchole.backend.controlplane.service;

import com.funchole.backend.controlplane.config.ArtifactPublisherProperties;
import com.funchole.backend.controlplane.entity.SourceFile;
import com.funchole.backend.core.base.exception.ResourceNotFoundException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * {@link SourceStore} backed by the same S3-compatible store (and the same
 * credentials/bucket, {@code app.artifact.*}) that already holds published
 * build artifacts - just a different key prefix
 * ({@code sources/<functionVersionId>/<relativePath>} vs.
 * {@code artifacts/<functionVersionId>/artifact.tar.gz}). Reusing the
 * existing bucket means an operator opting into this store doesn't need any
 * new credentials or configuration beyond what artifact publishing already
 * required - see {@code SourceStoreConfig}.
 *
 * <p>Unlike {@link LocalSourceStore}, this survives a {@code controlplane}
 * container being recreated: the durability an operator already has for
 * build artifacts now also covers the source those artifacts were built
 * from.
 */
public class S3SourceStore implements SourceStore {

    private static final String KEY_PREFIX = "sources/";

    private final S3Client s3Client;
    private final String bucket;

    public S3SourceStore(ArtifactPublisherProperties properties) {
        this.s3Client = S3Client.builder()
                .endpointOverride(properties.endpoint())
                .region(Region.of(properties.region() == null || properties.region().isBlank()
                        ? "us-east-1" : properties.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(properties.pathStyleAccess())
                        .build())
                .build();
        this.bucket = properties.bucket();
    }

    @Override
    public void save(UUID functionVersionId, List<SourceFile> files) {
        Set<String> newKeys = new HashSet<>();
        for (SourceFile file : files) {
            String key = objectKey(functionVersionId, file.relativePath());
            newKeys.add(key);
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType("text/plain; charset=utf-8")
                            .build(),
                    RequestBody.fromString(file.content(), StandardCharsets.UTF_8)
            );
        }

        // Replace-the-whole-set semantics (see SourceStore's own contract):
        // delete anything left over from a prior submission that isn't part
        // of this one, so a resubmission that removes a file doesn't leave
        // a stale object behind.
        String prefix = versionPrefix(functionVersionId);
        ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                .bucket(bucket)
                .prefix(prefix)
                .build();
        ListObjectsV2Response listResponse = s3Client.listObjectsV2(listRequest);
        for (S3Object existing : listResponse.contents()) {
            if (!newKeys.contains(existing.key())) {
                s3Client.deleteObject(DeleteObjectRequest.builder()
                        .bucket(bucket)
                        .key(existing.key())
                        .build());
            }
        }
    }

    @Override
    public List<SourceFile> load(UUID functionVersionId, List<String> relativePaths) {
        List<SourceFile> files = new ArrayList<>();
        for (String relativePath : relativePaths) {
            String key = objectKey(functionVersionId, relativePath);
            try {
                String content = s3Client.getObject(
                        GetObjectRequest.builder().bucket(bucket).key(key).build(),
                        ResponseTransformer.toBytes()
                ).asUtf8String();
                files.add(new SourceFile(relativePath, content));
            } catch (NoSuchKeyException exception) {
                // Same failure mode/contract as LocalSourceStore: the
                // Postgres-side manifest can outlive the actual object
                // (bucket lifecycle policy, manual deletion, etc.) - fail
                // with a clear, specific 404 rather than an opaque 500.
                throw new ResourceNotFoundException(
                        "Source content is no longer available for function version " + functionVersionId
                                + " (file " + relativePath + " is missing from storage, though its metadata survives)");
            }
        }
        return files;
    }

    private String versionPrefix(UUID functionVersionId) {
        return KEY_PREFIX + functionVersionId + "/";
    }

    private String objectKey(UUID functionVersionId, String relativePath) {
        return versionPrefix(functionVersionId) + relativePath;
    }
}
