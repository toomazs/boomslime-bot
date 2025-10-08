package com.tomaz.boomslime.music;

import com.tomaz.boomslime.config.BotConfig;

import java.io.*;
import java.nio.file.*;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

public class SpotifyDownloader {
    private static SpotifyDownloader INSTANCE;
    private final Path downloadDir;

    private SpotifyDownloader() {
        this.downloadDir = BotConfig.getMusicDir();
        try {
            Files.createDirectories(downloadDir);
            System.out.println("diretorio de downloads: " + downloadDir);
        } catch (IOException e) {
            System.err.println("erro ao criar diretorio de downloads: " + e.getMessage());
        }
    }

    public static synchronized SpotifyDownloader getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new SpotifyDownloader();
        }
        return INSTANCE;
    }

    public String downloadTrack(String spotifyUrl) {
        String cachedFile = checkCache(spotifyUrl);
        if (cachedFile != null) {
            System.out.println("✓ usando cache: " + cachedFile);
            return cachedFile;
        }

        final int MAX_RETRIES = 3;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            System.out.println("baixando com spotdl (tentativa " + attempt + "/" + MAX_RETRIES + "): " + spotifyUrl);

            String result = attemptDownload(spotifyUrl);

            if (result != null) {
                return result;
            }

            if (attempt < MAX_RETRIES) {
                System.out.println("aguardando 5s antes de tentar novamente...");
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        System.err.println("download falhou apos " + MAX_RETRIES + " tentativas: " + spotifyUrl);
        return null;
    }

    private String attemptDownload(String spotifyUrl) {
        Process process = null;
        BufferedReader reader = null;

        try {
            if (Thread.currentThread().isInterrupted()) {
                System.out.println("⏹ Download interrompido antes de começar");
                return null;
            }

            ProcessBuilder pb;
            String ffmpegPath = BotConfig.get("FFMPEG_PATH");

            String trackId = extractTrackId(spotifyUrl);
            String outputPattern = trackId != null
                ? downloadDir.toString() + "/{artists} - {title} [" + trackId + "].{output-ext}"
                : downloadDir.toString() + "/{artists} - {title}.{output-ext}";

            String proxyServer = BotConfig.get("PROXY_SERVER");
            java.util.List<String> command = new java.util.ArrayList<>();
            command.add("spotdl");
            command.add("download");
            command.add(spotifyUrl);

            if (ffmpegPath != null && !ffmpegPath.isEmpty()) {
                command.add("--ffmpeg");
                command.add(ffmpegPath);
            }

            command.add("--format");
            command.add("mp3");
            command.add("--bitrate");
            command.add("96k");
            command.add("--threads");
            command.add("8");
            command.add("--output");
            command.add(outputPattern);
            command.add("--print-errors");

            pb = new ProcessBuilder(command);

            if (proxyServer != null && !proxyServer.isEmpty()) {
                pb.environment().put("HTTP_PROXY", proxyServer);
                pb.environment().put("HTTPS_PROXY", proxyServer);
            }

            pb.redirectErrorStream(true);
            process = pb.start();

            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            String downloadedFile = null;

            String trackName = null;
            while ((line = reader.readLine()) != null) {
                if (Thread.currentThread().isInterrupted()) {
                    System.out.println("⏹ Download interrompido durante execução");
                    process.destroyForcibly();
                    return null;
                }

                System.out.println("spotdl: " + line);

                if (line.contains("Skipping") && line.contains("(file already exists)")) {
                    int start = line.indexOf("Skipping") + 9;
                    int end = line.indexOf("(file already exists)");
                    if (start > 0 && end > start) {
                        trackName = line.substring(start, end).trim();
                    }
                } else if (line.contains("Downloaded")) {
                    int start = line.indexOf("\"");
                    int end = line.lastIndexOf("\"");
                    if (start >= 0 && end > start) {
                        trackName = line.substring(start + 1, end).trim();
                    }
                }
            }

            boolean finished = process.waitFor(60, TimeUnit.SECONDS);

            reader.close();

            if (!finished) {
                System.err.println("spotdl timeout");
                process.destroyForcibly();
                return null;
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                System.err.println("spotdl falhou com codigo: " + exitCode);
                return null;
            }

            if (trackId != null) {
                File[] filesWithId = downloadDir.toFile().listFiles((dir, name) ->
                    name.endsWith(".mp3") && name.contains("[" + trackId + "]")
                );

                if (filesWithId != null && filesWithId.length > 0) {
                    downloadedFile = filesWithId[0].getAbsolutePath();
                    System.out.println("✓ download bem-sucedido: " + downloadedFile);
                    return downloadedFile;
                }
            }

            System.err.println("arquivo nao encontrado com trackId, possivelmente o download falhou");
            return null;

        } catch (InterruptedException e) {
            System.out.println("⏹ Download interrompido: " + spotifyUrl);
            if (process != null) {
                process.destroyForcibly();
            }
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            System.err.println("erro ao baixar musica: " + e.getMessage());
            e.printStackTrace();
            return null;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {}
            }
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    public void cleanupOldFiles() {
        try {
            File[] files = downloadDir.toFile().listFiles((dir, name) -> name.endsWith(".mp3"));
            if (files != null) {
                long now = System.currentTimeMillis();
                int deleted = 0;

                for (File file : files) {
                    long fileAge = now - file.lastModified();
                    if (fileAge > 15552000000L) {
                        if (file.delete()) {
                            deleted++;
                        }
                    }
                }

                if (deleted > 0) {
                    System.out.println("auto-limpeza: removeu " + deleted + " arquivos com +180 dias");
                }
            }
        } catch (Exception e) {
            System.err.println("erro ao limpar arquivos: " + e.getMessage());
        }
    }

    public void startAutoCleanup() {
        Timer cleanupTimer = new Timer("AutoCleanup", true);

        cleanupTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                System.out.println("executando auto-limpeza...");
                cleanupOldFiles();
            }
        }, 0, 86400000);

        System.out.println("auto-limpeza iniciada (arquivos +180 dias serao removidos a cada 24h)");
    }

    public Path getDownloadDir() {
        return downloadDir;
    }


    private String checkCache(String spotifyUrl) {
        try {
            String trackId = extractTrackId(spotifyUrl);
            if (trackId == null) return null;

            File[] files = downloadDir.toFile().listFiles((dir, name) ->
                name.endsWith(".mp3") && name.contains(trackId)
            );

            if (files != null && files.length > 0) {
                return files[0].getAbsolutePath();
            }

            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private String extractTrackId(String url) {
        try {
            String[] parts = url.split("/track/");
            if (parts.length > 1) {
                String idPart = parts[1].split("\\?")[0];
                return idPart;
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    public void cleanupAllFiles() {
        try {
            File[] files = downloadDir.toFile().listFiles();
            if (files != null) {
                int deleted = 0;
                for (File file : files) {
                    if (file.isFile() && file.delete()) {
                        deleted++;
                    }
                }
                if (deleted > 0) {
                    System.out.println("🗑️ Limpou " + deleted + " arquivo(s) da pasta music/");
                }
            }
        } catch (Exception e) {
            System.err.println("erro ao limpar pasta music/: " + e.getMessage());
        }
    }
}
