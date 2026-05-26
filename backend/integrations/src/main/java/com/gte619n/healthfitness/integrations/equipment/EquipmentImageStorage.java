package com.gte619n.healthfitness.integrations.equipment;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// Wraps GCS reads/writes for equipment images. Blobs live at
//   gs://{bucket}/equipment/{equipmentId}_{ts}.webp
// Each new upload uses a fresh timestamped object name so the public URL
// changes per generation. That lets us serve the resulting URLs with
// aggressive caching (immutable, long max-age) — clients never need to
// re-validate, because a regeneration produces a different URL entirely.
@Component
public class EquipmentImageStorage {

    private static final Logger log = LoggerFactory.getLogger(EquipmentImageStorage.class);

    private final Storage storage;
    private final String bucket;

    public EquipmentImageStorage(@Value("${app.equipment.bucket}") String bucket) {
        this.storage = StorageOptions.getDefaultInstance().getService();
        this.bucket = bucket;
    }

    public String upload(String equipmentId, byte[] imageBytes) {
        String objectName = versionedObjectName(equipmentId);
        BlobInfo info = BlobInfo.newBuilder(BlobId.of(bucket, objectName))
            .setContentType("image/webp")
            .setCacheControl("public, max-age=31536000, immutable")
            .build();
        storage.create(info, imageBytes);
        return publicUrl(objectName);
    }

    public void delete(String equipmentId) {
        storage.delete(BlobId.of(bucket, objectName(equipmentId)));
    }

    /**
     * Best-effort delete of a previously uploaded object by its public URL.
     * Used after a successful regeneration to clean up the old blob now
     * that the new (differently named) URL has been persisted.
     *
     * <p>Never throws — null/blank URLs, mismatched URL patterns, and
     * storage errors are all logged at WARN and swallowed. Worst case we
     * leave a stale object in GCS, which is harmless.
     */
    public void deleteByUrl(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        String prefix = "https://storage.googleapis.com/" + bucket + "/";
        if (!url.startsWith(prefix)) {
            log.warn("Skipping equipment image cleanup — URL does not match bucket prefix: {}", url);
            return;
        }
        String objectName = url.substring(prefix.length());
        // Strip any legacy ?v= query suffix in case old records still carry one.
        int q = objectName.indexOf('?');
        if (q >= 0) {
            objectName = objectName.substring(0, q);
        }
        if (objectName.isBlank()) {
            return;
        }
        try {
            storage.delete(BlobId.of(bucket, objectName));
        } catch (Exception e) {
            log.warn("Failed to delete stale equipment image {}: {}", objectName, e.getMessage());
        }
    }

    private static String objectName(String equipmentId) {
        return "equipment/" + equipmentId + ".webp";
    }

    private static String versionedObjectName(String equipmentId) {
        return "equipment/" + equipmentId + "_" + System.currentTimeMillis() + ".webp";
    }

    private String publicUrl(String objectName) {
        return "https://storage.googleapis.com/" + bucket + "/" + objectName;
    }
}
