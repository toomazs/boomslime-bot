package com.tomaz.boomslime.music;

import com.tomaz.boomslime.config.BotConfig;

import java.io.*;
import java.nio.file.*;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

/**
 * Usa spotdl para baixar músicas completas do Spotify
 */
public class SpotifyDownloader {
    private static SpotifyDownloader INSTANCE;
    private final Path downloadDir;

    private SpotifyDownloader() {
        // Usa o diretório configurável
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

    /**
     * Baixa uma música do Spotify e retorna o caminho do arquivo
     * Usa cache se a música já foi baixada
     * @param spotifyUrl URL da música no Spotify
     * @return Caminho do arquivo MP3 baixado ou null se falhar
     */
    public String downloadTrack(String spotifyUrl) {
        try {
            // Verifica se já existe no cache
            String cachedFile = checkCache(spotifyUrl);
            if (cachedFile != null) {
                System.out.println("✓ usando cache: " + cachedFile);
                return cachedFile;
            }

            System.out.println("baixando com spotdl: " + spotifyUrl);

            // Constrói o comando spotdl
            ProcessBuilder pb;
            String ffmpegPath = BotConfig.get("FFMPEG_PATH");

            // Extrai track ID para incluir no nome do arquivo (para cache)
            String trackId = extractTrackId(spotifyUrl);
            String outputPattern = trackId != null
                ? downloadDir.toString() + "/{artists} - {title} [" + trackId + "].{output-ext}"
                : downloadDir.toString() + "/{artists} - {title}.{output-ext}";

            if (ffmpegPath != null && !ffmpegPath.isEmpty()) {
                // Se FFMPEG_PATH estiver configurado, usa ele
                pb = new ProcessBuilder(
                        "spotdl",
                        "download",
                        spotifyUrl,  // URL primeiro
                        "--ffmpeg", ffmpegPath,
                        "--format", "mp3",
                        "--bitrate", "96k",
                        "--threads", "8",
                        "--output", outputPattern,
                        "--print-errors"
                );
            } else {
                // Caso contrário, deixa spotdl usar o ffmpeg do sistema
                pb = new ProcessBuilder(
                        "spotdl",
                        "download",
                        spotifyUrl,  // URL primeiro
                        "--format", "mp3",
                        "--bitrate", "96k",
                        "--threads", "8",
                        "--output", outputPattern,
                        "--print-errors"
                );
            }

            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Lê a saída do spotdl
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            String downloadedFile = null;

            String trackName = null;
            while ((line = reader.readLine()) != null) {
                System.out.println("spotdl: " + line);

                // Captura o nome da track (ex: "French Montana - Unforgettable")
                if (line.contains("Skipping") && line.contains("(file already exists)")) {
                    // Ex: "Skipping French Montana - Unforgettable (file already exists)"
                    int start = line.indexOf("Skipping") + 9;
                    int end = line.indexOf("(file already exists)");
                    if (start > 0 && end > start) {
                        trackName = line.substring(start, end).trim();
                    }
                } else if (line.contains("Downloaded")) {
                    // Ex: "Downloaded "French Montana - Unforgettable""
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

            // CRÍTICO: Usa trackId para busca exata
            if (trackId != null) {
                // Procura arquivo com trackId no nome
                File[] filesWithId = downloadDir.toFile().listFiles((dir, name) ->
                    name.endsWith(".mp3") && name.contains("[" + trackId + "]")
                );

                if (filesWithId != null && filesWithId.length > 0) {
                    downloadedFile = filesWithId[0].getAbsolutePath();
                    System.out.println("✓ cache hit: " + downloadedFile);
                } else {
                    // Procura arquivo mais recente (recém baixado)
                    System.out.println("procurando arquivo recém baixado...");
                    File[] allFiles = downloadDir.toFile().listFiles((dir, name) -> name.endsWith(".mp3"));
                    if (allFiles != null && allFiles.length > 0) {
                        File newest = allFiles[0];
                        for (File f : allFiles) {
                            if (f.lastModified() > newest.lastModified()) {
                                newest = f;
                            }
                        }
                        downloadedFile = newest.getAbsolutePath();
                        System.out.println("✓ novo download: " + downloadedFile);
                    }
                }
            } else {
                // Fallback: procura o mais recente
                System.out.println("sem trackId, procurando arquivo mais recente...");
                File[] files = downloadDir.toFile().listFiles((dir, name) -> name.endsWith(".mp3"));
                if (files != null && files.length > 0) {
                    File newest = files[0];
                    for (File f : files) {
                        if (f.lastModified() > newest.lastModified()) {
                            newest = f;
                        }
                    }
                    downloadedFile = newest.getAbsolutePath();
                }
            }

            if (downloadedFile != null && Files.exists(Paths.get(downloadedFile))) {
                System.out.println("musica baixada: " + downloadedFile);
                return downloadedFile;
            } else {
                System.err.println("arquivo nao encontrado apos download");
                return null;
            }

        } catch (Exception e) {
            System.err.println("erro ao baixar musica: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Limpa arquivos antigos do diretório de downloads (mais de 24h)
     */
    public void cleanupOldFiles() {
        try {
            File[] files = downloadDir.toFile().listFiles((dir, name) -> name.endsWith(".mp3"));
            if (files != null) {
                long now = System.currentTimeMillis();
                int deleted = 0;

                for (File file : files) {
                    // Deleta arquivos com mais de 24 horas
                    long fileAge = now - file.lastModified();
                    if (fileAge > 86400000) { // 24h em ms
                        if (file.delete()) {
                            deleted++;
                        }
                    }
                }

                if (deleted > 0) {
                    System.out.println("auto-limpeza: removeu " + deleted + " arquivos com +24h");
                }
            }
        } catch (Exception e) {
            System.err.println("erro ao limpar arquivos: " + e.getMessage());
        }
    }

    /**
     * Inicia timer de auto-limpeza que roda a cada 6 horas
     */
    public void startAutoCleanup() {
        Timer cleanupTimer = new Timer("AutoCleanup", true);

        // Roda a cada 6 horas (21600000 ms)
        cleanupTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                System.out.println("executando auto-limpeza...");
                cleanupOldFiles();
            }
        }, 0, 21600000); // Primeira execução imediata, depois a cada 6h

        System.out.println("auto-limpeza iniciada (arquivos +24h serao removidos a cada 6h)");
    }

    public Path getDownloadDir() {
        return downloadDir;
    }

    /**
     * Verifica se a música já foi baixada (cache)
     * Usa o ID do Spotify como chave
     */
    private String checkCache(String spotifyUrl) {
        try {
            // Extrai o ID da track da URL
            String trackId = extractTrackId(spotifyUrl);
            if (trackId == null) return null;

            // Procura arquivo que contenha o ID no nome
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

    /**
     * Extrai o ID da track do Spotify da URL
     */
    private String extractTrackId(String url) {
        try {
            // https://open.spotify.com/track/5C8h9PY9oTneqJihbn10NB?si=...
            // ou https://open.spotify.com/intl-pt/track/5C8h9PY9oTneqJihbn10NB
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

    /**
     * Limpa TODOS os arquivos do diretório de downloads
     */
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
