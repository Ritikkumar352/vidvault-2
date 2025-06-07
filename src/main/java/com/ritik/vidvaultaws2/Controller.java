package com.ritik.vidvaultaws2;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Map;

@RestController
public class Controller {

    @Autowired
    private S3Service s3Service;

    @GetMapping("/")
    public String index() {
        return "Hello World";
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        return s3Service.upload(file);
    }

    @PostMapping("/fetchVideo")
    public ResponseEntity<Map<String, String>> fetchVideo(@RequestBody Map<String, String> request) {
        String key = request.get("key");
        return s3Service.getVideo(key);
    }

    @PostMapping("/deleteVideo")
    public ResponseEntity<Map<String, String>> deleteVideo(@RequestBody Map<String, String> request) {
        String key = request.get("key");
        return s3Service.deleteVideo(key);
    }

    // *** New: List all videos ***
    @GetMapping("/listVideos")
    public List<Map<String, Object>> listAllVideos() {
        return s3Service.listAllVideos();
    }

    // Extra: Get video meta
    @GetMapping("/videoMeta/{key}")
    public Map<String, Object> getVideoMeta(@PathVariable String key) {
        return s3Service.getVideoMeta(key);
    }

    // Extra: Clean up (delete) old videos
    @DeleteMapping("/deleteOldVideos/{days}")
    public String deleteOld(@PathVariable int days) {
        int n = s3Service.deleteOldVideos(days);
        return "Deleted " + n + " old videos.";
    }
}


