package com.benchreadiness.interview.service;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

@Service
public class ResumeParsingService {
    
    private final Tika tika = new Tika();
    
    private static final List<String> SUPPORTED_MIME_TYPES = Arrays.asList(
        "application/pdf",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );
    
    public String extractTextFromResume(MultipartFile file) throws IOException, TikaException {
        validateFileType(file);
        
        try (InputStream inputStream = file.getInputStream()) {
            String extractedText = tika.parseToString(inputStream);
            return cleanExtractedText(extractedText);
        }
    }
    
    public String extractTextFromFile(String filePath) throws IOException, TikaException {
        java.io.File file = new java.io.File(filePath);
        if (!file.exists()) {
            throw new IOException("File not found: " + filePath);
        }
        
        String extractedText = tika.parseToString(file);
        return cleanExtractedText(extractedText);
    }
    
    public boolean isSupportedFileType(String mimeType) {
        return SUPPORTED_MIME_TYPES.contains(mimeType);
    }
    
    public void validateFileType(MultipartFile file) throws IOException {
        String mimeType = file.getContentType();
        if (isSupportedFileType(mimeType) || isSupportedExtension(file.getOriginalFilename())) {
            return;
        }
        throw new IOException("Unsupported file type: " + mimeType +
            ". Supported types: PDF, DOC, DOCX");
    }

    private boolean isSupportedExtension(String filename) {
        if (filename == null || filename.isBlank()) {
            return false;
        }
        String lower = filename.toLowerCase();
        return lower.endsWith(".pdf") || lower.endsWith(".doc") || lower.endsWith(".docx");
    }
    
    private String cleanExtractedText(String text) {
        if (text == null) {
            return "";
        }
        
        // Clean up the extracted text
        return text
            .replaceAll("\\r\\n", "\\n")  // Normalize line endings
            .replaceAll("\\n{3,}", "\\n\\n")  // Remove excessive line breaks
            .replaceAll("\\s{2,}", " ")  // Remove excessive spaces
            .trim();
    }
    
    public ResumeParseResult parseResume(MultipartFile file) {
        try {
            String extractedText = extractTextFromResume(file);
            return new ResumeParseResult(true, extractedText, null);
        } catch (Exception e) {
            return new ResumeParseResult(false, null, e.getMessage());
        }
    }
    
    public static class ResumeParseResult {
        private final boolean success;
        private final String extractedText;
        private final String errorMessage;
        
        public ResumeParseResult(boolean success, String extractedText, String errorMessage) {
            this.success = success;
            this.extractedText = extractedText;
            this.errorMessage = errorMessage;
        }
        
        public boolean isSuccess() { return success; }
        public String getExtractedText() { return extractedText; }
        public String getErrorMessage() { return errorMessage; }
    }
}