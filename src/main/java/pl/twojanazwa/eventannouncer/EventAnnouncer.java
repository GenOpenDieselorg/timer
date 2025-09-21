package pl.twojanazwa.eventannouncer;

import org.bukkit.plugin.java.JavaPlugin;
import pl.twojanazwa.eventannouncer.model.ScheduledEvent;

public final class EventAnnouncer extends JavaPlugin {
    private EventScheduler eventScheduler;
    private BossBarManager bossBarManager;

    @Override
    public void onEnable() {
        ConfigManager configManager = new ConfigManager(this);
        if (configManager.getScheduledEvents().isEmpty()) {
            getLogger().warning("Nie załadowano żadnych wydarzeń. Sprawdź plik config.yml.");
            return;
        }

        this.bossBarManager = new BossBarManager(this);
        this.eventScheduler = new EventScheduler(this, configManager, bossBarManager);

        getServer().getPluginManager().registerEvents(bossBarManager, this);
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

    public BossBarManager getBossBarManager() {
        return bossBarManager;
    }
}
