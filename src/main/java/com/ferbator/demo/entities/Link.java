package com.ferbator.demo.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "links")
public class Link {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String originalUrl;       // Длинный URL
    private String shortUrl;          // Сокращённый вариант
    private LocalDateTime createdAt;  // Когда создана
    private LocalDateTime expiresAt;  // Когда истекает

    private int currentClicks;        // Текущее число переходов
    private int clickLimit;           // Лимит переходов

    @ManyToOne
    @JoinColumn(name = "user_uuid", nullable = false)
    private User user;               // Владелец ссылки

    // Флаг активна/неактивна
    private boolean active;
}

