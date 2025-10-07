package com.tomaz.boomslime.config;

import io.github.cdimascio.dotenv.Dotenv;

import java.nio.file.Path;
import java.nio.file.Paths;

public class BotConfig {

    private static final Dotenv dotenv;

    static {
        // Carrega .env do diretório atual ou usa variáveis de ambiente do sistema
        dotenv = Dotenv.configure()
                .ignoreIfMissing()  // Não falha se .env não existir (usa env vars do sistema)
                .load();
        System.out.println(".env carregado com sucesso!");
    }

    /**
     * Gets a configuration value from environment variables
     * @param key The configuration key
     * @return The configuration value or null if not found
     */
    public static String get(String key) {
        return dotenv.get(key);
    }

    /**
     * Gets a configuration value with a default fallback
     * @param key The configuration key
     * @param defaultValue The default value if key is not found
     * @return The configuration value or default
     */
    public static String get(String key, String defaultValue) {
        String value = System.getenv(key);
        return value != null ? value : defaultValue;
    }

    /**
     * Gets the data directory path where the bot stores its files
     * @return Path to the data directory
     */
    public static Path getDataDir() {
        String dataDir = get("DATA_DIR", "./data");
        return Paths.get(dataDir);
    }

    /**
     * Gets the music download directory path
     * @return Path to the music directory
     */
    public static Path getMusicDir() {
        String musicDir = get("MUSIC_DIR");
        if (musicDir != null) {
            return Paths.get(musicDir);
        }
        // Default: ${DATA_DIR}/music
        return getDataDir().resolve("music");
    }

    /**
     * Gets the FFmpeg path for spotdl
     * @return Path to FFmpeg binary
     */
    public static String getFfmpegPath() {
        String ffmpegPath = get("FFMPEG_PATH");
        if (ffmpegPath != null) {
            return ffmpegPath;
        }
        // Default: ${DATA_DIR}/ffmpeg
        return getDataDir().resolve("ffmpeg").toString();
    }
}