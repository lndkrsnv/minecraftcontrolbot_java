# Minecraft Control Bot

Telegram-бот для управления серверами Minecraft через RCON и проверки их статуса.

## Возможности

- Управление несколькими серверами через единый интерфейс
- Выполнение команд на сервере через RCON
- Проверка статуса сервера (игроки, версия, моды)
- Отправка сообщений в чат сервера
- Управление погодой и временем
- Сохранение мира и перезапуск сервера

## Команды

- `/set_server` - выбрать сервер для управления
- `/status` - получить статус сервера
- `/say` - отправить сообщение в чат сервера
- `/save` - сохранить мир (требуются права)
- `/restart` - перезапустить сервер (требуются права)
- `/toggledownfall` - очистить погоду
- `/sleep` - установить время на день
- `/custom_command` - выполнить произвольную команду (только для супер-пользователя)

## Требования

- Java 21
- Gradle
- Telegram Bot Token
- Доступ к RCON серверов Minecraft
- URL для проверки статуса серверов

## Настройка

Создайте файл `application.yaml` или установите переменные окружения:

```yaml
bot:
  token: ${BOT_TOKEN}
  username: ${BOT_USERNAME}
  super-user: ${SUPER_USER}
  authorized-users: ${AUTHORIZED_USERS}

rcon:
  servers:
    MODERN:
      host: ${RCON_HOST_MODERN}
      port: ${RCON_PORT_MODERN}
      password: ${RCON_PASSWORD_MODERN}
    CLASSIC:
      host: ${RCON_HOST_CLASSIC}
      port: ${RCON_PORT_CLASSIC}
      password: ${RCON_PASSWORD_CLASSIC}

status:
  servers:
    MODERN:
      url: ${STATUS_URL_MODERN}
    CLASSIC:
      url: ${STATUS_URL_CLASSIC}
```

### Переменные окружения

- `BOT_TOKEN` - токен Telegram бота
- `BOT_USERNAME` - имя пользователя бота
- `SUPER_USER` - ID супер-пользователя (для `/custom_command`)
- `AUTHORIZED_USERS` - список ID авторизованных пользователей (через запятую)
- `RCON_HOST_*` - хост RCON сервера
- `RCON_PORT_*` - порт RCON сервера
- `RCON_PASSWORD_*` - пароль RCON
- `STATUS_URL_*` - URL для проверки статуса сервера

## Запуск


```bash
./gradlew build
# Добавьте аргумент --spring.config.location=file:application.yaml если создавали файл
java -jar build/libs/minecraftcontrolbot-0.0.1.jar
```