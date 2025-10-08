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
     * Tenta até 3 vezes em caso de erro
     * @param spotifyUrl URL da música no Spotify
     * @return Caminho do arquivo MP3 baixado ou null se falhar
     */
    public String downloadTrack(String spotifyUrl) {
        // Verifica se já existe no cache
        String cachedFile = checkCache(spotifyUrl);
        if (cachedFile != null) {
            System.out.println("✓ usando cache: " + cachedFile);
            return cachedFile;
        }

        // Tenta até 3 vezes
        final int MAX_RETRIES = 3;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            System.out.println("baixando com spotdl (tentativa " + attempt + "/" + MAX_RETRIES + "): " + spotifyUrl);

            String result = attemptDownload(spotifyUrl);

            if (result != null) {
                return result;
            }

            // Se falhou e não é última tentativa, espera 5 segundos
            if (attempt < MAX_RETRIES) {
                System.out.println("aguardando 5s antes de tentar novamente...");
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        // Falhou após 3 tentativas
        System.err.println("download falhou apos " + MAX_RETRIES + " tentativas: " + spotifyUrl);
        return null;
    }

    /**
     * Tenta fazer o download uma vez
     */
    private String attemptDownload(String spotifyUrl) {
        try {
            // Constrói o comando spotdl
            ProcessBuilder pb;
            String ffmpegPath = BotConfig.get("FFMPEG_PATH");

            // Extrai track ID para incluir no nome do arquivo (para cache)
            String trackId = extractTrackId(spotifyUrl);
            String outputPattern = trackId != null
                ? downloadDir.toString() + "/{artists} - {title} [" + trackId + "].{output-ext}"
                : downloadDir.toString() + "/{artists} - {title}.{output-ext}";

            // Configura proxy se disponível
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

            // Configura variáveis de ambiente para proxy
            if (proxyServer != null && !proxyServer.isEmpty()) {
                pb.environment().put("HTTP_PROXY", proxyServer);
                pb.environment().put("HTTPS_PROXY", proxyServer);
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
                    System.out.println("✓ download bem-sucedido: " + downloadedFile);
                    return downloadedFile;
                }
            }

            // Se não encontrou por trackId, procura o mais recente
            System.err.println("arquivo nao encontrado com trackId, possivelmente o download falhou");
            return null;

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
