package com.englishprep.api.identity.infrastructure;

import com.englishprep.api.identity.domain.User;
import com.englishprep.api.identity.domain.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class JpaUserRepository implements UserRepository {

    private final SpringDataUserRepository jpa;

    JpaUserRepository(SpringDataUserRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public User save(User user) {
        return toDomain(jpa.save(toEntity(user)));
    }

    @Override
    public Optional<User> findById(UUID id) {
        return jpa.findById(id).map(JpaUserRepository::toDomain);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return jpa.findByEmailIgnoreCase(email).map(JpaUserRepository::toDomain);
    }

    @Override
    public boolean existsByEmail(String email) {
        return jpa.existsByEmailIgnoreCase(email);
    }

    private static UserEntity toEntity(User user) {
        return new UserEntity(user.id(), user.email(), user.passwordHash(), user.timeZone(),
                user.createdAt(), user.updatedAt());
    }

    private static User toDomain(UserEntity entity) {
        return new User(entity.getId(), entity.getEmail(), entity.getPasswordHash(),
                entity.getTimeZone(), entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
