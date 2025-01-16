package com.ferbator.demo.service;

import com.ferbator.demo.entities.Link;
import com.ferbator.demo.entities.User;
import com.ferbator.demo.repositories.LinkRepository;
import com.ferbator.demo.repositories.UserRepository;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ShortLinkService {

    @Setter
    @Value("${shortener.default-ttl-seconds}")
    private long defaultTtlSeconds;

    @Setter
    @Value("${shortener.default-click-limit}")
    private int defaultClickLimit;
    @Autowired
    private LinkRepository linkRepository;
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailService emailService;


    public User getOrCreateUser(UUID userUuid, String email) {
        if (userUuid == null) {
            userUuid = UUID.randomUUID();
        }
        UUID finalUserUuid = userUuid;
        return userRepository.findById(finalUserUuid).orElseGet(() -> {
            User newUser = new User();
            newUser.setUuid(finalUserUuid);
            newUser.setEmail(email);
            userRepository.save(newUser);
            return newUser;
        });
    }

    public Link createShortLink(UUID userUuid, String email, String originalUrl,
                                Integer clickLimit, Long ttlSeconds) {
        User user = getOrCreateUser(userUuid, email);

        // 1. Определяем итоговый лимит кликов: берем большее из введённого пользователем и значения из конфигурации
        int finalClickLimit = (clickLimit == null)
                ? defaultClickLimit
                : Math.max(Math.abs(clickLimit), defaultClickLimit);

        // 2. Определяем итоговое время жизни: берем меньшее из введённого пользователем и значения из конфигурации
        long finalTtl = (ttlSeconds == null)
                ? defaultTtlSeconds
                : Math.min(ttlSeconds, defaultTtlSeconds);

        Link link = new Link();
        link.setOriginalUrl(originalUrl);
        link.setShortUrl(generateShortUrl(originalUrl, user.getUuid()));
        link.setCreatedAt(LocalDateTime.now());
        link.setExpiresAt(LocalDateTime.now().plusSeconds(finalTtl));
        link.setClickLimit(finalClickLimit);
        link.setCurrentClicks(0);
        link.setActive(true);
        link.setUser(user);

        linkRepository.save(link);
        return link;
    }

    private String generateShortUrl(String originalUrl, UUID userUuid) {
        return "http://localhost:8080/api/" + HashGenerator.hashUrlForUser(originalUrl, userUuid);
    }

    public Link findByShortUrl(String shortUrl) {
        return linkRepository.findByShortUrl(shortUrl);
    }

    public void incrementClicks(Link link) {
        link.setCurrentClicks(link.getCurrentClicks() + 1);
        linkRepository.save(link);
    }

    public void deactivateLink(Link link) {
        link.setActive(false);
        linkRepository.save(link);

        String userEmail = link.getUser().getEmail();
        if (userEmail != null && !userEmail.isBlank()) {
            emailService.sendSimpleEmail(
                    userEmail,
                    "Ваша ссылка недоступна",
                    "Здравствуйте!\n\n" +
                            "Ссылка на ресурс " + link.getOriginalUrl() + " более недоступна.\n" +
                            "Лимит переходов исчерпан или срок действия истёк.\n\n" +
                            "С уважением,\nКоманда ShortLinkService."
            );
        }
    }

    public void editLimitClicksForLink(Link link, int newLimit) {
        link.setClickLimit(newLimit);
        linkRepository.save(link);
    }

    public void deleteLink(UUID userUuid, String shortUrl) {
        String tmpUrl = "http://localhost:8080/api/" + shortUrl;
        Optional<Link> optLink = Optional.ofNullable(linkRepository.findByShortUrl(tmpUrl));
        if (optLink.isPresent()) {
            Link link = optLink.get();
            if (!link.getUser().getUuid().equals(userUuid)) {
                throw new RuntimeException("Нет доступа к удалению чужой ссылки!");
            }
            linkRepository.delete(link);
        }
    }

    @Scheduled(cron = "0 0 0 * * ?")
    public void checkAndDeactivateExpiredLinks() {
        List<Link> links = linkRepository.findAll();
        LocalDateTime now = LocalDateTime.now();

        for (Link link : links) {
            if (link.getExpiresAt().isBefore(now)) {
                deactivateLink(link);
            }
        }
    }
}

