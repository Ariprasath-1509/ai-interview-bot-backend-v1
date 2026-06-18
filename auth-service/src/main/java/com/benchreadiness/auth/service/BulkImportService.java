package com.benchreadiness.auth.service;

import com.benchreadiness.auth.dto.BulkImportRequest;
import com.benchreadiness.auth.dto.CreateCandidateRequest;
import com.benchreadiness.auth.entity.User;
import com.benchreadiness.auth.entity.UserRole;
import com.benchreadiness.auth.repository.UserRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class BulkImportService {

    private static final Logger log = LoggerFactory.getLogger(BulkImportService.class);

    private final UserRepository userRepository;
    private final PasswordService passwordService;
    private final ExcelParserService excelParserService;
    private final EmailService emailService;
    
    // Store import results temporarily (in production, use Redis or database)
    private final Map<String, BulkImportResult> importResults = new ConcurrentHashMap<>();

    public BulkImportService(UserRepository userRepository,
                           PasswordService passwordService,
                           ExcelParserService excelParserService,
                           EmailService emailService) {
        this.userRepository = userRepository;
        this.passwordService = passwordService;
        this.excelParserService = excelParserService;
        this.emailService = emailService;
    }

    @Transactional
    public CreatedCandidate createSingleCandidate(CreateCandidateRequest req) {
        if ((req.getOfficialEmail() == null || req.getOfficialEmail().isBlank())
                && (req.getPersonalEmail() == null || req.getPersonalEmail().isBlank())) {
            throw new IllegalArgumentException("Official email or personal email is required");
        }

        BulkImportRequest.CandidateBulkData candidateData = new BulkImportRequest.CandidateBulkData();
        candidateData.setRowNumber(1);
        candidateData.setName(req.getName());
        candidateData.setOfficialEmail(req.getOfficialEmail());
        candidateData.setPersonalEmail(req.getPersonalEmail());
        candidateData.setContactNumber(req.getContactNumber());
        candidateData.setBatch(req.getBatch());
        candidateData.setBatchMentor(req.getBatchMentor());
        candidateData.setSource(req.getSource());
        candidateData.setStatus(req.getCandidateStatus());
        candidateData.setRating(req.getRating());
        candidateData.setSkillSet(req.getSkillSet());
        candidateData.setYoeActual(req.getYoeActual());
        candidateData.setYoePortrayed(req.getYoePortrayed());
        candidateData.setYop(req.getYop());
        candidateData.setNoOfInterviews(req.getNoOfInterviews());
        candidateData.setInterviewMentorName(req.getInterviewMentorName());
        candidateData.setClientName(req.getClientName());

        return createCandidate(candidateData);
    }

    @Transactional
    public BulkImportResult processBulkImport(String sessionId) {
        BulkImportRequest importRequest = excelParserService.getImportSession(sessionId);
        if (importRequest == null) {
            throw new IllegalArgumentException("Invalid or expired session ID");
        }

        List<CreatedCandidate> createdCandidates = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int successCount = 0;

        try {
            for (BulkImportRequest.CandidateBulkData candidateData : importRequest.getCandidates()) {
                try {
                    CreatedCandidate created = createCandidate(candidateData);
                    createdCandidates.add(created);
                    successCount++;
                } catch (Exception e) {
                    errors.add("Row " + candidateData.getRowNumber() + ": " + e.getMessage());
                }
            }

            // Clear session after processing
            excelParserService.clearImportSession(sessionId);
            
            // Store the result for later download
            importResults.put(sessionId, new BulkImportResult(successCount, errors.size(), createdCandidates, errors));

            return new BulkImportResult(successCount, errors.size(), createdCandidates, errors);

        } catch (Exception e) {
            throw new RuntimeException("Bulk import failed: " + e.getMessage(), e);
        }
    }

    private CreatedCandidate createCandidate(BulkImportRequest.CandidateBulkData candidateData) {
        // Determine username (prefer official email, fallback to personal email)
        String username = null;
        if (candidateData.getOfficialEmail() != null && !candidateData.getOfficialEmail().trim().isEmpty()) {
            username = candidateData.getOfficialEmail();
        } else if (candidateData.getPersonalEmail() != null && !candidateData.getPersonalEmail().trim().isEmpty()) {
            username = candidateData.getPersonalEmail();
        }

        // If no valid email, generate one based on name and row number
        if (username == null || username.trim().isEmpty()) {
            String firstName = candidateData.getName() != null ? 
                candidateData.getName().split(" ")[0].toLowerCase() : "user";
            username = firstName + ".row" + candidateData.getRowNumber() + "@benchreadiness.com";
        }

        // Check if user already exists
        if (userRepository.existsByEmailIgnoreCase(username)) {
            throw new IllegalArgumentException("User with email " + username + " already exists");
        }

        // Generate password: FirstName@2025
        String firstName = candidateData.getName() != null ? 
            candidateData.getName().split(" ")[0] : "User";
        String plainPassword = firstName + "@" + LocalDateTime.now().getYear();

        // Create user entity
        User user = new User();
        user.setName(candidateData.getName());
        user.setEmail(username); // Set the primary email field
        user.setPassword(passwordService.encode(plainPassword));
        user.setRole(UserRole.CANDIDATE);
        
        // Set candidate-specific fields
        user.setBatch(candidateData.getBatch());
        user.setBatchMentor(candidateData.getBatchMentor());
        user.setSource(candidateData.getSource() != null
                ? candidateData.getSource().toUpperCase() : null);
        user.setCandidateStatus(candidateData.getStatus() != null
                ? candidateData.getStatus().toUpperCase() : "TRAINING");
        user.setRating(candidateData.getRating() != null
                ? candidateData.getRating().toUpperCase() : null);
        user.setContactNumber(candidateData.getContactNumber());
        user.setOfficialEmail(candidateData.getOfficialEmail());
        user.setPersonalEmail(candidateData.getPersonalEmail());
        
        // Convert Double to BigDecimal
        if (candidateData.getYoeActual() != null) {
            user.setYoeActual(BigDecimal.valueOf(candidateData.getYoeActual()));
        }
        if (candidateData.getYoePortrayed() != null) {
            user.setYoePortrayed(BigDecimal.valueOf(candidateData.getYoePortrayed()));
        }
        
        if (candidateData.getSkillSet() != null) {
            user.setSkillSet(candidateData.getSkillSet().toUpperCase());
        }
        user.setNoOfInterviews(candidateData.getNoOfInterviews() != null ? candidateData.getNoOfInterviews() : 0);
        user.setYop(candidateData.getYop());
        user.setInterviewMentorName(candidateData.getInterviewMentorName());
        user.setClientName(candidateData.getClientName());

        // Save user (timestamps are set automatically via @PrePersist)
        User savedUser = userRepository.save(user);

        sendWelcomeEmail(candidateData, savedUser.getName(), username, plainPassword);

        return new CreatedCandidate(
            savedUser.getId(),
            savedUser.getName(),
            username,
            plainPassword,
            candidateData.getSource(),
            candidateData.getBatch(),
            candidateData.getRowNumber()
        );
    }

    private void sendWelcomeEmail(BulkImportRequest.CandidateBulkData candidateData,
                                  String candidateName,
                                  String username,
                                  String plainPassword) {
        String notifyEmail = resolveNotifyEmail(candidateData, username);
        if (notifyEmail == null) {
            return;
        }
        try {
            emailService.sendCandidateWelcomeEmail(notifyEmail, candidateName, username, plainPassword);
        } catch (Exception e) {
            log.warn("Failed to send welcome email for bulk import row {}: {}",
                candidateData.getRowNumber(), e.getMessage());
        }
    }

    private String resolveNotifyEmail(BulkImportRequest.CandidateBulkData candidateData, String username) {
        if (candidateData.getPersonalEmail() != null && !candidateData.getPersonalEmail().trim().isEmpty()) {
            return candidateData.getPersonalEmail().trim();
        }
        if (candidateData.getOfficialEmail() != null && !candidateData.getOfficialEmail().trim().isEmpty()) {
            return candidateData.getOfficialEmail().trim();
        }
        if (username != null && username.contains("@")) {
            return username.trim();
        }
        return null;
    }

    public byte[] generateCredentialsExcel(List<CreatedCandidate> candidates) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Login Credentials");

            // Create header style
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 12);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // Create header row
            Row headerRow = sheet.createRow(0);
            String[] headers = {
                "S.No", "Name", "Batch", "Source", "Username (Email)", 
                "Password", "Login URL", "Status"
            };

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Create data rows
            for (int i = 0; i < candidates.size(); i++) {
                CreatedCandidate candidate = candidates.get(i);
                Row row = sheet.createRow(i + 1);

                row.createCell(0).setCellValue(i + 1);
                row.createCell(1).setCellValue(candidate.getName());
                row.createCell(2).setCellValue(candidate.getBatch());
                row.createCell(3).setCellValue(candidate.getSource());
                row.createCell(4).setCellValue(candidate.getUsername());
                row.createCell(5).setCellValue(candidate.getPassword());
                row.createCell(6).setCellValue("https://prod.voiceaibot.in/login");
                row.createCell(7).setCellValue("Created Successfully");
            }

            // Auto-size columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            // Write to byte array
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }
    
    /**
     * Get stored import result by session ID
     */
    public BulkImportResult getImportResult(String sessionId) {
        return importResults.get(sessionId);
    }
    
    /**
     * Clear stored import result (optional cleanup)
     */
    public void clearImportResult(String sessionId) {
        importResults.remove(sessionId);
    }

    public static class BulkImportResult {
        private final int successCount;
        private final int errorCount;
        private final List<CreatedCandidate> createdCandidates;
        private final List<String> errors;

        public BulkImportResult(int successCount, int errorCount, 
                               List<CreatedCandidate> createdCandidates, List<String> errors) {
            this.successCount = successCount;
            this.errorCount = errorCount;
            this.createdCandidates = createdCandidates;
            this.errors = errors;
        }

        public int getSuccessCount() {
            return successCount;
        }

        public int getErrorCount() {
            return errorCount;
        }

        public List<CreatedCandidate> getCreatedCandidates() {
            return createdCandidates;
        }

        public List<String> getErrors() {
            return errors;
        }
    }

    public static class CreatedCandidate {
        private final String id;
        private final String name;
        private final String username;
        private final String password;
        private final String source;
        private final String batch;
        private final int rowNumber;

        public CreatedCandidate(String id, String name, String username, String password, 
                               String source, String batch, int rowNumber) {
            this.id = id;
            this.name = name;
            this.username = username;
            this.password = password;
            this.source = source;
            this.batch = batch;
            this.rowNumber = rowNumber;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getUsername() {
            return username;
        }

        public String getPassword() {
            return password;
        }

        public String getSource() {
            return source;
        }

        public String getBatch() {
            return batch;
        }

        public int getRowNumber() {
            return rowNumber;
        }
    }
}