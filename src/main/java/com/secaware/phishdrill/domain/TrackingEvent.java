package com.secaware.phishdrill.domain;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

import java.time.Instant;

/**
 * 追加型追踪事件。只记录事件类型（OPEN/CLICK/SUBMIT/REPORT）与时间，
 * 永远不记录请求体、表单字段或任何口令内容。
 */
@MappedEntity("tracking_events")
public record TrackingEvent(
        @Id @GeneratedValue(GeneratedValue.Type.IDENTITY) Long id,
        Long targetId,
        String type,
        Instant at
) {
}
