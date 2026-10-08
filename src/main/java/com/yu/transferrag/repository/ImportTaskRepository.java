package com.yu.transferrag.repository;

import com.yu.transferrag.entity.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.*;

public interface ImportTaskRepository extends JpaRepository<ImportTask, Long> {
    Optional<ImportTask> findByDocument_Id(Long documentId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from ImportTask t where t.id = :id")
    Optional<ImportTask> lockById(@Param("id") Long id);
    @Query(value = "SELECT CURRENT_TIMESTAMP(6)", nativeQuery = true)
    LocalDateTime databaseNow();
    @Query("select t.id from ImportTask t where t.status = :status and t.nextAttemptAt <= :now order by t.id")
    List<Long> readyIds(@Param("status") ImportTaskStatus status, @Param("now") LocalDateTime now, Pageable page);
    @Query("select t.id from ImportTask t where t.status = :status and t.leaseUntil <= :now order by t.id")
    List<Long> expiredIds(@Param("status") ImportTaskStatus status, @Param("now") LocalDateTime now, Pageable page);
}
