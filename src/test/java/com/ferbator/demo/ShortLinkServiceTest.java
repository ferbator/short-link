package com.ferbator.demo;

import com.ferbator.demo.entities.Link;
import com.ferbator.demo.entities.User;
import com.ferbator.demo.repositories.LinkRepository;
import com.ferbator.demo.repositories.UserRepository;
import com.ferbator.demo.service.EmailService;
import com.ferbator.demo.service.ShortLinkService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Юнит-тесты для ShortLinkService
 */
@ExtendWith(MockitoExtension.class)
class ShortLinkServiceTest {

    @Mock
    private LinkRepository linkRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private ShortLinkService shortLinkService;

    @BeforeEach
    void initMocks() {
        MockitoAnnotations.openMocks(this);
        shortLinkService.setDefaultClickLimit(10);
        shortLinkService.setDefaultTtlSeconds(86400);
    }

    @Test
    void testGetOrCreateUser_NullUuid() {
        // Если UUID == null, метод должен создать нового пользователя,
        // а userRepository.save(...) должен быть вызван
        when(userRepository.findById(any(UUID.class))).thenReturn(Optional.empty());

        User createdUser = shortLinkService.getOrCreateUser(null, null);

        assertNotNull(createdUser, "User не должен быть null");
        assertNotNull(createdUser.getUuid(), "UUID должен быть установлен");
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void testGetOrCreateUser_ExistingUuid() {
        // Если UUID существует, метод не должен заново создавать пользователя
        UUID existingUuid = UUID.randomUUID();
        User existingUser = new User();
        existingUser.setUuid(existingUuid);
        existingUser.setEmail("test@example.com");

        when(userRepository.findById(existingUuid)).thenReturn(Optional.of(existingUser));

        User foundUser = shortLinkService.getOrCreateUser(existingUuid, existingUser.getEmail());

        assertNotNull(foundUser, "Должны найти существующего пользователя");
        assertEquals(existingUuid, foundUser.getUuid());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void testCreateShortLink_NullLimitAndTTL() {
        // Если пользователь не передал ни clickLimit, ни ttlSeconds,
        // то берутся значения по умолчанию:
        //   clickLimit = 10
        //   expiresAt = now + 86400 секунд (сутки)
        UUID userUuid = UUID.randomUUID();
        User user = new User();
        user.setUuid(userUuid);
        user.setEmail("test@example.com");

        when(userRepository.findById(userUuid)).thenReturn(Optional.of(user));
        when(linkRepository.save(any(Link.class))).thenAnswer(invocation -> {
            Link link = invocation.getArgument(0);
            link.setId(1L); // имитируем, что база сгенерировала ID
            return link;
        });

        Link link = shortLinkService.createShortLink(
                userUuid,
                user.getEmail(),
                "https://google.com",
                null,   // clickLimit
                null    // ttlSeconds
        );

        assertNotNull(link, "Link не должен быть null");
        assertEquals("https://google.com", link.getOriginalUrl());
        assertEquals(10, link.getClickLimit(), "Если limit не задан, должен быть default=10");

        LocalDateTime now = LocalDateTime.now();
        assertTrue(link.getExpiresAt().isAfter(now), "expiresAt должен быть в будущем");
        // Дополнительно можем проверить, не позднее чем через сутки + 1-2 сек запаса
        assertTrue(link.getExpiresAt().isBefore(now.plusSeconds(86400 + 5)),
                "expiresAt не должен выходить за 86400 c большим запасом");

        verify(linkRepository, times(1)).save(any(Link.class));
    }

    @Test
    void testCreateShortLink_WithCustomLimitAndTTL() {
        // Установим вручную дефолты, чтобы совпадали с логикой теста:
        shortLinkService.setDefaultClickLimit(10);     // дефолтный лимит
        shortLinkService.setDefaultTtlSeconds(86400);  // дефолтная "сутки"

        // Пользователь хочет clickLimit=5 (меньше дефолта) => итог будет max(5,10)=10
        // Пользователь хочет ttl=100000 (больше дефолта) => итог будет min(100000,86400)=86400
        UUID userUuid = UUID.randomUUID();
        User user = new User();
        user.setUuid(userUuid);
        user.setEmail("abs@example.com");

        when(userRepository.findById(userUuid)).thenReturn(Optional.of(user));
        when(linkRepository.save(any(Link.class))).thenAnswer(invocation -> invocation.getArgument(0));

        int customClickLimit = 5;
        long customTtlSeconds = 100000L;

        Link link = shortLinkService.createShortLink(
                userUuid,
                user.getEmail(),
                "https://example.com",
                customClickLimit,
                customTtlSeconds
        );

        assertEquals(10, link.getClickLimit(),
                "Должно взять большее из (5, 10) -> 10");

        // expiresAt = createdAt + 86400
        LocalDateTime expectedExpiry = link.getCreatedAt().plusSeconds(86400);
        assertEquals(expectedExpiry, link.getExpiresAt());

        verify(linkRepository, times(1)).save(any(Link.class));
    }

    @Test
    void testIncrementClicks() {
        // Проверяем, что при incrementClicks счётчик растёт, и ссылка сохраняется
        Link link = new Link();
        link.setId(1L);
        link.setCurrentClicks(0);

        when(linkRepository.save(any(Link.class))).thenAnswer(invocation -> invocation.getArgument(0));

        shortLinkService.incrementClicks(link);

        assertEquals(1, link.getCurrentClicks(),
                "После инкремента кол-во кликов должно стать 1");
        verify(linkRepository, times(1)).save(link);
    }

    @Test
    void testDeactivateLink() {
        // Проверяем, что деактивирует ссылку и сохраняет её
        Link link = new Link();
        link.setActive(true);
        User user = new User();
        UUID userUuid = UUID.randomUUID();
        user.setUuid(userUuid);
        user.setEmail("abs@example.com");
        link.setUser(user);

        when(linkRepository.save(any(Link.class))).thenAnswer(invocation -> invocation.getArgument(0));

        shortLinkService.deactivateLink(link);

        assertFalse(link.isActive(), "Ссылка должна стать неактивной");
        verify(linkRepository, times(1)).save(link);
    }

    @Test
    void testDeleteLink_Success() {
        UUID userUuid = UUID.randomUUID();
        Link link = new Link();
        link.setId(42L);
        link.setShortUrl("shortUrl");
        link.setUser(new User());
        link.getUser().setUuid(userUuid);

        when(linkRepository.findByShortUrl("http://localhost:8080/api/shortUrl")).thenReturn(link);

        shortLinkService.deleteLink(userUuid, link.getShortUrl());

        verify(linkRepository, times(1)).delete(link);
    }


    @Test
    void testDeleteLink_ForeignUser() {
        // Если ссылка не принадлежит пользователю, должен бросаться RuntimeException
        UUID userUuid = UUID.randomUUID();
        UUID otherUuid = UUID.randomUUID();

        Link link = new Link();
        link.setId(42L);
        link.setShortUrl("shortUrl");
        link.setUser(new User());
        link.getUser().setUuid(otherUuid);

        when(shortLinkService.findByShortUrl("http://localhost:8080/api/shortUrl")).thenReturn(link);

        assertThrows(RuntimeException.class,
                () -> shortLinkService.deleteLink(userUuid, "shortUrl"),
                "Ожидаем RuntimeException при попытке удалить чужую ссылку");

        verify(linkRepository, never()).delete(link);
    }

    @Test
    void testFindByShortUrl() {
        // Проверяем, что findByShortUrl пробрасывает вызов в linkRepository
        Link link = new Link();
        link.setShortUrl("http://localhost:8080/abcd1234");

        when(linkRepository.findByShortUrl(link.getShortUrl())).thenReturn(link);

        Link found = shortLinkService.findByShortUrl("http://localhost:8080/abcd1234");

        assertNotNull(found, "Ссылка должна быть найдена");
        assertEquals("http://localhost:8080/abcd1234", found.getShortUrl());
        verify(linkRepository, times(1)).findByShortUrl(link.getShortUrl());
    }
}
