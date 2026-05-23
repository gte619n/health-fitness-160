package com.gte619n.healthfitness.integrations.medication;

import com.google.genai.Client;
import com.google.genai.types.GenerateImagesConfig;
import com.google.genai.types.GenerateImagesResponse;
import com.google.genai.types.GeneratedImage;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Generates medication images using Google's Imagen model.
 * Uses the "still life photography" framing to avoid content filter issues
 * with pharmaceutical imagery.
 */
@Component
@ConditionalOnProperty(name = "app.medications.enabled", havingValue = "true", matchIfMissing = true)
public class DrugImageGenerator {

    /**
     * Photography prompt template following the product photography guide.
     * Uses still-life framing to avoid content filter rejections for medications.
     */
    private static final String PROMPT_TEMPLATE = """
        Professional still-life product photography of a single %s, centered on a clean
        white marble surface. Soft diffused natural lighting from upper left, creating
        gentle shadows. Shallow depth of field with f/2.8 aperture. Shot with a 100mm
        macro lens. Clean, minimal composition with negative space. Premium healthcare
        aesthetic. No text, no labels, no branding. High-end editorial style suitable
        for a health and wellness publication.
        """;

    /**
     * Form-specific subjects for the prompt.
     */
    private static String getSubject(String form, String drugName) {
        return switch (form) {
            case "INJECTABLE_VIAL" -> "clear glass medication vial with rubber stopper, containing clear liquid";
            case "TABLET" -> "small white round pharmaceutical tablet";
            case "CAPSULE" -> "two-tone gelatin capsule (half white, half clear)";
            case "SOFTGEL" -> "amber-colored oval softgel supplement";
            case "CREAM" -> "white pharmaceutical cream in a small open jar";
            case "PATCH" -> "beige transdermal patch on clean surface";
            case "LIQUID" -> "amber glass dropper bottle with clear liquid";
            case "POWDER" -> "white powder supplement in a small glass dish";
            default -> "pharmaceutical product";
        };
    }

    private final Client client;
    private final String model;

    public DrugImageGenerator(
        @Value("${app.medications.gemini-api-key:}") String apiKey,
        @Value("${app.medications.imagen-model}") String model
    ) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                "GEMINI_API_KEY is required for image generation");
        }
        this.client = Client.builder().apiKey(apiKey).build();
        this.model = model;
    }

    /**
     * Generate a medication image.
     *
     * @param drugName The name of the drug (for context)
     * @param form The physical form (INJECTABLE_VIAL, TABLET, etc.)
     * @return The generated image as PNG bytes, or empty if generation failed
     */
    public Optional<byte[]> generate(String drugName, String form) {
        String subject = getSubject(form, drugName);
        String prompt = String.format(PROMPT_TEMPLATE, subject);

        try {
            GenerateImagesConfig config = GenerateImagesConfig.builder()
                .numberOfImages(1)
                .aspectRatio("1:1")
                .build();

            GenerateImagesResponse response = client.models.generateImages(
                model, prompt, config);

            // Check if we got any images - generatedImages() returns Optional<List<>>
            var generatedImagesOpt = response.generatedImages();
            if (generatedImagesOpt.isEmpty()) {
                return Optional.empty();
            }

            var generatedImages = generatedImagesOpt.get();
            if (generatedImages.isEmpty()) {
                return Optional.empty();
            }

            GeneratedImage image = generatedImages.get(0);

            // Get the image bytes from the response - image() returns Optional<Image>
            var imageOpt = image.image();
            if (imageOpt.isEmpty()) {
                return Optional.empty();
            }

            var imageData = imageOpt.get();
            // imageBytes() returns Optional<byte[]>
            var imageBytesOpt = imageData.imageBytes();
            if (imageBytesOpt.isEmpty()) {
                return Optional.empty();
            }

            byte[] imageBytes = imageBytesOpt.get();
            if (imageBytes.length == 0) {
                return Optional.empty();
            }

            return Optional.of(imageBytes);

        } catch (Exception e) {
            // Log but don't throw - image generation is optional
            System.err.println("Image generation failed for " + drugName + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Get a fallback image URL based on drug form.
     * These are pre-generated static images stored in GCS.
     *
     * @param form The physical form
     * @return URL to the fallback image
     */
    public static String getFallbackUrl(String form, String bucket) {
        String filename = switch (form) {
            case "INJECTABLE_VIAL" -> "fallback-vial.png";
            case "TABLET" -> "fallback-tablet.png";
            case "CAPSULE" -> "fallback-capsule.png";
            case "SOFTGEL" -> "fallback-softgel.png";
            case "CREAM" -> "fallback-cream.png";
            case "PATCH" -> "fallback-patch.png";
            case "LIQUID" -> "fallback-liquid.png";
            case "POWDER" -> "fallback-powder.png";
            default -> "fallback-generic.png";
        };
        return "https://storage.googleapis.com/" + bucket + "/fallbacks/" + filename;
    }
}
