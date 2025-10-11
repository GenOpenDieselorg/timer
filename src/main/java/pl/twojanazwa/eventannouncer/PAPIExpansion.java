package pl.twojanazwa.eventannouncer;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public class PAPIExpansion extends PlaceholderExpansion {

    private final PreferenceManager preferenceManager;

    public PAPIExpansion(PreferenceManager preferenceManager) {
        this.preferenceManager = preferenceManager;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "eventannouncer";
    }

    @Override
    public @NotNull String getAuthor() {
        return "TwojaNazwa";
    }

    @Override
    public @NotNull String getVersion() {
        return "2.0.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return "";
        }
        if (params.equalsIgnoreCase("status")) {
            return preferenceManager.hasAnnouncementsEnabled(player.getUniqueId()) ? "Włączone" : "Wyłączone";
        }
        return null;
    }
}
