package com.realtimevideo.repository;

import com.realtimevideo.model.Channel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChannelRepository extends JpaRepository<Channel, Long> {

    List<Channel> findByDeviceSerialOrderByChannelNo(String deviceSerial);

    Optional<Channel> findByDeviceSerialAndChannelNo(String deviceSerial, Integer channelNo);

    @Modifying
    @Transactional
    void deleteByDeviceSerial(String deviceSerial);

    long countByDeviceSerial(String deviceSerial);

    @Query("SELECT DISTINCT c.deviceSerial FROM Channel c")
    List<String> findDistinctDeviceSerials();

    @Modifying
    @Transactional
    @Query("UPDATE Channel c SET c.status = :status WHERE c.deviceSerial = :deviceSerial AND c.channelNo = :channelNo")
    int updateStatus(String deviceSerial, Integer channelNo, String status);
}
