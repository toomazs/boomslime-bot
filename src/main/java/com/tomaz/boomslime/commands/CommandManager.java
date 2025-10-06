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
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.awt.Color;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class CommandManager extends ListenerAdapter {

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
                case "help":
                case "ajuda":
                    handleHelpCommand(event);
                    break;
                default:
                    // Comando não reconhecido - não faz nada
                    break;
            }
        }
    }

    private void handlePlayCommand(MessageReceivedEvent event, String[] args) {
        MessageChannel channel = event.getChannel();

        if (args.length < 2) {
            channel.sendMessage("uso: !play <url do spotify>").queue();
            return;
        }

        // Verifica se o usuário está em um canal de voz
        if (event.getMember() == null || event.getMember().getVoiceState() == null || !event.getMember().getVoiceState().inAudioChannel()) {
            channel.sendMessage("voce precisa estar em um canal de voz").queue();
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
            channel.sendMessage("nada tocando no momento").queue();
            return;
        }

        List<AudioTrack> queue = musicManager.getScheduler().getQueue();

        EmbedBuilder embed = new EmbedBuilder();
        embed.setColor(Color.CYAN);
        embed.setTitle("🎵 fila");

        // Música atual
        AudioTrackInfo info = currentTrack.getInfo();
        long position = currentTrack.getPosition();
        long duration = currentTrack.getDuration();

        embed.addField("tocando agora",
                String.format("**%s** - **%s**\n[%s / %s]",
                        info.title,
                        info.author,
                        formatTime(position),
                        formatTime(duration)),
                false);

        // Próximas músicas
        if (queue.isEmpty()) {
            embed.addField("proximas", "fila vazia", false);
        } else {
            StringBuilder queueString = new StringBuilder();
            int count = Math.min(queue.size(), 10); // Mostra até 10 músicas

            for (int i = 0; i < count; i++) {
                AudioTrack track = queue.get(i);
                queueString.append(String.format("%d. **%s** - **%s** [%s]\n",
                        i + 1,
                        track.getInfo().title,
                        track.getInfo().author,
                        formatTime(track.getDuration())));
            }

            if (queue.size() > 10) {
                queueString.append(String.format("\n... e mais %d musica(s)", queue.size() - 10));
            }

            embed.addField("proximas (" + queue.size() + ")", queueString.toString(), false);
        }

        channel.sendMessageEmbeds(embed.build()).queue();
    }

    private void handleSkipCommand(MessageReceivedEvent event) {
        MessageChannel channel = event.getChannel();

        if (!isUserInVoiceChannel(event)) {
            channel.sendMessage("precisa estar no canal de voz").queue();
            return;
        }

        GuildMusicManager musicManager = PlayerManager.getInstance().getMusicManager(event.getGuild());
        AudioPlayer player = musicManager.getAudioPlayer();

        if (player.getPlayingTrack() == null) {
            channel.sendMessage("nada tocando").queue();
            return;
        }

        String trackName = player.getPlayingTrack().getInfo().title;
        musicManager.getScheduler().nextTrack();
        channel.sendMessage("⏭ pulando: **" + trackName + "**").queue();
    }

    private void handleRewindCommand(MessageReceivedEvent event) {
        MessageChannel channel = event.getChannel();

        if (!isUserInVoiceChannel(event)) {
            channel.sendMessage("precisa estar no canal de voz").queue();
            return;
        }

        GuildMusicManager musicManager = PlayerManager.getInstance().getMusicManager(event.getGuild());

        boolean success = musicManager.getScheduler().rewind();

        if (success) {
            channel.sendMessage("⏮ voltando para a anterior").queue();
        } else {
            channel.sendMessage("sem historico").queue();
        }
    }

    private void handlePauseCommand(MessageReceivedEvent event) {
        MessageChannel channel = event.getChannel();

        if (!isUserInVoiceChannel(event)) {
            channel.sendMessage("precisa estar no canal de voz").queue();
            return;
        }

        GuildMusicManager musicManager = PlayerManager.getInstance().getMusicManager(event.getGuild());
        AudioPlayer player = musicManager.getAudioPlayer();

        if (player.getPlayingTrack() == null) {
            channel.sendMessage("nada tocando").queue();
            return;
        }

        if (player.isPaused()) {
            channel.sendMessage("ja pausado").queue();
            return;
        }

        player.setPaused(true);
        channel.sendMessage("⏸ pausado").queue();
    }

    private void handleResumeCommand(MessageReceivedEvent event) {
        MessageChannel channel = event.getChannel();

        if (!isUserInVoiceChannel(event)) {
            channel.sendMessage("precisa estar no canal de voz").queue();
            return;
        }

        GuildMusicManager musicManager = PlayerManager.getInstance().getMusicManager(event.getGuild());
        AudioPlayer player = musicManager.getAudioPlayer();

        if (player.getPlayingTrack() == null) {
            channel.sendMessage("nada tocando").queue();
            return;
        }

        if (!player.isPaused()) {
            channel.sendMessage("ja tocando").queue();
            return;
        }

        player.setPaused(false);
        channel.sendMessage("▶ retomado").queue();
    }

    private void handleStopCommand(MessageReceivedEvent event) {
        MessageChannel channel = event.getChannel();

        if (!isUserInVoiceChannel(event)) {
            channel.sendMessage("precisa estar no canal de voz").queue();
            return;
        }

        GuildMusicManager musicManager = PlayerManager.getInstance().getMusicManager(event.getGuild());
        musicManager.getScheduler().stop();
        event.getGuild().getAudioManager().closeAudioConnection();
        channel.sendMessage("⏹ parado, fila limpa").queue();
    }

    private void handleNowPlayingCommand(MessageReceivedEvent event) {
        MessageChannel channel = event.getChannel();
        GuildMusicManager musicManager = PlayerManager.getInstance().getMusicManager(event.getGuild());
        AudioPlayer player = musicManager.getAudioPlayer();
        AudioTrack track = player.getPlayingTrack();

        if (track == null) {
            channel.sendMessage("❌ Não há nada tocando no momento!").queue();
            return;
        }

        AudioTrackInfo info = track.getInfo();
        long position = track.getPosition();
        long duration = track.getDuration();

        EmbedBuilder embed = new EmbedBuilder();
        embed.setColor(Color.GREEN);
        embed.setTitle("🎵 Tocando Agora");
        embed.addField("Música", info.title, false);
        embed.addField("Artista", info.author, false);
        embed.addField("Progresso", formatTime(position) + " / " + formatTime(duration), false);

        // Barra de progresso
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
            embed.setFooter("⏸️ Pausado");
        }

        channel.sendMessageEmbeds(embed.build()).queue();
    }

    private void handleHelpCommand(MessageReceivedEvent event) {
        MessageChannel channel = event.getChannel();
        String prefix = BotConfig.get("PREFIX", "!");

        EmbedBuilder embed = new EmbedBuilder();
        embed.setColor(Color.ORANGE);
        embed.setTitle("🎵 Comandos do Boomslime Bot");
        embed.setDescription("Aqui estão todos os comandos disponíveis:");

        embed.addField(prefix + "play <música/url>", "Toca uma música (Spotify, YouTube ou busca por nome)", false);
        embed.addField(prefix + "queue", "Mostra a fila de músicas", false);
        embed.addField(prefix + "skip", "Pula a música atual", false);
        embed.addField(prefix + "rewind", "Volta para a música anterior", false);
        embed.addField(prefix + "pause", "Pausa a música atual", false);
        embed.addField(prefix + "resume", "Retoma a música pausada", false);
        embed.addField(prefix + "stop", "Para o player e limpa a fila", false);
        embed.addField(prefix + "nowplaying", "Mostra informações da música atual", false);
        embed.addField(prefix + "help", "Mostra esta mensagem de ajuda", false);

        embed.setFooter("Boomslime Bot - Seu DJ favorito! 🎶");

        channel.sendMessageEmbeds(embed.build()).queue();
    }

    // Métodos auxiliares

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
