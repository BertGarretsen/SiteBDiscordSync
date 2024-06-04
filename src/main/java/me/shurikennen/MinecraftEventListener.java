package me.shurikennen;

import com.earth2me.essentials.Essentials;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.awt.*;

public class MinecraftEventListener implements Listener {

    private final SiteBDiscordSync plugin;
    private final long syncChannelID;
    private final String discordMessageFormat;

    private final Essentials essentials;

    public MinecraftEventListener(SiteBDiscordSync plugin) {
        this.plugin = plugin;
        this.essentials = (Essentials) Bukkit.getPluginManager().getPlugin("Essentials");
        this.syncChannelID = plugin.getConfig().getLong("SyncChannelID");
        this.discordMessageFormat = plugin.getConfig().getString("DiscordMessageFormat");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncChatEvent event) {
        sendMessage(discordMessageFormat.replace("{user}", stripColor(event.getPlayer().getDisplayName())).replace("{message}", PlainTextComponentSerializer.plainText().serialize(event.message())));
    }

    @EventHandler
    public void onAdvancementMade(PlayerAdvancementDoneEvent event) {
        // dont show advancement if player is vanished
        if (essentials.getUser(event.getPlayer()).isVanished()) return;
        if (event.getAdvancement().getDisplay() == null) return;
        if (!event.getAdvancement().getDisplay().doesAnnounceToChat()) return;
        sendEmbed(Color.yellow, stripColor(event.getPlayer().getDisplayName()) + " has made the advancement " + PlainTextComponentSerializer.plainText().serialize(event.getAdvancement().displayName()) + "!");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        if (essentials.getUser(event.getPlayer()).isVanished()) return;
        if (!event.getPlayer().hasPlayedBefore()) {
            sendEmbed(Color.yellow, stripColor(event.getPlayer().getDisplayName()) + " joined the server for the first time");
        } else {
            sendEmbed(Color.green, stripColor(event.getPlayer().getDisplayName()) + " joined the server");
        }
    }

    private String stripColor(String input) {
        return ChatColor.stripColor(input);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        if (essentials.getUser(event.getPlayer()).isVanished()) return;
        sendEmbed(Color.red, stripColor(event.getPlayer().getDisplayName()) + " left the server");
    }

    private void sendMessage(String message) {
        TextChannel textChannel = plugin.getJda().getTextChannelById(syncChannelID);
        if (textChannel == null) return;
        textChannel.sendMessage(message).queue();
    }

    private void sendEmbed(Color color, String message) {
        EmbedBuilder eb = new EmbedBuilder()
                .setColor(color)
                .setDescription("**" + message + "**");

        TextChannel textChannel = plugin.getJda().getTextChannelById(syncChannelID);
        if (textChannel == null) return;
        textChannel.sendMessageEmbeds(eb.build()).queue();
    }
}
