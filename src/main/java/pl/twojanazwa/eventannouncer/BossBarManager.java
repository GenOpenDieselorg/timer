package pl.twojanazwa.eventannouncer;

import org.bukkit.Bukkit;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import pl.twojanazwa.eventannouncer.model.ScheduledEvent;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class BossBarManager implements Listener {
    private final EventAnnouncer plugin;
    private final PreferenceManager preferenceManager;
    private final Map<String, BossBar> activeBossBars = new ConcurrentHashMap<>();
    private volatile List<ScheduledEvent> currentEvents = List.of();
    private volatile int currentEventIndex = 0;
    private volatile long lastRotationTime = 0;
    
    // Cache dla aktualnie wyświetlanego BossBara
    private volatile BossBar currentlyShownBar = null;

    public BossBarManager(EventAnnouncer plugin, PreferenceManager preferenceManager) {
        this.plugin = plugin;
        this.preferenceManager = preferenceManager;
    }

    public void updateBossBars(List<ScheduledEvent> todaysEvents, ZonedDateTime now) {
        Set<String> eventIds = todaysEvents.stream().map(ScheduledEvent::id).collect(Collectors.toSet());

        if (!activeBossBars.keySet().equals(eventIds)) {
            removeAllBossBars();
            todaysEvents.forEach(this::createBossBar);
        }
        this.currentEvents = todaysEvents;
        if (currentEvents.isEmpty()) return;

        int rotationMinutes = plugin.getConfig().getInt("bossbar_rotation_minutes", 1);
        if (currentEvents.size() > 1 && System.currentTimeMillis() - lastRotationTime > rotationMinutes * 60 * 1000L) {
            currentEventIndex = (currentEventIndex + 1) % currentEvents.size();
            lastRotationTime = System.currentTimeMillis();
        }

        updateAndShowCurrentBossBar(now);
    }
    
    private void updateAndShowCurrentBossBar(ZonedDateTime now) {
        if (currentEvents.isEmpty()) return;

        long currentTime = System.currentTimeMillis();
        long interval = 5 * 60 * 1000; // 5 minut
        long duration = 30 * 1000;     // 30 sekund
        
        boolean shouldShow = (currentTime % interval) < duration;

        if (!shouldShow) {
            if (currentlyShownBar != null) {
                currentlyShownBar.removeAll();
                currentlyShownBar = null;
            }
            return;
        }

        ScheduledEvent eventToShow = currentEvents.get(currentEventIndex);
        BossBar barToShow = activeBossBars.get(eventToShow.id());
        if (barToShow == null) return;
        
        ZonedDateTime eventTime = now.toLocalDate().atTime(eventToShow.time()).atZone(now.getZone());
        Duration timeLeft = Duration.between(now, eventTime);

        String title;
        if (eventToShow.countdownEnabled() && !timeLeft.isNegative() && timeLeft.compareTo(eventToShow.countdownStartBefore()) <= 0) {
            title = eventToShow.getFormattedText(eventToShow.countdownFormat())
                .replace("{countdown}", formatDuration(timeLeft));
        } else {
            title = eventToShow.getFormattedText(eventToShow.bossBarText());
        }
        barToShow.setTitle(title);

        // Optymalizacja: usuń gracza ze starego bara tylko jeśli się zmienił
        if (currentlyShownBar != null && !currentlyShownBar.equals(barToShow)) {
            currentlyShownBar.removeAll();
        }
        currentlyShownBar = barToShow;

        // Jedna iteracja po graczach
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (preferenceManager.hasAnnouncementsEnabled(player.getUniqueId())) {
                if (!barToShow.getPlayers().contains(player)) {
                    barToShow.addPlayer(player);
                }
            } else {
                barToShow.removePlayer(player);
            }
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
        currentlyShownBar = null;
    }
    
    public boolean hasActiveBars() {
        return !activeBossBars.isEmpty();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Gracz zostanie dodany w następnym uaktualnieniu bossbara
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        // Optymalizacja: usuń tylko z aktualnie pokazywanego
        if (currentlyShownBar != null) {
            currentlyShownBar.removePlayer(player);
        }
    }
}
