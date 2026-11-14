package com.pantrypilot.ai.internal.jobs;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AiJobRepository extends JpaRepository<AiJob, Long> {

    /** Loads a job and locks its row until the transaction ends, so two threads cannot finish it twice. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from AiJob j where j.id = :id")
    Optional<AiJob> findForUpdate(@Param("id") long id);

    List<AiJob> findTop20ByHouseholdIdOrderByIdDesc(long householdId);

    List<AiJob> findByStatusIn(Collection<JobStatus> statuses);
}
