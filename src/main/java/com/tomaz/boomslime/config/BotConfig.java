package com.tomaz.boomslime.config;

import io.github.cdimascio.dotenv.Dotenv;

import java.nio.file.Path;
import java.nio.file.Paths;

public class BotConfig {

    private static final Dotenv dotenv;

    static {
        dotenv = Dotenv.configure()
                .ignoreIfMissing()
                .load();
        System.out.println(".env carregado com sucesso!");
    }

    public static String get(String key) {
        return dotenv.get(key);
    }


    public static String get(String key, String defaultValue) {
        String value = System.getenv(key);
        return value != null ? value : defaultValue;
    }


    public static Path getDataDir() {
        String dataDir = get("DATA_DIR", "./data");
        return Paths.get(dataDir);
    }


    public static Path getMusicDir() {
        String musicDir = get("MUSIC_DIR");
        if (musicDir != null) {
            return Paths.get(musicDir);
        }
        return getDataDir().resolve("music");
    }

    public static String getFfmpegPath() {
        String ffmpegPath = get("FFMPEG_PATH");
        if (ffmpegPath != null) {
            return ffmpegPath;
        }
        return getDataDir().resolve("ffmpeg").toString();
    }
}