package com.ssafy.welstory.logging;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.AppenderBase;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

@Service
public class InMemoryLogService extends AppenderBase<ILoggingEvent> {
    private static final int CAPACITY = 500;
    private static final Pattern SECRET = Pattern.compile(
            "(?i)(password|authorization|bearer|token)(\\s*[=:]\\s*)([^\\s,;]+)");

    private final ConcurrentLinkedDeque<LogEntry> entries = new ConcurrentLinkedDeque<>();
    private final AtomicLong sequence = new AtomicLong();

    @PostConstruct
    void attach() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        setContext(context);
        setName("admin-memory-log");
        start();
        context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).addAppender(this);
    }

    @Override
    protected void append(ILoggingEvent event) {
        IThrowableProxy throwable = event.getThrowableProxy();
        String exception = throwable == null ? null : throwable.getClassName() + ": " + sanitize(throwable.getMessage());
        entries.addLast(new LogEntry(sequence.incrementAndGet(), Instant.ofEpochMilli(event.getTimeStamp()),
                event.getLevel().toString(), event.getThreadName(), event.getLoggerName(),
                sanitize(event.getFormattedMessage()), exception));
        while (entries.size() > CAPACITY) entries.pollFirst();
    }

    public List<LogEntry> recent(long after, int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, 500));
        List<LogEntry> result = new ArrayList<>();
        for (LogEntry entry : entries) {
            if (entry.sequence() > after) result.add(entry);
        }
        return result.size() <= limit ? List.copyOf(result)
                : List.copyOf(result.subList(result.size() - limit, result.size()));
    }

    private static String sanitize(String message) {
        if (message == null) return null;
        return SECRET.matcher(message).replaceAll("$1$2***");
    }

    @PreDestroy
    void detach() {
        if (getContext() instanceof LoggerContext context) {
            context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).detachAppender(this);
        }
        stop();
    }

    public record LogEntry(long sequence, Instant timestamp, String level, String thread,
                           String logger, String message, String exception) {}
}
