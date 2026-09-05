package io.opencode.bestauth.metrics;

import org.bstats.bukkit.Metrics;
import org.bukkit.plugin.java.JavaPlugin;

public class MetricsManager {

    private final JavaPlugin plugin;
    private final int pluginId = 12345;

    public MetricsManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void baslat() {
        try {
            Metrics metrics = new Metrics(plugin, pluginId);

            metrics.addCustomChart(new org.bstats.charts.SingleLineChart("total_players", () -> {
                return plugin.getConfig().getInt("stats.total-players", 0);
            }));

            metrics.addCustomChart(new org.bstats.charts.SingleLineChart("daily_logins", () -> {
                return plugin.getConfig().getInt("stats.daily-logins", 0);
            }));

            metrics.addCustomChart(new org.bstats.charts.SimplePie("language", () -> {
                return plugin.getConfig().getString("language", plugin.getConfig().getString("dil", "en"));
            }));

            plugin.getLogger().info("bStats metrics started successfully!");
        } catch (Exception e) {
            plugin.getLogger().warning("Could not start bStats metrics: " + e.getMessage());
        }
    }
}

