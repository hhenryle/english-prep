package com.englishprep.api.identity.domain;

import java.util.Optional;

public interface UserRepository {

    /** Inserts when the user has no id, otherwise updates the existing one. Returns the saved user. */
    User save(User user);

    Optional<User> findById(Long id);

    Optional<User> findByEmail(String email);

    Optional<User> findByUserName(String userName);

    boolean existsByEmail(String email);

    boolean existsByUserName(String userName);
}
