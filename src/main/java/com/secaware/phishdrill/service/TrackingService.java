package com.secaware.phishdrill.service;

import com.secaware.phishdrill.domain.CampaignTarget;
import com.secaware.phishdrill.domain.TrackingEvent;
import com.secaware.phishdrill.repo.EventRepository;
import com.secaware.phishdrill.repo.TargetRepository;
import io.micronaut.transaction.annotation.Transactional;
import jakarta.inject.Singleton;

import java.time.Instant;

/**
 * 追踪状态机。四类事件 OPEN / CLICK / SUBMIT / REPORT 全部追加写入事件表，
 * target 上的阶段只进不退；同一事件重复触发只追加日志、不重复推进阶段。
 *
 * 安全约束：本服务的任何方法都不接收请求体或表单字段，
 * “提交凭证”事件只记类型，真实口令在控制器层即被丢弃，永不进入业务/持久层。
 */
@Singleton
public class TrackingService {

    public static final String OPEN = "OPEN";
    public static final String CLICK = "CLICK";
    public static final String SUBMIT = "SUBMIT";
    public static final String REPORT = "REPORT";

    private final TargetRepository targets;
    private final EventRepository events;
    private final TrainingService training;

    public TrackingService(TargetRepository targets, EventRepository events, TrainingService training) {
        this.targets = targets;
        this.events = events;
        this.training = training;
    }

    @Transactional
    public void record(CampaignTarget target, String type) {
        Instant now = Instant.now();
        events.save(new TrackingEvent(null, target.id(), type, now));

        switch (type) {
            case OPEN -> {
                if (target.openedAt() == null) {
                    targets.updateStatusAndOpenedAtById(target.id(), "OPENED", now);
                }
            }
            case CLICK -> {
                if (target.clickedAt() == null) {
                    targets.updateStatusAndClickedAtById(target.id(), "CLICKED", now);
                    // 首次点击自动分派短课（已分派过则不重复）
                    training.assignIfAbsent(target.employeeId(), target.id());
                }
            }
            case SUBMIT -> {
                if (target.submittedAt() == null) {
                    targets.updateStatusAndSubmittedAtById(target.id(), "SUBMITTED", now);
                }
            }
            case REPORT -> {
                if (target.reportedAt() == null) {
                    targets.updateReportedAtById(target.id(), now);
                }
            }
            default -> throw new IllegalArgumentException("未知事件类型: " + type);
        }
    }
}
