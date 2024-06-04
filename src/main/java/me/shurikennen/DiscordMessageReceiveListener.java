package me.shurikennen;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class DiscordMessageReceiveListener extends ListenerAdapter {

    static final Pattern DEFAULT_URL_PATTERN = Pattern.compile("(?:(https?)://)?([-\\w_.]+\\.\\w{2,})(/\\S*)?");
    static final Pattern URL_SCHEME_PATTERN = Pattern.compile("^[a-z][a-z0-9+\\-.]*:");
    static final LegacyComponentSerializer DEFAULT_MESSAGE_SERIALIZER = LegacyComponentSerializer.builder().extractUrls().build();
    private final SiteBDiscordSync plugin;
    private final long syncChannelID;
    private final TextComponent format;
    private final boolean mentionEnabled;
    private final boolean extractUrlsEnabled;
    private final TextComponent extractedURLFormat;
    private TextColor mentionColor;
    private Sound mentionSound;

    public DiscordMessageReceiveListener(SiteBDiscordSync plugin) {
        this.plugin = plugin;
        this.syncChannelID = plugin.getConfig().getLong("SyncChannelID");
        this.format = plugin.getLegacyComponentSerializer().deserialize(plugin.getConfig().getString("MinecraftFormat", "{message}"));
        this.mentionEnabled = plugin.getConfig().getBoolean("MentionMinecraftPlayers");
        this.extractUrlsEnabled = plugin.getConfig().getBoolean("ExtractUrls");
        this.extractedURLFormat = plugin.getLegacyComponentSerializer().deserialize(plugin.getConfig().getString("ExtractedUrlFormat", "&f[URL]"));
        loadMentionColor();
        loadMentionSound();
    }

    private void loadMentionColor() {
        try {
            this.mentionColor = TextColor.fromHexString(plugin.getConfig().getString("MentionColor", ""));
        } catch (Throwable ignored) {
        }

    }

    private void loadMentionSound() {
        try {
            String rawSound = plugin.getConfig().getString("MentionMinecraftSound", "");
            String[] spliced = rawSound.split(",");
            Key soundKey = Key.key(spliced[0].trim());
            float volume = Float.parseFloat(spliced[1]);
            float pitch = Float.parseFloat(spliced[2]);
            mentionSound = Sound.sound(soundKey, Sound.Source.BLOCK, volume, pitch);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (event.getAuthor().isBot() || event.getAuthor().isSystem()) return;
        if (event.getChannelType() != ChannelType.TEXT) return;
        if (event.getChannel().getIdLong() != syncChannelID) return;

        Member member = event.getMessage().getMember();
        if (member == null) {
            event.getMessage().reply("Unfortunately i could not fetch your username. your message was not sent. sorry about that :(").queue();
            event.getMessage().delete().queueAfter(5, TimeUnit.SECONDS);
            return;
        }

        String sender = member.getEffectiveName();

        if (event.getMessage().getContentStripped().isBlank()) return;

        Component message = formatMessage(sender, event.getMessage().getContentStripped());
        if (!mentionEnabled) {
            // if mentioning is not enabled. send the message straight to everyone

            Bukkit.getOnlinePlayers().forEach((player) -> {
                player.sendMessage(message);
            });
            return;
        }

        Map<UUID, String> mentionedPlayers = findMentionedPlayers(event.getMessage().getContentStripped());

        Bukkit.getOnlinePlayers().forEach((player) -> {
            if (mentionedPlayers.containsKey(player.getUniqueId())) {
                // this player was mentioned
                Component component = formatMentionMessage(sender, event.getMessage().getContentStripped(), player, mentionedPlayers.get(player.getUniqueId()));
                player.sendMessage(component);
                if (mentionSound != null) player.playSound(this.mentionSound);
            } else {
                player.sendMessage(message);
            }
        });
    }


    private Component extractURLCustom(Component message) {
        TextReplacementConfig urlReplacementConfig = TextReplacementConfig.builder()
                .match(DEFAULT_URL_PATTERN)
                .replacement(url -> {
                    String clickUrl = url.content();
                    if (!URL_SCHEME_PATTERN.matcher(clickUrl).find()) {
                        clickUrl = "http://" + clickUrl;
                    }
                    return this.extractedURLFormat
                            .hoverEvent(HoverEvent.showText(Component.text(clickUrl)))
                            .clickEvent(ClickEvent.openUrl(clickUrl));
                })
                .build();

        return message.replaceText(urlReplacementConfig);
    }

    private Component formatMentionMessage(String sender, String rawmessage, Player formatfor, String matchedWord) {
        Component component = formatMessage(rawmessage);

        Component displayname = mentionColor != null ? formatfor.displayName().color(mentionColor) : formatfor.displayName();

        return this.format
                .replaceText(builder -> builder.matchLiteral("{user}").replacement(sender))
                .replaceText(builder -> builder.matchLiteral("{message}").replacement(component
                        .replaceText(builder2 -> builder2.matchLiteral(matchedWord).replacement(displayname))));
    }

    private Component formatMessage(String message) {
        if (!extractUrlsEnabled) {
            return DEFAULT_MESSAGE_SERIALIZER.deserialize(message);
        } else {
            // custom url extraction enabled
            Component component = Component.text()
                    .content(message)
                    .build();
            return extractURLCustom(component);
        }
    }

    private Component formatMessage(String sender, String rawmessage) {

        Component component = formatMessage(rawmessage);
        return this.format.replaceText(builder -> builder.matchLiteral("{user}").replacement(sender)).replaceText(builder -> builder.matchLiteral("{message}").replacement(component));
    }

    /**
     * @param message
     * @return a set of matched players along with the matched word.
     */
    private Map<UUID, String> findMentionedPlayers(String message) {
        Map<UUID, String> result = new HashMap<>();

        String[] spliced = message.split(" ");
        Map<String, UUID> onlinePlayerNames = plugin.getOnlinePlayerNames();
        for (String s : spliced) {
            UUID uuid = onlinePlayerNames.get(s.toLowerCase());
            if (uuid != null) result.put(uuid, s);
        }

        return result;
    }
}
