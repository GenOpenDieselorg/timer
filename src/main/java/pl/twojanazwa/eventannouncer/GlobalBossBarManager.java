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
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GlobalBossBarManager implements Listener {

    private final EventAnnouncer plugin;
    private final PreferenceManager preferenceManager;
    
    private final List<BossBarData> bossBarList = new ArrayList<>();
    private BossBar activeBossBar;
    
    private int intervalTicks;
    private int durationTicks;
    
    private BukkitTask task;
    private int currentTimer = 0;
    private int currentIndex = 0;
    private boolean isShowing = false;
    
    // Cache graczy w bossbarze - O(1) lookup
    private final Set<UUID> playersInBar = ConcurrentHashMap.newKeySet();
    
    // Pattern do kolorów HEX &#RRGGBB
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    public GlobalBossBarManager(EventAnnouncer plugin, PreferenceManager preferenceManager) {
        this.plugin = plugin;
        this.preferenceManager = preferenceManager;
        loadConfig();
    }

    public void loadConfig() {
        File file = new File(plugin.getDataFolder(), "bossbar.yml");
        if (!file.exists()) {
            plugin.saveResource("bossbar.yml", false);
        }
        
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        
        // Wczytanie ustawień czasu
        this.intervalTicks = config.getInt("settings.interval", 180) * 20;
        this.durationTicks = config.getInt("settings.duration", 30) * 20;
        
        // Wczytanie listy barów
        bossBarList.clear();
        ConfigurationSection barsSection = config.getConfigurationSection("bars");
        if (barsSection == null && config.isList("bars")) {
            // Obsługa listy map (standard YAML)
            List<java.util.Map<?, ?>> list = config.getMapList("bars");
            for (java.util.Map<?, ?> map : list) {
                String text = (String) map.getOrDefault("text", "");
                String colorStr = (String) map.getOrDefault("color", "WHITE");
                String styleStr = (String) map.getOrDefault("style", "SOLID");
                
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
        // Utwórz pusty BossBar (niewidoczny na start)
        activeBossBar = Bukkit.createBossBar("", BarColor.WHITE, BarStyle.SOLID);
        activeBossBar.setVisible(false);
        
        // Task co 20 ticków (1 sekunda) zamiast co 1 tick - wystarczająca płynność
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 0L, 20L);
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
    }

    private void tick() {
        if (bossBarList.isEmpty()) return;

        if (isShowing) {
            // Faza wyświetlania - inkrementuj o 20 ticków
            currentTimer += 20;
            
            // Oblicz postęp (od 1.0 do 0.0)
            double progress = 1.0 - ((double) currentTimer / durationTicks);
            if (progress < 0) progress = 0;
            activeBossBar.setProgress(progress);
            
            if (currentTimer >= durationTicks) {
                hideBar();
            }
        } else {
            // Faza oczekiwania - inkrementuj o 20 ticków
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

        // Zaktualizuj istniejącą instancję BossBara (brak alokacji nowej pamięci)
        activeBossBar.setTitle(data.text);
        activeBossBar.setColor(data.color);
        activeBossBar.setStyle(data.style);
        activeBossBar.setProgress(1.0);
        activeBossBar.setVisible(true);

        // Dodaj wszystkich graczy, którzy mają włączone powiadomienia
        playersInBar.clear();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (preferenceManager.hasAnnouncementsEnabled(player.getUniqueId())) {
                activeBossBar.addPlayer(player);
                playersInBar.add(player.getUniqueId());
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
    
    // Metoda pomocnicza do HEX kolorów
    private String colorize(String message) {
        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            try {
                // Obsługa API 1.16+
                String hexCode = matcher.group(1);
                matcher.appendReplacement(buffer, net.md_5.bungee.api.ChatColor.of("#" + hexCode).toString());
            } catch (Exception e) {
                // Fallback dla starszych wersji (rzadki przypadek w 1.21)
                matcher.appendReplacement(buffer, "");
            }
        }
        matcher.appendTail(buffer);
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // Jeśli pasek jest aktualnie wyświetlany, dodaj gracza (jeśli ma włączone opcje)
        if (isShowing && activeBossBar != null) {
            UUID uuid = event.getPlayer().getUniqueId();
            if (preferenceManager.hasAnnouncementsEnabled(uuid)) {
                activeBossBar.addPlayer(event.getPlayer());
                playersInBar.add(uuid);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        playersInBar.remove(uuid);
        if (activeBossBar != null) {
            activeBossBar.removePlayer(event.getPlayer());
        }
    }

    // Rekord do przechowywania danych
    private record BossBarData(String text, BarColor color, BarStyle style) {}
}
