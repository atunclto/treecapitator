package com.daniel.treecapitator.command;

import com.daniel.treecapitator.TreeCapitatorPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class ToggleCommand implements CommandExecutor {

    private final TreeCapitatorPlugin plugin;

    public ToggleCommand(TreeCapitatorPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("toggle")) {
            if (args.length >= 2 && args[1].equalsIgnoreCase("global")) {
                if (!sender.hasPermission("treecap.admin")) {
                    sender.sendMessage(ChatColor.RED + "No tienes permiso.");
                    return true;
                }
                boolean cur = plugin.isGlobalEnabled();
                plugin.setGlobalEnabled(!cur);
                plugin.getConfig().set("global-enabled", !cur);
                plugin.saveConfig();
                sender.sendMessage(ChatColor.GREEN + "TreeCapitator global: " + (plugin.isGlobalEnabled() ? "ON" : "OFF"));
                return true;
            } else {
                // toggle per-sender (player only)
                if (!(sender instanceof org.bukkit.entity.Player p)) {
                    sender.sendMessage(ChatColor.RED + "Solo jugadores pueden togglear para sí mismos.");
                    return true;
                }
                // store player toggle in metadata: we'll use persistent player metadata not necessary; for simplicity use player metadata
                boolean disabled = p.hasMetadata("TreeCapitatorDisabled") && p.getMetadata("TreeCapitatorDisabled").get(0).asBoolean();
                if (disabled) {
                    p.removeMetadata("TreeCapitatorDisabled", plugin);
                    p.sendMessage(ChatColor.GREEN + "TreeCapitator activado para ti.");
                } else {
                    p.setMetadata("TreeCapitatorDisabled", new org.bukkit.metadata.FixedMetadataValue(plugin, true));
                    p.sendMessage(ChatColor.YELLOW + "TreeCapitator desactivado para ti.");
                }
                return true;
            }
        }

        sender.sendMessage(ChatColor.YELLOW + "Uso: /tc toggle [global]");
        return true;
    }
}
