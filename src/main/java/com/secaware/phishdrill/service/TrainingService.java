package com.secaware.phishdrill.service;

import com.secaware.phishdrill.domain.TrainingAssignment;
import com.secaware.phishdrill.repo.TrainingRepository;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 培训分派：员工首次点击钓鱼链接时自动生成一节短课，一人只派一次。
 */
@Singleton
public class TrainingService {

    private final TrainingRepository repo;

    public TrainingService(TrainingRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public void assignIfAbsent(Long employeeId, Long targetId) {
        if (repo.findByEmployeeId(employeeId).isPresent()) {
            return;
        }
        repo.save(new TrainingAssignment(null, employeeId, targetId, Instant.now(), null));
    }

    public List<TrainingAssignment> listAll() {
        return repo.findAllOrderByAssignedAtDesc();
    }

    public Optional<TrainingAssignment> forEmployee(Long employeeId) {
        return repo.findByEmployeeId(employeeId);
    }

    @Transactional
    public void complete(Long assignmentId) {
        repo.updateCompletedAtById(assignmentId, Instant.now());
    }

    public long completedCount() {
        return repo.countByCompletedAtIsNotNull();
    }
}
