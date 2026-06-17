package pl.twojanazwa.eventannouncer;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class EventAnnouncer extends JavaPlugin {

    private ConfigManager configManager;
    private PreferenceManager preferenceManager;
    private BossBarManager bossBarManager;
    private GlobalBossBarManager globalBossBarManager; // <--- NOWE
    private EventScheduler eventScheduler;
    private ServerScheduler scheduler;

    @Override
    public void onEnable() {
        scheduler = new ServerScheduler(this);
        configManager = new ConfigManager(this);
        preferenceManager = new PreferenceManager(this, scheduler);
        
        // Manager od Eventów (istniejący)
        bossBarManager = new BossBarManager(this, preferenceManager, scheduler);
        
        // NOWE: Manager od cyklicznych BossBarów z bossbar.yml
        globalBossBarManager = new GlobalBossBarManager(this, preferenceManager, scheduler);
        globalBossBarManager.start();

        eventScheduler = new EventScheduler(this, configManager, bossBarManager, preferenceManager, scheduler);

        Bukkit.getPluginManager().registerEvents(bossBarManager, this);

        getCommand("eventannouncer").setExecutor(
            new pl.twojanazwa.eventannouncer.command.ToggleCommand(preferenceManager)
        );

        eventScheduler.run();

        // Rejestracja PlaceholderAPI jeśli dostępne
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PAPIExpansion(preferenceManager).register();
            getLogger().info("PlaceholderAPI expansion registered.");
        }

        getLogger().info("EventAnnouncer enabled! Scheduler: " + (scheduler.isFolia() ? "Folia" : "Bukkit"));
    }

    @Override
    public void onDisable() {
        if (eventScheduler != null) {
            eventScheduler.stop();
        }
        if (bossBarManager != null) {
            bossBarManager.removeAllBossBars();
        }
        // NOWE: Czyszczenie globalnego bossbara
        if (globalBossBarManager != null) {
            globalBossBarManager.stop();
        }
        if (preferenceManager != null) {
            preferenceManager.shutdown(); // Zapisz oczekujące zmiany
        }
        getLogger().info("EventAnnouncer disabled!");
    }
}
