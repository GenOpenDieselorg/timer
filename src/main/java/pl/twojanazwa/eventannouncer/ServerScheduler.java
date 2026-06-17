package pl.twojanazwa.eventannouncer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.TimeUnit;

public class ServerScheduler {
    private final EventAnnouncer plugin;
    private final boolean folia;

    public ServerScheduler(EventAnnouncer plugin) {
        this.plugin = plugin;
        this.folia = isFoliaServer();
    }

    public boolean isFolia() {
        return folia;
    }

    public TaskHandle runGlobal(Runnable runnable) {
        if (folia) {
            return plugin.getServer().getGlobalRegionScheduler()
                .run(plugin, task -> runnable.run())::cancel;
        }

        BukkitTask task = Bukkit.getScheduler().runTask(plugin, runnable);
        return task::cancel;
    }

    public TaskHandle runGlobalTimer(Runnable runnable, long initialDelayTicks, long periodTicks) {
        if (folia) {
            return plugin.getServer().getGlobalRegionScheduler()
                .runAtFixedRate(
                    plugin,
                    task -> runnable.run(),
                    Math.max(1L, initialDelayTicks),
                    Math.max(1L, periodTicks)
                )::cancel;
        }

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, runnable, initialDelayTicks, periodTicks);
        return task::cancel;
    }

    public TaskHandle runAsyncLater(Runnable runnable, long delayTicks) {
        if (folia) {
            return plugin.getServer().getAsyncScheduler()
                .runDelayed(plugin, task -> runnable.run(), ticksToMillis(delayTicks), TimeUnit.MILLISECONDS)::cancel;
        }

        BukkitTask task = Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, runnable, delayTicks);
        return task::cancel;
    }

    public void runForPlayer(Player player, Runnable runnable) {
        if (folia) {
            player.getScheduler().run(plugin, task -> {
                if (player.isOnline()) {
                    runnable.run();
                }
            }, null);
            return;
        }

        runnable.run();
    }

    private long ticksToMillis(long ticks) {
        return Math.max(1L, ticks) * 50L;
    }

    private boolean isFoliaServer() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    public interface TaskHandle {
        void cancel();
    }
}
