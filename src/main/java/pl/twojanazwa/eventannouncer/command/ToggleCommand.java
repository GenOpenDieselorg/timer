package pl.twojanazwa.eventannouncer.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import pl.twojanazwa.eventannouncer.PreferenceManager;

public class ToggleCommand implements CommandExecutor {

    private final PreferenceManager preferenceManager;

    public ToggleCommand(PreferenceManager preferenceManager) {
        this.preferenceManager = preferenceManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Ta komenda może być użyta tylko przez gracza.");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("toggle")) {
            boolean newState = preferenceManager.togglePreferences(player.getUniqueId());
            if (newState) {
                player.sendMessage("§aOtrzymywanie ogłoszeń i BossBarów zostało §lWŁĄCZONE§a.");
            } else {
                player.sendMessage("§cOtrzymywanie ogłoszeń i BossBarów zostało §lWYŁĄCZONE§c.");
            }
            return true;
        }

        sender.sendMessage("§cPoprawne użycie: /" + label + " toggle");
        return true;
    }
}
