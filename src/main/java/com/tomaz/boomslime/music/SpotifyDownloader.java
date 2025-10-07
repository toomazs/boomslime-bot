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

        // Tenta até 3 vezes com backoff exponencial
        final int MAX_RETRIES = 3;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            System.out.println("baixando com spotdl (tentativa " + attempt + "/" + MAX_RETRIES + "): " + spotifyUrl);

            String result = attemptDownload(spotifyUrl);

            if (result != null) {
                return result;
            }

            // Backoff exponencial: 10s, 20s, 30s (evita rate limit)
            if (attempt < MAX_RETRIES) {
                int waitTime = attempt * 10;
                System.out.println("aguardando " + waitTime + "s antes de tentar novamente...");
                try {
                    Thread.sleep(waitTime * 1000L);
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
            // Delay aleatório de 1-3s para evitar detecção de bot
            int randomDelay = 1000 + (int)(Math.random() * 2000);
            Thread.sleep(randomDelay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        try {
            // Constrói o comando spotdl
            ProcessBuilder pb;
            String ffmpegPath = BotConfig.get("FFMPEG_PATH");

            // Extrai track ID para incluir no nome do arquivo (para cache)
            String trackId = extractTrackId(spotifyUrl);
            // Usa apenas trackId no nome para evitar problemas com caracteres especiais em nomes
            String outputPattern = trackId != null
                ? downloadDir.toString() + "/" + trackId + ".{output-ext}"
                : downloadDir.toString() + "/{artists} - {title}.{output-ext}";

            if (ffmpegPath != null && !ffmpegPath.isEmpty()) {
                // Se FFMPEG_PATH estiver configurado, usa ele
                pb = new ProcessBuilder(
                        "spotdl",
                        "download",
                        spotifyUrl,
                        "--ffmpeg", ffmpegPath,
                        "--format", "mp3",
                        "--bitrate", "96k",
                        "--threads", "4",
                        "--output", outputPattern,
                        "--print-errors",
                        "--restrict", "none",  // Não restringe caracteres nos nomes
                        "--no-cache",          // Não usa cache (opção correta do spotdl)
                        "--ytm-data",          // Força usar YouTube Music API
                        "--yt-dlp-args", "--extractor-args youtube:player_client=android,web --no-check-certificate"
                );
            } else {
                // Caso contrário, deixa spotdl usar o ffmpeg do sistema
                pb = new ProcessBuilder(
                        "spotdl",
                        "download",
                        spotifyUrl,
                        "--format", "mp3",
                        "--bitrate", "96k",
                        "--threads", "4",
                        "--output", outputPattern,
                        "--print-errors",
                        "--restrict", "none",  // Não restringe caracteres nos nomes
                        "--no-cache",          // Não usa cache (opção correta do spotdl)
                        "--ytm-data",          // Força usar YouTube Music API
                        "--yt-dlp-args", "--extractor-args youtube:player_client=android,web --no-check-certificate"
                );
            }

            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Lê a saída do spotdl
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            String downloadedFile = null;
            StringBuilder fullOutput = new StringBuilder();

            while ((line = reader.readLine()) != null) {
                fullOutput.append(line).append("\n");
                System.out.println("spotdl: " + line);
            }

            // Aumentado para 3 minutos (produção pode ser mais lenta)
            boolean finished = process.waitFor(180, TimeUnit.SECONDS);

            reader.close();

            if (!finished) {
                System.err.println("spotdl timeout (>180s)");
                process.destroyForcibly();
                return null;
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                System.err.println("========== SPOTDL ERROR ==========");
                System.err.println("Exit code: " + exitCode);
                System.err.println("URL: " + spotifyUrl);
                System.err.println("Full output:\n" + fullOutput.toString());
                System.err.println("==================================");
                return null;
            }

            // CRÍTICO: Usa trackId para busca exata
            if (trackId != null) {
                // Procura arquivo com trackId no nome (agora simplificado: trackId.mp3)
                File[] filesWithId = downloadDir.toFile().listFiles((dir, name) ->
                    name.endsWith(".mp3") && (name.contains(trackId) || name.startsWith(trackId))
                );

                if (filesWithId != null && filesWithId.length > 0) {
                    downloadedFile = filesWithId[0].getAbsolutePath();
                    System.out.println("✓ download bem-sucedido: " + downloadedFile);
                    return downloadedFile;
                }
            }

            // Se não encontrou por trackId, procura o arquivo mais recente
            File[] allMp3Files = downloadDir.toFile().listFiles((dir, name) -> name.endsWith(".mp3"));
            if (allMp3Files != null && allMp3Files.length > 0) {
                // Ordena por data de modificação (mais recente primeiro)
                java.util.Arrays.sort(allMp3Files, (f1, f2) ->
                    Long.compare(f2.lastModified(), f1.lastModified())
                );
                downloadedFile = allMp3Files[0].getAbsolutePath();
                System.out.println("⚠ usando arquivo mais recente: " + downloadedFile);
                return downloadedFile;
            }

            System.err.println("arquivo nao encontrado, download provavelmente falhou");
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
