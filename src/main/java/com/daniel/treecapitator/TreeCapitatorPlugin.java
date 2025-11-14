package com.daniel.treecapitator;

import com.daniel.treecapitator.command.ToggleCommand;
import com.daniel.treecapitator.listener.TreeCapListener;
import org.bukkit.plugin.java.JavaPlugin;

public class TreeCapitatorPlugin extends JavaPlugin {

    private static TreeCapitatorPlugin instance;
    private boolean globalEnabled;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        this.globalEnabled = getConfig().getBoolean("global-enabled", true);

        getServer().getPluginManager().registerEvents(new TreeCapListener(this), this);
        getCommand("tc").setExecutor(new ToggleCommand(this));

        getLogger().info("[TreeCapitator] Habilitado. Global enabled = " + globalEnabled);
    }

    @Override
    public void onDisable() {
        // save global state
        getConfig().set("global-enabled", globalEnabled);
        saveConfig();
        getLogger().info("[TreeCapitator] Deshabilitado.");
    }

    public static TreeCapitatorPlugin getInstance() {
        return instance;
    }

    public boolean isGlobalEnabled() {
        return globalEnabled;
    }

    public void setGlobalEnabled(boolean globalEnabled) {
        this.globalEnabled = globalEnabled;
    }
}
