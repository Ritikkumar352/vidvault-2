package com.ritik.vidvaultaws2;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Data
public class Video {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String key;
    private String title;
    private String contentType;
    private Double fileSize;
    private LocalDateTime uploadedAt;
}
