package com.gte619n.healthfitness.googlehealth;

import com.gte619n.healthfitness.core.bodycomposition.BodyCompositionMeasurement;
import com.gte619n.healthfitness.core.bodycomposition.BodyCompositionRepository;
import com.gte619n.healthfitness.core.user.User;
import com.gte619n.healthfitness.core.user.UserRepository;
import com.gte619n.healthfitness.integrations.googlehealth.GoogleHealthClient;
import com.gte619n.healthfitness.integrations.googlehealth.GoogleHealthDataPoint;
import com.gte619n.healthfitness.integrations.googlehealth.GoogleHealthDataType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

// Handles a single webhook notification once the controller has parsed
// it. Idempotent on UPSERT (record IDs are stable Firestore doc keys);
// DELETE removes everything in the notification's interval for the
// reported data type.
@Service
public class WebhookHandlerService {

    private static final Logger log = LoggerFactory.getLogger(WebhookHandlerService.class);

    private final UserRepository users;
    private final BodyCompositionRepository measurements;
    private final AccessTokenService tokens;
    private final GoogleHealthClient googleHealth;

    public WebhookHandlerService(
        UserRepository users,
        BodyCompositionRepository measurements,
        AccessTokenService tokens,
        GoogleHealthClient googleHealth
    ) {
        this.users = users;
        this.measurements = measurements;
        this.tokens = tokens;
        this.googleHealth = googleHealth;
    }

    public void handle(Notification notification) {
        Optional<User> match = users.findByHealthUserId(notification.healthUserId);
        if (match.isEmpty()) {
            // Notification arrived before we had a chance to record the
            // healthUserId. Drop on the floor; Google will retry for 7
            // days and the connect endpoint backfills regardless.
            log.warn("Webhook notification for unknown healthUserId={}",
                notification.healthUserId);
            return;
        }
        String userId = match.get().userId();
        switch (notification.operation) {
            case UPSERT -> handleUpsert(userId, notification);
            case DELETE -> handleDelete(userId, notification);
        }
    }

    private void handleUpsert(String userId, Notification n) {
        String accessToken = tokens.accessTokenFor(userId);

        List<GoogleHealthDataPoint> all = new ArrayList<>(
            googleHealth.listDataPoints(accessToken, n.dataType, n.intervalStart, n.intervalEnd));

        // Lean mass and BMI aren't accepted as webhook-subscribable types,
        // but smart scales emit them in the same weigh-in as weight. When
        // we get a weight notification, opportunistically pull both for the
        // same interval so the user's lean-mass + BMI history stays current
        // without a separate poller. Triggering on weight (and not body-fat
        // too) keeps this to one bonus pull per weigh-in — body-fat
        // notifications for the same event would otherwise double-pull.
        if (n.dataType == GoogleHealthDataType.WEIGHT) {
            all.addAll(googleHealth.listDataPoints(
                accessToken, GoogleHealthDataType.LEAN_MASS, n.intervalStart, n.intervalEnd));
            all.addAll(googleHealth.listDataPoints(
                accessToken, GoogleHealthDataType.BMI, n.intervalStart, n.intervalEnd));
        }

        List<BodyCompositionMeasurement> measurementsList = all.stream()
            .map(dp -> toMeasurement(userId, dp))
            .toList();
        measurements.saveAll(measurementsList);
        log.info("Webhook UPSERT user={} type={} stored={}",
            userId, n.dataType, measurementsList.size());
    }

    private void handleDelete(String userId, Notification n) {
        measurements.deleteByUserMetricAndRange(
            userId, n.dataType.toMetric(), n.intervalStart, n.intervalEnd);
        log.info("Webhook DELETE user={} type={} range=[{},{}]",
            userId, n.dataType, n.intervalStart, n.intervalEnd);
    }

    private static BodyCompositionMeasurement toMeasurement(String userId, GoogleHealthDataPoint dp) {
        return new BodyCompositionMeasurement(
            userId,
            dp.recordId(),
            dp.dataType().toMetric(),
            dp.value(),
            dp.sampleTime(),
            dp.sourcePlatform(),
            dp.recordingMethod(),
            null,
            null
        );
    }

    public enum Operation { UPSERT, DELETE }

    public record Notification(
        String healthUserId,
        GoogleHealthDataType dataType,
        Operation operation,
        Instant intervalStart,
        Instant intervalEnd
    ) {}
}
