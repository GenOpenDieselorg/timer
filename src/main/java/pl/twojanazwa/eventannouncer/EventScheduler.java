package pl.twojanazwa.eventannouncer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import pl.twojanazwa.eventannouncer.model.ScheduledEvent;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class EventScheduler {
    private final EventAnnouncer plugin;
    private final ConfigManager configManager;
    private final BossBarManager bossBarManager;
    private final PreferenceManager preferenceManager;
    private BukkitTask mainTask;
    
    private final Set<String> todaysAnnounced = ConcurrentHashMap.newKeySet();
    private volatile List<ScheduledEvent> todaysEvents = List.of();
    private volatile LocalDate lastCheckedDate = null;
    
    // Cache dla uniknięcia tworzenia obiektów
    private final List<ScheduledEvent> upcomingEventsCache = new ArrayList<>();

    public EventScheduler(EventAnnouncer plugin, ConfigManager configManager, BossBarManager bossBarManager, PreferenceManager preferenceManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.bossBarManager = bossBarManager;
        this.preferenceManager = preferenceManager;
    }

    public void run() {
        mainTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 0L, 20L);
    }

    public void stop() {
        if (mainTask != null) {
            mainTask.cancel();
            mainTask = null;
        }
    }

    private void tick() {
        ZonedDateTime now = ZonedDateTime.now(configManager.getTimeZone());
        LocalDate today = now.toLocalDate();
        LocalTime currentTime = now.toLocalTime();

        if (lastCheckedDate == null || !lastCheckedDate.equals(today)) {
            lastCheckedDate = today;
            updateTodaysEvents(today);
            todaysAnnounced.clear();
        }

        if (currentTime.isBefore(configManager.getAnnouncementStartTime())) {
            if (bossBarManager.hasActiveBars()) {
                bossBarManager.removeAllBossBars();
            }
            return;
        }

        List<ScheduledEvent> events = todaysEvents;
        
        // Reużyj listy zamiast tworzyć nową
        upcomingEventsCache.clear();
        for (ScheduledEvent event : events) {
            if (currentTime.isBefore(event.time())) {
                upcomingEventsCache.add(event);
            }
        }

        if (upcomingEventsCache.isEmpty()) {
            if (bossBarManager.hasActiveBars()) {
                bossBarManager.removeAllBossBars();
            }
            return;
        }
        
        bossBarManager.updateBossBars(upcomingEventsCache, now);
        checkAndSendAnnouncements(now, upcomingEventsCache);
    }

    private void updateTodaysEvents(LocalDate today) {
        List<ScheduledEvent> allEvents = configManager.getScheduledEvents();
        List<ScheduledEvent> filtered = new ArrayList<>();
        
        for (ScheduledEvent event : allEvents) {
            if (event.isEventScheduledFor(today)) {
                filtered.add(event);
            }
        }
        
        todaysEvents = filtered;
        
        if (!todaysEvents.isEmpty()) {
            plugin.getLogger().info("Znaleziono " + todaysEvents.size() + " wydarzeń na dziś (" + today + ").");
        }
    }

    private void checkAndSendAnnouncements(ZonedDateTime now, List<ScheduledEvent> upcomingEvents) {
        for (int i = 0; i < upcomingEvents.size(); i++) {
            ScheduledEvent event = upcomingEvents.get(i);
            ZonedDateTime eventTime = now.toLocalDate().atTime(event.time()).atZone(configManager.getTimeZone());
            
            List<ScheduledEvent.Announcement> announcements = event.announcements();
            for (int j = 0; j < announcements.size(); j++) {
                ScheduledEvent.Announcement announcement = announcements.get(j);
                ZonedDateTime announcementTime = eventTime.minus(announcement.timeBefore());
                String announcementId = event.id() + "@" + announcement.timeBefore().toString();

                if (now.isAfter(announcementTime) && todaysAnnounced.add(announcementId)) {
                    String formattedMessage = event.getFormattedText(announcement.message());
                    
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        if (preferenceManager.hasAnnouncementsEnabled(player.getUniqueId())) {
                            player.sendMessage(formattedMessage);
                        }
                    }
                }
            }
        }
    }
}
