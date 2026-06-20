package pl.twojanazwa.eventannouncer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import pl.twojanazwa.eventannouncer.model.ScheduledEvent;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

public class EventScheduler {
    private final EventAnnouncer plugin;
    private final ConfigManager configManager;
    private final BossBarManager bossBarManager;
    private final PreferenceManager preferenceManager;
    private final ServerScheduler scheduler;
    private final ExecutionStateManager stateManager;
    private ServerScheduler.TaskHandle mainTask;

    // Trwały rejestr wysłanych ogłoszeń / wykonanych komend (przeżywa restart serwera)
    private volatile List<ScheduledEvent> todaysEvents = List.of();
    private volatile LocalDate lastCheckedDate = null;

    // Cache dla uniknięcia tworzenia obiektów
    private final List<ScheduledEvent> upcomingEventsCache = new ArrayList<>();

    public EventScheduler(EventAnnouncer plugin, ConfigManager configManager, BossBarManager bossBarManager, PreferenceManager preferenceManager, ServerScheduler scheduler) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.bossBarManager = bossBarManager;
        this.preferenceManager = preferenceManager;
        this.scheduler = scheduler;
        this.stateManager = new ExecutionStateManager(plugin, scheduler);
    }

    public void run() {
        mainTask = scheduler.runGlobalTimer(this::tick, 1L, 20L);
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
            stateManager.ensureDate(today); // Reset rejestru na nowy dzień
        }

        if (currentTime.isBefore(configManager.getAnnouncementStartTime())) {
            if (bossBarManager.hasActiveBars()) {
                bossBarManager.removeAllBossBars();
            }
            return;
        }

        List<ScheduledEvent> events = todaysEvents;
        
        upcomingEventsCache.clear();
        for (ScheduledEvent event : events) {
            if (currentTime.isBefore(event.time())) {
                upcomingEventsCache.add(event);
            }
        }

        if (upcomingEventsCache.isEmpty() && !bossBarManager.hasActiveBars()) {
            // Bossbary usunięte
        } else if (upcomingEventsCache.isEmpty() && bossBarManager.hasActiveBars()) {
            bossBarManager.removeAllBossBars();
        } else {
            bossBarManager.updateBossBars(upcomingEventsCache, now);
        }

        // Sprawdzamy powiadomienia i komendy na wszystkich dzisiejszych wydarzeniach
        checkAndSendAnnouncements(now, events);
        checkAndExecuteCommands(now, events);
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

    private void checkAndSendAnnouncements(ZonedDateTime now, List<ScheduledEvent> events) {
        LocalDate today = now.toLocalDate();
        for (int i = 0; i < events.size(); i++) {
            ScheduledEvent event = events.get(i);
            ZonedDateTime eventTime = now.toLocalDate().atTime(event.time()).atZone(configManager.getTimeZone());
            
            List<ScheduledEvent.Announcement> announcements = event.announcements();
            for (int j = 0; j < announcements.size(); j++) {
                ScheduledEvent.Announcement announcement = announcements.get(j);
                ZonedDateTime announcementTime = eventTime.minus(announcement.timeBefore());
                String announcementId = event.id() + "@" + announcement.timeBefore().toString();

                // Sprawdzamy czy czas nadszedł i zamykamy okienko w ciągu 2 minut by uniknąć spamu po restarcie serwera
                if (!now.isBefore(announcementTime) && now.isBefore(announcementTime.plusMinutes(2)) && stateManager.markAnnounced(today, announcementId)) {
                    String formattedMessage = event.getFormattedText(announcement.message());
                    
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        scheduler.runForPlayer(player, () -> {
                            if (preferenceManager.hasAnnouncementsEnabled(player.getUniqueId())) {
                                player.sendMessage(formattedMessage);
                            }
                        });
                    }
                }
            }
        }
    }

    private void checkAndExecuteCommands(ZonedDateTime now, List<ScheduledEvent> events) {
        LocalDate today = now.toLocalDate();
        for (int i = 0; i < events.size(); i++) {
            ScheduledEvent event = events.get(i);
            ZonedDateTime eventTime = now.toLocalDate().atTime(event.time()).atZone(configManager.getTimeZone());

            // Komendy wykonują się w momencie "0" (lub do 2 minut w przypadku chwilowego zawieszenia serwera)
            if (!now.isBefore(eventTime) && now.isBefore(eventTime.plusMinutes(2))) {
                if (stateManager.markCommand(today, event.id())) {
                    List<String> commands = event.commands();
                    if (commands != null && !commands.isEmpty()) {
                        for (String command : commands) {
                            // Komend NIE kolorujemy (&->§) – psułoby to argumenty zawierające '&'
                            String formattedCommand = event.getFormattedRaw(command);
                            // Usunięcie slashy na początku, jeżeli użytkownik je omyłkowo dopisze
                            if (formattedCommand.startsWith("/")) {
                                formattedCommand = formattedCommand.substring(1);
                            }
                            // Wywołanie z perspektywy konsoli
                            String commandToRun = formattedCommand;
                            scheduler.runGlobal(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), commandToRun));
                        }
                    }
                }
            }
        }
    }
}
