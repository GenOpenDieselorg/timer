package pl.twojanazwa.eventannouncer;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import pl.twojanazwa.eventannouncer.model.ScheduledEvent;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class EventScheduler {
    private final EventAnnouncer plugin;
    private final ConfigManager configManager;
    private final BossBarManager bossBarManager;
    private BukkitTask mainTask;
    private final Set<String> todaysAnnounced = new HashSet<>();
    private List<ScheduledEvent> todaysEvents = List.of();
    private LocalDate lastCheckedDate = null;

    public EventScheduler(EventAnnouncer plugin, ConfigManager configManager, BossBarManager bossBarManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.bossBarManager = bossBarManager;
    }

    public void run() {
        mainTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::tick, 0L, 20L);
    }

    public void stop() {
        if (mainTask != null) mainTask.cancel();
    }

    private void tick() {
        ZonedDateTime now = ZonedDateTime.now(configManager.getTimeZone());

        if (lastCheckedDate == null || !lastCheckedDate.equals(now.toLocalDate())) {
            lastCheckedDate = now.toLocalDate();
            updateTodaysEvents(lastCheckedDate);
            todaysAnnounced.clear();
        }

        if (todaysEvents.isEmpty()) {
            if (bossBarManager.hasActiveBars()) {
                 Bukkit.getScheduler().runTask(plugin, bossBarManager::removeAllBossBars);
            }
            return;
        }
        
        // Przekazuj aktualny czas do menedżera bossbarów
        Bukkit.getScheduler().runTask(plugin, () -> bossBarManager.updateBossBars(todaysEvents, now));
        
        checkAndSendAnnouncements(now);
    }

    private void updateTodaysEvents(LocalDate today) {
        todaysEvents = configManager.getScheduledEvents().stream()
                .filter(event -> event.isEventScheduledFor(today))
                .collect(Collectors.toList());
        
        if (!todaysEvents.isEmpty()) {
            plugin.getLogger().info("Znaleziono " + todaysEvents.size() + " wydarzeń na dziś (" + today + ").");
        }
    }

    private void checkAndSendAnnouncements(ZonedDateTime now) {
        for (ScheduledEvent event : todaysEvents) {
            ZonedDateTime eventTime = now.toLocalDate().atTime(event.time()).atZone(configManager.getTimeZone());
            for (ScheduledEvent.Announcement announcement : event.announcements()) {
                ZonedDateTime announcementTime = eventTime.minus(announcement.timeBefore());
                String announcementId = event.id() + "@" + announcement.timeBefore().toString();

                if (now.isAfter(announcementTime) && !todaysAnnounced.contains(announcementId)) {
                    Bukkit.getScheduler().runTask(plugin, () ->
                        Bukkit.broadcastMessage(event.getFormattedText(announcement.message()))
                    );
                    todaysAnnounced.add(announcementId);
                }
            }
        }
    }
}
