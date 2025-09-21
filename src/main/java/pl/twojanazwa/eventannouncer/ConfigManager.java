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
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ConfigManager {
    private final EventAnnouncer plugin;
    private final List<ScheduledEvent> scheduledEvents = new ArrayList<>();
    private ZoneId timeZone;
    private int bossBarRotationSeconds;

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

        try {
            this.timeZone = ZoneId.of(config.getString("timezone", "Europe/Warsaw"));
        } catch (Exception e) {
            plugin.getLogger().warning("Nieprawidłowa strefa czasowa! Używam domyślnej: Europe/Warsaw");
            this.timeZone = ZoneId.of("Europe/Warsaw");
        }
        this.bossBarRotationSeconds = config.getInt("bossbar_rotation_seconds", 10);

        ConfigurationSection eventsSection = config.getConfigurationSection("events");
        if (eventsSection == null) return;

        for (String key : eventsSection.getKeys(false)) {
            ConfigurationSection eventSection = eventsSection.getConfigurationSection(key);
            if (eventSection == null) continue;

            String scheduleStr = eventSection.getString("schedule");
            if (scheduleStr == null || scheduleStr.isBlank()) {
                plugin.getLogger().warning("Pominięto wydarzenie '" + key + "', ponieważ nie ma zdefiniowanego harmonogramu.");
                continue;
            }

            parseAndCreateEvent(key, eventSection);
        }
    }
    
    private void parseAndCreateEvent(String key, ConfigurationSection section) {
        String displayName = section.getString("display_name", key);
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
            String datesPart = specificMatcher.group(1).trim();
            specificDates = Arrays.stream(datesPart.split(",\\s*"))
                .map(dateStr -> LocalDate.parse(dateStr, DATE_FORMATTER))
                .collect(Collectors.toList());
            time = LocalTime.parse(specificMatcher.group(2), TIME_FORMATTER);
        } else {
            plugin.getLogger().warning("Nieprawidłowy format harmonogramu dla wydarzenia '" + key + "'. Pomijam.");
            return;
        }

        ConfigurationSection bossBarSection = section.getConfigurationSection("bossbar");
        BarColor barColor = BarColor.WHITE;
        BarStyle barStyle = BarStyle.SOLID;
        String barText = "";
        if (bossBarSection != null) {
            barColor = BarColor.valueOf(bossBarSection.getString("color", "WHITE").toUpperCase());
            barStyle = BarStyle.valueOf(bossBarSection.getString("style", "SOLID").toUpperCase());
            barText = bossBarSection.getString("text", "");
        }

        List<ScheduledEvent.Announcement> announcements = section.getStringList("announcements").stream()
            .map(this::parseAnnouncement).collect(Collectors.toList());

        scheduledEvents.add(new ScheduledEvent(
            key, displayName, time, type, everyDays, startDate, specificDates,
            barColor, barStyle, barText, announcements
        ));
    }
    
    private ScheduledEvent.Announcement parseAnnouncement(String line) {
        String[] parts = line.split(":", 2);
        String timeStr = parts[0];
        String message = parts[1].trim();

        String[] timeParts = timeStr.split("\\s+");
        long amount = Long.parseLong(timeParts[0]);
        TimeUnit unit = TimeUnit.valueOf(timeParts[1].toUpperCase());
        Duration duration = Duration.ofSeconds(unit.toSeconds(amount));

        return new ScheduledEvent.Announcement(duration, message);
    }

    public List<ScheduledEvent> getScheduledEvents() { return scheduledEvents; }
    public ZoneId getTimeZone() { return timeZone; }
    public int getBossBarRotationSeconds() { return bossBarRotationSeconds; }
}
