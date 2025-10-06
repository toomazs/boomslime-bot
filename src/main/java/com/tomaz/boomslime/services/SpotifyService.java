package com.tomaz.boomslime.services;

import com.tomaz.boomslime.config.BotConfig;
import org.apache.hc.core5.http.ParseException;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.exceptions.SpotifyWebApiException;
import se.michaelthelin.spotify.model_objects.credentials.ClientCredentials;
import se.michaelthelin.spotify.model_objects.specification.Track;
import se.michaelthelin.spotify.requests.authorization.client_credentials.ClientCredentialsRequest;
import se.michaelthelin.spotify.requests.data.tracks.GetTrackRequest;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Serviço para interagir com a API do Spotify.
 */
public class SpotifyService {
    private static SpotifyService INSTANCE;
    private final SpotifyApi spotifyApi;
    private long tokenExpirationTime = 0;

    // Padrões de URL do Spotify
    private static final Pattern SPOTIFY_TRACK_PATTERN = Pattern.compile("^https://open\\.spotify\\.com/track/([a-zA-Z0-9]+)");
    private static final Pattern SPOTIFY_TRACK_URI_PATTERN = Pattern.compile("^spotify:track:([a-zA-Z0-9]+)");

    private SpotifyService() {
        String clientId = BotConfig.get("SPOTIFY_CLIENT_ID");
        String clientSecret = BotConfig.get("SPOTIFY_CLIENT_SECRET");

        this.spotifyApi = new SpotifyApi.Builder()
                .setClientId(clientId)
                .setClientSecret(clientSecret)
                .build();

        // Autentica imediatamente
        authenticate();
    }

    public static synchronized SpotifyService getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new SpotifyService();
        }
        return INSTANCE;
    }

    /**
     * Autentica com o Spotify usando Client Credentials.
     */
    private void authenticate() {
        try {
            ClientCredentialsRequest clientCredentialsRequest = spotifyApi.clientCredentials().build();
            ClientCredentials clientCredentials = clientCredentialsRequest.execute();

            // Define o access token
            spotifyApi.setAccessToken(clientCredentials.getAccessToken());

            // Calcula quando o token expira (em milissegundos)
            tokenExpirationTime = System.currentTimeMillis() + (clientCredentials.getExpiresIn() * 1000);

            System.out.println("Autenticado com o Spotify! Token expira em " + clientCredentials.getExpiresIn() + " segundos.");
        } catch (IOException | SpotifyWebApiException | ParseException e) {
            System.err.println("Erro ao autenticar com o Spotify: " + e.getMessage());
        }
    }

    /**
     * Verifica se o token expirou e reautentica se necessário.
     */
    private void checkTokenExpiration() {
        if (System.currentTimeMillis() >= tokenExpirationTime - 60000) { // Renova 1 minuto antes
            authenticate();
        }
    }

    /**
     * Verifica se uma URL é do Spotify.
     */
    public boolean isSpotifyUrl(String url) {
        return SPOTIFY_TRACK_PATTERN.matcher(url).find() || SPOTIFY_TRACK_URI_PATTERN.matcher(url).find();
    }

    /**
     * Extrai o ID da track de uma URL do Spotify.
     */
    private String extractTrackId(String url) {
        Matcher matcher = SPOTIFY_TRACK_PATTERN.matcher(url);
        if (matcher.find()) {
            return matcher.group(1);
        }

        matcher = SPOTIFY_TRACK_URI_PATTERN.matcher(url);
        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }

    /**
     * Busca a URL do preview (30 segundos) de uma track do Spotify.
     * @param spotifyUrl URL da música no Spotify
     * @return URL do preview MP3 ou null se não houver
     */
    public String getSpotifyPreviewUrl(String spotifyUrl) {
        checkTokenExpiration();

        String trackId = extractTrackId(spotifyUrl);
        if (trackId == null) {
            System.err.println("nao consegui extrair ID do Spotify da URL: " + spotifyUrl);
            return null;
        }

        System.out.println("track ID extraido: " + trackId);

        try {
            GetTrackRequest getTrackRequest = spotifyApi.getTrack(trackId).build();
            Track track = getTrackRequest.execute();

            String previewUrl = track.getPreviewUrl();

            if (previewUrl == null) {
                System.out.println("track nao tem preview disponivel");
                return null;
            }

            String artist = track.getArtists()[0].getName();
            String trackName = track.getName();
            System.out.println("preview encontrado: " + artist + " - " + trackName);

            return previewUrl;
        } catch (IOException | SpotifyWebApiException | ParseException e) {
            System.err.println("erro ao buscar track do Spotify: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
}
