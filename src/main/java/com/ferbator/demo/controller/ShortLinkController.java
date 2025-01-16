package com.ferbator.demo.controller;

import com.ferbator.demo.entities.Link;
import com.ferbator.demo.service.ShortLinkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ShortLinkController {

    @Autowired
    private ShortLinkService shortLinkService;

    @Operation(
            summary = "Создание короткой ссылки",
            description = """
                    Создаёт короткую ссылку из оригинального URL. Параметры передаются как query:
                    - userUuid (необязательный) — Если не передан, генерируется автоматически
                    - email (необязательный) — Email, на который отправлять уведомления
                    - originalUrl (обязательный) — Оригинальная (длинная) ссылка
                    - clickLimit (необязательный) — Лимит переходов (по умолчанию берётся из конфигурации)
                    - ttlSeconds (необязательный) — Время жизни ссылки (берётся из конфигурации, если не передано)
                    """
    )
    @PostMapping("/create")
    public ResponseEntity<String> createShortLink(
            @Parameter(
                    description = "UUID пользователя (если не задан, генерируется автоматически)",
                    example = "4b59c943-891d-4cc1-95ee-2111c3fca035",
                    required = false
            )
            @RequestParam(required = false) String userUuid,

            @Parameter(
                    description = "Email для уведомлений (необязательный)",
                    example = "test@example.com",
                    required = false
            )
            @RequestParam(required = false) String email,

            @Parameter(
                    description = "Оригинальный (длинный) URL",
                    example = "https://google.com",
                    required = true
            )
            @RequestParam String originalUrl,

            @Parameter(
                    description = "Лимит переходов (если не задан, берётся значение по умолчанию)",
                    example = "5",
                    required = false
            )
            @RequestParam(required = false) Integer clickLimit,

            @Parameter(
                    description = "Время жизни ссылки в секундах (если не задано, берётся из конфигурации)",
                    example = "86400",
                    required = false
            )
            @RequestParam(required = false) Long ttlSeconds
    ) {
        UUID uuid = (userUuid == null || userUuid.isEmpty())
                ? null
                : UUID.fromString(userUuid);

        Link link = shortLinkService.createShortLink(uuid, email, originalUrl, clickLimit, ttlSeconds);

        return ResponseEntity.ok("Короткая ссылка: " + link.getShortUrl()
                + "\nВаш UUID: " + link.getUser().getUuid()
                + "\nУведомления будут приходить на email: " + link.getUser().getEmail());
    }

    @Operation(
            summary = "Редирект короткой ссылки",
            description = """
                    Возвращает 301 Moved Permanently с заголовком Location.
                    **Важно**: в Swagger UI при "Try it out" это может вызвать
                    сообщение "Failed to fetch", из-за AJAX-редиректа на внешний домен.
                    Для проверки откройте короткий URL напрямую в браузере.
                    """
    )

    @GetMapping("/{shortUrl}")
    public ResponseEntity<String> redirectToOriginal(
            @Parameter(
                    description = "Короткая часть ссылки (генерируется при создании)",
                    example = "08c895b9",
                    in = ParameterIn.PATH,
                    required = true
            )
            @PathVariable String shortUrl
    ) {
        Link link = shortLinkService.findByShortUrl("http://localhost:8080/api/" + shortUrl);

        // 1. Проверяем, что ссылка существует и активна
        if (link == null || !link.isActive()) {
            return ResponseEntity.badRequest().body("Ссылка недоступна!");
        }

        // 2. Проверяем лимит кликов
        if (link.getCurrentClicks() >= link.getClickLimit()) {
            shortLinkService.deactivateLink(link);
            return ResponseEntity.badRequest().body("Лимит переходов исчерпан!");
        }

        // 3. Проверяем срок годности
        if (link.getExpiresAt().isBefore(java.time.LocalDateTime.now())) {
            shortLinkService.deactivateLink(link);
            return ResponseEntity.badRequest().body("Время жизни ссылки истекло!");
        }

        // 4. Увеличиваем счётчик
        shortLinkService.incrementClicks(link);

        // 5. Возвращаем 301 с Location
        return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY)
                .location(URI.create(link.getOriginalUrl()))
                .build();
    }

    @Operation(
            summary = "Удаление ссылки (только владелец)",
            description = """
                    Удаляет короткую ссылку, если указан UUID владельца. 
                    Если UUID не совпадает с владельцем, удаление не произойдёт.
                    """
    )

    @DeleteMapping("/delete/{shortUrl}")
    public ResponseEntity<String> deleteLink(
            @Parameter(
                    description = "UUID владельца (обязательно). Пример: f8b6127f-068e-46a9-bcf5-4c752cef242c",
                    example = "f8b6127f-068e-46a9-bcf5-4c752cef242c",
                    required = true
            )
            @RequestParam String userUuid,

            @Parameter(
                    description = "Короткая часть ссылки",
                    example = "08c895b9",
                    in = ParameterIn.PATH,
                    required = true
            )
            @PathVariable String shortUrl
    ) {
        UUID uuid = UUID.fromString(userUuid);
        shortLinkService.deleteLink(uuid, shortUrl);
        return ResponseEntity.ok("Ссылка удалена!");
    }

    @Operation(
            summary = "Редактирование лимита переходов (только владелец)",
            description = """
                    Изменяет лимит кликов (clickLimit) у ссылки. 
                    Если владелец не совпадает, возвращает 403.
                    """
    )
    @PutMapping("/editLimit/{shortUrl}")
    public ResponseEntity<String> editLimitClicksForLink(
            @Parameter(
                    description = "UUID владельца",
                    example = "f8b6127f-068e-46a9-bcf5-4c752cef242c",
                    required = true
            )
            @RequestParam String userUuid,

            @Parameter(
                    description = "Короткая часть ссылки",
                    example = "08c895b9",
                    in = ParameterIn.PATH,
                    required = true
            )
            @PathVariable String shortUrl,

            @Parameter(
                    description = "Новый лимит кликов",
                    example = "15",
                    required = true
            )
            @RequestParam int newLimit
    ) {
        UUID uuid = UUID.fromString(userUuid);

        Optional<Link> optLink = Optional.ofNullable(shortLinkService.findByShortUrl("http://localhost:8080/api/" + shortUrl));
        if (optLink.isEmpty()) {
            return ResponseEntity.badRequest().body("Ссылка не найдена!");
        }
        Link link = optLink.get();

        if (!link.getUser().getUuid().equals(uuid)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Нет доступа для изменения лимита!");
        }

        shortLinkService.editLimitClicksForLink(link, newLimit);

        return ResponseEntity.ok("Лимит обновлён на " + newLimit + " для ссылки = " + link.getShortUrl());
    }
}
