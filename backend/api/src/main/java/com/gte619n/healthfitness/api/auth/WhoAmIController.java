package com.gte619n.healthfitness.api.auth;

import com.gte619n.healthfitness.core.auth.CurrentUser;
import com.gte619n.healthfitness.core.auth.CurrentUserProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
public class WhoAmIController {
    private final CurrentUserProvider currentUser;

    public WhoAmIController(CurrentUserProvider currentUser) {
        this.currentUser = currentUser;
    }

    @GetMapping
    public WhoAmIResponse whoAmI() {
        CurrentUser cu = currentUser.get();
        return new WhoAmIResponse(cu.userId(), cu.email(), cu.displayName());
    }
}
