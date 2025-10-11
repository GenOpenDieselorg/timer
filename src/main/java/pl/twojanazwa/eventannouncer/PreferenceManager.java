package pl.twojanazwa.eventannouncer;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class PreferenceManager {

    private final EventAnnouncer plugin;
    private final Set<UUID> disabledPlayers = new HashSet<>();
    private final File preferencesFile;
    private FileConfiguration preferencesConfig;

    public PreferenceManager(EventAnnouncer plugin) {
        this.plugin = plugin;
        this.preferencesFile = new File(plugin.getDataFolder(), "preferences.yml");
        loadPreferences();
    }

    public void loadPreferences() {
        if (!preferencesFile.exists()) {
            plugin.saveResource("preferences.yml", false);
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

    public void savePreferences() {
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

    public boolean hasAnnouncementsEnabled(UUID playerUuid) {
        return !disabledPlayers.contains(playerUuid);
    }

    /**
     * Toggles the player's preference.
     * @return The new state (true for enabled, false for disabled).
     */
    public boolean togglePreferences(UUID playerUuid) {
        if (disabledPlayers.contains(playerUuid)) {
            disabledPlayers.remove(playerUuid);
            savePreferences();
            return true; // Enabled
        } else {
            disabledPlayers.add(playerUuid);
            savePreferences();
            return false; // Disabled
        }
    }
}
