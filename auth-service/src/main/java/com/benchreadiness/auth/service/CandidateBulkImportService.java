package com.benchreadiness.auth.service;

import com.benchreadiness.auth.dto.BulkImportResponse;
import com.benchreadiness.auth.dto.CandidateImportRow;
import com.benchreadiness.auth.entity.User;
import com.benchreadiness.auth.entity.UserRole;
import com.benchreadiness.auth.masterdata.MasterDataCategory;
import com.benchreadiness.auth.masterdata.MasterDataService;
import com.benchreadiness.auth.repository.UserRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class CandidateBulkImportService {

    private static final Logger log = LoggerFactory.getLogger(CandidateBulkImportService.class);

    @Value("${app.third-party.excel-api.base-url:http://localhost:8000}")
    private String thirdPartyApiUrl;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MasterDataService masterDataService;

    public BulkImportResponse importFromThirdPartyApi(String gdriveFileUrl, String userId) {
        try {
            log.info("Starting bulk import from third-party API for user: {}", userId);
            
            // 1. Fetch Excel from third-party API
            byte[] excelData = fetchExcelFromApi(gdriveFileUrl);
            
            // 2. Parse "Candidate" sheet
            List<CandidateImportRow> candidates = parseExcelCandidateSheet(excelData);
            
            // 3. Process candidates
            return processCandidates(candidates, userId);
            
        } catch (Exception e) {
            log.error("Bulk import failed for user: {}", userId, e);
            return BulkImportResponse.error("Import failed: " + e.getMessage());
        }
    }

    private byte[] fetchExcelFromApi(String gdriveFileUrl) {
        try {
            String apiUrl = thirdPartyApiUrl + "/gdrive/latest-excel?GDRIVE_FILE_URL=" + 
                           URLEncoder.encode(gdriveFileUrl, StandardCharsets.UTF_8);
            
            log.info("Fetching Excel from API: {}", apiUrl);
            
            RestTemplate restTemplate = new RestTemplate();
            restTemplate.getMessageConverters().add(new ByteArrayHttpMessageConverter());
            
            ResponseEntity<byte[]> response = restTemplate.getForEntity(apiUrl, byte[].class);
            
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new RuntimeException("Failed to fetch Excel from API: " + response.getStatusCode());
            }
            
            log.info("Successfully fetched Excel file, size: {} bytes", response.getBody().length);
            return response.getBody();
            
        } catch (Exception e) {
            log.error("Error fetching Excel from third-party API", e);
            throw new RuntimeException("Failed to fetch Excel: " + e.getMessage(), e);
        }
    }

    private List<CandidateImportRow> parseExcelCandidateSheet(byte[] excelData) {
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excelData))) {
            
            // Find "Candidate" sheet (case-insensitive)
            Sheet candidateSheet = null;
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                if (sheet.getSheetName().equalsIgnoreCase("Candidate")) {
                    candidateSheet = sheet;
                    break;
                }
            }
            
            if (candidateSheet == null) {
                throw new RuntimeException("'Candidate' sheet not found in Excel file");
            }
            
            log.info("Found 'Candidate' sheet with {} rows", candidateSheet.getLastRowNum() + 1);
            
            List<CandidateImportRow> candidates = new ArrayList<>();
            
            // Skip header row, start from row 1
            for (int i = 1; i <= candidateSheet.getLastRowNum(); i++) {
                Row row = candidateSheet.getRow(i);
                if (row == null || isEmptyRow(row)) continue;
                
                CandidateImportRow candidate = parseRow(row, i + 1);
                if (candidate != null) {
                    candidates.add(candidate);
                }
            }
            
            log.info("Parsed {} candidate rows from Excel", candidates.size());
            return candidates;
            
        } catch (Exception e) {
            log.error("Error parsing Excel file", e);
            throw new RuntimeException("Failed to parse Excel: " + e.getMessage(), e);
        }
    }

    private CandidateImportRow parseRow(Row row, int rowNumber) {
        try {
            CandidateImportRow candidate = new CandidateImportRow();
            candidate.setRowNumber(rowNumber);
            
            // Skip column A (No), start from column B
            candidate.setBatch(getCellValueAsString(row.getCell(1)));                 // B - Batch (DOH)
            candidate.setBatchMentor(getCellValueAsString(row.getCell(2)));          // C - Batch Mentor
            candidate.setSource(getCellValueAsString(row.getCell(3)));               // D - Source
            candidate.setStatus(getCellValueAsString(row.getCell(4)));               // E - Status
            candidate.setRating(getCellValueAsString(row.getCell(5)));               // F - Rating
            candidate.setName(getCellValueAsString(row.getCell(6)));                 // G - Name
            candidate.setContactNumber(getCellValueAsString(row.getCell(7)));        // H - Contact Number
            candidate.setOfficialEmail(getCellValueAsString(row.getCell(8)));        // I - Official Mail ID
            candidate.setPersonalEmail(getCellValueAsString(row.getCell(9)));        // J - Personal Mail ID
            candidate.setYoeActual(getCellValueAsString(row.getCell(10)));          // K - YOE - A
            candidate.setYoePortrayed(getCellValueAsString(row.getCell(11)));       // L - YOE - P
            candidate.setSkillSet(getCellValueAsString(row.getCell(12)));           // M - Skill Set
            candidate.setYop(getCellValueAsString(row.getCell(13)));                // N - YOP
            candidate.setNoOfInterviews(getCellValueAsString(row.getCell(14)));     // O - No of Interviews
            candidate.setInterviewMentorName(getCellValueAsString(row.getCell(15))); // P - Interview Mentor Name
            candidate.setClientName(getCellValueAsString(row.getCell(16)));         // Q - Client Name
            
            return candidate;
            
        } catch (Exception e) {
            log.warn("Failed to parse row {}: {}", rowNumber, e.getMessage());
            return null;
        }
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) return null;
        
        switch (cell.getCellType()) {
            case STRING:
                return StringUtils.hasText(cell.getStringCellValue()) ? cell.getStringCellValue().trim() : null;
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getLocalDateTimeCellValue().toLocalDate().toString();
                } else {
                    // Handle both integers and decimals
                    double numValue = cell.getNumericCellValue();
                    if (numValue == Math.floor(numValue)) {
                        return String.valueOf((int) numValue);
                    } else {
                        return String.valueOf(numValue);
                    }
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return getCellValueAsString(cell); // Evaluate formula
                } catch (Exception e) {
                    return null;
                }
            default:
                return null;
        }
    }

    private boolean isEmptyRow(Row row) {
        // Check if all important cells are empty
        return getCellValueAsString(row.getCell(6)) == null && // Name
               getCellValueAsString(row.getCell(8)) == null && // Official Email
               getCellValueAsString(row.getCell(9)) == null;   // Personal Email
    }

    private BulkImportResponse processCandidates(List<CandidateImportRow> candidates, String userId) {
        BulkImportResponse response = new BulkImportResponse();
        response.setTotalRows(candidates.size());
        
        log.info("Processing {} candidates for bulk import", candidates.size());
        
        for (CandidateImportRow candidate : candidates) {
            try {
                // Skip if required fields missing
                if (!hasRequiredFields(candidate)) {
                    response.addSkipped(candidate.getRowNumber(), 
                        "Missing required fields: " + getMissingFields(candidate));
                    continue;
                }
                
                // Skip if candidate already exists
                if (candidateExists(candidate)) {
                    response.addSkipped(candidate.getRowNumber(), 
                        "Candidate already exists with email/contact: " + 
                        getExistingIdentifier(candidate));
                    continue;
                }
                
                // Create new candidate
                User newCandidate = createCandidateFromRow(candidate, userId);
                userRepository.save(newCandidate);
                
                response.addSuccess(candidate.getRowNumber(), newCandidate.getId());
                
            } catch (Exception e) {
                log.error("Failed to process row {}", candidate.getRowNumber(), e);
                response.addError(candidate.getRowNumber(), 
                    "Processing failed: " + e.getMessage());
            }
        }
        
        log.info("Bulk import completed - Success: {}, Skipped: {}, Errors: {}", 
                response.getSuccessCount(), response.getSkippedCount(), response.getErrorCount());
        
        return response;
    }

    private boolean hasRequiredFields(CandidateImportRow candidate) {
        // Minimum required fields for candidate creation
        return StringUtils.hasText(candidate.getName()) &&
               (StringUtils.hasText(candidate.getOfficialEmail()) || 
                StringUtils.hasText(candidate.getPersonalEmail())) &&
               StringUtils.hasText(candidate.getBatch()) &&
               isValidSource(candidate.getSource()) &&
               isValidSkillSet(candidate.getSkillSet());
    }

    private String getMissingFields(CandidateImportRow candidate) {
        List<String> missing = new ArrayList<>();
        
        if (!StringUtils.hasText(candidate.getName())) missing.add("Name");
        if (!StringUtils.hasText(candidate.getOfficialEmail()) && 
            !StringUtils.hasText(candidate.getPersonalEmail())) missing.add("Email (Official or Personal)");
        if (!StringUtils.hasText(candidate.getBatch())) missing.add("Batch (DOH)");
        if (!isValidSource(candidate.getSource())) missing.add("Source (B2B/BENCH/MARKET)");
        if (!isValidSkillSet(candidate.getSkillSet())) missing.add("Skill Set (JAVA_SB/JFSR/REACT_JS)");
        
        return String.join(", ", missing);
    }

    private boolean isValidSource(String source) {
        return masterDataService.isValid(MasterDataCategory.CANDIDATE_SOURCE, source);
    }

    private boolean isValidSkillSet(String skillSet) {
        return masterDataService.isValid(MasterDataCategory.SKILL_SET, skillSet);
    }

    private boolean candidateExists(CandidateImportRow candidate) {
        return userRepository.existsByEmailOrOfficialEmailOrPersonalEmailOrContactNumber(
            candidate.getOfficialEmail(),
            candidate.getPersonalEmail(),
            candidate.getOfficialEmail(),
            candidate.getPersonalEmail(),
            candidate.getContactNumber()
        );
    }

    private String getExistingIdentifier(CandidateImportRow candidate) {
        if (StringUtils.hasText(candidate.getOfficialEmail())) return candidate.getOfficialEmail();
        if (StringUtils.hasText(candidate.getPersonalEmail())) return candidate.getPersonalEmail();
        return candidate.getContactNumber();
    }

    private User createCandidateFromRow(CandidateImportRow row, String createdByUserId) {
        User candidate = new User();
        
        // Basic required fields
        candidate.setName(row.getName());
        candidate.setRole(UserRole.CANDIDATE);
        
        // Email handling - prefer official, fallback to personal
        String primaryEmail = StringUtils.hasText(row.getOfficialEmail()) ? 
                             row.getOfficialEmail() : row.getPersonalEmail();
        candidate.setEmail(primaryEmail);
        candidate.setOfficialEmail(row.getOfficialEmail());
        candidate.setPersonalEmail(row.getPersonalEmail());
        
        // Contact and batch info
        candidate.setContactNumber(row.getContactNumber());
        candidate.setBatch(row.getBatch());
        candidate.setBatchMentor(row.getBatchMentor());
        
        // Source (required)
        if (isValidSource(row.getSource())) {
            candidate.setSource(masterDataService.normalizeCode(row.getSource()));
        }

        if (isValidSkillSet(row.getSkillSet())) {
            candidate.setSkillSet(masterDataService.normalizeCode(row.getSkillSet()));
        }

        if (StringUtils.hasText(row.getStatus()) && masterDataService.isValid(MasterDataCategory.CANDIDATE_STATUS, row.getStatus())) {
            candidate.setCandidateStatus(masterDataService.normalizeCode(row.getStatus()));
        } else {
            candidate.setCandidateStatus("RFD");
        }

        if (StringUtils.hasText(row.getRating()) && masterDataService.isValid(MasterDataCategory.CANDIDATE_RATING, row.getRating())) {
            candidate.setRating(masterDataService.normalizeCode(row.getRating()));
        }
        
        // Experience fields
        candidate.setYoeActual(parseDecimal(row.getYoeActual()));
        candidate.setYoePortrayed(parseDecimal(row.getYoePortrayed()));
        candidate.setYop(parseInteger(row.getYop()));
        candidate.setNoOfInterviews(parseInteger(row.getNoOfInterviews()));
        
        // Additional fields from bulk import
        candidate.setInterviewMentorName(row.getInterviewMentorName());
        candidate.setClientName(row.getClientName());
        
        // Generate default password
        candidate.setPassword(generateDefaultPassword());
        
        // Timestamps are handled by @PrePersist and @PreUpdate in User entity
        // No need to set them manually
        
        return candidate;
    }

    private BigDecimal parseDecimal(String value) {
        if (!StringUtils.hasText(value)) return null;
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer parseInteger(String value) {
        if (!StringUtils.hasText(value)) return null;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String generateDefaultPassword() {
        // Generate a simple default password
        return "Candidate@" + System.currentTimeMillis() % 10000;
    }
}