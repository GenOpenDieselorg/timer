package pl.twojanazwa.eventannouncer;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GlobalBossBarManager implements Listener {

    private final EventAnnouncer plugin;
    private final PreferenceManager preferenceManager;
    private final ServerScheduler scheduler;
    
    private final List<BossBarData> bossBarList = new ArrayList<>();
    private BossBar activeBossBar;
    
    private int intervalTicks;
    private int durationTicks;
    
    private ServerScheduler.TaskHandle task;
    private int currentTimer = 0;
    private int currentIndex = -1; // -1 => pierwszy pokazany bar to indeks 0
    private boolean isShowing = false;
    
    private final Set<UUID> playersInBar = ConcurrentHashMap.newKeySet();
    
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    public GlobalBossBarManager(EventAnnouncer plugin, PreferenceManager preferenceManager, ServerScheduler scheduler) {
        this.plugin = plugin;
        this.preferenceManager = preferenceManager;
        this.scheduler = scheduler;
        loadConfig();
    }

    public void loadConfig() {
        File file = new File(plugin.getDataFolder(), "bossbar.yml");
        if (!file.exists()) {
            plugin.saveResource("bossbar.yml", false);
        }
        
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        
        this.intervalTicks = config.getInt("settings.interval", 180) * 20;
        this.durationTicks = config.getInt("settings.duration", 30) * 20;
        
        bossBarList.clear();
        ConfigurationSection barsSection = config.getConfigurationSection("bars");
        if (barsSection == null && config.isList("bars")) {
            List<Map<?, ?>> list = config.getMapList("bars");
            for (Map<?, ?> map : list) {
                Object textObj = map.get("text");
                Object colorObj = map.get("color");
                Object styleObj = map.get("style");
                
                String text = textObj != null ? textObj.toString() : "";
                String colorStr = colorObj != null ? colorObj.toString() : "WHITE";
                String styleStr = styleObj != null ? styleObj.toString() : "SOLID";
                
                try {
                    BarColor color = BarColor.valueOf(colorStr.toUpperCase());
                    BarStyle style = BarStyle.valueOf(styleStr.toUpperCase());
                    bossBarList.add(new BossBarData(colorize(text), color, style));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Błąd konfiguracji bossbar.yml: " + e.getMessage());
                }
            }
        }
    }

    public void start() {
        stop();
        activeBossBar = Bukkit.createBossBar("", BarColor.WHITE, BarStyle.SOLID);
        activeBossBar.setVisible(false);
        
        task = scheduler.runGlobalTimer(this::tick, 1L, 20L);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (activeBossBar != null) {
            activeBossBar.removeAll();
            activeBossBar = null;
        }
        playersInBar.clear();
        isShowing = false;
        currentTimer = 0;
        currentIndex = -1;

        // Wyrejestruj listener
        HandlerList.unregisterAll(this);
    }

    private void tick() {
        if (bossBarList.isEmpty()) return;

        if (isShowing) {
            currentTimer += 20;
            
            double progress = 1.0 - ((double) currentTimer / durationTicks);
            if (progress < 0) progress = 0;
            activeBossBar.setProgress(progress);
            
            if (currentTimer >= durationTicks) {
                hideBar();
            }
        } else {
            currentTimer += 20;
            if (currentTimer >= intervalTicks) {
                showNextBar();
            }
        }
    }

    private void showNextBar() {
        if (bossBarList.isEmpty()) return;

        currentIndex = (currentIndex + 1) % bossBarList.size();
        BossBarData data = bossBarList.get(currentIndex);

        activeBossBar.setTitle(data.text);
        activeBossBar.setColor(data.color);
        activeBossBar.setStyle(data.style);
        activeBossBar.setProgress(1.0);
        activeBossBar.setVisible(true);

        // Operacje na bossbarze trzymamy na global region thread (ten tick już tam działa) –
        // bez tego lista graczy bossbara byłaby modyfikowana z wielu wątków na Folii.
        playersInBar.clear();
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            if (preferenceManager.hasAnnouncementsEnabled(uuid) && activeBossBar != null) {
                activeBossBar.addPlayer(player);
                playersInBar.add(uuid);
            }
        }

        isShowing = true;
        currentTimer = 0;
    }

    private void hideBar() {
        activeBossBar.removeAll();
        playersInBar.clear();
        activeBossBar.setVisible(false);
        isShowing = false;
        currentTimer = 0;
    }
    
    private String colorize(String message) {
        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            try {
                String hexCode = matcher.group(1);
                matcher.appendReplacement(buffer, net.md_5.bungee.api.ChatColor.of("#" + hexCode).toString());
            } catch (Exception e) {
                matcher.appendReplacement(buffer, "");
            }
        }
        matcher.appendTail(buffer);
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (isShowing && activeBossBar != null) {
            Player player = event.getPlayer();
            scheduler.runGlobal(() -> {
                UUID uuid = player.getUniqueId();
                if (isShowing && activeBossBar != null && player.isOnline()
                        && preferenceManager.hasAnnouncementsEnabled(uuid)) {
                    activeBossBar.addPlayer(player);
                    playersInBar.add(uuid);
                }
            });
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        playersInBar.remove(uuid);
        if (activeBossBar != null) {
            Player player = event.getPlayer();
            scheduler.runGlobal(() -> {
                if (activeBossBar != null) {
                    activeBossBar.removePlayer(player);
                }
            });
        }
    }

    private record BossBarData(String text, BarColor color, BarStyle style) {}
}
