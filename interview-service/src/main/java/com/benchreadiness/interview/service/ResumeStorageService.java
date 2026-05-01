package com.benchreadiness.interview.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class ResumeStorageService {
    
    @Value("${app.resume.storage.path:uploads/resumes}")
    private String resumeStoragePath;
    
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    
    public String storeResume(String candidateId, MultipartFile file) throws IOException {
        // Create directory structure: uploads/resumes/candidate-{id}/
        Path candidateDir = Paths.get(resumeStoragePath, "candidate-" + candidateId);
        Path currentDir = candidateDir.resolve("current");
        Path historyDir = candidateDir.resolve("history");
        
        // Create directories if they don't exist
        Files.createDirectories(currentDir);
        Files.createDirectories(historyDir);
        
        // Move existing current resume to history if it exists
        Path currentResumePath = currentDir.resolve("resume" + getFileExtension(file.getOriginalFilename()));
        if (Files.exists(currentResumePath)) {
            String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
            String historyFilename = timestamp + "_" + file.getOriginalFilename();
            Path historyPath = historyDir.resolve(historyFilename);
            Files.move(currentResumePath, historyPath, StandardCopyOption.REPLACE_EXISTING);
        }
        
        // Store new resume as current
        String fileExtension = getFileExtension(file.getOriginalFilename());
        Path newResumePath = currentDir.resolve("resume" + fileExtension);
        Files.copy(file.getInputStream(), newResumePath, StandardCopyOption.REPLACE_EXISTING);
        
        return newResumePath.toString();
    }
    
    public byte[] getResumeContent(String filePath) throws IOException {
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            throw new IOException("Resume file not found: " + filePath);
        }
        return Files.readAllBytes(path);
    }
    
    public boolean deleteResume(String filePath) {
        try {
            Path path = Paths.get(filePath);
            return Files.deleteIfExists(path);
        } catch (IOException e) {
            return false;
        }
    }
    
    public boolean resumeExists(String filePath) {
        return Files.exists(Paths.get(filePath));
    }
    
    private String getFileExtension(String filename) {
        if (filename == null || filename.lastIndexOf('.') == -1) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.'));
    }
    
    public String getResumeDirectory(String candidateId) {
        return Paths.get(resumeStoragePath, "candidate-" + candidateId).toString();
    }
}