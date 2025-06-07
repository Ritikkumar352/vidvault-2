package com.ritik.vidvaultaws2;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VideoRepo extends JpaRepository<Video, Long> {
    Video findByKey(String key);
}

