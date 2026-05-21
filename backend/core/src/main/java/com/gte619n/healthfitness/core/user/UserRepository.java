package com.gte619n.healthfitness.core.user;

import java.util.Optional;

public interface UserRepository {
    Optional<User> findById(String userId);
    void save(User user);
}
