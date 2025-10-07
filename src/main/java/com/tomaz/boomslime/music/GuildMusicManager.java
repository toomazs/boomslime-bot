package com.tomaz.boomslime.music;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

/**
 * Guarda um AudioPlayer e TrackScheduler para cada servidor (Guild).
 */
public class GuildMusicManager {
    private final AudioPlayer audioPlayer;
    private final TrackScheduler scheduler;
    private final AudioPlayerSendHandler sendHandler;
    private MessageChannel textChannel;

    /**
     * Cria um player e um track scheduler.
     * @param manager Gerenciador de áudio que vai criar o player.
     */
    public GuildMusicManager(AudioPlayerManager manager) {
        this.audioPlayer = manager.createPlayer();
        this.scheduler = new TrackScheduler(this.audioPlayer);
        this.audioPlayer.addListener(this.scheduler);
        this.sendHandler = new AudioPlayerSendHandler(this.audioPlayer);
    }

    /**
     * @return O handler que envia áudio para o Discord.
     */
    public AudioPlayerSendHandler getSendHandler() {
        return sendHandler;
    }

    /**
     * @return O player de áudio.
     */
    public AudioPlayer getAudioPlayer() {
        return audioPlayer;
    }

    /**
     * @return O agendador de faixas.
     */
    public TrackScheduler getScheduler() {
        return scheduler;
    }

    /**
     * Define o canal de texto para enviar mensagens.
     */
    public void setTextChannel(MessageChannel channel) {
        this.textChannel = channel;
        this.scheduler.setTextChannel(channel);
    }

    /**
     * @return O canal de texto.
     */
    public MessageChannel getTextChannel() {
        return textChannel;
    }
}
