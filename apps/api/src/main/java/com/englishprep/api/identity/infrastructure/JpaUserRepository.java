package com.englishprep.api.identity.infrastructure;

import com.englishprep.api.identity.domain.User;
import com.englishprep.api.identity.domain.UserRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class JpaUserRepository implements UserRepository {

    private final SpringDataUserRepository jpa;

    JpaUserRepository(SpringDataUserRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public User save(User user) {
        UserEntity entity;
        if (user.id() == null) {
            entity = new UserEntity(user.firstName(), user.lastName(), user.userName(), user.email(),
                    user.passwordHash(), user.timeZone());
        } else {
            // Load the existing row and change it, so the audit columns (created_at...) are kept.
            entity = jpa.findById(user.id())
                    .orElseThrow(() -> new IllegalStateException("User not found: " + user.id()));
            entity.update(user.firstName(), user.lastName(), user.userName(), user.email(),
                    user.passwordHash(), user.timeZone());
        }
        return toDomain(jpa.save(entity));
    }

    @Override
    public Optional<User> findById(Long id) {
        return jpa.findById(id).map(JpaUserRepository::toDomain);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return jpa.findByEmailIgnoreCase(email).map(JpaUserRepository::toDomain);
    }

    @Override
    public Optional<User> findByUserName(String userName) {
        return jpa.findByUserNameIgnoreCase(userName).map(JpaUserRepository::toDomain);
    }

    @Override
    public boolean existsByEmail(String email) {
        return jpa.existsByEmailIgnoreCase(email);
    }

    @Override
    public boolean existsByUserName(String userName) {
        return jpa.existsByUserNameIgnoreCase(userName);
    }

    private static User toDomain(UserEntity e) {
        return new User(e.getId(), e.getFirstName(), e.getLastName(), e.getUserName(), e.getEmail(),
                e.getPasswordHash(), e.getTimeZone(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
