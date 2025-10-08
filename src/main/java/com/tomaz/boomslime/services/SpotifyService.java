package com.tomaz.boomslime.services;

import com.tomaz.boomslime.config.BotConfig;
import org.apache.hc.core5.http.ParseException;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.exceptions.SpotifyWebApiException;
import se.michaelthelin.spotify.model_objects.credentials.ClientCredentials;
import se.michaelthelin.spotify.model_objects.specification.PlaylistTrack;
import se.michaelthelin.spotify.model_objects.specification.Paging;
import se.michaelthelin.spotify.requests.authorization.client_credentials.ClientCredentialsRequest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Serviço para interagir com o Spotify.
 */
public class SpotifyService {
    private static SpotifyService INSTANCE;
    private final SpotifyApi spotifyApi;
    private long tokenExpirationTime = 0;

    private static final Pattern SPOTIFY_TRACK_PATTERN = Pattern.compile("^https://open\\.spotify\\.com/(?:intl-[a-z]{2}/)?track/([a-zA-Z0-9]+)");
    private static final Pattern SPOTIFY_PLAYLIST_PATTERN = Pattern.compile("^https://open\\.spotify\\.com/(?:intl-[a-z]{2}/)?playlist/([a-zA-Z0-9]+)");
    private static final Pattern SPOTIFY_ALBUM_PATTERN = Pattern.compile("^https://open\\.spotify\\.com/(?:intl-[a-z]{2}/)?album/([a-zA-Z0-9]+)");
    private static final Pattern SPOTIFY_TRACK_URI_PATTERN = Pattern.compile("^spotify:track:([a-zA-Z0-9]+)");

    private SpotifyService() {
        String clientId = BotConfig.get("SPOTIFY_CLIENT_ID");
        String clientSecret = BotConfig.get("SPOTIFY_CLIENT_SECRET");

        this.spotifyApi = new SpotifyApi.Builder()
                .setClientId(clientId)
                .setClientSecret(clientSecret)
                .build();

        authenticate();
        System.out.println("SpotifyService inicializado com API");
    }

    public static synchronized SpotifyService getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new SpotifyService();
        }
        return INSTANCE;
    }

    private void authenticate() {
        try {
            ClientCredentialsRequest request = spotifyApi.clientCredentials().build();
            ClientCredentials credentials = request.execute();

            spotifyApi.setAccessToken(credentials.getAccessToken());
            tokenExpirationTime = System.currentTimeMillis() + (credentials.getExpiresIn() * 1000);

            System.out.println("✓ Autenticado com Spotify API");
        } catch (IOException | SpotifyWebApiException | ParseException e) {
            System.err.println("❌ Erro ao autenticar com Spotify: " + e.getMessage());
        }
    }

    private void checkTokenExpiration() {
        if (System.currentTimeMillis() >= tokenExpirationTime - 60000) {
            authenticate();
        }
    }

    /**
     * Verifica se uma URL é do Spotify (track, playlist ou album).
     */
    public boolean isSpotifyUrl(String url) {
        return SPOTIFY_TRACK_PATTERN.matcher(url).find()
            || SPOTIFY_PLAYLIST_PATTERN.matcher(url).find()
            || SPOTIFY_ALBUM_PATTERN.matcher(url).find()
            || SPOTIFY_TRACK_URI_PATTERN.matcher(url).find();
    }

    /**
     * Verifica se é uma playlist
     */
    public boolean isPlaylist(String url) {
        return SPOTIFY_PLAYLIST_PATTERN.matcher(url).find();
    }

    /**
     * Extrai o ID da playlist
     */
    private String extractPlaylistId(String url) {
        Matcher matcher = SPOTIFY_PLAYLIST_PATTERN.matcher(url);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * Busca todas as músicas de uma playlist NA ORDEM
     * @return Lista de URLs das tracks
     */
    public List<String> getPlaylistTracks(String playlistUrl) {
        checkTokenExpiration();
        List<String> trackUrls = new ArrayList<>();

        try {
            String playlistId = extractPlaylistId(playlistUrl);
            if (playlistId == null) {
                System.err.println("❌ ID da playlist inválido");
                return trackUrls;
            }

            System.out.println("📋 Buscando playlist: " + playlistId);

            int offset = 0;
            Paging<PlaylistTrack> playlistTracks;

            do {
                playlistTracks = spotifyApi.getPlaylistsItems(playlistId)
                        .limit(100)
                        .offset(offset)
                        .build()
                        .execute();

                for (PlaylistTrack item : playlistTracks.getItems()) {
                    if (item.getTrack() != null && item.getTrack().getId() != null) {
                        String trackUrl = "https://open.spotify.com/track/" + item.getTrack().getId();
                        trackUrls.add(trackUrl);
                    }
                }

                offset += 100;
            } while (playlistTracks.getNext() != null);

            System.out.println("✓ Encontradas " + trackUrls.size() + " músicas na playlist");

        } catch (IOException | SpotifyWebApiException | ParseException e) {
            System.err.println("❌ Erro ao buscar playlist: " + e.getMessage());
            e.printStackTrace();
        }

        return trackUrls;
    }
}
