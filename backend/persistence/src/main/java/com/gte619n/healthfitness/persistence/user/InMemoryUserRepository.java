package com.gte619n.healthfitness.persistence.user;

import com.gte619n.healthfitness.core.user.User;
import com.gte619n.healthfitness.core.user.UserRepository;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

// IMPL-02 placeholder. Replaced with a Firestore-backed implementation in
// IMPL-03 alongside the rest of the persistence layer. Keeping the shape
// faithful (Optional<User>, idempotent save) so the switch is a one-class swap.
@Repository
public class InMemoryUserRepository implements UserRepository {
    private final Map<String, User> store = new ConcurrentHashMap<>();

    @Override
    public Optional<User> findById(String userId) {
        return Optional.ofNullable(store.get(userId));
    }

    @Override
    public void save(User user) {
        store.put(user.userId(), user);
    }
}
