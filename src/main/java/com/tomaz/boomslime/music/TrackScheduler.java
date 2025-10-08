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

public class TrackScheduler extends AudioEventAdapter {
    private final AudioPlayer player;
    private final BlockingQueue<AudioTrack> queue;
    private final List<AudioTrack> history;
    private AudioTrack lastTrack;
    private MessageChannel textChannel;

    private static final long FADE_DURATION = 3000;
    private Timer fadeTimer;
    private boolean fadeStarted = false;

    public TrackScheduler(AudioPlayer player) {
        this.player = player;
        this.queue = new LinkedBlockingQueue<>();
        this.history = new ArrayList<>();
        this.fadeTimer = new Timer("FadeTimer", true);
    }

    public void setTextChannel(MessageChannel channel) {
        this.textChannel = channel;
    }

    public void queue(AudioTrack track) {
        if (!this.player.startTrack(track, true)) {
            this.queue.offer(track);
        }
    }

    public void nextTrack() {
        AudioTrack nextTrack = this.queue.poll();
        player.startTrack(nextTrack, false);
    }

    public boolean rewind() {
        if (history.isEmpty()) {
            return false;
        }

        AudioTrack previousTrack = history.remove(history.size() - 1);

        AudioTrack currentTrack = player.getPlayingTrack();
        if (currentTrack != null) {
            List<AudioTrack> tempQueue = new ArrayList<>();
            queue.drainTo(tempQueue);

            queue.clear();

            queue.offer(currentTrack.makeClone());

            queue.addAll(tempQueue);
        }

        player.startTrack(previousTrack, false);
        return true;
    }

    public void stop() {
        if (fadeTimer != null) {
            fadeTimer.cancel();
            fadeTimer = new Timer("FadeTimer", true);
        }

        queue.clear();
        history.clear();
        player.stopTrack();
        fadeStarted = false;
    }

    public List<AudioTrack> getQueue() {
        return new ArrayList<>(queue);
    }


    public List<AudioTrack> getHistory() {
        return Collections.unmodifiableList(history);
    }

    public void shuffle() {
        List<AudioTrack> tracks = new ArrayList<>(queue);
        Collections.shuffle(tracks);
        queue.clear();
        queue.addAll(tracks);
    }

    @Override
    public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
        if (endReason != AudioTrackEndReason.STOPPED && endReason != AudioTrackEndReason.CLEANUP) {
            if (history.size() >= 50) {
                history.remove(0);
            }
            history.add(track.makeClone());
        }

        if (endReason.mayStartNext) {
            AudioTrack nextTrack = this.queue.poll();
            player.startTrack(nextTrack, false);
        }
    }

    @Override
    public void onTrackStart(AudioPlayer player, AudioTrack track) {
        this.lastTrack = track;
        this.fadeStarted = false;
        scheduleFadeOut(track);

        if (textChannel != null) {
            Timer msgTimer = new Timer("NowPlayingMessageTimer", true);
            msgTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    String artist = track.getInfo().author;
                    String title = track.getInfo().title;
                    textChannel.sendMessage("> ▶ **" + artist + " - " + title + "**").queue();
                    msgTimer.cancel();
                }
            }, 1500);
        }
    }

    private void scheduleFadeOut(AudioTrack track) {
        if (fadeTimer != null) {
            fadeTimer.cancel();
            fadeTimer = new Timer("FadeTimer", true);
        }

        if (queue.isEmpty()) {
            return;
        }

        long duration = track.getDuration();
        if (duration < 8000) {
            return;
        }

        long fadeStartTime = duration - FADE_DURATION;

        fadeTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                startFadeOut();
            }
        }, fadeStartTime);

        System.out.println("🎵 Fade-out agendado para " + (fadeStartTime / 1000) + "s");
    }

    private void startFadeOut() {
        if (fadeStarted || queue.isEmpty()) {
            return;
        }

        fadeStarted = true;
        System.out.println("🎵 Iniciando fade-out...");

        final int originalVolume = player.getVolume();
        Timer timer = new Timer("FadeOutTimer", true);

        timer.scheduleAtFixedRate(new TimerTask() {
            int steps = 30;
            int currentStep = 0;

            @Override
            public void run() {
                if (currentStep >= steps) {
                    timer.cancel();
                    player.setVolume(originalVolume);
                    fadeStarted = false;
                    return;
                }

                float progress = (float) currentStep / steps;
                int newVolume = (int) (originalVolume * (1.0f - (progress * 0.8f)));
                player.setVolume(newVolume);
                currentStep++;
            }
        }, 0, FADE_DURATION / 30);
    }
}
