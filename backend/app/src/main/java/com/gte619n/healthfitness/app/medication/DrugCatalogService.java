package com.gte619n.healthfitness.app.medication;

import com.gte619n.healthfitness.core.medication.Drug;
import com.gte619n.healthfitness.core.medication.DrugCategory;
import com.gte619n.healthfitness.core.medication.DrugForm;
import com.gte619n.healthfitness.core.medication.DrugRepository;
import com.gte619n.healthfitness.integrations.medication.DrugImageGenerator;
import com.gte619n.healthfitness.integrations.medication.DrugImageStorage;
import com.gte619n.healthfitness.integrations.medication.DrugLookupService;
import com.gte619n.healthfitness.integrations.medication.DrugLookupService.DrugLookupResult;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Orchestrates drug catalog operations including:
 * - AI-powered drug lookup with Google Search grounding
 * - Automatic image generation using Imagen
 * - Catalog deduplication
 */
@Service
@ConditionalOnProperty(name = "app.medications.enabled", havingValue = "true", matchIfMissing = true)
public class DrugCatalogService {

    private final DrugRepository drugs;
    private final DrugLookupService lookupService;
    private final DrugImageGenerator imageGenerator;
    private final DrugImageStorage imageStorage;
    private final String bucket;

    public DrugCatalogService(
        DrugRepository drugs,
        DrugLookupService lookupService,
        DrugImageGenerator imageGenerator,
        DrugImageStorage imageStorage,
        @Value("${app.medications.bucket}") String bucket
    ) {
        this.drugs = drugs;
        this.lookupService = lookupService;
        this.imageGenerator = imageGenerator;
        this.imageStorage = imageStorage;
        this.bucket = bucket;
    }

    /**
     * Search the existing drug catalog by name.
     */
    public List<Drug> search(String query) {
        if (query == null || query.isBlank()) {
            return drugs.findAll();
        }
        return drugs.search(query);
    }

    /**
     * Look up a drug using AI with Google Search grounding.
     * If the drug exists in the catalog, returns the existing entry.
     * If not, creates a new entry with AI-generated metadata.
     *
     * @param query User's search query
     * @return The drug (existing or newly created), or empty if not found
     */
    public Optional<Drug> lookupOrCreate(String query) {
        // First, check if we have an exact or close match in the catalog
        List<Drug> existing = drugs.search(query);
        if (!existing.isEmpty()) {
            // Return the best match
            return Optional.of(existing.get(0));
        }

        // No match found - use AI to look up the drug
        DrugLookupResult result = lookupService.lookup(query);
        if (result == null) {
            return Optional.empty();
        }

        // Check again by canonical name (in case user searched by alias)
        existing = drugs.search(result.name());
        if (!existing.isEmpty()) {
            return Optional.of(existing.get(0));
        }

        // Create new drug entry
        String drugId = UUID.randomUUID().toString();
        String fallbackUrl = DrugImageGenerator.getFallbackUrl(result.form(), bucket);

        Drug drug = new Drug(
            drugId,
            result.name(),
            result.aliases() != null ? result.aliases() : List.of(),
            parseCategory(result.category()),
            parseForm(result.form()),
            result.defaultUnit() != null ? result.defaultUnit() : "mg",
            result.commonDoses() != null ? result.commonDoses() : List.of(),
            null,  // imageUrl - will be set async
            fallbackUrl,
            result.suggestedMarkers() != null ? result.suggestedMarkers() : List.of(),
            result.description(),
            Instant.now(),
            Instant.now()
        );

        drugs.save(drug);

        // Generate image asynchronously
        generateImageAsync(drugId, result.name(), result.form());

        return Optional.of(drug);
    }

    /**
     * Get a drug by ID.
     */
    public Optional<Drug> findById(String drugId) {
        return drugs.findById(drugId);
    }

    /**
     * Get all drugs in the catalog.
     */
    public List<Drug> findAll() {
        return drugs.findAll();
    }

    /**
     * Manually trigger image generation for a drug.
     */
    public void regenerateImage(String drugId) {
        Drug drug = drugs.findById(drugId)
            .orElseThrow(() -> new IllegalArgumentException("Drug not found: " + drugId));
        generateImageAsync(drugId, drug.name(), drug.form().name());
    }

    /**
     * Generate and upload drug image asynchronously.
     */
    private void generateImageAsync(String drugId, String drugName, String form) {
        CompletableFuture.runAsync(() -> {
            try {
                Optional<byte[]> imageBytes = imageGenerator.generate(drugName, form);
                if (imageBytes.isPresent()) {
                    String imageUrl = imageStorage.upload(drugId, imageBytes.get());

                    // Update drug with image URL
                    drugs.findById(drugId).ifPresent(drug -> {
                        Drug updated = new Drug(
                            drug.drugId(),
                            drug.name(),
                            drug.aliases(),
                            drug.category(),
                            drug.form(),
                            drug.defaultUnit(),
                            drug.commonDoses(),
                            imageUrl,
                            drug.imageFallback(),
                            drug.suggestedMarkers(),
                            drug.description(),
                            drug.createdAt(),
                            Instant.now()
                        );
                        drugs.save(updated);
                    });
                }
            } catch (Exception e) {
                System.err.println("Failed to generate image for drug " + drugId + ": " + e.getMessage());
            }
        });
    }

    private DrugCategory parseCategory(String category) {
        if (category == null) return DrugCategory.SUPPLEMENT;
        try {
            return DrugCategory.valueOf(category);
        } catch (IllegalArgumentException e) {
            return DrugCategory.SUPPLEMENT;
        }
    }

    private DrugForm parseForm(String form) {
        if (form == null) return DrugForm.TABLET;
        try {
            return DrugForm.valueOf(form);
        } catch (IllegalArgumentException e) {
            return DrugForm.TABLET;
        }
    }
}
