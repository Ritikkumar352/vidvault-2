package com.ritik.vidvaultaws2;


import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class S3Service {

    @Autowired private S3Client s3Client;
    @Autowired private S3Presigner s3Presigner;
    @Autowired private VideoRepo videoRepo;
    @Value("${aws.s3.bucket}") private String bucketName; // TODO

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
            result.put("message", "Video deleted");
            return new ResponseEntity<>(result, HttpStatus.OK);
        } catch (S3Exception e) {
            result.put("message", "Delete failed");
            return new ResponseEntity<>(result, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}

