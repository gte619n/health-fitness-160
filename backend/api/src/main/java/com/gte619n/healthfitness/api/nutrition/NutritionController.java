package com.gte619n.healthfitness.api.nutrition;

import com.gte619n.healthfitness.core.auth.CurrentUserProvider;
import com.gte619n.healthfitness.core.nutrition.NutritionDailyLog;
import com.gte619n.healthfitness.core.nutrition.NutritionService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me/nutrition")
public class NutritionController {

    private final CurrentUserProvider currentUser;
    private final NutritionService nutrition;

    public NutritionController(CurrentUserProvider currentUser, NutritionService nutrition) {
        this.currentUser = currentUser;
        this.nutrition = nutrition;
    }

    @PostMapping
    public ResponseEntity<LogResponse> upsert(@RequestBody LogRequest body) {
        if (body == null || body.date() == null) {
            throw new IllegalArgumentException("date is required");
        }
        String userId = currentUser.get().userId();
        NutritionDailyLog log = nutrition.logDay(
            userId,
            body.date(),
            body.proteinGrams(),
            body.carbsGrams(),
            body.fatGrams(),
            body.caloriesKcal());
        return ResponseEntity.status(201).body(LogResponse.from(log));
    }

    @GetMapping
    public List<LogResponse> list(
        @RequestParam(required = false) LocalDate from,
        @RequestParam(required = false) LocalDate to
    ) {
        String userId = currentUser.get().userId();
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.minusDays(6);
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("from must not be after to");
        }
        return nutrition.findRange(userId, start, end).stream()
            .map(LogResponse::from)
            .toList();
    }

    @GetMapping("/today")
    public ResponseEntity<LogResponse> today() {
        String userId = currentUser.get().userId();
        return nutrition.findByDate(userId, LocalDate.now())
            .map(log -> ResponseEntity.ok(LogResponse.from(log)))
            .orElseGet(() -> ResponseEntity.noContent().build());
    }

    public record LogRequest(
        LocalDate date,
        Double proteinGrams,
        Double carbsGrams,
        Double fatGrams,
        Double caloriesKcal
    ) {}

    public record LogResponse(
        LocalDate date,
        Double proteinGrams,
        Double carbsGrams,
        Double fatGrams,
        Double caloriesKcal
    ) {
        public static LogResponse from(NutritionDailyLog log) {
            return new LogResponse(
                log.date(),
                log.proteinGrams(),
                log.carbsGrams(),
                log.fatGrams(),
                log.caloriesKcal());
        }
    }
}
