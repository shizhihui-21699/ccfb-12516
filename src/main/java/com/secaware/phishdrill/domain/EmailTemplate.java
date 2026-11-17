package com.secaware.phishdrill.domain;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

@MappedEntity("email_templates")
public record EmailTemplate(
        @Id @GeneratedValue(GeneratedValue.Type.IDENTITY) Long id,
        String name,
        String subject,
        String bodyHtml,
        String fromName,
        String fromEmail
) {
}
