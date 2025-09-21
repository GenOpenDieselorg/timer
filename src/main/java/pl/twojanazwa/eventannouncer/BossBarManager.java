package pl.twojanazwa.eventannouncer;

import org.bukkit.Bukkit;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import pl.twojanazwa.eventannouncer.model.ScheduledEvent;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class BossBarManager implements Listener {
    private final EventAnnouncer plugin;
    private final Map<String, BossBar> activeBossBars = new ConcurrentHashMap<>();
    private List<String> currentEventIds = List.of();
    private int currentEventIndex = 0;
    private long lastRotationTime = 0;

    public BossBarManager(EventAnnouncer plugin) {
        this.plugin = plugin;
    }

    public void updateBossBars(List<ScheduledEvent> todaysEvents) {
        Set<String> eventIds = todaysEvents.stream().map(ScheduledEvent::id).collect(Collectors.toSet());

        if (!activeBossBars.keySet().equals(eventIds)) {
            removeAllBossBars();
            todaysEvents.forEach(this::createBossBar);
        }
        this.currentEventIds = todaysEvents.stream().map(ScheduledEvent::id).toList();
        if (currentEventIds.isEmpty()) return;

        long now = System.currentTimeMillis();
        int rotationSeconds = plugin.getConfig().getInt("bossbar_rotation_seconds", 10);

        if (now - lastRotationTime > rotationSeconds * 1000L) {
            currentEventIndex = (currentEventIndex + 1) % currentEventIds.size();
            lastRotationTime = now;
        }
        showCurrentBossBarToAll();
    }

    private void showCurrentBossBarToAll() {
        if (currentEventIds.isEmpty()) return;
        String eventIdToShow = currentEventIds.get(currentEventIndex);
        BossBar barToShow = activeBossBars.get(eventIdToShow);

        for (Player player : Bukkit.getOnlinePlayers()) {
            for (Map.Entry<String, BossBar> entry : activeBossBars.entrySet()) {
                if (!entry.getKey().equals(eventIdToShow)) {
                    entry.getValue().removePlayer(player);
                }
            }
            if (barToShow != null) {
                barToShow.addPlayer(player);
            }
        }
    }

    private void createBossBar(ScheduledEvent event) {
        BossBar bossBar = Bukkit.createBossBar(
            event.getFormattedBossBarText(),
            event.bossBarColor(),
            event.bossBarStyle()
        );
        bossBar.setProgress(1.0);
        activeBossBars.put(event.id(), bossBar);
    }

    public void removeAllBossBars() {
        for (BossBar bossBar : activeBossBars.values()) {
            bossBar.removeAll();
        }
        activeBossBars.clear();
        currentEventIds = List.of();
        currentEventIndex = 0;
    }
    
    public boolean hasActiveBars() {
        return !activeBossBars.isEmpty();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (currentEventIds.isEmpty()) return;
        String eventIdToShow = currentEventIds.get(currentEventIndex);
        BossBar bar = activeBossBars.get(eventIdToShow);
        if (bar != null) {
            bar.addPlayer(event.getPlayer());
        }
    }
}
