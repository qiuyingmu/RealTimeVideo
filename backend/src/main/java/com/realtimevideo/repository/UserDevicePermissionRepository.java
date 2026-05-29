package com.realtimevideo.repository;

import com.realtimevideo.model.UserDevicePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserDevicePermissionRepository extends JpaRepository<UserDevicePermission, Long> {

    List<UserDevicePermission> findByUserId(Long userId);

    List<UserDevicePermission> findByUsername(String username);

    List<UserDevicePermission> findByDeviceSerial(String deviceSerial);

    @Modifying
    @Query("DELETE FROM UserDevicePermission p WHERE p.userId = :userId AND p.deviceSerial = :deviceSerial")
    void deleteByUserIdAndDeviceSerial(@Param("userId") Long userId, @Param("deviceSerial") String deviceSerial);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM UserDevicePermission p WHERE p.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    boolean existsByUserIdAndDeviceSerial(Long userId, String deviceSerial);

    List<UserDevicePermission> findByUserIdOrderByDeviceSerial(Long userId);
}
