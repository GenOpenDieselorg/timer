package pl.twojanazwa.eventannouncer;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Trwały (zapisywany na dysk) rejestr wykonanych komend i wysłanych ogłoszeń per dzień.
 * <p>
 * Dzięki temu po restarcie/reloadzie serwera komendy (np. nagrody typu {@code give @a ...})
 * nie wykonają się ponownie w 2-minutowym oknie tolerancji – co zapobiega duplikacji
 * itemków/nagród. Wcześniej stan był trzymany tylko w pamięci i ginął przy restarcie.
 */
public class ExecutionStateManager {
    private final EventAnnouncer plugin;
    private final ServerScheduler scheduler;
    private final File file;

    private volatile LocalDate storedDate;
    private final Set<String> announced = ConcurrentHashMap.newKeySet();
    private final Set<String> commands = ConcurrentHashMap.newKeySet();

    private final Object saveLock = new Object();

    public ExecutionStateManager(EventAnnouncer plugin, ServerScheduler scheduler) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.file = new File(plugin.getDataFolder(), "executed.yml");
        load();
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        String dateStr = cfg.getString("date");
        if (dateStr == null) return;
        try {
            storedDate = LocalDate.parse(dateStr);
        } catch (Exception e) {
            plugin.getLogger().warning("Nieprawidłowa data w executed.yml: " + dateStr);
            return;
        }
        announced.addAll(cfg.getStringList("announced"));
        commands.addAll(cfg.getStringList("commands"));
    }

    /** Resetuje rejestr, jeśli zmienił się dzień. */
    public void ensureDate(LocalDate today) {
        if (today.equals(storedDate)) return;
        synchronized (saveLock) {
            if (today.equals(storedDate)) return;
            storedDate = today;
            announced.clear();
            commands.clear();
        }
        saveAsync();
    }

    /** @return true jeśli ogłoszenie nie było jeszcze wysłane dziś (i właśnie je oznaczyliśmy). */
    public boolean markAnnounced(LocalDate today, String id) {
        ensureDate(today);
        if (announced.add(id)) {
            saveAsync();
            return true;
        }
        return false;
    }

    /** @return true jeśli komenda nie była jeszcze wykonana dziś (i właśnie ją oznaczyliśmy). */
    public boolean markCommand(LocalDate today, String id) {
        ensureDate(today);
        if (commands.add(id)) {
            saveAsync();
            return true;
        }
        return false;
    }

    private void saveAsync() {
        scheduler.runAsyncLater(this::saveNow, 1L);
    }

    private void saveNow() {
        synchronized (saveLock) {
            YamlConfiguration cfg = new YamlConfiguration();
            cfg.set("date", storedDate != null ? storedDate.toString() : null);
            cfg.set("announced", new ArrayList<>(announced));
            cfg.set("commands", new ArrayList<>(commands));
            try {
                if (!plugin.getDataFolder().exists()) {
                    plugin.getDataFolder().mkdirs();
                }
                cfg.save(file);
            } catch (IOException e) {
                plugin.getLogger().warning("Nie można zapisać executed.yml: " + e.getMessage());
            }
        }
    }
}
