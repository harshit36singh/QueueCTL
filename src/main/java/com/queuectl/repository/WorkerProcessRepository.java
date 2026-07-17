package com.queuectl.repository;

import com.queuectl.domain.WorkerProcess;
import com.queuectl.domain.WorkerStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkerProcessRepository extends JpaRepository<WorkerProcess, String> {

    List<WorkerProcess> findByStatusIn(List<WorkerStatus> statuses);
}
