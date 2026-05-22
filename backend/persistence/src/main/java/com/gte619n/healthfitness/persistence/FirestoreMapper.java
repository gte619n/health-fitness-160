package com.gte619n.healthfitness.persistence;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.FieldValue;
import java.time.Instant;
import java.time.LocalDate;

public final class FirestoreMapper {

    private FirestoreMapper() {}

    public static Instant toInstant(Object value) {
        if (value == null) return null;
        if (value instanceof Timestamp ts) return ts.toDate().toInstant();
        throw new IllegalArgumentException(
            "Expected Firestore Timestamp, got " + value.getClass().getName());
    }

    public static Object serverTimestamp() {
        return FieldValue.serverTimestamp();
    }

    public static String toDocumentId(LocalDate date) {
        return date.toString();
    }

    public static LocalDate fromDocumentId(String documentId) {
        return LocalDate.parse(documentId);
    }
}
