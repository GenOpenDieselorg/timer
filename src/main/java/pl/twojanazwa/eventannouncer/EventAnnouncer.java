package pl.twojanazwa.eventannouncer;

import org.bukkit.plugin.java.JavaPlugin;
import pl.twojanazwa.eventannouncer.command.ToggleCommand;

import java.util.Objects;

public final class EventAnnouncer extends JavaPlugin {
    private EventScheduler eventScheduler;
    private BossBarManager bossBarManager;
    private PreferenceManager preferenceManager;

    @Override
    public void onEnable() {
        ConfigManager configManager = new ConfigManager(this);
        if (configManager.getScheduledEvents().isEmpty()) {
            getLogger().warning("Nie załadowano żadnych wydarzeń. Sprawdź plik config.yml.");
            return;
        }

        this.preferenceManager = new PreferenceManager(this);
        this.bossBarManager = new BossBarManager(this, preferenceManager);
        this.eventScheduler = new EventScheduler(this, configManager, bossBarManager, preferenceManager);

        getServer().getPluginManager().registerEvents(bossBarManager, this);
        Objects.requireNonNull(getCommand("eventannouncer")).setExecutor(new ToggleCommand(preferenceManager));

        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new PAPIExpansion(preferenceManager).register();
            getLogger().info("Pomyślnie zintegrowano z PlaceholderAPI.");
        }

        eventScheduler.run();

        getLogger().info("EventAnnouncer v2.0 został pomyślnie włączony!");
    }

    @Override
    public void onDisable() {
        if (eventScheduler != null) {
            eventScheduler.stop();
        }
        if (bossBarManager != null) {
            bossBarManager.removeAllBossBars();
        }
        getLogger().info("EventAnnouncer został wyłączony.");
    }
}
