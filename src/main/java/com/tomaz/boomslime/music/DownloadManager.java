package com.tomaz.boomslime.music;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Gerencia downloads de músicas com suporte a cancelamento e priorização
 */
public class DownloadManager {
    private static DownloadManager INSTANCE;

    // ThreadPool para downloads (máximo 3 downloads simultâneos)
    private final ExecutorService downloadExecutor;

    // Map de guild -> status de download
    private final Map<Long, GuildDownloadState> guildStates;

    private DownloadManager() {
        this.downloadExecutor = Executors.newFixedThreadPool(3, r -> {
            Thread t = new Thread(r, "DownloadThread");
            t.setDaemon(true);
            return t;
        });
        this.guildStates = new ConcurrentHashMap<>();
    }

    public static synchronized DownloadManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new DownloadManager();
        }
        return INSTANCE;
    }

    /**
     * Obtém o estado de download de uma guild
     */
    public GuildDownloadState getGuildState(long guildId) {
        return guildStates.computeIfAbsent(guildId, id -> new GuildDownloadState());
    }

    /**
     * Cancela TODOS os downloads de uma guild
     */
    public void cancelAllDownloads(long guildId) {
        GuildDownloadState state = guildStates.get(guildId);
        if (state != null) {
            state.cancelAll();
            System.out.println("🛑 Cancelados todos os downloads da guild " + guildId);
        }
    }

    /**
     * Submete uma tarefa de download
     */
    public Future<String> submitDownload(long guildId, Callable<String> downloadTask) {
        GuildDownloadState state = getGuildState(guildId);

        Callable<String> wrappedTask = () -> {
            // Registra a thread atual
            Thread currentThread = Thread.currentThread();
            state.registerThread(currentThread);

            try {
                // Verifica se foi cancelado antes de começar
                if (state.isCancelled() || currentThread.isInterrupted()) {
                    System.out.println("⏹ Download cancelado antes de iniciar");
                    return null;
                }

                return downloadTask.call();
            } finally {
                state.unregisterThread(currentThread);
            }
        };

        Future<String> future = downloadExecutor.submit(wrappedTask);
        state.registerFuture(future);

        return future;
    }

    /**
     * Estado de downloads de uma guild
     */
    public static class GuildDownloadState {
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private final Set<Future<?>> activeFutures = ConcurrentHashMap.newKeySet();
        private final Set<Thread> activeThreads = ConcurrentHashMap.newKeySet();

        public void cancelAll() {
            cancelled.set(true);

            // Cancela todos os futures
            for (Future<?> future : activeFutures) {
                future.cancel(true);
            }

            // Interrompe todas as threads
            for (Thread thread : activeThreads) {
                thread.interrupt();
            }

            activeFutures.clear();
            activeThreads.clear();
        }

        public void reset() {
            cancelled.set(false);
        }

        public boolean isCancelled() {
            return cancelled.get();
        }

        void registerFuture(Future<?> future) {
            activeFutures.add(future);
        }

        void registerThread(Thread thread) {
            activeThreads.add(thread);
        }

        void unregisterThread(Thread thread) {
            activeThreads.remove(thread);
        }
    }
}
