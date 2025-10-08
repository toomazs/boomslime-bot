package com.tomaz.boomslime.commands;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.tomaz.boomslime.config.BotConfig;
import com.tomaz.boomslime.music.GuildMusicManager;
import com.tomaz.boomslime.music.PlayerManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.awt.Color;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class CommandManager extends ListenerAdapter {

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String buttonId = event.getComponentId();

        if (buttonId.startsWith("queue_")) {
            GuildMusicManager musicManager = PlayerManager.getInstance().getMusicManager(event.getGuild());
            AudioPlayer player = musicManager.getAudioPlayer();
            AudioTrack currentTrack = player.getPlayingTrack();

            if (currentTrack == null) {
                event.reply("There is no song playing now.").setEphemeral(true).queue();
                return;
            }

            List<AudioTrack> queue = musicManager.getScheduler().getQueue();
            int page = Integer.parseInt(buttonId.substring(buttonId.lastIndexOf("_") + 1));

            updateQueuePage(event, currentTrack, queue, page);
        }
    }

    private void updateQueuePage(ButtonInteractionEvent event, AudioTrack currentTrack, List<AudioTrack> queue, int page) {
        final int TRACKS_PER_PAGE = 10;
        int totalPages = (int) Math.ceil((double) queue.size() / TRACKS_PER_PAGE);

        if (totalPages == 0) totalPages = 1;
        if (page < 0) page = 0;
        if (page >= totalPages) page = totalPages - 1;

        EmbedBuilder embed = new EmbedBuilder();
        embed.setColor(Color.MAGENTA);
        embed.setTitle("\uD83C\uDFB5  Actual queue");

        AudioTrackInfo info = currentTrack.getInfo();
        long position = currentTrack.getPosition();
        long duration = currentTrack.getDuration();

        embed.addField("▶\uFE0F  Currently playing:",
                String.format("**%s** - **%s**\n[%s / %s]",
                        info.title,
                        info.author,
                        formatTime(position),
                        formatTime(duration)),
                false);

        if (queue.isEmpty()) {
            embed.addField("Song list:", "The queue is empty.", false);
        } else {
            StringBuilder queueString = new StringBuilder();
            int start = page * TRACKS_PER_PAGE;
            int end = Math.min(start + TRACKS_PER_PAGE, queue.size());

            for (int i = start; i < end; i++) {
                AudioTrack track = queue.get(i);
                queueString.append(String.format("%d. **%s** - **%s** [%s]\n",
                        i + 1,
                        track.getInfo().title,
                        track.getInfo().author,
                        formatTime(track.getDuration())));
            }

            embed.addField("Next queue songs (" + queue.size() + ") - Page " + (page + 1) + "/" + totalPages,
                          queueString.toString(), false);
        }

        net.dv8tion.jda.api.interactions.components.buttons.Button prevButton =
            net.dv8tion.jda.api.interactions.components.buttons.Button.secondary("queue_prev_" + (page - 1), "<<<")
                .withDisabled(page == 0);

        net.dv8tion.jda.api.interactions.components.buttons.Button nextButton =
            net.dv8tion.jda.api.interactions.components.buttons.Button.secondary("queue_next_" + (page + 1), ">>>")
                .withDisabled(page >= totalPages - 1 || queue.isEmpty());

        event.editMessageEmbeds(embed.build())
            .setActionRow(prevButton, nextButton)
            .queue();
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) {
            return;
        }

        String prefix = BotConfig.get("PREFIX", "!");
        String content = event.getMessage().getContentRaw();

        if (content.startsWith(prefix)) {
            String[] args = content.substring(prefix.length()).split("\\s+", 2);
            String command = args[0].toLowerCase();

            switch (command) {
                case "play":
                case "p":
                    handlePlayCommand(event, args);
                    break;
                case "queue":
                case "q":
                    handleQueueCommand(event);
                    break;
                case "skip":
                case "s":
                    handleSkipCommand(event);
                    break;
                case "rewind":
                case "prev":
                case "previous":
                    handleRewindCommand(event);
                    break;
                case "pause":
                    handlePauseCommand(event);
                    break;
                case "resume":
                case "unpause":
                    handleResumeCommand(event);
                    break;
                case "stop":
                    handleStopCommand(event);
                    break;
                case "nowplaying":
                case "np":
                    handleNowPlayingCommand(event);
                    break;
                case "shuffle":
                case "embaralhar":
                    handleShuffleCommand(event);
                    break;
                case "help":
                case "ajuda":
                    handleHelpCommand(event);
                    break;
                default:
                    break;
            }
        }
    }

    private void handlePlayCommand(MessageReceivedEvent event, String[] args) {
        MessageChannel channel = event.getChannel();

        if (args.length < 2) {
            channel.sendMessage("> Correct use: !play <Spotify URL>").queue();
            return;
        }

        if (event.getMember() == null || event.getMember().getVoiceState() == null || !event.getMember().getVoiceState().inAudioChannel()) {
            channel.sendMessage("> Please, join a voice channel before use this command.").queue();
            return;
        }

        String input = args[1].trim();

        PlayerManager.getInstance().loadAndPlay(event, input);
    }

    private void handleQueueCommand(MessageReceivedEvent event) {
        MessageChannel channel = event.getChannel();
        GuildMusicManager musicManager = PlayerManager.getInstance().getMusicManager(event.getGuild());
        AudioPlayer player = musicManager.getAudioPlayer();
        AudioTrack currentTrack = player.getPlayingTrack();

        if (currentTrack == null) {
            channel.sendMessage("> There is no song playing now.").queue();
            return;
        }

        List<AudioTrack> queue = musicManager.getScheduler().getQueue();

        sendQueuePage(event, currentTrack, queue, 0);
    }

    private void sendQueuePage(MessageReceivedEvent event, AudioTrack currentTrack, List<AudioTrack> queue, int page) {
        final int TRACKS_PER_PAGE = 10;
        int totalPages = (int) Math.ceil((double) queue.size() / TRACKS_PER_PAGE);

        if (totalPages == 0) totalPages = 1;
        if (page < 0) page = 0;
        if (page >= totalPages) page = totalPages - 1;

        EmbedBuilder embed = new EmbedBuilder();
        embed.setColor(Color.MAGENTA);
        embed.setTitle("\uD83C\uDFB5  Actual queue");

        AudioTrackInfo info = currentTrack.getInfo();
        long position = currentTrack.getPosition();
        long duration = currentTrack.getDuration();

        embed.addField("Currently playing:",
                String.format("**%s** - **%s**\n[%s / %s]",
                        info.title,
                        info.author,
                        formatTime(position),
                        formatTime(duration)),
                false);

        if (queue.isEmpty()) {
            embed.addField("Song list:", "The queue is empty.", false);
        } else {
            StringBuilder queueString = new StringBuilder();
            int start = page * TRACKS_PER_PAGE;
            int end = Math.min(start + TRACKS_PER_PAGE, queue.size());

            for (int i = start; i < end; i++) {
                AudioTrack track = queue.get(i);
                queueString.append(String.format("%d. **%s** - **%s** [%s]\n",
                        i + 1,
                        track.getInfo().title,
                        track.getInfo().author,
                        formatTime(track.getDuration())));
            }

            embed.addField("Next queue songs (" + queue.size() + ") - Page " + (page + 1) + "/" + totalPages,
                          queueString.toString(), false);
        }

        net.dv8tion.jda.api.interactions.components.buttons.Button prevButton =
            net.dv8tion.jda.api.interactions.components.buttons.Button.secondary("queue_prev_" + (page - 1), "<<<")
                .withDisabled(page == 0);

        net.dv8tion.jda.api.interactions.components.buttons.Button nextButton =
            net.dv8tion.jda.api.interactions.components.buttons.Button.secondary("queue_next_" + (page + 1), ">>>")
                .withDisabled(page >= totalPages - 1 || queue.isEmpty());

        event.getChannel().sendMessageEmbeds(embed.build())
            .setActionRow(prevButton, nextButton)
            .queue();
    }

    private void handleSkipCommand(MessageReceivedEvent event) {
        MessageChannel channel = event.getChannel();

        if (!isUserInVoiceChannel(event)) {
            channel.sendMessage("> Please, join a voice channel before use this command.").queue();
            return;
        }

        GuildMusicManager musicManager = PlayerManager.getInstance().getMusicManager(event.getGuild());
        AudioPlayer player = musicManager.getAudioPlayer();

        if (player.getPlayingTrack() == null) {
            channel.sendMessage("> There is no music playing now.").queue();
            return;
        }

        String trackName = player.getPlayingTrack().getInfo().title;
        musicManager.getScheduler().nextTrack();
        channel.sendMessage("> ⏭ Skipping for the next song...").queue();
    }

    private void handleRewindCommand(MessageReceivedEvent event) {
        MessageChannel channel = event.getChannel();

        if (!isUserInVoiceChannel(event)) {
            channel.sendMessage("> Please, join a voice channel before use this command.").queue();
            return;
        }

        GuildMusicManager musicManager = PlayerManager.getInstance().getMusicManager(event.getGuild());

        boolean success = musicManager.getScheduler().rewind();

        if (success) {
            channel.sendMessage("> ⏮ Going back to the previous song...").queue();
        } else {
            channel.sendMessage("> No rewind history.").queue();
        }
    }

    private void handlePauseCommand(MessageReceivedEvent event) {
        MessageChannel channel = event.getChannel();

        if (!isUserInVoiceChannel(event)) {
            channel.sendMessage("> Please, join a voice channel before use this command.").queue();
            return;
        }

        GuildMusicManager musicManager = PlayerManager.getInstance().getMusicManager(event.getGuild());
        AudioPlayer player = musicManager.getAudioPlayer();

        if (player.getPlayingTrack() == null) {
            channel.sendMessage("> There is no song playing now.").queue();
            return;
        }

        if (player.isPaused()) {
            channel.sendMessage("> This song is already paused.").queue();
            return;
        }

        player.setPaused(true);
        channel.sendMessage("> ⏸ Pausing song...").queue();
    }

    private void handleResumeCommand(MessageReceivedEvent event) {
        MessageChannel channel = event.getChannel();

        if (!isUserInVoiceChannel(event)) {
            channel.sendMessage("> Please, join a voice channel before use this command.").queue();
            return;
        }

        GuildMusicManager musicManager = PlayerManager.getInstance().getMusicManager(event.getGuild());
        AudioPlayer player = musicManager.getAudioPlayer();

        if (player.getPlayingTrack() == null) {
            channel.sendMessage("> There is no song playing now.").queue();
            return;
        }

        if (!player.isPaused()) {
            channel.sendMessage("> The music is already playing.").queue();
            return;
        }

        player.setPaused(false);
        channel.sendMessage("> ▶ Resuming the song...").queue();
    }

    private void handleStopCommand(MessageReceivedEvent event) {
        MessageChannel channel = event.getChannel();

        if (!isUserInVoiceChannel(event)) {
            channel.sendMessage("> Please, join a voice channel before use this command.").queue();
            return;
        }

        long guildId = event.getGuild().getIdLong();

        com.tomaz.boomslime.music.DownloadManager.getInstance().cancelAllDownloads(guildId);

        GuildMusicManager musicManager = PlayerManager.getInstance().getMusicManager(event.getGuild());
        musicManager.getScheduler().stop();

        event.getGuild().getAudioManager().closeAudioConnection();

        channel.sendMessage("> ■ Stopping the song...").queue();
    }

    private void handleNowPlayingCommand(MessageReceivedEvent event) {
        MessageChannel channel = event.getChannel();
        GuildMusicManager musicManager = PlayerManager.getInstance().getMusicManager(event.getGuild());
        AudioPlayer player = musicManager.getAudioPlayer();
        AudioTrack track = player.getPlayingTrack();

        if (track == null) {
            channel.sendMessage("> There is no song playing now.").queue();
            return;
        }

        AudioTrackInfo info = track.getInfo();
        long position = track.getPosition();
        long duration = track.getDuration();

        EmbedBuilder embed = new EmbedBuilder();
        embed.setColor(Color.MAGENTA);
        embed.setTitle("▶\uFE0F  Currently playing:");
        embed.addField("Song:", info.title, false);
        embed.addField("Artist:", info.author, false);
        embed.addField("Progress:", formatTime(position) + " / " + formatTime(duration), false);

        int progressBarLength = 20;
        int progress = (int) ((double) position / duration * progressBarLength);
        StringBuilder progressBar = new StringBuilder();
        for (int i = 0; i < progressBarLength; i++) {
            if (i == progress) {
                progressBar.append("🔘");
            } else {
                progressBar.append("▬");
            }
        }
        embed.addField("", progressBar.toString(), false);

        if (player.isPaused()) {
            embed.setFooter("> ⏸ Paused.");
        }

        channel.sendMessageEmbeds(embed.build()).queue();
    }

    private void handleShuffleCommand(MessageReceivedEvent event) {
        MessageChannel channel = event.getChannel();

        if (!isUserInVoiceChannel(event)) {
            channel.sendMessage("> Please, join a voice channel before use this command.").queue();
            return;
        }

        GuildMusicManager musicManager = PlayerManager.getInstance().getMusicManager(event.getGuild());
        List<AudioTrack> queue = musicManager.getScheduler().getQueue();

        if (queue.isEmpty()) {
            channel.sendMessage("> The queue is empty.").queue();
            return;
        }

        musicManager.getScheduler().shuffle();
        channel.sendMessage("> Queue shuffled successfully.").queue();
    }

    private void handleHelpCommand(MessageReceivedEvent event) {
        MessageChannel channel = event.getChannel();
        String prefix = BotConfig.get("PREFIX", "!");

        EmbedBuilder embed = new EmbedBuilder();
        embed.setColor(Color.MAGENTA);
        embed.setTitle("🆘  All commands:");

        embed.addField(prefix + "play or !p <Spotify-URL>", "Play Spotify song or Spotify playlist", false);
        embed.addField(prefix + "queue or !q", "Show the song queue", false);
        embed.addField(prefix + "skip or !s", "Skip the current song", false);
        embed.addField(prefix + "rewind or !prev or !previous", "Go back to the previous song", false);
        embed.addField(prefix + "pause", "Pause the current song", false);
        embed.addField(prefix + "resume or !unpause", "Resume the paused song", false);
        embed.addField(prefix + "shuffle or !embaralhar", "Shuffle the song queue", false);
        embed.addField(prefix + "stop", "Stops the player and clears the queue", false);
        embed.addField(prefix + "nowplaying or !np", "Shows information of the current song", false);
        embed.addField(prefix + "help or !ajuda", "Show this help menu", false);

        channel.sendMessageEmbeds(embed.build()).queue();
    }


    private boolean isUrl(String input) {
        try {
            new URI(input);
            return input.startsWith("http://") || input.startsWith("https://");
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private boolean isUserInVoiceChannel(MessageReceivedEvent event) {
        Member member = event.getMember();
        if (member == null) return false;

        GuildVoiceState voiceState = member.getVoiceState();
        if (voiceState == null) return false;

        return voiceState.inAudioChannel();
    }

    private String formatTime(long millis) {
        long seconds = millis / 1000;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, secs);
        } else {
            return String.format("%02d:%02d", minutes, secs);
        }
    }
}
