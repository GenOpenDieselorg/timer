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
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class BossBarManager implements Listener {
    private final EventAnnouncer plugin;
    private final PreferenceManager preferenceManager;
    private final Map<String, BossBar> activeBossBars = new ConcurrentHashMap<>();
    private List<ScheduledEvent> currentEvents = List.of();
    private int currentEventIndex = 0;
    private long lastRotationTime = 0;

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

        for (Player player : Bukkit.getOnlinePlayers()) {
            activeBossBars.values().forEach(bar -> bar.removePlayer(player));
            if (preferenceManager.hasAnnouncementsEnabled(player.getUniqueId())) {
                barToShow.addPlayer(player);
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
    }
    
    public boolean hasActiveBars() {
        return !activeBossBars.isEmpty();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (currentEvents.isEmpty() || !preferenceManager.hasAnnouncementsEnabled(event.getPlayer().getUniqueId())) return;
        
        ScheduledEvent eventToShow = currentEvents.get(currentEventIndex);
        BossBar bar = activeBossBars.get(eventToShow.id());
        if (bar != null) {
            bar.addPlayer(event.getPlayer());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        activeBossBars.values().forEach(bar -> bar.removePlayer(event.getPlayer()));
    }
}
