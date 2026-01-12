package com.lndkrsnv.minecraftcontrolbot.telegram;

import com.lndkrsnv.minecraftcontrolbot.config.BotProperties;
import com.lndkrsnv.minecraftcontrolbot.service.RconService;
import com.lndkrsnv.minecraftcontrolbot.service.StatusClient;
import com.lndkrsnv.minecraftcontrolbot.service.StatusFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class MinecraftControlTelegramBot extends TelegramLongPollingBot {
    private static final Logger log = LoggerFactory.getLogger("MinecraftControlBot");

    private final BotProperties botProps;
    private final RconService rcon;
    private final StatusClient statusClient;
    private final StatusFormatter statusFormatter;

    private final Set<Long> authorizedUsers;
    private final ConcurrentHashMap<Long, PendingAction> pending = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, ServerId> selectedServerByChat = new ConcurrentHashMap<>();

    public MinecraftControlTelegramBot(
            BotProperties botProps,
            RconService rcon,
            StatusClient statusClient,
            StatusFormatter statusFormatter
    ) {
        super(botProps.token());
        this.botProps = botProps;
        this.rcon = rcon;
        this.statusClient = statusClient;
        this.statusFormatter = statusFormatter;
        this.authorizedUsers = parseAuthorized(botProps.authorizedUsers());
    }

    @Override
    public String getBotUsername() {
        return botProps.username();
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasCallbackQuery()) {
            handleCallback(update.getCallbackQuery());
            return;
        }

        if (!update.hasMessage() || !update.getMessage().hasText()) return;

        var msg = update.getMessage();
        long chatId = msg.getChatId();
        long userId = msg.getFrom().getId();
        String text = msg.getText();

        String serverId = String.valueOf(selectedServerByChat.get(chatId));

        var action = pending.get(chatId);
        if (action != null) {
            log.info("chat_id={} user_id={} username={} text={} serverId={} [pending action {}]", chatId, userId, msg.getFrom().getUserName(), text, serverId, action);
            handlePending(chatId, userId, text, action, serverId);
            return;
        }

        if (!isCommandForBot(text)) {
            return;
        }

        log.info("chat_id={} user_id={} username={} text={} serverId={}", chatId, userId, msg.getFrom().getUserName(), text, serverId);

        String cmd = stripBotUsername(text);

        if (!cmd.equals("/set_server") && (serverId == null || serverId.isBlank() || "null".equalsIgnoreCase(serverId))) {
            send(chatId, "Сервер не определен");
            sendServerPicker(chatId);
            return;
        }

        switch (cmd) {
            case "/set_server" -> sendServerPicker(chatId);
            case "/status" -> handleStatus(chatId, serverId);
            case "/say" -> {
                pending.put(chatId, PendingAction.SAY_TEXT);
                send(chatId, "Введи текст для отправки в чат сервера (или /cancel)");
            }
            case "/custom_command" -> {
                if (userId == botProps.superUser()) {
                    pending.put(chatId, PendingAction.CUSTOM_COMMAND);
                    send(chatId, "Какую команду выполнить? (/cancel для отмены)");
                } else {
                    send(chatId, "Недостаточно прав.");
                }
            }
            case "/save" -> {
                if (authorizedUsers.contains(userId)) {
                    rcon.command(serverId, "save-all");
                    send(chatId, "Сохранение выполнено.");
                } else send(chatId, "Недостаточно прав.");
            }
            case "/restart" -> {
                if (authorizedUsers.contains(userId)) {
                    rcon.command(serverId,"stop");
                    send(chatId, "Сервер перезапускается. Подожди 5 минут");
                } else send(chatId, "Недостаточно прав.");
            }
            case "/toggledownfall" -> {
                rcon.command(serverId,"weather clear");
                send(chatId, "Дождь отключен");
            }
            case "/sleep" -> {
                rcon.command(serverId,"time set day");
                send(chatId, "Настало утро");
            }
            default -> {
                send(chatId, "Неизвестная команда");
            }
        }
    }

    private void handlePending(long chatId, long userId, String text, PendingAction action, String serverId) {
        String t = stripBotUsername(text).trim();

        if ("/cancel".equals(t)) {
            pending.remove(chatId);
            send(chatId, "Ок, отменено.");
            return;
        }

        if (action == PendingAction.SAY_TEXT) {
            if (t.isBlank() || t.contains("/")) {
                send(chatId, "Недопустимый ввод. Попробуй ещё раз или /cancel");
                return;
            }
            pending.remove(chatId);
            rcon.command(serverId,"say " + t);
            send(chatId, "Сообщение отправлено: " + t);
            return;
        }

        if (action == PendingAction.CUSTOM_COMMAND) {
            if (userId != botProps.superUser()) {
                pending.remove(chatId);
                send(chatId, "Недостаточно прав.");
                return;
            }
            pending.remove(chatId);
            try {
                rcon.command(serverId, t);
                send(chatId, "Выполнено успешно: " + t);
            } catch (Exception e) {
                log.warn("RCON custom command error", e);
                send(chatId, "❌ Ошибка RCON: " + e.getMessage());
            }
        }
    }

    private void handleStatus(long chatId, String serverId) {
        try {
            var data = statusClient.fetchStatus(serverId);
            send(chatId, statusFormatter.format(data));
        } catch (Exception e) {
            send(chatId, "Не удалось получить статус сервера: " + e.getMessage());
        }
    }

    private void send(long chatId, String text) {
        try {
            execute(SendMessage.builder().chatId(String.valueOf(chatId)).text(text).build());
        } catch (TelegramApiException e) {
            log.warn("Telegram send error", e);
        }
    }

    private boolean isCommandForBot(String text) {
        return text != null && text.startsWith("/");
    }

    private String stripBotUsername(String text) {
        int at = text.indexOf('@');
        if (at > 0) return text.substring(0, at);
        return text;
    }

    private static Set<Long> parseAuthorized(String s) {
        if (s == null || s.isBlank()) return Set.of();
        return Set.of(s.split(",")).stream()
                .map(String::trim)
                .filter(x -> !x.isBlank())
                .map(Long::parseLong)
                .collect(Collectors.toUnmodifiableSet());
    }

    private void sendServerPicker(long chatId) {
        var b1 = InlineKeyboardButton.builder()
                .text("ATM10")
                .callbackData("set_server:MODERN")
                .build();

        var b2 = InlineKeyboardButton.builder()
                .text("Классика")
                .callbackData("set_server:CLASSIC")
                .build();

        var markup = InlineKeyboardMarkup.builder()
                .keyboard(java.util.List.of(java.util.List.of(b1, b2)))
                .build();

        try {
            execute(SendMessage.builder()
                    .chatId(String.valueOf(chatId))
                    .text("Выбери сервер:")
                    .replyMarkup(markup)
                    .build());
        } catch (TelegramApiException e) {
            log.warn("Telegram send error", e);
        }
    }

    private void handleCallback(CallbackQuery cq) {
        long chatId = cq.getMessage().getChatId();
        long userId = cq.getFrom().getId();
        String data = cq.getData();

        try {
            execute(AnswerCallbackQuery.builder()
                    .callbackQueryId(cq.getId())
                    .build());
        } catch (TelegramApiException e) {
            log.warn("AnswerCallbackQuery error", e);
        }

        if (data != null && data.startsWith("set_server:")) {
            var id = data.substring("set_server:".length());
            ServerId server = ServerId.valueOf(id);
            selectedServerByChat.put(chatId, server);
            send(chatId, "Ок, выбран сервер: " + server);
            try {
                execute(EditMessageReplyMarkup.builder()
                        .chatId(String.valueOf(cq.getMessage().getChatId()))
                        .messageId(cq.getMessage().getMessageId())
                        .replyMarkup(null)
                        .build());
            } catch (TelegramApiException e) {
                log.warn("Failed to remove inline keyboard", e);
            }
        }
    }
}
