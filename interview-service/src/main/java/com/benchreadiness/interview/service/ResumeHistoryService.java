package com.benchreadiness.interview.service;

import com.benchreadiness.interview.client.AuthServiceClient;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ResumeHistoryService {
    
    private final ResumeStorageService storageService;
    private final AuthServiceClient authServiceClient;
    
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    
    public ResumeHistoryService(ResumeStorageService storageService, AuthServiceClient authServiceClient) {
        this.storageService = storageService;
        this.authServiceClient = authServiceClient;
    }
    
    public List<ResumeVersion> getResumeHistory(String candidateId) {
        List<ResumeVersion> history = new ArrayList<>();
        
        try {
            String candidateDir = storageService.getResumeDirectory(candidateId);
            Path historyDir = Paths.get(candidateDir, "history");
            
            if (!Files.exists(historyDir)) {
                return history;
            }
            
            // Get all files in history directory
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(historyDir)) {
                for (Path file : stream) {
                    if (Files.isRegularFile(file)) {
                        ResumeVersion version = parseResumeVersion(file);
                        if (version != null) {
                            history.add(version);
                        }
                    }
                }
            }
            
            // Sort by upload date (newest first)
            history.sort((a, b) -> b.getUploadDate().compareTo(a.getUploadDate()));
            
        } catch (IOException e) {
            System.err.println("Failed to get resume history for candidate " + candidateId + ": " + e.getMessage());
        }
        
        return history;
    }
    
    public ResumeVersion getCurrentResume(String candidateId) {
        try {
            Map<String, Object> candidate = authServiceClient.getUserById(candidateId);
            if (candidate == null) {
                return null;
            }
            
            String resumeFilePath = (String) candidate.get("resumeFilePath");
            String resumeFilename = (String) candidate.get("resumeFilename");
            String resumeUploadedAt = (String) candidate.get("resumeUploadedAt");
            
            if (resumeFilePath == null || !storageService.resumeExists(resumeFilePath)) {
                return null;
            }
            
            Path filePath = Paths.get(resumeFilePath);
            long fileSize = Files.size(filePath);
            
            ResumeVersion current = new ResumeVersion();
            current.setFilename(resumeFilename);
            current.setFilePath(resumeFilePath);
            current.setFileSize(fileSize);
            current.setCurrent(true);
            
            if (resumeUploadedAt != null) {
                current.setUploadDate(resumeUploadedAt);
            }
            
            return current;
            
        } catch (Exception e) {
            System.err.println("Failed to get current resume for candidate " + candidateId + ": " + e.getMessage());
            return null;
        }
    }
    
    public boolean deleteResumeVersion(String candidateId, String filename) {
        try {
            String candidateDir = storageService.getResumeDirectory(candidateId);
            Path historyDir = Paths.get(candidateDir, "history");
            Path fileToDelete = historyDir.resolve(filename);
            
            if (Files.exists(fileToDelete)) {
                Files.delete(fileToDelete);
                return true;
            }
            
        } catch (IOException e) {
            System.err.println("Failed to delete resume version " + filename + " for candidate " + candidateId + ": " + e.getMessage());
        }
        
        return false;
    }
    
    public byte[] downloadResumeVersion(String candidateId, String filename) throws IOException {
        String candidateDir = storageService.getResumeDirectory(candidateId);
        Path historyDir = Paths.get(candidateDir, "history");
        Path filePath = historyDir.resolve(filename);
        
        if (!Files.exists(filePath)) {
            throw new IOException("Resume version not found: " + filename);
        }
        
        return Files.readAllBytes(filePath);
    }
    
    public ResumeHistorySummary getHistorySummary(String candidateId) {
        List<ResumeVersion> history = getResumeHistory(candidateId);
        ResumeVersion current = getCurrentResume(candidateId);
        
        ResumeHistorySummary summary = new ResumeHistorySummary();
        summary.setCandidateId(candidateId);
        summary.setCurrentResume(current);
        summary.setHistoryVersions(history);
        summary.setTotalVersions(history.size() + (current != null ? 1 : 0));
        
        if (current != null) {
            summary.setLastUploadDate(current.getUploadDate());
        } else if (!history.isEmpty()) {
            summary.setLastUploadDate(history.get(0).getUploadDate());
        }
        
        return summary;
    }
    
    private ResumeVersion parseResumeVersion(Path file) {
        try {
            String filename = file.getFileName().toString();
            long fileSize = Files.size(file);
            
            ResumeVersion version = new ResumeVersion();
            version.setFilename(filename);
            version.setFilePath(file.toString());
            version.setFileSize(fileSize);
            version.setCurrent(false);
            
            // Extract timestamp from filename (format: 2024-04-30_14-30-45_originalname.pdf)
            if (filename.matches("\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}_.*")) {
                String timestampPart = filename.substring(0, 19); // "2024-04-30_14-30-45"
                LocalDateTime uploadTime = LocalDateTime.parse(timestampPart, TIMESTAMP_FORMAT);
                version.setUploadDate(uploadTime.toString());
            } else {
                // Fallback to file modification time
                version.setUploadDate(Files.getLastModifiedTime(file).toString());
            }
            
            return version;
            
        } catch (Exception e) {
            System.err.println("Failed to parse resume version from file " + file + ": " + e.getMessage());
            return null;
        }
    }
    
    public static class ResumeVersion {
        private String filename;
        private String filePath;
        private long fileSize;
        private String uploadDate;
        private boolean current;
        
        public String getFilename() { return filename; }
        public void setFilename(String filename) { this.filename = filename; }
        public String getFilePath() { return filePath; }
        public void setFilePath(String filePath) { this.filePath = filePath; }
        public long getFileSize() { return fileSize; }
        public void setFileSize(long fileSize) { this.fileSize = fileSize; }
        public String getUploadDate() { return uploadDate; }
        public void setUploadDate(String uploadDate) { this.uploadDate = uploadDate; }
        public boolean isCurrent() { return current; }
        public void setCurrent(boolean current) { this.current = current; }
    }
    
    public static class ResumeHistorySummary {
        private String candidateId;
        private ResumeVersion currentResume;
        private List<ResumeVersion> historyVersions;
        private int totalVersions;
        private String lastUploadDate;
        
        public String getCandidateId() { return candidateId; }
        public void setCandidateId(String candidateId) { this.candidateId = candidateId; }
        public ResumeVersion getCurrentResume() { return currentResume; }
        public void setCurrentResume(ResumeVersion currentResume) { this.currentResume = currentResume; }
        public List<ResumeVersion> getHistoryVersions() { return historyVersions; }
        public void setHistoryVersions(List<ResumeVersion> historyVersions) { this.historyVersions = historyVersions; }
        public int getTotalVersions() { return totalVersions; }
        public void setTotalVersions(int totalVersions) { this.totalVersions = totalVersions; }
        public String getLastUploadDate() { return lastUploadDate; }
        public void setLastUploadDate(String lastUploadDate) { this.lastUploadDate = lastUploadDate; }
    }
}