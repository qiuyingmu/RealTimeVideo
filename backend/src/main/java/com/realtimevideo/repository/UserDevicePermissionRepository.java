package com.realtimevideo.repository;

import com.realtimevideo.model.UserDevicePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserDevicePermissionRepository extends JpaRepository<UserDevicePermission, Long> {

    List<UserDevicePermission> findByUserId(Long userId);

    List<UserDevicePermission> findByUsername(String username);

    List<UserDevicePermission> findByDeviceSerial(String deviceSerial);

    void deleteByUserIdAndDeviceSerial(Long userId, String deviceSerial);

    void deleteByUserId(Long userId);

    boolean existsByUserIdAndDeviceSerial(Long userId, String deviceSerial);

    List<UserDevicePermission> findByUserIdOrderByDeviceSerial(Long userId);
}
