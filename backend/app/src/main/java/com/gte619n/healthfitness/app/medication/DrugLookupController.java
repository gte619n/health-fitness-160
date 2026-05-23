package com.gte619n.healthfitness.app.medication;

import com.gte619n.healthfitness.api.medication.DrugResponse;
import com.gte619n.healthfitness.core.medication.Drug;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for drug lookup operations.
 * Uses AI with Google Search grounding to find and classify drugs.
 * Endpoints: /api/drugs/lookup
 */
@RestController
@RequestMapping("/api/drugs")
@ConditionalOnProperty(name = "app.medications.enabled", havingValue = "true", matchIfMissing = true)
public class DrugLookupController {

    private final DrugCatalogService catalogService;

    public DrugLookupController(DrugCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    /**
     * Look up a drug using AI with Google Search grounding.
     * If the drug exists in the catalog, returns the existing entry.
     * If not found in catalog, uses AI to search for information and
     * creates a new catalog entry with AI-generated metadata.
     *
     * @param body The lookup request containing the search query
     * @return The drug information (existing or newly created)
     */
    @PostMapping("/lookup")
    public ResponseEntity<DrugLookupResponse> lookup(@RequestBody LookupRequest body) {
        if (body.query() == null || body.query().isBlank()) {
            throw new IllegalArgumentException("query is required");
        }

        Optional<Drug> result = catalogService.lookupOrCreate(body.query());
        if (result.isEmpty()) {
            return ResponseEntity.ok(new DrugLookupResponse(
                false,
                null,
                "No drug found matching: " + body.query()
            ));
        }

        return ResponseEntity.ok(new DrugLookupResponse(
            true,
            DrugResponse.from(result.get()),
            null
        ));
    }

    /**
     * Search the drug catalog by name/alias.
     * This is a fast local search - does not use AI.
     */
    @GetMapping("/search")
    public List<DrugResponse> search(@RequestParam String q) {
        return catalogService.search(q).stream()
            .map(DrugResponse::from)
            .toList();
    }

    /**
     * Trigger image regeneration for a drug.
     */
    @PostMapping("/{drugId}/regenerate-image")
    public ResponseEntity<Void> regenerateImage(@PathVariable String drugId) {
        catalogService.regenerateImage(drugId);
        return ResponseEntity.accepted().build();
    }

    // Request/Response DTOs

    public record LookupRequest(String query) {}

    public record DrugLookupResponse(
        boolean found,
        DrugResponse drug,
        String message
    ) {}
}
