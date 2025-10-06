package com.tomaz.boomslime.music;

import com.tomaz.boomslime.config.BotConfig;

import java.io.*;
import java.nio.file.*;
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
     * @param spotifyUrl URL da música no Spotify
     * @return Caminho do arquivo MP3 baixado ou null se falhar
     */
    public String downloadTrack(String spotifyUrl) {
        try {
            System.out.println("baixando com spotdl: " + spotifyUrl);

            // Usa caminho configurável do ffmpeg
            String ffmpegPath = BotConfig.getFfmpegPath();

            // Executa spotdl com caminho de output correto
            ProcessBuilder pb = new ProcessBuilder(
                    "spotdl",
                    "--ffmpeg", ffmpegPath,
                    "--output", downloadDir.toString() + "/{artists} - {title}.{output-ext}",
                    spotifyUrl
            );

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

            // Usa o trackName para encontrar o arquivo
            if (trackName != null) {
                // Procura arquivo com base no nome da track
                String expectedFileName = trackName + ".mp3";
                File expectedFile = downloadDir.resolve(expectedFileName).toFile();

                if (expectedFile.exists()) {
                    downloadedFile = expectedFile.getAbsolutePath();
                    System.out.println("arquivo encontrado: " + downloadedFile);
                } else {
                    // Tenta procurar arquivo similar
                    System.out.println("procurando arquivo similar a: " + expectedFileName);
                    final String artistName = trackName.contains(" - ") ? trackName.split(" - ")[0] : trackName;
                    File[] files = downloadDir.toFile().listFiles((dir, name) ->
                        name.endsWith(".mp3") && name.contains(artistName)
                    );
                    if (files != null && files.length > 0) {
                        downloadedFile = files[0].getAbsolutePath();
                        System.out.println("arquivo similar encontrado: " + downloadedFile);
                    }
                }
            }

            // Se ainda não encontrou, procura o mais recente
            if (downloadedFile == null) {
                System.out.println("procurando arquivo mais recente no diretorio...");
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
     * Limpa arquivos antigos do diretório de downloads
     */
    public void cleanupOldFiles() {
        try {
            File[] files = downloadDir.toFile().listFiles((dir, name) -> name.endsWith(".mp3"));
            if (files != null) {
                long now = System.currentTimeMillis();
                int deleted = 0;

                for (File file : files) {
                    // Deleta arquivos com mais de 1 hora
                    if (now - file.lastModified() > 3600000) {
                        if (file.delete()) {
                            deleted++;
                        }
                    }
                }

                if (deleted > 0) {
                    System.out.println("limpou " + deleted + " arquivos antigos");
                }
            }
        } catch (Exception e) {
            System.err.println("erro ao limpar arquivos: " + e.getMessage());
        }
    }

    public Path getDownloadDir() {
        return downloadDir;
    }
}
