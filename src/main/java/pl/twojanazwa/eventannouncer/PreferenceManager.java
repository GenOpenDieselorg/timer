package pl.twojanazwa.eventannouncer;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class PreferenceManager {

    private final EventAnnouncer plugin;
    private final Set<UUID> disabledPlayers = ConcurrentHashMap.newKeySet();
    private final File preferencesFile;
    private FileConfiguration preferencesConfig;
    
    // Debounced save - zapisuje max raz na 30 sekund
    private volatile boolean pendingSave = false;
    private BukkitTask saveTask = null;
    private static final long SAVE_DELAY_TICKS = 30 * 20L; // 30 sekund

    public PreferenceManager(EventAnnouncer plugin) {
        this.plugin = plugin;
        this.preferencesFile = new File(plugin.getDataFolder(), "preferences.yml");
        loadPreferences();
    }

    public void loadPreferences() {
        if (!preferencesFile.exists()) {
            try {
                if (!plugin.getDataFolder().exists()) {
                    plugin.getDataFolder().mkdirs();
                }
                preferencesFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Nie można utworzyć pliku preferences.yml! " + e.getMessage());
            }
        }
        
        preferencesConfig = YamlConfiguration.loadConfiguration(preferencesFile);
        List<String> disabledUuids = preferencesConfig.getStringList("disabled_players");
        disabledPlayers.clear();
        for (String uuidStr : disabledUuids) {
            try {
                disabledPlayers.add(UUID.fromString(uuidStr));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Nieprawidłowy format UUID w preferences.yml: " + uuidStr);
            }
        }
    }

    private void scheduleSave() {
        if (pendingSave) return;
        pendingSave = true;
        
        saveTask = Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            savePreferencesNow();
            pendingSave = false;
        }, SAVE_DELAY_TICKS);
    }
    
    private void savePreferencesNow() {
        List<String> disabledUuids = disabledPlayers.stream()
                .map(UUID::toString)
                .collect(Collectors.toList());
        preferencesConfig.set("disabled_players", disabledUuids);
        try {
            preferencesConfig.save(preferencesFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Nie można zapisać pliku preferences.yml!");
            e.printStackTrace();
        }
    }
    
    /**
     * Wywoływane przy wyłączaniu pluginu - zapisz natychmiast jeśli są oczekujące zmiany
     */
    public void shutdown() {
        if (saveTask != null) {
            saveTask.cancel();
        }
        if (pendingSave) {
            savePreferencesNow();
        }
    }

    public boolean hasAnnouncementsEnabled(UUID playerUuid) {
        return !disabledPlayers.contains(playerUuid);
    }

    /**
     * Toggles the player's preference.
     * @return The new state (true for enabled, false for disabled).
     */
    public boolean togglePreferences(UUID playerUuid) {
        boolean newState;
        if (disabledPlayers.contains(playerUuid)) {
            disabledPlayers.remove(playerUuid);
            newState = true; // Enabled
        } else {
            disabledPlayers.add(playerUuid);
            newState = false; // Disabled
        }
        scheduleSave(); // Debounced save
        return newState;
    }
}
