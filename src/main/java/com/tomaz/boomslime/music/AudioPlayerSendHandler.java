package com.tomaz.boomslime.music;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.playback.MutableAudioFrame;
import net.dv8tion.jda.api.audio.AudioSendHandler;

import java.nio.ByteBuffer;

/**
 * Esta é uma classe ponte entre o Lavaplayer e o JDA.
 * O JDA pede pacotes de áudio através desta classe, que por sua vez os pega do AudioPlayer do Lavaplayer.
 */
public class AudioPlayerSendHandler implements AudioSendHandler {
    private final AudioPlayer audioPlayer;
    private final ByteBuffer buffer;
    private final MutableAudioFrame frame;

    /**
     * @param audioPlayer O AudioPlayer do qual esta classe irá obter os dados de áudio.
     */
    public AudioPlayerSendHandler(AudioPlayer audioPlayer) {
        this.audioPlayer = audioPlayer;
        this.buffer = ByteBuffer.allocate(1024); // Aloca um buffer de 1kb
        this.frame = new MutableAudioFrame();
        this.frame.setBuffer(buffer);
    }

    /**
     * O JDA chama este método quando precisa de mais áudio para enviar.
     * @return true se o frame foi fornecido, false caso contrário.
     */
    @Override
    public boolean canProvide() {
        // Pede ao player para fornecer um frame de áudio.
        // O resultado é colocado no 'frame' que passamos no construtor.
        return this.audioPlayer.provide(this.frame);
    }

    /**
     * O JDA chama este método para obter o pacote de áudio em si.
     * @return O buffer de bytes de áudio no formato Opus.
     */
    @Override
    public ByteBuffer provide20MsAudio() {
        // Vira o buffer para que ele possa ser lido a partir do início.
        return this.buffer.flip();
    }

    @Override
    public boolean isOpus() {
        // Lavaplayer já fornece o áudio no formato Opus, que é o que o Discord usa.
        return true;
    }
}