package com.ferbator.demo.repositories;

import com.ferbator.demo.entities.Link;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LinkRepository extends JpaRepository<Link, Long> {
    Link findByShortUrl(String shortUrl);
}

