package pl.twojanazwa.eventannouncer;

import org.bukkit.Bukkit;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import pl.twojanazwa.eventannouncer.model.ScheduledEvent;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class BossBarManager implements Listener {
    private final EventAnnouncer plugin;
    private final Map<String, BossBar> activeBossBars = new ConcurrentHashMap<>();
    private List<ScheduledEvent> currentEvents = List.of();
    private int currentEventIndex = 0;
    private long lastRotationTime = 0;

    public BossBarManager(EventAnnouncer plugin) {
        this.plugin = plugin;
    }

    public void updateBossBars(List<ScheduledEvent> todaysEvents, ZonedDateTime now) {
        Set<String> eventIds = todaysEvents.stream().map(ScheduledEvent::id).collect(Collectors.toSet());

        if (!activeBossBars.keySet().equals(eventIds)) {
            removeAllBossBars();
            todaysEvents.forEach(this::createBossBar);
        }
        this.currentEvents = todaysEvents;
        if (currentEvents.isEmpty()) return;

        // Rotacja bossbarów, jeśli jest więcej niż 1
        int rotationSeconds = plugin.getConfig().getInt("bossbar_rotation_seconds", 10);
        if (System.currentTimeMillis() - lastRotationTime > rotationSeconds * 1000L) {
            currentEventIndex = (currentEventIndex + 1) % currentEvents.size();
            lastRotationTime = System.currentTimeMillis();
        }

        // Aktualizuj tekst i pokaż odpowiedni bossbar
        updateAndShowCurrentBossBar(now);
    }
    
    private void updateAndShowCurrentBossBar(ZonedDateTime now) {
        ScheduledEvent eventToShow = currentEvents.get(currentEventIndex);
        BossBar barToShow = activeBossBars.get(eventToShow.id());
        if (barToShow == null) return;
        
        // LOGIKA ODLICZANIA
        ZonedDateTime eventTime = now.toLocalDate().atTime(eventToShow.time()).atZone(now.getZone());
        Duration timeLeft = Duration.between(now, eventTime);

        String title;
        if (eventToShow.countdownEnabled() && !timeLeft.isNegative() && timeLeft.compareTo(eventToShow.countdownStartBefore()) <= 0) {
            // Jesteśmy w okresie odliczania
            title = eventToShow.getFormattedText(eventToShow.countdownFormat())
                .replace("{countdown}", formatDuration(timeLeft));
        } else {
            // Standardowy tekst
            title = eventToShow.getFormattedText(eventToShow.bossBarText());
        }
        barToShow.setTitle(title);

        // Pokaż graczom
        for (Player player : Bukkit.getOnlinePlayers()) {
            activeBossBars.values().forEach(bar -> bar.removePlayer(player));
            barToShow.addPlayer(player);
        }
    }

    private String formatDuration(Duration duration) {
        long seconds = duration.getSeconds();
        if (seconds < 0) return "0s";
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        if (hours > 0) {
            return String.format("%dh %02dm %02ds", hours, minutes, secs);
        } else {
            return String.format("%dm %02ds", minutes, secs);
        }
    }

    private void createBossBar(ScheduledEvent event) {
        BossBar bossBar = Bukkit.createBossBar(
            event.getFormattedText(event.bossBarText()),
            event.bossBarColor(),
            event.bossBarStyle()
        );
        bossBar.setProgress(1.0);
        activeBossBars.put(event.id(), bossBar);
    }

    public void removeAllBossBars() {
        activeBossBars.values().forEach(BossBar::removeAll);
        activeBossBars.clear();
        currentEvents = List.of();
        currentEventIndex = 0;
    }
    
    public boolean hasActiveBars() {
        return !activeBossBars.isEmpty();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (currentEvents.isEmpty()) return;
        ScheduledEvent eventToShow = currentEvents.get(currentEventIndex);
        BossBar bar = activeBossBars.get(eventToShow.id());
        if (bar != null) {
            bar.addPlayer(event.getPlayer());
        }
    }
}
