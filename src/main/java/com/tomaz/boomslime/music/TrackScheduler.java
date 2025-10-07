package com.tomaz.boomslime.music;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
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
    private MessageChannel textChannel; // Canal para enviar mensagens

    // Fade-out simples
    private static final long FADE_DURATION = 3000; // 3 segundos em ms
    private Timer fadeTimer;
    private boolean fadeStarted = false;

    /**
     * @param player O player de áudio que este agendador irá gerenciar.
     */
    public TrackScheduler(AudioPlayer player) {
        this.player = player;
        this.queue = new LinkedBlockingQueue<>();
        this.history = new ArrayList<>();
        this.fadeTimer = new Timer("FadeTimer", true);
    }

    /**
     * Define o canal de texto para enviar mensagens.
     */
    public void setTextChannel(MessageChannel channel) {
        this.textChannel = channel;
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
     * Embaralha a fila de músicas.
     */
    public void shuffle() {
        List<AudioTrack> tracks = new ArrayList<>(queue);
        Collections.shuffle(tracks);
        queue.clear();
        queue.addAll(tracks);
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
        this.fadeStarted = false;
        scheduleFadeOut(track);

        // Envia mensagem "tocando agora:"
        if (textChannel != null) {
            String artist = track.getInfo().author;
            String title = track.getInfo().title;
            textChannel.sendMessage("▶ to tocano agr: **" + artist + " - " + title + "**").queue();
        }
    }

    /**
     * Agenda o fade-out para começar 3 segundos antes do fim da música
     */
    private void scheduleFadeOut(AudioTrack track) {
        // Cancela qualquer timer anterior
        if (fadeTimer != null) {
            fadeTimer.cancel();
            fadeTimer = new Timer("FadeTimer", true);
        }

        // Se não há próxima música na fila, não faz fade
        if (queue.isEmpty()) {
            return;
        }

        long duration = track.getDuration();
        // Se a música for muito curta (menos de 8 segundos), não faz fade
        if (duration < 8000) {
            return;
        }

        // Calcula quando começar o fade (3 segundos antes do fim)
        long fadeStartTime = duration - FADE_DURATION;

        fadeTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                startFadeOut();
            }
        }, fadeStartTime);

        System.out.println("🎵 Fade-out agendado para " + (fadeStartTime / 1000) + "s");
    }

    /**
     * Inicia o fade-out: reduz volume gradualmente nos últimos 3 segundos
     */
    private void startFadeOut() {
        if (fadeStarted || queue.isEmpty()) {
            return;
        }

        fadeStarted = true;
        System.out.println("🎵 Iniciando fade-out...");

        final int originalVolume = player.getVolume();
        Timer timer = new Timer("FadeOutTimer", true);

        timer.scheduleAtFixedRate(new TimerTask() {
            int steps = 30; // 30 steps em 3 segundos = 100ms por step
            int currentStep = 0;

            @Override
            public void run() {
                if (currentStep >= steps) {
                    timer.cancel();
                    // Restaura volume e pula para próxima música
                    player.setVolume(originalVolume);
                    fadeStarted = false;
                    return;
                }

                // Reduz o volume gradualmente (100% -> 20%)
                float progress = (float) currentStep / steps;
                int newVolume = (int) (originalVolume * (1.0f - (progress * 0.8f)));
                player.setVolume(newVolume);
                currentStep++;
            }
        }, 0, FADE_DURATION / 30); // 100ms por step
    }
}
