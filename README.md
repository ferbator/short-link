# ShortLink Service

**ShortLink Service** — это REST-приложение на Spring Boot + PostgreSQL для сокращения ссылок с учётом:
- Лимитов переходов (clickLimit)
- Времени жизни (ttlSeconds)
- Привязки к пользователю (по UUID)
- Уведомлений по email (опционально)
- Редактирования и удаления ссылки только её владельцем

## Технологии
- **Java 17** (или совместимая версия)
- **Spring Boot** 3+
- **PostgreSQL**
- **Gradle** (сборщик)
- **Swagger** (springdoc-openapi) для автогенерации документации

## Как запустить проект

1. **Подготовьте БД**
    - Создайте базу данных `shortlink_db` в PostgreSQL.
    - Создайте пользователя `sl_user` с паролем `121212` (или отредактируйте их в `application.properties`).
    - Убедитесь, что доступ к БД возможен по `jdbc:postgresql://localhost:5432/shortlink_db`.

2. **Склонируйте репозиторий**
   ```bash
   git clone https://github.com/ferbator/short-link.git
   cd short-link
   ```
3. **Соберите и запустите проект**
   ```bash
   gradle clean build
   gradle bootRun
   ```
4. **Откройте Swagger UI**
   http://localhost:8080/swagger-ui/index.html
