package com.tomaz.boomslime.utils;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.Cookie;
import com.microsoft.playwright.options.LoadState;
import com.tomaz.boomslime.config.BotConfig;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Renova automaticamente os cookies do YouTube usando Playwright
 * Roda em background e atualiza cookies.txt periodicamente
 */
public class CookieRenewer {
    private static CookieRenewer INSTANCE;
    private final ScheduledExecutorService scheduler;
    private final Path cookiesPath;
    private final Path persistentContextPath;
    private boolean initialized = false;

    // Intervalo de renovação: a cada 6 horas
    private static final long RENEWAL_INTERVAL_HOURS = 6;

    private CookieRenewer() {
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "CookieRenewer");
            t.setDaemon(true); // Thread daemon não impede JVM de fechar
            return t;
        });

        // Caminho do cookies.txt (mesmo diretório do JAR)
        this.cookiesPath = Paths.get("cookies.txt");

        // Diretório para armazenar sessão persistente do navegador
        String dataDir = BotConfig.get("DATA_DIR", "./data");
        this.persistentContextPath = Paths.get(dataDir).resolve("browser-session");

        try {
            Files.createDirectories(persistentContextPath);
        } catch (IOException e) {
            System.err.println("erro ao criar diretorio de sessao do navegador: " + e.getMessage());
        }
    }

    public static synchronized CookieRenewer getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new CookieRenewer();
        }
        return INSTANCE;
    }

    /**
     * Inicia o renovador automático de cookies
     * Primeiro verifica se já existe uma sessão autenticada
     */
    public void start() {
        // Verifica se renovação automática está habilitada
        String enabled = BotConfig.get("COOKIE_RENEWER_ENABLED", "true");
        if (!enabled.equalsIgnoreCase("true")) {
            System.out.println("⏭️  renovacao automatica de cookies desabilitada");
            return;
        }

        // Verifica se já existe sessão persistente (usuário já fez login manual)
        if (!hasPersistedSession()) {
            System.out.println("⚠️  nenhuma sessao do youtube encontrada!");
            System.out.println("📝 execute 'java -cp boomslime-bot.jar com.tomaz.boomslime.utils.CookieRenewer' para fazer login inicial");
            System.out.println("💡 ou defina COOKIE_RENEWER_ENABLED=false no .env para desabilitar");
            return;
        }

        System.out.println("🔄 renovador automatico de cookies iniciado");
        System.out.println("⏰ renovacao a cada " + RENEWAL_INTERVAL_HOURS + " horas");

        // Primeira renovação imediata (ao iniciar bot)
        scheduler.submit(() -> {
            try {
                renewCookies();
            } catch (Exception e) {
                System.err.println("erro na primeira renovacao de cookies: " + e.getMessage());
            }
        });

        // Renovações periódicas
        scheduler.scheduleAtFixedRate(() -> {
            try {
                renewCookies();
            } catch (Exception e) {
                System.err.println("erro ao renovar cookies: " + e.getMessage());
            }
        }, RENEWAL_INTERVAL_HOURS, RENEWAL_INTERVAL_HOURS, TimeUnit.HOURS);

        initialized = true;
    }

    /**
     * Verifica se existe uma sessão persistente salva
     */
    private boolean hasPersistedSession() {
        // Checa se existe diretório de sessão com arquivos
        try {
            if (!Files.exists(persistentContextPath)) {
                return false;
            }
            // Verifica se há arquivos dentro (cookies, storage, etc)
            try (var stream = Files.list(persistentContextPath)) {
                return stream.findAny().isPresent();
            }
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Renova os cookies do YouTube
     * Usa contexto persistente para manter login
     */
    private void renewCookies() {
        System.out.println("🔄 renovando cookies do youtube...");

        Playwright playwright = null;
        Browser browser = null;
        BrowserContext context = null;

        try {
            // Cria instância do Playwright
            playwright = Playwright.create();

            // Usa Firefox (mais leve que Chrome)
            browser = playwright.firefox().launch(new BrowserType.LaunchOptions()
                    .setHeadless(true) // Headless = sem GUI
            );

            // Cria ou reabre contexto persistente (mantém login entre execuções)
            context = browser.newContext(new Browser.NewContextOptions()
                    .setStorageStatePath(persistentContextPath.resolve("state.json"))
                    .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:122.0) Gecko/20100101 Firefox/122.0")
            );

            // Acessa YouTube Music
            Page page = context.newPage();
            page.navigate("https://music.youtube.com/", new Page.NavigateOptions()
                    .setTimeout(30000) // 30 segundos timeout
            );

            // Aguarda página carregar completamente
            page.waitForLoadState(LoadState.NETWORKIDLE);

            // Pequena espera adicional para garantir que tudo carregou
            Thread.sleep(2000);

            // Extrai cookies
            List<Cookie> cookies = context.cookies();

            // Salva cookies no formato Netscape (compatível com yt-dlp/spotdl)
            saveCookiesNetscapeFormat(cookies);

            System.out.println("✅ cookies renovados com sucesso! (" + cookies.size() + " cookies)");

        } catch (Exception e) {
            System.err.println("❌ erro ao renovar cookies: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Cleanup
            if (context != null) {
                try {
                    context.close();
                } catch (Exception ignored) {}
            }
            if (browser != null) {
                try {
                    browser.close();
                } catch (Exception ignored) {}
            }
            if (playwright != null) {
                try {
                    playwright.close();
                } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Salva cookies no formato Netscape (usado por yt-dlp/spotdl)
     */
    private void saveCookiesNetscapeFormat(List<Cookie> cookies) throws IOException {
        StringBuilder content = new StringBuilder();
        content.append("# Netscape HTTP Cookie File\n");
        content.append("# This is a generated file! Do not edit.\n\n");

        for (Cookie cookie : cookies) {
            // Formato Netscape:
            // domain	flag	path	secure	expiration	name	value
            String domain = cookie.domain;
            String flag = domain.startsWith(".") ? "TRUE" : "FALSE";
            String path = cookie.path;
            String secure = cookie.secure ? "TRUE" : "FALSE";
            long expiration = cookie.expires.longValue(); // Converte Double para long
            String name = cookie.name;
            String value = cookie.value;

            content.append(String.format("%s\t%s\t%s\t%s\t%d\t%s\t%s\n",
                    domain, flag, path, secure, expiration, name, value));
        }

        // Salva arquivo
        Files.writeString(cookiesPath, content.toString(), StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);
    }

    /**
     * Para o renovador (cleanup)
     */
    public void stop() {
        if (initialized) {
            scheduler.shutdown();
            System.out.println("🛑 renovador de cookies parado");
        }
    }

    /**
     * Método main para realizar o login inicial
     * Uso:
     *   Automático: YOUTUBE_EMAIL=email YOUTUBE_PASSWORD=senha java -cp bot.jar com.tomaz.boomslime.utils.CookieRenewer
     *   Manual: java -cp bot.jar com.tomaz.boomslime.utils.CookieRenewer
     */
    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("🔐 SETUP INICIAL - LOGIN NO YOUTUBE");
        System.out.println("=================================================");
        System.out.println();

        // Checa se tem credenciais no .env
        String email = BotConfig.get("YOUTUBE_EMAIL");
        String password = BotConfig.get("YOUTUBE_PASSWORD");

        if (email != null && password != null) {
            System.out.println("🤖 modo automatico detectado!");
            System.out.println("📧 email: " + email);
            System.out.println("🔒 senha: " + "*".repeat(password.length()));
            System.out.println();
            performAutomaticLogin(email, password);
        } else {
            System.out.println("👤 modo manual: um navegador sera aberto");
            System.out.println("💡 dica: adicione YOUTUBE_EMAIL e YOUTUBE_PASSWORD no .env para login automatico");
            System.out.println();
            performManualLogin();
        }
    }

    /**
     * Login automático usando email/senha (headless)
     */
    private static void performAutomaticLogin(String email, String password) {
        CookieRenewer renewer = CookieRenewer.getInstance();
        Playwright playwright = null;
        Browser browser = null;
        BrowserContext context = null;

        try {
            System.out.println("🚀 iniciando login automatico...");
            playwright = Playwright.create();

            // Headless = sem interface gráfica
            browser = playwright.firefox().launch(new BrowserType.LaunchOptions()
                    .setHeadless(true)
            );

            // Cria contexto (sem state.json no primeiro login)
            Path statePath = renewer.persistentContextPath.resolve("state.json");
            Browser.NewContextOptions contextOptions = new Browser.NewContextOptions()
                    .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:122.0) Gecko/20100101 Firefox/122.0");

            // Só usa storageState se arquivo já existir
            if (Files.exists(statePath)) {
                contextOptions.setStorageStatePath(statePath);
            }

            context = browser.newContext(contextOptions);

            Page page = context.newPage();

            // Vai para página de login do Google
            System.out.println("🌐 acessando accounts.google.com...");
            page.navigate("https://accounts.google.com/");
            page.waitForLoadState(LoadState.NETWORKIDLE);

            // Preenche email
            System.out.println("✍️  preenchendo email...");
            page.fill("input[type='email']", email);
            page.click("button:has-text('Next'), button:has-text('Próxima')");
            page.waitForTimeout(2000);

            // Preenche senha
            System.out.println("🔐 preenchendo senha...");
            page.fill("input[type='password']", password);
            page.click("button:has-text('Next'), button:has-text('Próxima')");
            page.waitForTimeout(3000);

            // Vai pro YouTube Music
            System.out.println("🎵 acessando YouTube Music...");
            page.navigate("https://music.youtube.com/");
            page.waitForLoadState(LoadState.NETWORKIDLE);
            page.waitForTimeout(2000);

            System.out.println("✅ login concluido!");
            System.out.println();

            // Salva a sessão no state.json
            context.storageState(new BrowserContext.StorageStateOptions().setPath(statePath));
            System.out.println("💾 sessao salva em: " + statePath);

            // Fecha navegador
            context.close();
            browser.close();
            playwright.close();

            // Testa renovação
            System.out.println("🔄 testando renovacao de cookies...");
            renewer.renewCookies();

            System.out.println();
            System.out.println("=================================================");
            System.out.println("✅ SETUP CONCLUIDO COM SUCESSO!");
            System.out.println("=================================================");
            System.out.println();
            System.out.println("Os cookies serao renovados automaticamente a cada " + RENEWAL_INTERVAL_HOURS + " horas.");
            System.out.println("Voce pode iniciar o bot normalmente agora.");
            System.out.println();

        } catch (Exception e) {
            System.err.println("❌ erro durante login automatico: " + e.getMessage());
            e.printStackTrace();
            System.err.println();
            System.err.println("dicas:");
            System.err.println("- verifique se o email/senha estao corretos");
            System.err.println("- se a conta tem 2FA, desabilite temporariamente ou use modo manual");
            System.err.println("- tente executar sem YOUTUBE_EMAIL/PASSWORD para modo manual");
        } finally {
            if (context != null) try { context.close(); } catch (Exception ignored) {}
            if (browser != null) try { browser.close(); } catch (Exception ignored) {}
            if (playwright != null) try { playwright.close(); } catch (Exception ignored) {}
        }
    }

    /**
     * Login manual com interface gráfica
     */
    private static void performManualLogin() {
        CookieRenewer renewer = CookieRenewer.getInstance();
        Playwright playwright = null;
        Browser browser = null;
        BrowserContext context = null;

        try {
            System.out.println("Aguardando...");
            System.out.println();

            playwright = Playwright.create();

            // Lança navegador NÃO-HEADLESS para o usuário fazer login
            browser = playwright.firefox().launch(new BrowserType.LaunchOptions()
                    .setHeadless(false) // COM interface gráfica
                    .setSlowMo(50) // Pequeno delay para visualização
            );

            // Contexto persistente (vai salvar o login)
            context = browser.newContext(new Browser.NewContextOptions()
                    .setStorageStatePath(renewer.persistentContextPath.resolve("state.json"))
                    .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:122.0) Gecko/20100101 Firefox/122.0")
            );

            Page page = context.newPage();
            page.navigate("https://music.youtube.com/");

            System.out.println("✅ navegador aberto! Faca login no YouTube Music.");
            System.out.println("⏳ aguardando voce fechar o navegador...");
            System.out.println();

            // Aguarda usuário fechar o navegador
            page.waitForTimeout(0); // Aguarda indefinidamente até fechar

        } catch (Exception e) {
            if (e.getMessage().contains("Target page, context or browser has been closed")) {
                // Usuário fechou o navegador (comportamento esperado)
                System.out.println();
                System.out.println("✅ navegador fechado!");
                System.out.println();

                // Testa se login foi bem-sucedido renovando cookies
                System.out.println("🔄 testando renovacao de cookies...");
                try {
                    renewer.renewCookies();
                    System.out.println();
                    System.out.println("=================================================");
                    System.out.println("✅ SETUP CONCLUIDO COM SUCESSO!");
                    System.out.println("=================================================");
                    System.out.println();
                    System.out.println("Os cookies serao renovados automaticamente a cada " + RENEWAL_INTERVAL_HOURS + " horas.");
                    System.out.println("Voce pode iniciar o bot normalmente agora.");
                    System.out.println();
                } catch (Exception ex) {
                    System.err.println("❌ erro ao testar renovacao: " + ex.getMessage());
                    System.err.println("Tente executar o setup novamente.");
                }
            } else {
                System.err.println("❌ erro durante setup: " + e.getMessage());
                e.printStackTrace();
            }
        } finally {
            if (context != null) {
                try {
                    context.close();
                } catch (Exception ignored) {}
            }
            if (browser != null) {
                try {
                    browser.close();
                } catch (Exception ignored) {}
            }
            if (playwright != null) {
                try {
                    playwright.close();
                } catch (Exception ignored) {}
            }
        }
    }
}
