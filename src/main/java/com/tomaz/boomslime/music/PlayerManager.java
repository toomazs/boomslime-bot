package com.tomaz.boomslime.music;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.tomaz.boomslime.config.BotConfig;
import com.tomaz.boomslime.services.SpotifyService;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.managers.AudioManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlayerManager {
    // Usamos um padrão Singleton para ter apenas uma instância dessa classe no bot todo.
    private static PlayerManager INSTANCE;

    private final AudioPlayerManager audioPlayerManager;
    // Um mapa para guardar um gerenciador de música para cada servidor.
    private final Map<Long, GuildMusicManager> musicManagers;

    private PlayerManager() {
        this.musicManagers = new HashMap<>();
        this.audioPlayerManager = new DefaultAudioPlayerManager();

        // Registra apenas HTTP source para tocar previews do Spotify
        AudioSourceManagers.registerRemoteSources(this.audioPlayerManager);
        AudioSourceManagers.registerLocalSource(this.audioPlayerManager);

        System.out.println("PlayerManager inicializado (somente Spotify)!");
    }

    // Método para pegar a instância única do PlayerManager
    public static synchronized PlayerManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new PlayerManager();
        }
        return INSTANCE;
    }

    // Pega ou cria um GuildMusicManager para um servidor específico.
    public synchronized GuildMusicManager getMusicManager(Guild guild) {
        return this.musicManagers.computeIfAbsent(guild.getIdLong(), (guildId) -> {
            final GuildMusicManager guildMusicManager = new GuildMusicManager(this.audioPlayerManager);
            guild.getAudioManager().setSendingHandler(guildMusicManager.getSendHandler());
            return guildMusicManager;
        });
    }

    // O método principal que será chamado pelos comandos.
    public void loadAndPlay(MessageReceivedEvent event, String input) {
        final GuildMusicManager musicManager = this.getMusicManager(event.getGuild());
        Member member = event.getMember();

        if (member == null) {
            event.getChannel().sendMessage("erro ao identificar usuario").queue();
            return;
        }

        GuildVoiceState voiceState = member.getVoiceState();

        if (voiceState == null || !voiceState.inAudioChannel()) {
            event.getChannel().sendMessage("voce precisa estar em um canal de voz").queue();
            return;
        }

        AudioChannelUnion audioChannel = voiceState.getChannel();
        AudioManager audioManager = event.getGuild().getAudioManager();

        // Conecta ao canal de voz se ainda não estiver conectado
        if (!audioManager.isConnected()) {
            audioManager.openAudioConnection(audioChannel);
        }

        // Verifica se é URL do Spotify
        SpotifyService spotifyService = SpotifyService.getInstance();

        if (!spotifyService.isSpotifyUrl(input)) {
            event.getChannel().sendMessage("por enquanto apenas links do spotify funcionam").queue();
            return;
        }

        // Verifica se é playlist
        if (spotifyService.isPlaylist(input)) {
            loadPlaylist(event, input, musicManager);
            return;
        }

        event.getChannel().sendMessage("🔍 procurando e baixando musica... (pode demorar ~20s)").queue();

        // Usa spotdl para baixar a música completa em uma thread separada
        new Thread(() -> {
            long startTime = System.currentTimeMillis();
            SpotifyDownloader downloader = SpotifyDownloader.getInstance();
            String filePath = downloader.downloadTrack(input);

            if (filePath == null) {
                event.getChannel().sendMessage("❌ erro ao baixar musica").queue();
                return;
            }

            long downloadTime = (System.currentTimeMillis() - startTime) / 1000;
            System.out.println("⏱️ Download concluído em " + downloadTime + "s");

            // Carrega o arquivo local no Lavaplayer
            this.audioPlayerManager.loadItemOrdered(musicManager, filePath, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                boolean isPlaying = musicManager.getAudioPlayer().getPlayingTrack() != null;
                String trackInfo = track.getInfo().title + " - " + track.getInfo().author;

                musicManager.getScheduler().queue(track);

                if (isPlaying) {
                    event.getChannel().sendMessage("✓ " + trackInfo + " foi adicionado a fila").queue();
                } else {
                    event.getChannel().sendMessage("▶ tocando: " + trackInfo).queue();
                }
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                if (playlist.isSearchResult()) {
                    AudioTrack firstTrack = playlist.getTracks().get(0);
                    event.getChannel().sendMessage("tocando: " + firstTrack.getInfo().title + " - " + firstTrack.getInfo().author).queue();
                    musicManager.getScheduler().queue(firstTrack);
                } else {
                    event.getChannel().sendMessage("playlist adicionada: " + playlist.getName() + " (" + playlist.getTracks().size() + " musicas)").queue();
                    for (AudioTrack track : playlist.getTracks()) {
                        musicManager.getScheduler().queue(track);
                    }
                }
            }

            @Override
            public void noMatches() {
                event.getChannel().sendMessage("nao encontrei a musica").queue();
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                event.getChannel().sendMessage("erro ao tocar: " + exception.getMessage()).queue();
                exception.printStackTrace(); // Debug
            }
        });

            // Limpa arquivos antigos após adicionar à fila
            downloader.cleanupOldFiles();
        }).start();
    }

    // Carrega e toca uma playlist completa do Spotify
    private void loadPlaylist(MessageReceivedEvent event, String playlistUrl, GuildMusicManager musicManager) {
        event.getChannel().sendMessage("📋 carregando playlist...").queue();

        new Thread(() -> {
            SpotifyService spotifyService = SpotifyService.getInstance();
            List<String> trackUrls = spotifyService.getPlaylistTracks(playlistUrl);

            if (trackUrls.isEmpty()) {
                event.getChannel().sendMessage("❌ playlist vazia ou erro ao carregar").queue();
                return;
            }

            event.getChannel().sendMessage("✓ " + trackUrls.size() + " musicas encontradas").queue();

            // CRÍTICO: Processa SEQUENCIALMENTE para manter ordem
            for (int i = 0; i < trackUrls.size(); i++) {
                final String trackUrl = trackUrls.get(i);
                final int trackNumber = i + 1;
                final int totalTracks = trackUrls.size();

                SpotifyDownloader downloader = SpotifyDownloader.getInstance();

                // Download SÍNCRONO para manter ordem
                String filePath = downloader.downloadTrack(trackUrl);

                if (filePath == null) {
                    System.err.println("❌ erro ao baixar track #" + trackNumber);
                    continue; // Pula para próxima
                }

                // Carrega o arquivo no Lavaplayer
                this.audioPlayerManager.loadItemOrdered(musicManager, filePath, new AudioLoadResultHandler() {
                    @Override
                    public void trackLoaded(AudioTrack track) {
                        musicManager.getScheduler().queue(track);

                        if (trackNumber == 1) {
                            event.getChannel().sendMessage("▶ tocando primeira musica, baixando resto...").queue();
                        }

                        System.out.println("✓ [" + trackNumber + "/" + totalTracks + "] " + track.getInfo().title);
                    }

                    @Override
                    public void playlistLoaded(AudioPlaylist playlist) {
                        // Não deve acontecer com arquivo local
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

                // Delay de 5 segundos entre downloads (exceto o último)
                if (i < trackUrls.size() - 1) {
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }

            event.getChannel().sendMessage("✅ playlist completa! " + trackUrls.size() + " musicas enfileiradas").queue();
        }).start();
    }
}
