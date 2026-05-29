package com.realtimevideo.repository;

import com.realtimevideo.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    @Modifying
    @Query("UPDATE User u SET u.failedLoginAttempts = u.failedLoginAttempts + 1 WHERE u.username = :username")
    void incrementFailedAttempts(@Param("username") String username);

    @Modifying
    @Query("UPDATE User u SET u.failedLoginAttempts = 0, u.lockTime = NULL, u.accountNonLocked = true WHERE u.username = :username")
    void resetFailedAttempts(@Param("username") String username);

    @Modifying
    @Query("UPDATE User u SET u.failedLoginAttempts = :attempts, u.lockTime = :lockTime, u.accountNonLocked = false WHERE u.username = :username")
    void lockAccount(@Param("username") String username,
                     @Param("attempts") int attempts,
                     @Param("lockTime") LocalDateTime lockTime);

    @Modifying
    @Query("UPDATE User u SET u.lastLoginAt = :time WHERE u.username = :username")
    void updateLastLogin(@Param("username") String username, @Param("time") LocalDateTime time);

    @Modifying
    @Query("UPDATE User u SET u.password = :password, u.passwordChangeRequired = false WHERE u.username = :username")
    void updatePassword(@Param("username") String username, @Param("password") String password);
}
