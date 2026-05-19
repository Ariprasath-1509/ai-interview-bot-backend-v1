package com.benchreadiness.auth.service;

import com.benchreadiness.auth.dto.BulkImportRequest;
import com.benchreadiness.auth.dto.BulkImportResponse;
import com.benchreadiness.auth.repository.UserRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class ExcelParserService {

    private final UserRepository userRepository;
    private final Map<String, BulkImportRequest> importSessions = new HashMap<>();

    // Column mapping based on Excel headers
    private static final Map<String, Integer> COLUMN_MAPPING = new HashMap<String, Integer>() {{
        put("Batch (DOH)", 1);
        put("Batch Mentor", 2);
        put("Source", 3);
        put("Status", 4);
        put("Rating", 5);
        put("Name", 6);
        put("Contact Number", 7);
        put("Official Mail ID", 8);
        put("Personal Mail ID", 9);
        put("YOE - A", 10);
        put("YOE - P", 11);
        put("Skill Set", 12);
        put("YOP", 13);
        put("No of Interviews", 14);
        put("Interview Mentor Name", 15);
        put("Client Name", 16);
    }};

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );

    private static final Pattern PHONE_PATTERN = Pattern.compile("^[0-9]{10}$");

    public ExcelParserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public BulkImportResponse parseExcelFile(MultipartFile file) throws IOException {
        String sessionId = UUID.randomUUID().toString();
        List<BulkImportRequest.CandidateBulkData> candidates = new ArrayList<>();
        List<BulkImportResponse.ValidationError> errors = new ArrayList<>();

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            
            // Validate headers
            Row headerRow = sheet.getRow(0);
            if (!validateHeaders(headerRow, errors)) {
                return createErrorResponse(sessionId, errors);
            }

            // Parse data rows
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || isEmptyRow(row)) continue;

                BulkImportRequest.CandidateBulkData candidate = parseRow(row, i + 1, errors);
                if (candidate != null) {
                    candidates.add(candidate);
                }
            }
        }

        // Validate business rules
        validateBusinessRules(candidates, errors);

        // Create import session
        BulkImportRequest importRequest = new BulkImportRequest();
        importRequest.setSessionId(sessionId);
        importRequest.setCandidates(candidates);
        importSessions.put(sessionId, importRequest);

        // Generate credential previews
        List<BulkImportResponse.CredentialPreview> previews = generateCredentialPreviews(candidates);

        // Build response
        BulkImportResponse response = new BulkImportResponse();
        response.setSessionId(sessionId);
        response.setTotalRows(candidates.size());
        response.setValidRows((int) candidates.stream().filter(c -> 
            errors.stream().noneMatch(e -> e.getRowNumber() == c.getRowNumber() && "ERROR".equals(e.getSeverity()))
        ).count());
        response.setErrorRows(response.getTotalRows() - response.getValidRows());
        response.setErrors(errors);
        response.setCredentialPreviews(previews);
        response.setCanProceed(response.getErrorRows() == 0);
        response.setCreatedAt(LocalDateTime.now());

        return response;
    }

    public BulkImportRequest getImportSession(String sessionId) {
        return importSessions.get(sessionId);
    }

    public void clearImportSession(String sessionId) {
        importSessions.remove(sessionId);
    }

    private boolean validateHeaders(Row headerRow, List<BulkImportResponse.ValidationError> errors) {
        if (headerRow == null) {
            errors.add(new BulkImportResponse.ValidationError(1, "headers",
                "Header row is missing", "", "ERROR"));
            return false;
        }
        // Header row exists — parsing uses fixed column indices so no strict header check needed
        return true;
    }

    private BulkImportRequest.CandidateBulkData parseRow(Row row, int rowNumber, 
                                                        List<BulkImportResponse.ValidationError> errors) {
        BulkImportRequest.CandidateBulkData candidate = new BulkImportRequest.CandidateBulkData();
        candidate.setRowNumber(rowNumber);

        try {
            // Parse each field with validation and normalization
            candidate.setBatch(getCellValueAsString(row, COLUMN_MAPPING.get("Batch (DOH)")));
            candidate.setBatchMentor(nullIfBlankOrNA(getCellValueAsString(row, COLUMN_MAPPING.get("Batch Mentor"))));
            candidate.setSource(normalizeSource(getCellValueAsString(row, COLUMN_MAPPING.get("Source"))));
            candidate.setStatus(normalizeStatus(getCellValueAsString(row, COLUMN_MAPPING.get("Status"))));
            candidate.setRating(normalizeRating(getCellValueAsString(row, COLUMN_MAPPING.get("Rating"))));
            candidate.setName(getCellValueAsString(row, COLUMN_MAPPING.get("Name")));
            candidate.setContactNumber(getCellValueAsString(row, COLUMN_MAPPING.get("Contact Number")));
            candidate.setOfficialEmail(normalizeEmail(getCellValueAsString(row, COLUMN_MAPPING.get("Official Mail ID"))));
            candidate.setPersonalEmail(normalizeEmail(getCellValueAsString(row, COLUMN_MAPPING.get("Personal Mail ID"))));
            candidate.setYoeActual(getCellValueAsDouble(row, COLUMN_MAPPING.get("YOE - A")));
            candidate.setYoePortrayed(getCellValueAsDouble(row, COLUMN_MAPPING.get("YOE - P")));
            candidate.setSkillSet(normalizeSkillSet(getCellValueAsString(row, COLUMN_MAPPING.get("Skill Set"))));
            candidate.setYop(getCellValueAsInteger(row, COLUMN_MAPPING.get("YOP")));
            candidate.setNoOfInterviews(getCellValueAsInteger(row, COLUMN_MAPPING.get("No of Interviews")));
            candidate.setInterviewMentorName(nullIfBlankOrNA(getCellValueAsString(row, COLUMN_MAPPING.get("Interview Mentor Name"))));
            candidate.setClientName(nullIfBlankOrNA(getCellValueAsString(row, COLUMN_MAPPING.get("Client Name"))));

            // Validate individual fields
            validateCandidate(candidate, errors);

            return candidate;
        } catch (Exception e) {
            errors.add(new BulkImportResponse.ValidationError(rowNumber, "row", 
                "Error parsing row: " + e.getMessage(), "", "ERROR"));
            return null;
        }
    }

    private void validateCandidate(BulkImportRequest.CandidateBulkData candidate, 
                                  List<BulkImportResponse.ValidationError> errors) {
        int row = candidate.getRowNumber();

        // Required fields
        if (isBlank(candidate.getName())) {
            errors.add(new BulkImportResponse.ValidationError(row, "name", 
                "Name is required", candidate.getName(), "ERROR"));
        }

        // Note: Batch is optional, can be null

        // Source validation — optional for TRAINING status
        if (!isBlank(candidate.getSource()) && !Arrays.asList("B2B", "BENCH", "MARKET").contains(candidate.getSource())) {
            errors.add(new BulkImportResponse.ValidationError(row, "source",
                "Source must be B2B, BENCH, or MARKET", candidate.getSource(), "ERROR"));
        }

        // Status validation — null treated as TRAINING
        if (candidate.getStatus() != null && !Arrays.asList("RFD", "WFD", "DOB", "TRAINING", "DEPLOYED").contains(candidate.getStatus())) {
            errors.add(new BulkImportResponse.ValidationError(row, "status",
                "Status must be RFD, WFD, DOB, TRAINING, or DEPLOYED", candidate.getStatus(), "ERROR"));
        }

        // Rating validation — optional
        if (!isBlank(candidate.getRating()) && !Arrays.asList("ASSET", "MEDIUM", "LIABILITY").contains(candidate.getRating())) {
            errors.add(new BulkImportResponse.ValidationError(row, "rating",
                "Rating must be ASSET, MEDIUM, or LIABILITY", candidate.getRating(), "ERROR"));
        }

        // Email validation
        if (isBlank(candidate.getOfficialEmail()) && isBlank(candidate.getPersonalEmail())) {
            errors.add(new BulkImportResponse.ValidationError(row, "email", 
                "At least one email (official or personal) is required", "", "ERROR"));
        }

        if (!isBlank(candidate.getOfficialEmail()) && !"NA".equalsIgnoreCase(candidate.getOfficialEmail()) && !EMAIL_PATTERN.matcher(candidate.getOfficialEmail()).matches()) {
            errors.add(new BulkImportResponse.ValidationError(row, "officialEmail", 
                "Invalid official email format", candidate.getOfficialEmail(), "ERROR"));
        }

        if (!isBlank(candidate.getPersonalEmail()) && !"NA".equalsIgnoreCase(candidate.getPersonalEmail()) && !EMAIL_PATTERN.matcher(candidate.getPersonalEmail()).matches()) {
            errors.add(new BulkImportResponse.ValidationError(row, "personalEmail", 
                "Invalid personal email format", candidate.getPersonalEmail(), "ERROR"));
        }

        // Phone validation
        if (!isBlank(candidate.getContactNumber()) && !PHONE_PATTERN.matcher(candidate.getContactNumber()).matches()) {
            errors.add(new BulkImportResponse.ValidationError(row, "contactNumber", 
                "Contact number must be 10 digits", candidate.getContactNumber(), "ERROR"));
        }

        // Skill set validation — optional, warn on unknown values
        if (!isBlank(candidate.getSkillSet())) {
            try {
                com.benchreadiness.auth.entity.SkillSet.valueOf(candidate.getSkillSet());
            } catch (IllegalArgumentException e) {
                errors.add(new BulkImportResponse.ValidationError(row, "skillSet",
                    "Unrecognized skill set, will be skipped: " + candidate.getSkillSet(), candidate.getSkillSet(), "WARNING"));
            }
        }

        // YOE validation
        if (candidate.getYoeActual() != null && (candidate.getYoeActual() < 0 || candidate.getYoeActual() > 50)) {
            errors.add(new BulkImportResponse.ValidationError(row, "yoeActual", 
                "YOE Actual must be between 0 and 50", candidate.getYoeActual().toString(), "WARNING"));
        }

        if (candidate.getYoePortrayed() != null && (candidate.getYoePortrayed() < 0 || candidate.getYoePortrayed() > 50)) {
            errors.add(new BulkImportResponse.ValidationError(row, "yoePortrayed", 
                "YOE Portrayed must be between 0 and 50", candidate.getYoePortrayed().toString(), "WARNING"));
        }

        // YOP validation
        int currentYear = LocalDateTime.now().getYear();
        if (candidate.getYop() != null && (candidate.getYop() < 1990 || candidate.getYop() > currentYear + 5)) {
            errors.add(new BulkImportResponse.ValidationError(row, "yop", 
                "Year of Passing must be between 1990 and " + (currentYear + 5), 
                candidate.getYop().toString(), "WARNING"));
        }
    }

    private void validateBusinessRules(List<BulkImportRequest.CandidateBulkData> candidates, 
                                      List<BulkImportResponse.ValidationError> errors) {
        // Check for duplicate emails within the file
        Set<String> seenEmails = new HashSet<>();
        
        for (BulkImportRequest.CandidateBulkData candidate : candidates) {
            String officialEmail = candidate.getOfficialEmail();
            String personalEmail = candidate.getPersonalEmail();
            
            if (!isBlank(officialEmail)) {
                if ("NA".equalsIgnoreCase(officialEmail)) {
                    // Skip NA values for duplicate checking
                } else {
                    if (seenEmails.contains(officialEmail.toLowerCase())) {
                        errors.add(new BulkImportResponse.ValidationError(candidate.getRowNumber(), "officialEmail", 
                            "Duplicate email in file", officialEmail, "ERROR"));
                    } else {
                        seenEmails.add(officialEmail.toLowerCase());
                        
                        // Check if email exists in database
                        if (userRepository.existsByEmailIgnoreCase(officialEmail)) {
                            errors.add(new BulkImportResponse.ValidationError(candidate.getRowNumber(), "officialEmail", 
                                "Email already exists in database", officialEmail, "WARNING"));
                        }
                    }
                }
            }
            
            if (!isBlank(personalEmail)) {
                if ("NA".equalsIgnoreCase(personalEmail)) {
                    // Skip NA values for duplicate checking
                } else {
                    if (seenEmails.contains(personalEmail.toLowerCase())) {
                        errors.add(new BulkImportResponse.ValidationError(candidate.getRowNumber(), "personalEmail", 
                            "Duplicate email in file", personalEmail, "ERROR"));
                    } else {
                        seenEmails.add(personalEmail.toLowerCase());
                        
                        // Check if email exists in database
                        if (userRepository.existsByEmailIgnoreCase(personalEmail)) {
                            errors.add(new BulkImportResponse.ValidationError(candidate.getRowNumber(), "personalEmail", 
                                "Email already exists in database", personalEmail, "WARNING"));
                        }
                    }
                }
            }
        }
    }

    private List<BulkImportResponse.CredentialPreview> generateCredentialPreviews(
            List<BulkImportRequest.CandidateBulkData> candidates) {
        List<BulkImportResponse.CredentialPreview> previews = new ArrayList<>();
        
        for (BulkImportRequest.CandidateBulkData candidate : candidates) {
            BulkImportResponse.CredentialPreview preview = new BulkImportResponse.CredentialPreview();
            preview.setRowNumber(candidate.getRowNumber());
            preview.setName(candidate.getName());
            preview.setSource(candidate.getSource());
            preview.setBatch(candidate.getBatch());
            
            // Determine username (prefer official email)
            String username = !isBlank(candidate.getOfficialEmail()) ? 
                candidate.getOfficialEmail() : candidate.getPersonalEmail();
            preview.setUsername(username);
            
            // Generate password: FirstName@2024
            String firstName = candidate.getName() != null ? 
                candidate.getName().split(" ")[0] : "User";
            String password = firstName + "@" + LocalDateTime.now().getYear();
            preview.setGeneratedPassword(password);
            
            previews.add(preview);
        }
        
        return previews;
    }

    private BulkImportResponse createErrorResponse(String sessionId, List<BulkImportResponse.ValidationError> errors) {
        BulkImportResponse response = new BulkImportResponse();
        response.setSessionId(sessionId);
        response.setTotalRows(0);
        response.setValidRows(0);
        response.setErrorRows(0);
        response.setErrors(errors);
        response.setCredentialPreviews(new ArrayList<>());
        response.setCanProceed(false);
        response.setCreatedAt(LocalDateTime.now());
        return response;
    }

    private boolean isEmptyRow(Row row) {
        for (Cell cell : row) {
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                return false;
            }
        }
        return true;
    }

    private String getCellValueAsString(Row row, Integer columnIndex) {
        if (columnIndex == null) return null;
        Cell cell = row.getCell(columnIndex);
        if (cell == null) return null;
        
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                return String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            default:
                return null;
        }
    }

    private Double getCellValueAsDouble(Row row, Integer columnIndex) {
        if (columnIndex == null) return null;
        Cell cell = row.getCell(columnIndex);
        if (cell == null) return null;
        
        try {
            if (cell.getCellType() == CellType.NUMERIC) {
                return cell.getNumericCellValue();
            } else if (cell.getCellType() == CellType.STRING) {
                String value = cell.getStringCellValue().trim();
                return isBlank(value) ? null : Double.parseDouble(value);
            }
        } catch (NumberFormatException e) {
            // Return null for invalid numbers
        }
        return null;
    }

    private Integer getCellValueAsInteger(Row row, Integer columnIndex) {
        Double doubleValue = getCellValueAsDouble(row, columnIndex);
        return doubleValue != null ? doubleValue.intValue() : null;
    }

    private boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }

    // Normalization methods to handle Excel data variations
    private String normalizeSource(String source) {
        if (isBlank(source)) return null;
        String normalized = source.trim().toUpperCase();
        switch (normalized) {
            case "BENCH":
                return "BENCH";
            case "B2B":
                return "B2B";
            case "MARKET":
                return "MARKET";
            default:
                return source; // Return original for validation error
        }
    }

    private String normalizeStatus(String status) {
        if (isBlank(status)) return null;
        String normalized = status.trim().toUpperCase();
        switch (normalized) {
            case "RFD": return "RFD";
            case "WFD": return "WFD";
            case "DOB": return "DOB";
            case "TRAINING": return "TRAINING";
            case "DEPLOYED": return "DEPLOYED";
            default: return status;
        }
    }

    private String normalizeRating(String rating) {
        if (isBlank(rating)) return null;
        String normalized = rating.trim().toUpperCase();
        switch (normalized) {
            case "ASSET":
                return "ASSET";
            case "MEDIUM":
                return "MEDIUM";
            case "LIABILITY":
            case "LIALIBILITY": // Common typo
                return "LIABILITY";
            default:
                return rating; // Return original for validation error
        }
    }

    private String normalizeSkillSet(String skillSet) {
        if (isBlank(skillSet)) return null;
        String normalized = skillSet.trim().toUpperCase().replace(" ", "_").replace("+", "_");
        switch (normalized) {
            case "JAVA_SB":
            case "JAVASB":
            case "JAVA_SPRINGBOOT":
                return "JAVA_SB";
            case "JFSR":
                return "JFSR";
            case "REACT_JS":
            case "REACTJS":
            case "REACT":
                return "REACT_JS";
            case "ANGULAR":
                return "ANGULAR";
            case "PYTHON":
                return "PYTHON";
            case "QA_ENGINEER":
            case "QA":
            case "QAENGINEER":
                return "QA_ENGINEER";
            case "PLAYWRIGHT_AUTOMATION":
            case "PLAYWRIGHT":
            case "PLAYWRIGHTAUTOMATION":
                return "PLAYWRIGHT_AUTOMATION";
            default:
                return skillSet; // Return original for validation error
        }
    }

    private String nullIfBlankOrNA(String value) {
        if (isBlank(value) || "NA".equalsIgnoreCase(value.trim())) return null;
        return value;
    }

    private String normalizeEmail(String email) {
        if (isBlank(email) || "NA".equalsIgnoreCase(email.trim())) {
            return null; // Convert NA to null
        }
        return email.trim().toLowerCase();
    }
}