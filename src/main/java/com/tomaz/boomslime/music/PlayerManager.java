package com.tomaz.boomslime.music;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.tomaz.boomslime.services.SpotifyService;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.managers.AudioManager;

public class PlayerManager {
    private static PlayerManager INSTANCE;

    private final AudioPlayerManager audioPlayerManager;
    private final Map<Long, GuildMusicManager> musicManagers;

    private PlayerManager() {
        this.musicManagers = new HashMap<>();
        this.audioPlayerManager = new DefaultAudioPlayerManager();

        AudioSourceManagers.registerRemoteSources(this.audioPlayerManager);
        AudioSourceManagers.registerLocalSource(this.audioPlayerManager);

        System.out.println("PlayerManager inicializado (somente Spotify)!");
    }

    public static synchronized PlayerManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new PlayerManager();
        }
        return INSTANCE;
    }

    public synchronized GuildMusicManager getMusicManager(Guild guild) {
        return this.musicManagers.computeIfAbsent(guild.getIdLong(), (guildId) -> {
            final GuildMusicManager guildMusicManager = new GuildMusicManager(this.audioPlayerManager);
            guild.getAudioManager().setSendingHandler(guildMusicManager.getSendHandler());
            return guildMusicManager;
        });
    }

    public void loadAndPlay(MessageReceivedEvent event, String input) {
        final GuildMusicManager musicManager = this.getMusicManager(event.getGuild());
        Member member = event.getMember();

        if (member == null) {
            event.getChannel().sendMessage("> ⚠ Critical error identifying user. Contact @toomazs.").queue();
            return;
        }

        GuildVoiceState voiceState = member.getVoiceState();

        if (voiceState == null || !voiceState.inAudioChannel()) {
            event.getChannel().sendMessage("> Please join a voice channel.").queue();
            return;
        }

        AudioChannelUnion audioChannel = voiceState.getChannel();
        AudioManager audioManager = event.getGuild().getAudioManager();

        if (!audioManager.isConnected()) {
            audioManager.openAudioConnection(audioChannel);
        }

        musicManager.setTextChannel(event.getChannel());

        long guildId = event.getGuild().getIdLong();
        DownloadManager.getInstance().getGuildState(guildId).reset();

        SpotifyService spotifyService = SpotifyService.getInstance();

        if (!spotifyService.isSpotifyUrl(input)) {
            event.getChannel().sendMessage("> For now, only Spotify links work. Please provide a valid link.").queue();
            return;
        }

        if (spotifyService.isPlaylist(input)) {
            loadPlaylist(event, input, musicManager);
            return;
        }

        event.getChannel().sendMessage("> Downloading and playing in the queue, one moment...").queue();

        DownloadManager downloadManager = DownloadManager.getInstance();

        downloadManager.submitDownload(guildId, () -> {
            long startTime = System.currentTimeMillis();
            SpotifyDownloader downloader = SpotifyDownloader.getInstance();
            String filePath = downloader.downloadTrack(input);

            if (filePath == null) {
                event.getChannel().sendMessage("> ⚠ Three attempts were made to download the requested song, but a download error occurred.").queue();
                return null;
            }

            long downloadTime = (System.currentTimeMillis() - startTime) / 1000;
            System.out.println("⏱️ Download concluído em " + downloadTime + "s");

            this.audioPlayerManager.loadItemOrdered(musicManager, filePath, new AudioLoadResultHandler() {
                @Override
                public void trackLoaded(AudioTrack track) {
                    boolean isPlaying = musicManager.getAudioPlayer().getPlayingTrack() != null;
                    String trackInfo = track.getInfo().title + " - " + track.getInfo().author;

                    musicManager.getScheduler().queue(track);

                    if (isPlaying) {
                        event.getChannel().sendMessage("> ✓ " + trackInfo + " was successfully added to the queue.").queue();
                    }
                }

                @Override
                public void playlistLoaded(AudioPlaylist playlist) {
                    if (playlist.isSearchResult()) {
                        AudioTrack firstTrack = playlist.getTracks().get(0);
                        event.getChannel().sendMessage("> ▶ **" + firstTrack.getInfo().title + " - " + firstTrack.getInfo().author + "**").queue();
                        musicManager.getScheduler().queue(firstTrack);
                    } else {
                        event.getChannel().sendMessage("> Successfully added playlist: " + playlist.getName() + " (" + playlist.getTracks().size() + " songs.)").queue();
                        for (AudioTrack track : playlist.getTracks()) {
                            musicManager.getScheduler().queue(track);
                        }
                    }
                }

                @Override
                public void noMatches() {
                    event.getChannel().sendMessage("> Song not found. Try another one.").queue();
                }

                @Override
                public void loadFailed(FriendlyException exception) {
                    event.getChannel().sendMessage("> Error playing music. Contact @toomazs: " + exception.getMessage()).queue();
                    exception.printStackTrace();
                }
            });

            return filePath;
        });
    }

    private void loadPlaylist(MessageReceivedEvent event, String playlistUrl, GuildMusicManager musicManager) {
        event.getChannel().sendMessage("> Loading playlist and queuing...").queue();

        long guildId = event.getGuild().getIdLong();
        DownloadManager downloadManager = DownloadManager.getInstance();

        downloadManager.submitDownload(guildId, () -> {
            SpotifyService spotifyService = SpotifyService.getInstance();
            List<String> trackUrls = spotifyService.getPlaylistTracks(playlistUrl);

            if (trackUrls.isEmpty()) {
                event.getChannel().sendMessage("> Error enqueuing playlist. The playlist may be private, empty, or the player may have an error.").queue();
                return null;
            }

            event.getChannel().sendMessage("> " + trackUrls.size() + " Found songs. Downloading them...").queue();

            for (int i = 0; i < trackUrls.size(); i++) {
                if (Thread.currentThread().isInterrupted()) {
                    System.out.println("⏹ Download de playlist interrompido");
                    event.getChannel().sendMessage("> ⏹ Playlist download canceled.").queue();
                    return null;
                }

                final String trackUrl = trackUrls.get(i);
                final int trackNumber = i + 1;
                final int totalTracks = trackUrls.size();

                SpotifyDownloader downloader = SpotifyDownloader.getInstance();

                String filePath = downloader.downloadTrack(trackUrl);

                if (filePath == null) {
                    if (Thread.currentThread().isInterrupted()) {
                        System.out.println("⏹ Download de playlist interrompido");
                        return null;
                    }

                    System.err.println("❌ erro ao baixar track #" + trackNumber);
                    event.getChannel().sendMessage("> ⚠ Track number  #" + trackNumber + " was skipped because three attempts to download were made it and all three failed.").queue();
                    continue;
                }

                this.audioPlayerManager.loadItemOrdered(musicManager, filePath, new AudioLoadResultHandler() {
                    @Override
                    public void trackLoaded(AudioTrack track) {
                        musicManager.getScheduler().queue(track);

                        System.out.println("✓ [" + trackNumber + "/" + totalTracks + "] " + track.getInfo().title);
                    }

                    @Override
                    public void playlistLoaded(AudioPlaylist playlist) {
                    }

                    @Override
                    public void noMatches() {
                        System.err.println("❌ arquivo não encontrado: " + filePath);
                    }

                    @Override
                    public void loadFailed(FriendlyException exception) {
                        System.err.println("❌ erro ao carregar track #" + trackNumber + ": " + exception.getMessage());
                    }
                });

                if (i < trackUrls.size() - 1) {
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        System.out.println("⏹ Download de playlist interrompido durante delay");
                        Thread.currentThread().interrupt();
                        return null;
                    }
                }
            }

            event.getChannel().sendMessage("> Complete playlist: " + trackUrls.size() + " songs lined up.").queue();
            return "playlist_complete";
        });
    }
}
