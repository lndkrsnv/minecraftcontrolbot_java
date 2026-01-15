package com.lndkrsnv.minecraftcontrolbot.telegram;

import com.lndkrsnv.minecraftcontrolbot.config.BotProperties;
import com.lndkrsnv.minecraftcontrolbot.service.RconService;
import com.lndkrsnv.minecraftcontrolbot.service.StatusClient;
import com.lndkrsnv.minecraftcontrolbot.service.StatusFormatter;
import jakarta.annotation.PreDestroy;
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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
public class MinecraftControlTelegramBot extends TelegramLongPollingBot {
    private static final Logger log = LoggerFactory.getLogger("MinecraftControlBot");

    private final BotProperties botProps;
    private final RconService rcon;
    private final StatusClient statusClient;
    private final StatusFormatter statusFormatter;

    private final Set<Long> authorizedUsers;
    private final ConcurrentHashMap<Long, PendingActionInfo> pending = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, ServerId> selectedServerByChat = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, ServerPickerInfo> serverPickerOwnersByMessage = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> sleepLastUsed = new ConcurrentHashMap<>(); // key: "chatId:serverId"
    private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor();

    private record PendingActionInfo(PendingAction action, long initiatorUserId, long createdAt) {}
    private record ServerPickerInfo(long ownerUserId, long chatId, long createdAt) {}

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
        
        cleanupExecutor.scheduleAtFixedRate(this::cleanupExpiredActions, 10, 10, TimeUnit.SECONDS);
    }
    
    private void cleanupExpiredActions() {
        long now = System.currentTimeMillis();
        long timeoutMs = TimeUnit.SECONDS.toMillis(15);
        
        pending.entrySet().removeIf(entry -> {
            if (now - entry.getValue().createdAt() > timeoutMs) {
                long chatId = entry.getKey();
                PendingActionInfo actionInfo = entry.getValue();
                log.info("Cleaning up expired pending action for chat_id={}, action={}", chatId, actionInfo.action());
                
                String message = switch (actionInfo.action()) {
                    case SAY_TEXT -> "Время вышло. Команда /say отменена.";
                    case CUSTOM_COMMAND -> "Время вышло. Команда /custom_command отменена.";
                };
                send(chatId, message);
                return true;
            }
            return false;
        });
        
        serverPickerOwnersByMessage.entrySet().removeIf(entry -> {
            if (now - entry.getValue().createdAt() > timeoutMs) {
                Integer messageId = entry.getKey();
                ServerPickerInfo pickerInfo = entry.getValue();
                long chatId = pickerInfo.chatId();
                log.info("Cleaning up expired server picker for message_id={}, chat_id={}", messageId, chatId);
                
                try {
                    execute(EditMessageReplyMarkup.builder()
                            .chatId(String.valueOf(chatId))
                            .messageId(messageId)
                            .replyMarkup(null)
                            .build());
                } catch (TelegramApiException e) {
                    log.warn("Failed to remove inline keyboard on timeout", e);
                }
                
                send(chatId, "Время вышло. Команда /set_server отменена.");
                return true;
            }
            return false;
        });
        
        // Очистка старых записей sleep (старше 24 часов)
        long sleepCleanupMs = TimeUnit.HOURS.toMillis(24);
        sleepLastUsed.entrySet().removeIf(entry -> {
            if (now - entry.getValue() > sleepCleanupMs) {
                log.info("Cleaning up old sleep record for key={}", entry.getKey());
                return true;
            }
            return false;
        });
    }
    
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down cleanup executor");
        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
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

        var actionInfo = pending.get(chatId);
        if (actionInfo != null) {
            log.info("chat_id={} user_id={} username={} text={} serverId={} [pending action {}]", chatId, userId, msg.getFrom().getUserName(), text, serverId, actionInfo.action());
            handlePending(chatId, userId, text, actionInfo, serverId);
            return;
        }

        if (!isCommandForBot(text)) {
            return;
        }

        log.info("chat_id={} user_id={} username={} text={} serverId={}", chatId, userId, msg.getFrom().getUserName(), text, serverId);

        String cmd = stripBotUsername(text);

        if (!cmd.equals("/set_server") && (serverId == null || serverId.isBlank() || "null".equalsIgnoreCase(serverId))) {
            send(chatId, "Сервер не определен");
            sendServerPicker(chatId, userId);
            return;
        }

        switch (cmd) {
            case "/set_server" -> sendServerPicker(chatId, userId);
            case "/status" -> handleStatus(chatId, serverId);
            case "/say" -> {
                pending.put(chatId, new PendingActionInfo(PendingAction.SAY_TEXT, userId, System.currentTimeMillis()));
                send(chatId, "Введи текст для отправки в чат сервера (или /cancel)");
            }
            case "/custom_command" -> {
                if (userId == botProps.superUser()) {
                    pending.put(chatId, new PendingActionInfo(PendingAction.CUSTOM_COMMAND, userId, System.currentTimeMillis()));
                    send(chatId, "Какую команду выполнить? (/cancel для отмены)");
                } else {
                    send(chatId, "Недостаточно прав.");
                }
            }
            case "/save" -> {
                if (authorizedUsers.contains(userId)) {
                    try {
                        rcon.command(serverId, "save-all");
                        send(chatId, "Сохранение выполнено.");
                    } catch (Exception e) {
                        log.warn("RCON save command error", e);
                        send(chatId, "❌ " + getErrorMessage(e));
                    }
                } else send(chatId, "Недостаточно прав.");
            }
            case "/restart" -> {
                if (authorizedUsers.contains(userId)) {
                    try {
                        rcon.command(serverId,"stop");
                        send(chatId, "Сервер перезапускается. Подожди 5 минут");
                    } catch (Exception e) {
                        log.warn("RCON restart command error", e);
                        send(chatId, "❌ " + getErrorMessage(e));
                    }
                } else send(chatId, "Недостаточно прав.");
            }
            case "/toggledownfall" -> {
                try {
                    rcon.command(serverId,"weather clear");
                    send(chatId, "Дождь отключен");
                } catch (Exception e) {
                    log.warn("RCON toggledownfall command error", e);
                    send(chatId, "❌ " + getErrorMessage(e));
                }
            }
            case "/sleep" -> handleSleep(chatId, serverId);
            default -> {
                send(chatId, "Неизвестная команда");
            }
        }
    }

    private void handlePending(long chatId, long userId, String text, PendingActionInfo actionInfo, String serverId) {
        if (actionInfo.initiatorUserId() != userId) {
            return;
        }

        String t = stripBotUsername(text).trim();
        PendingAction action = actionInfo.action();

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
            try {
                rcon.command(serverId,"say " + t);
                send(chatId, "Сообщение отправлено: " + t);
            } catch (Exception e) {
                log.warn("RCON say command error", e);
                send(chatId, "❌ " + getErrorMessage(e));
            }
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
                send(chatId, "❌ " + getErrorMessage(e));
            }
        }
    }

    private void handleStatus(long chatId, String serverId) {
        try {
            var data = statusClient.fetchStatus(serverId);
            send(chatId, statusFormatter.format(data));
        } catch (Exception e) {
            log.warn("Status fetch error", e);
            send(chatId, "❌ " + getErrorMessage(e));
        }
    }

    private void handleSleep(long chatId, String serverId) {
        String key = chatId + ":" + serverId;
        long now = System.currentTimeMillis();
        long cooldownMs = TimeUnit.MINUTES.toMillis(20);
        
        Long lastUsed = sleepLastUsed.get(key);
        if (lastUsed != null) {
            long timeSinceLastUse = now - lastUsed;
            if (timeSinceLastUse < cooldownMs) {
                long remainingSeconds = TimeUnit.MILLISECONDS.toSeconds(cooldownMs - timeSinceLastUse);
                long remainingMinutes = remainingSeconds / 60;
                long remainingSecondsMod = remainingSeconds % 60;
                
                String message;
                if (remainingMinutes > 0) {
                    message = String.format("Команда /sleep использовалась недавно. Подожди ещё %d мин. %d сек.", remainingMinutes, remainingSecondsMod);
                } else {
                    message = String.format("Команда /sleep использовалась недавно. Подожди ещё %d сек.", remainingSecondsMod);
                }
                send(chatId, message);
                return;
            }
        }
        
        try {
            rcon.command(serverId, "time set day");
            sleepLastUsed.put(key, now);
            send(chatId, "Настало утро");
        } catch (Exception e) {
            log.warn("RCON sleep command error", e);
            send(chatId, "❌ " + getErrorMessage(e));
        }
    }
    
    private String getErrorMessage(Exception e) {
        String message = e.getMessage();
        if (message != null && message.contains("недоступен")) {
            return message;
        }
        if (message != null && message.contains("превышено время ожидания")) {
            return message;
        }
        return "Бэкенд недоступен: " + (message != null ? message : e.getClass().getSimpleName());
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

    private void sendServerPicker(long chatId, long userId) {
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
            var sent = execute(SendMessage.builder()
                    .chatId(String.valueOf(chatId))
                    .text("Выбери сервер:")
                    .replyMarkup(markup)
                    .build());
            if (sent != null && sent.getMessageId() != null) {
                serverPickerOwnersByMessage.put(sent.getMessageId(), new ServerPickerInfo(userId, chatId, System.currentTimeMillis()));
            }
        } catch (TelegramApiException e) {
            log.warn("Telegram send error", e);
        }
    }

    private void handleCallback(CallbackQuery cq) {
        long chatId = cq.getMessage().getChatId();
        long userId = cq.getFrom().getId();
        String data = cq.getData();

        if (data != null && data.startsWith("set_server:")) {
            Integer messageId = cq.getMessage().getMessageId();
            ServerPickerInfo pickerInfo = serverPickerOwnersByMessage.get(messageId);

            if (pickerInfo != null && pickerInfo.ownerUserId() != userId) {
                try {
                    execute(AnswerCallbackQuery.builder()
                            .callbackQueryId(cq.getId())
                            .text("Выбрать может только инициатор")
                            .showAlert(false)
                            .build());
                } catch (TelegramApiException e) {
                    log.warn("AnswerCallbackQuery error (unauthorized)", e);
                }
                return;
            }

            var id = data.substring("set_server:".length());
            ServerId server = ServerId.valueOf(id);
            selectedServerByChat.put(chatId, server);
            send(chatId, "Ок, выбран сервер: " + server);
            try {
                serverPickerOwnersByMessage.remove(messageId);
                execute(AnswerCallbackQuery.builder()
                        .callbackQueryId(cq.getId())
                        .build());
                execute(EditMessageReplyMarkup.builder()
                        .chatId(String.valueOf(cq.getMessage().getChatId()))
                        .messageId(messageId)
                        .replyMarkup(null)
                        .build());
            } catch (TelegramApiException e) {
                log.warn("Failed to remove inline keyboard", e);
            }
        }
    }
}
