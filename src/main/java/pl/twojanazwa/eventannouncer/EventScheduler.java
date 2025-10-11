package pl.twojanazwa.eventannouncer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
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
    private final PreferenceManager preferenceManager;
    private BukkitTask mainTask;
    private final Set<String> todaysAnnounced = new HashSet<>();
    private List<ScheduledEvent> todaysEvents = List.of();
    private LocalDate lastCheckedDate = null;

    public EventScheduler(EventAnnouncer plugin, ConfigManager configManager, BossBarManager bossBarManager, PreferenceManager preferenceManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.bossBarManager = bossBarManager;
        this.preferenceManager = preferenceManager;
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

        if (now.toLocalTime().isBefore(configManager.getAnnouncementStartTime())) {
            if (bossBarManager.hasActiveBars()) {
                Bukkit.getScheduler().runTask(plugin, bossBarManager::removeAllBossBars);
            }
            return;
        }

        List<ScheduledEvent> upcomingEvents = todaysEvents.stream()
                .filter(event -> now.toLocalTime().isBefore(event.time()))
                .collect(Collectors.toList());

        if (upcomingEvents.isEmpty()) {
            if (bossBarManager.hasActiveBars()) {
                 Bukkit.getScheduler().runTask(plugin, bossBarManager::removeAllBossBars);
            }
            return;
        }
        
        Bukkit.getScheduler().runTask(plugin, () -> bossBarManager.updateBossBars(upcomingEvents, now));
        
        checkAndSendAnnouncements(now, upcomingEvents);
    }

    private void updateTodaysEvents(LocalDate today) {
        todaysEvents = configManager.getScheduledEvents().stream()
                .filter(event -> event.isEventScheduledFor(today))
                .collect(Collectors.toList());
        
        if (!todaysEvents.isEmpty()) {
            plugin.getLogger().info("Znaleziono " + todaysEvents.size() + " wydarzeń na dziś (" + today + ").");
        }
    }

    private void checkAndSendAnnouncements(ZonedDateTime now, List<ScheduledEvent> upcomingEvents) {
        for (ScheduledEvent event : upcomingEvents) {
            ZonedDateTime eventTime = now.toLocalDate().atTime(event.time()).atZone(configManager.getTimeZone());
            for (ScheduledEvent.Announcement announcement : event.announcements()) {
                ZonedDateTime announcementTime = eventTime.minus(announcement.timeBefore());
                String announcementId = event.id() + "@" + announcement.timeBefore().toString();

                if (now.isAfter(announcementTime) && !todaysAnnounced.contains(announcementId)) {
                    String formattedMessage = event.getFormattedText(announcement.message());
                    
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        for (Player player : Bukkit.getOnlinePlayers()) {
                            if (preferenceManager.hasAnnouncementsEnabled(player.getUniqueId())) {
                                player.sendMessage(formattedMessage);
                            }
                        }
                    });
                    
                    todaysAnnounced.add(announcementId);
                }
            }
        }
    }
}
