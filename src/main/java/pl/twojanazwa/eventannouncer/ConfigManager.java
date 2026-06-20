package pl.twojanazwa.eventannouncer;

import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.configuration.ConfigurationSection;
import pl.twojanazwa.eventannouncer.model.ScheduledEvent;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ConfigManager {
    private final EventAnnouncer plugin;
    private final List<ScheduledEvent> scheduledEvents = new ArrayList<>();
    private ZoneId timeZone;
    private int bossBarRotationMinutes;
    private LocalTime announcementStartTime;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final Pattern REPEATING_PATTERN = Pattern.compile("every (\\d+) days from (\\d{4}-\\d{2}-\\d{2}) at (\\d{2}:\\d{2})");
    private static final Pattern SPECIFIC_PATTERN = Pattern.compile("on ([\\d\\s,-]+) at (\\d{2}:\\d{2})");

    public ConfigManager(EventAnnouncer plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
        loadConfig();
    }

    private void loadConfig() {
        plugin.reloadConfig();
        var config = plugin.getConfig();
        this.timeZone = ZoneId.of(config.getString("timezone", "Europe/Warsaw"));
        this.bossBarRotationMinutes = config.getInt("bossbar_rotation_minutes", 1);
        this.announcementStartTime = LocalTime.parse(config.getString("announcement_start_time", "10:00"), TIME_FORMATTER);

        ConfigurationSection eventsSection = config.getConfigurationSection("events");
        if (eventsSection == null) return;
        for (String key : eventsSection.getKeys(false)) {
            try {
                parseAndCreateEvent(key, eventsSection.getConfigurationSection(key));
            } catch (Exception e) {
                plugin.getLogger().warning("Pominięto wydarzenie '" + key + "' z powodu błędu konfiguracji: " + e.getMessage());
            }
        }
    }
    
    private void parseAndCreateEvent(String key, ConfigurationSection section) {
        String scheduleStr = section.getString("schedule", "");
        Matcher repeatingMatcher = REPEATING_PATTERN.matcher(scheduleStr);
        Matcher specificMatcher = SPECIFIC_PATTERN.matcher(scheduleStr);

        ScheduledEvent.ScheduleType type;
        int everyDays = 0;
        LocalDate startDate = null;
        List<LocalDate> specificDates = Collections.emptyList();
        LocalTime time;

        if (repeatingMatcher.matches()) {
            type = ScheduledEvent.ScheduleType.REPEATING;
            everyDays = Integer.parseInt(repeatingMatcher.group(1));
            startDate = LocalDate.parse(repeatingMatcher.group(2), DATE_FORMATTER);
            time = LocalTime.parse(repeatingMatcher.group(3), TIME_FORMATTER);
        } else if (specificMatcher.matches()) {
            type = ScheduledEvent.ScheduleType.SPECIFIC;
            specificDates = Arrays.stream(specificMatcher.group(1).trim().split(",\\s*"))
                .map(dateStr -> LocalDate.parse(dateStr, DATE_FORMATTER)).toList();
            time = LocalTime.parse(specificMatcher.group(2), TIME_FORMATTER);
        } else {
            plugin.getLogger().warning("Nieprawidłowy format harmonogramu dla wydarzenia '" + key + "'.");
            return;
        }

        BarColor barColor = BarColor.valueOf(section.getString("bossbar.color", "WHITE").toUpperCase());
        BarStyle barStyle = BarStyle.valueOf(section.getString("bossbar.style", "SOLID").toUpperCase());
        String barText = section.getString("bossbar.text", "");
        boolean countdownEnabled = section.getBoolean("bossbar.countdown.enabled", false);
        Duration countdownStart = parseDuration(section.getString("bossbar.countdown.start_before", "1 hour"));
        String countdownFormat = section.getString("bossbar.countdown.format", "");
        
        List<ScheduledEvent.Announcement> announcements = section.getStringList("announcements").stream()
            .map(this::parseAnnouncement).collect(Collectors.toList());

        // NOWE: Pobieranie listy komend z konfiguracji
        List<String> commands = section.getStringList("commands");

        scheduledEvents.add(new ScheduledEvent(
            key, section.getString("display_name", key), time, type, everyDays, startDate, specificDates,
            barColor, barStyle, barText, countdownEnabled, countdownStart, countdownFormat, announcements, commands
        ));
    }
    
    private Duration parseDuration(String timeStr) {
        String[] parts = timeStr.trim().split("\\s+");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Nieprawidłowy format czasu: '" + timeStr + "' (oczekiwano np. '1 hours', '30 minutes')");
        }
        long amount = Long.parseLong(parts[0]);
        // Akceptujemy liczbę pojedynczą i mnogą: hour/hours, minute/minutes itd.
        String unit = parts[1].toLowerCase();
        if (unit.endsWith("s")) {
            unit = unit.substring(0, unit.length() - 1);
        }
        return switch (unit) {
            case "second" -> Duration.ofSeconds(amount);
            case "minute" -> Duration.ofMinutes(amount);
            case "hour" -> Duration.ofHours(amount);
            case "day" -> Duration.ofDays(amount);
            default -> throw new IllegalArgumentException("Nieznana jednostka czasu: '" + parts[1] + "' (użyj seconds/minutes/hours/days)");
        };
    }

    private ScheduledEvent.Announcement parseAnnouncement(String line) {
        String[] parts = line.split(":", 2);
        return new ScheduledEvent.Announcement(parseDuration(parts[0].trim()), parts[1].trim());
    }

    public List<ScheduledEvent> getScheduledEvents() { return scheduledEvents; }
    public ZoneId getTimeZone() { return timeZone; }
    public int getBossBarRotationMinutes() { return bossBarRotationMinutes; }
    public LocalTime getAnnouncementStartTime() { return announcementStartTime; }
}
