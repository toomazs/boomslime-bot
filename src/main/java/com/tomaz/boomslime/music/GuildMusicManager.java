package com.tomaz.boomslime.music;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;

/**
 * Guarda um AudioPlayer e TrackScheduler para cada servidor (Guild).
 */
public class GuildMusicManager {
    private final AudioPlayer audioPlayer;
    private final TrackScheduler scheduler;
    private final AudioPlayerSendHandler sendHandler;

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
}
