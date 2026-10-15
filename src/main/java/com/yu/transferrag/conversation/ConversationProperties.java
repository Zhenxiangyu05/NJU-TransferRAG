package com.yu.transferrag.conversation;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import java.time.Duration;

@ConfigurationProperties("app.rag.conversation")
public record ConversationProperties(@DefaultValue("false") boolean enabled,
        @DefaultValue("30m") Duration ttl, @DefaultValue("5m") Duration processingLease,
        @DefaultValue("8s") Duration completionTimeout, @DefaultValue("4") int maxTurns,
        @DefaultValue("500") int maxQuestionChars, @DefaultValue("4000") int maxContextChars) {
    public ConversationProperties {
        if (ttl == null || ttl.compareTo(Duration.ofMinutes(5)) < 0 || ttl.compareTo(Duration.ofHours(2)) > 0
                || processingLease == null || processingLease.compareTo(Duration.ofMinutes(3)) < 0
                || processingLease.compareTo(ttl) > 0 || completionTimeout == null
                || completionTimeout.isNegative() || completionTimeout.isZero() || completionTimeout.compareTo(Duration.ofSeconds(10)) > 0
                || maxTurns < 3 || maxTurns > 5 || maxQuestionChars < 100 || maxQuestionChars > 1000
                || maxContextChars < maxQuestionChars * 2 || maxContextChars > 8000)
            throw new IllegalArgumentException("Invalid conversation limits");
    }
}
