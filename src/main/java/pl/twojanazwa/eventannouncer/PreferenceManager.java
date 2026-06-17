package pl.twojanazwa.eventannouncer;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PreferenceManager {

    private final EventAnnouncer plugin;
    private final ServerScheduler scheduler;
    private final Set<UUID> disabledPlayers = ConcurrentHashMap.newKeySet();
    private final File preferencesFile;
    private FileConfiguration preferencesConfig;
    
    private volatile boolean pendingSave = false;
    private ServerScheduler.TaskHandle saveTask = null;
    private static final long SAVE_DELAY_TICKS = 30 * 20L;

    public PreferenceManager(EventAnnouncer plugin, ServerScheduler scheduler) {
        this.plugin = plugin;
        this.scheduler = scheduler;
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
        
        saveTask = scheduler.runAsyncLater(() -> {
            savePreferencesNow();
            pendingSave = false;
        }, SAVE_DELAY_TICKS);
    }
    
    private void savePreferencesNow() {
        // Unikaj stream() - użyj zwykłej pętli
        List<String> disabledUuids = new ArrayList<>(disabledPlayers.size());
        for (UUID uuid : disabledPlayers) {
            disabledUuids.add(uuid.toString());
        }
        
        preferencesConfig.set("disabled_players", disabledUuids);
        try {
            preferencesConfig.save(preferencesFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Nie można zapisać pliku preferences.yml!");
            e.printStackTrace();
        }
    }
    
    public void shutdown() {
        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }
        if (pendingSave) {
            savePreferencesNow();
        }
    }

    public boolean hasAnnouncementsEnabled(UUID playerUuid) {
        return !disabledPlayers.contains(playerUuid);
    }

    public boolean togglePreferences(UUID playerUuid) {
        boolean newState;
        if (disabledPlayers.contains(playerUuid)) {
            disabledPlayers.remove(playerUuid);
            newState = true;
        } else {
            disabledPlayers.add(playerUuid);
            newState = false;
        }
        scheduleSave();
        return newState;
    }
}
