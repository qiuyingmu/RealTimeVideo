package com.realtimevideo.repository;

import com.realtimevideo.model.OperationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OperationLogRepository extends JpaRepository<OperationLog, Long> {

    Page<OperationLog> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<OperationLog> findByActionOrderByCreatedAtDesc(String action, Pageable pageable);

    Page<OperationLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<OperationLog> findByUsernameOrderByCreatedAtDesc(String username, Pageable pageable);

    Page<OperationLog> findByActionAndUsernameOrderByCreatedAtDesc(String action, String username, Pageable pageable);
}
