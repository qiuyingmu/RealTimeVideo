package com.realtimevideo.repository;

import com.realtimevideo.model.Channel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChannelRepository extends JpaRepository<Channel, Long> {

    List<Channel> findByDeviceSerialOrderByChannelNo(String deviceSerial);

    Optional<Channel> findByDeviceSerialAndChannelNo(String deviceSerial, Integer channelNo);

    void deleteByDeviceSerial(String deviceSerial);

    long countByDeviceSerial(String deviceSerial);
}
