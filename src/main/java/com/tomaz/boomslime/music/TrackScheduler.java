package com.tomaz.boomslime.music;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Esta classe agenda as faixas para o player de áudio.
 * Ela funciona como um "ouvinte" de eventos do player e tem uma fila de faixas.
 */
public class TrackScheduler extends AudioEventAdapter {
    private final AudioPlayer player;
    private final BlockingQueue<AudioTrack> queue;
    private final List<AudioTrack> history; // Histórico de músicas tocadas
    private AudioTrack lastTrack; // Última música que tocou

    /**
     * @param player O player de áudio que este agendador irá gerenciar.
     */
    public TrackScheduler(AudioPlayer player) {
        this.player = player;
        this.queue = new LinkedBlockingQueue<>();
        this.history = new ArrayList<>();
    }

    /**
     * Adiciona uma nova faixa à fila. Se não houver nada tocando, a faixa começa a tocar imediatamente.
     *
     * @param track A faixa a ser adicionada na fila.
     */
    public void queue(AudioTrack track) {
        // Tenta iniciar a faixa. Se o player já estiver tocando algo, startTrack retorna falso.
        if (!this.player.startTrack(track, true)) {
            // Se não conseguiu iniciar (porque já estava tocando), oferece a faixa para a fila.
            this.queue.offer(track);
        }
    }

    /**
     * Pula a música atual e toca a próxima.
     */
    public void nextTrack() {
        AudioTrack nextTrack = this.queue.poll();
        player.startTrack(nextTrack, false);
    }

    /**
     * Volta para a música anterior (rewind).
     * @return true se conseguiu voltar, false se não há histórico
     */
    public boolean rewind() {
        if (history.isEmpty()) {
            return false;
        }

        // Pega a última música do histórico
        AudioTrack previousTrack = history.remove(history.size() - 1);

        // Se houver uma música tocando atualmente, adiciona de volta ao início da fila
        AudioTrack currentTrack = player.getPlayingTrack();
        if (currentTrack != null) {
            // Cria uma cópia da fila atual
            List<AudioTrack> tempQueue = new ArrayList<>();
            queue.drainTo(tempQueue);

            // Limpa a fila
            queue.clear();

            // Adiciona a música atual no início
            queue.offer(currentTrack.makeClone());

            // Adiciona de volta todas as músicas que estavam na fila
            queue.addAll(tempQueue);
        }

        // Toca a música anterior
        player.startTrack(previousTrack, false);
        return true;
    }

    /**
     * Para o player e limpa a fila.
     */
    public void stop() {
        queue.clear();
        history.clear();
        player.stopTrack();
    }

    /**
     * Retorna uma lista não modificável da fila atual.
     */
    public List<AudioTrack> getQueue() {
        return new ArrayList<>(queue);
    }

    /**
     * Retorna o histórico de músicas.
     */
    public List<AudioTrack> getHistory() {
        return Collections.unmodifiableList(history);
    }

    /**
     * Chamado pelo Lavaplayer quando uma faixa termina de tocar.
     */
    @Override
    public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
        // Adiciona a música que acabou ao histórico (com um clone para preservar as informações)
        if (endReason == AudioTrackEndReason.FINISHED || endReason == AudioTrackEndReason.LOAD_FAILED) {
            // Mantém apenas as últimas 50 músicas no histórico
            if (history.size() >= 50) {
                history.remove(0);
            }
            history.add(track.makeClone());
        }

        // Só começa a próxima faixa se a anterior terminou de forma que permita isso.
        // (ex: terminou normalmente, foi pulada, etc. e não se falhou ao carregar).
        if (endReason.mayStartNext) {
            // Pega a próxima música da fila. Se a fila estiver vazia, poll() retorna null.
            AudioTrack nextTrack = this.queue.poll();
            // Inicia a próxima faixa. Se nextTrack for null, o player simplesmente para de tocar.
            player.startTrack(nextTrack, false);
        }
    }

    @Override
    public void onTrackStart(AudioPlayer player, AudioTrack track) {
        this.lastTrack = track;
    }
}
