package com.tomaz.boomslime;

import com.tomaz.boomslime.commands.CommandManager;
import com.tomaz.boomslime.config.BotConfig;
import com.tomaz.boomslime.music.SpotifyDownloader;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;

public class BoomslimeBot {
    public static void main(String[] args) throws InterruptedException {
        String token = BotConfig.get("TOKEN");

        if (token == null || token.isEmpty() || token.equals("SEU_TOKEN_AQUI")) {
            System.err.println("ERRO: Token do bot não foi configurado no arquivo config.properties!");
            return;
        }

        SpotifyDownloader.getInstance().startAutoCleanup();

        JDA jda = JDABuilder.createDefault(token)
                .setActivity(Activity.customStatus("\uD83D\uDD30  Made by @toomazs"))
                .enableIntents(
                        GatewayIntent.GUILD_MESSAGES,
                        GatewayIntent.GUILD_VOICE_STATES,
                        GatewayIntent.MESSAGE_CONTENT
                )
                .addEventListeners(new CommandManager())
                .build();

        jda.awaitReady();
        System.out.println("✅ Bot online! Ctrl+C para parar.");
    }
}