package com.lndkrsnv.minecraftcontrolbot.telegram;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.*;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.List;

@Configuration
public class TelegramStartup {

    @Bean
    public org.springframework.boot.ApplicationRunner telegramCommandsRunner(MinecraftControlTelegramBot bot) {
        return args -> {
            var commands = List.of(
                    new BotCommand("/set_server", "Выбрать сервер"),
                    new BotCommand("/status", "Статус сервера"),
                    new BotCommand("/say", "Сказать в чат сервера"),
                    new BotCommand("/save", "Сохранить мир"),
                    new BotCommand("/restart", "Перезапустить сервер"),
                    new BotCommand("/toggledownfall", "Отключить дождь"),
                    new BotCommand("/sleep", "Поспать"),
                    new BotCommand("/custom_command", "Выполнить произвольную команду")
            );

            setCommandsEverywhere(bot, commands);
        };
    }

    private void setCommandsEverywhere(MinecraftControlTelegramBot bot, List<BotCommand> commands)
            throws TelegramApiException {

        bot.execute(SetMyCommands.builder()
                .scope(new BotCommandScopeDefault())
                .commands(commands)
                .build());

        bot.execute(SetMyCommands.builder()
                .scope(new BotCommandScopeAllPrivateChats())
                .commands(commands)
                .build());

        bot.execute(SetMyCommands.builder()
                .scope(new BotCommandScopeAllGroupChats())
                .commands(commands)
                .build());

        bot.execute(SetMyCommands.builder()
                .scope(new BotCommandScopeAllChatAdministrators())
                .commands(commands)
                .build());
    }
}
