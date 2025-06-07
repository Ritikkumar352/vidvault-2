package com.ritik.vidvaultaws2;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class S3Service {

    @Autowired private S3Client s3Client;
    @Autowired private S3Presigner s3Presigner;
    @Autowired private VideoRepo videoRepo;
    @Value("${aws.s3.bucket}") private String bucketName;

    public ResponseEntity<Map<String, String>> upload(MultipartFile file) {
        String originalFileName = file.getOriginalFilename();
        String ext = originalFileName.substring(originalFileName.lastIndexOf('.'));
        String uniqueName = originalFileName.replace(ext, "") + "-" + UUID.randomUUID() + ext;

        try {
            RequestBody reqBody = RequestBody.fromInputStream(file.getInputStream(), file.getSize());
            PutObjectRequest req = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .contentType(file.getContentType())
                    .key(uniqueName)
                    .build();
            s3Client.putObject(req, reqBody);

            // Save meta to DB
            Video video = new Video();
            video.setKey(uniqueName);
            video.setTitle(originalFileName);
            video.setContentType(file.getContentType());
            video.setFileSize(file.getSize() / (1024.0 * 1024.0));
            video.setUploadedAt(LocalDateTime.now());
            videoRepo.save(video);

            Map<String, String> resp = new HashMap<>();
            resp.put("message", "Upload successful");
            resp.put("key", uniqueName);
            return new ResponseEntity<>(resp, HttpStatus.OK);
        } catch (IOException e) {
            Map<String, String> err = new HashMap<>();
            err.put("message", "Upload failed");
            err.put("error", e.getMessage());
            return new ResponseEntity<>(err, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public ResponseEntity<Map<String, String>> getVideo(String key) {
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofHours(3))
                .getObjectRequest(r -> r.bucket(bucketName).key(key))
                .build();
        PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);

        Map<String, String> result = new HashMap<>();
        result.put("url", presignedRequest.url().toString());
        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    public ResponseEntity<Map<String, String>> deleteVideo(String key) {
        Map<String, String> result = new HashMap<>();
        try {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();
            s3Client.deleteObject(deleteRequest);

            // Remove from DB
            Video video = videoRepo.findByKey(key);
            if (video != null) videoRepo.delete(video);

            result.put("message", "Video deleted");
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (S3Exception e) {
            result.put("message", "Delete failed");
            return new ResponseEntity<>(result, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // *** New: List all videos with original name and date ***
    public List<Map<String, Object>> listAllVideos() {
        return videoRepo.findAll().stream()
                .sorted(Comparator.comparing(Video::getUploadedAt).reversed())
                .map(video -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("title", video.getTitle());
                    map.put("uploadedAt", video.getUploadedAt());
                    map.put("key", video.getKey());
                    map.put("sizeMB", video.getFileSize());
                    return map;
                })
                .collect(Collectors.toList());
    }

    // Extra: Get meta for a single video
    public Map<String, Object> getVideoMeta(String key) {
        Video v = videoRepo.findByKey(key);
        if (v == null) return null;
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("title", v.getTitle());
        meta.put("uploadedAt", v.getUploadedAt());
        meta.put("sizeMB", v.getFileSize());
        meta.put("contentType", v.getContentType());
        meta.put("key", v.getKey());
        return meta;
    }

    // Extra: Clean up old videos (e.g., > 30 days)
    public int deleteOldVideos(int days) {
        List<Video> old = videoRepo.findAll().stream()
                .filter(v -> v.getUploadedAt().isBefore(LocalDateTime.now().minusDays(days)))
                .collect(Collectors.toList());
        for (Video v : old) {
            deleteVideo(v.getKey());
        }
        return old.size();
    }
}
