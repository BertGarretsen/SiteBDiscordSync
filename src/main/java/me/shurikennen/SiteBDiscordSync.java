package me.shurikennen;


import lombok.Getter;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SiteBDiscordSync extends JavaPlugin implements Listener {

    @Getter
    private JDA jda;
    @Getter
    private final LegacyComponentSerializer legacyComponentSerializer = LegacyComponentSerializer.builder().character('&').extractUrls().build();

    // keep an up to date map of all online player's names lowercased
    @Getter
    private Map<String, UUID> onlinePlayerNames = new HashMap<>();

    private DiscordMessageReceiveListener discordReceiveListener;

    @Override
    public void onEnable() {
        long msStart = System.currentTimeMillis();
        setupConfiguration();
        if (!testConfigured()) {
            getLogger().warning("A configuration file has been generated. it must be configured with a bot token & channel id before the plugin can load properly!");
            return;
        }

        registerEvents();

        getLogger().info("Starting discord bot...");

        startBot();

        // account for any already online players
        for (Player op : Bukkit.getOnlinePlayers()) {
            onlinePlayerNames.put(op.getName().toLowerCase(), op.getUniqueId());
            onlinePlayerNames.put(op.getDisplayName().toLowerCase(), op.getUniqueId());
        }

        getLogger().info("Plugin & Bot started in " + (System.currentTimeMillis() - msStart) + " MS");
    }

    @Override
    public void onDisable() {
        PlayerJoinEvent.getHandlerList().unregister((Plugin) this);
        PlayerQuitEvent.getHandlerList().unregister((Plugin) this);

        jda.removeEventListener(discordReceiveListener);
        jda.shutdownNow();
    }

    private void startBot() {
        this.discordReceiveListener = new DiscordMessageReceiveListener(this);
        jda = JDABuilder.createLight(getConfig().getString("BotToken"), GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
                .addEventListeners(this.discordReceiveListener)
                .setActivity(Activity.of(Activity.ActivityType.valueOf(getConfig().getString("BotActivityType", "PLAYING")), getConfig().getString("BotActivityName", "Minecraft!")))
                .build();

    }

    /**
     * Tests whether the plugin has been configured correctly.
     *
     * @return
     */
    private boolean testConfigured() {
        FileConfiguration config = getConfig();
        boolean hasToken = config.contains("BotToken") && !config.getString("BotToken", "").isBlank();
        boolean hasChannelID = config.contains("SyncChannelID") && config.getLong("SyncChannelID", 0) != 0;

        return hasToken && hasChannelID;
    }

    private void registerEvents() {
        getServer().getPluginManager().registerEvents(new MinecraftEventListener(this), this);
        getServer().getPluginManager().registerEvents(this, this);
    }

    private void setupConfiguration() {
        saveDefaultConfig();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        this.onlinePlayerNames.put(player.getName().toLowerCase(), player.getUniqueId());
        this.onlinePlayerNames.put(player.getDisplayName().toLowerCase(), player.getUniqueId());
    }

    @EventHandler
    public void onLeave(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        this.onlinePlayerNames.remove(player.getName().toLowerCase());
        this.onlinePlayerNames.remove(player.getDisplayName().toLowerCase());
    }
}