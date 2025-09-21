package pl.twojanazwa.eventannouncer.model;

import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

public record ScheduledEvent(
    String id,
    String displayName,
    LocalTime time,
    ScheduleType scheduleType,
    int everyDays,
    LocalDate startDate,
    List<LocalDate> specificDates,

    // BossBar
    BarColor bossBarColor,
    BarStyle bossBarStyle,
    String bossBarText,
    boolean countdownEnabled,
    Duration countdownStartBefore,
    String countdownFormat,

    // Announcements
    List<Announcement> announcements
) {
    public record Announcement(Duration timeBefore, String message) {}
    public enum ScheduleType { REPEATING, SPECIFIC }

    public boolean isEventScheduledFor(LocalDate date) {
        return switch (scheduleType) {
            case SPECIFIC -> specificDates.contains(date);
            case REPEATING -> {
                if (startDate == null || date.isBefore(startDate)) yield false;
                yield ChronoUnit.DAYS.between(startDate, date) % everyDays == 0;
            }
        };
    }

    public String getFormattedText(String text) {
        return text
            .replace("{eventName}", displayName)
            .replace("{time}", time.toString())
            .replace('&', '§');
    }
}
