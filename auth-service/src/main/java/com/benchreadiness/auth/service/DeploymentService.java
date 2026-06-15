package com.benchreadiness.auth.service;

import com.benchreadiness.auth.dto.DeploymentBulkRequest;
import com.benchreadiness.auth.entity.DeploymentHistory;
import com.benchreadiness.auth.entity.User;
import com.benchreadiness.auth.entity.UserRole;
import com.benchreadiness.auth.repository.DeploymentHistoryRepository;
import com.benchreadiness.auth.repository.UserRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@Service
public class DeploymentService {

    private final UserRepository userRepository;
    private final DeploymentHistoryRepository deploymentHistoryRepository;
    private final PasswordService passwordService;
    private final AuditService auditService;

    public DeploymentService(UserRepository userRepository, 
                           DeploymentHistoryRepository deploymentHistoryRepository,
                           PasswordService passwordService,
                           AuditService auditService) {
        this.userRepository = userRepository;
        this.deploymentHistoryRepository = deploymentHistoryRepository;
        this.passwordService = passwordService;
        this.auditService = auditService;
    }

    @Transactional
    public Map<String, Object> bulkImportDeployments(MultipartFile file) throws IOException {
        List<Map<String, Object>> details = new ArrayList<>();
        int successCount = 0;
        int warningCount = 0;
        int failureCount = 0;
        int totalRows = 0;

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            
            // Skip header row
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                
                // Skip completely empty rows
                boolean isEmptyRow = true;
                for (int cellNum = 0; cellNum < 11; cellNum++) {
                    Cell cell = row.getCell(cellNum);
                    if (cell != null && cell.getCellType() != CellType.BLANK) {
                        String value = getCellValueAsString(cell);
                        if (value != null && !value.trim().isEmpty()) {
                            isEmptyRow = false;
                            break;
                        }
                    }
                }
                if (isEmptyRow) continue;
                
                totalRows++;
                Map<String, Object> rowResult = new HashMap<>();
                rowResult.put("row", i);

                try {
                    // Detect format by checking first non-empty cell
                    // Format 1: No. | Emp ID | Name | Contact | Official Email | Personal Email | YOE | Technology | Client | Date | Mentor (11 columns)
                    // Format 2: Name | Contact | Official Email | Personal Email | YOE | Technology | Client | Date | Mentor (9 columns)
                    
                    int columnOffset = 0;
                    String firstCell = getCellValueAsString(row.getCell(0));
                    
                    // If first cell is a number (row number), we have Format 1 with No. and Emp ID
                    if (firstCell != null && firstCell.matches("\\d+")) {
                        columnOffset = 2; // Skip No. and Emp ID columns
                    }
                    
                    // Parse row data based on detected format
                    String empId = columnOffset == 2 ? getCellValueAsString(row.getCell(1)) : null;  // Column B: Emp ID (if exists)
                    String name = getCellValueAsString(row.getCell(columnOffset));    // Name
                    String contactNumber = getCellValueAsString(row.getCell(columnOffset + 1)); // Contact Number
                    String officialEmail = getCellValueAsString(row.getCell(columnOffset + 2));   // Official Mail ID
                    String personalEmail = getCellValueAsString(row.getCell(columnOffset + 3)); // Personal Mail ID
                    String yoeStr = getCellValueAsString(row.getCell(columnOffset + 4));  // YOE
                    String technology = getCellValueAsString(row.getCell(columnOffset + 5)); // Technology
                    String clientName = getCellValueAsString(row.getCell(columnOffset + 6)); // Client Name
                    LocalDate deployedDate = getCellValueAsDate(row.getCell(columnOffset + 7)); // Deployed Date
                    String mentor = getCellValueAsString(row.getCell(columnOffset + 8)); // Mentor
                    
                    // Treat "NA" as empty/null
                    if (officialEmail != null && (officialEmail.trim().isEmpty() || officialEmail.trim().equalsIgnoreCase("NA"))) {
                        officialEmail = null;
                    }
                    if (personalEmail != null && (personalEmail.trim().isEmpty() || personalEmail.trim().equalsIgnoreCase("NA"))) {
                        personalEmail = null;
                    }
                    
                    // Use official email as primary, fallback to personal email
                    String email = (officialEmail != null) ? officialEmail.trim() : (personalEmail != null ? personalEmail.trim() : null);

                    // Validate required fields
                    if (email == null || email.trim().isEmpty()) {
                        rowResult.put("status", "FAILURE");
                        rowResult.put("message", "Missing required field: Official Mail ID or Personal Mail ID (Row " + (i + 1) + ")");
                        rowResult.put("rowNumber", i + 1);
                        if (name != null) rowResult.put("name", name);
                        if (empId != null) rowResult.put("empId", empId);
                        if (contactNumber != null) rowResult.put("contactNumber", contactNumber);
                        failureCount++;
                        details.add(rowResult);
                        continue;
                    }
                    if (name == null || name.trim().isEmpty()) {
                        rowResult.put("status", "FAILURE");
                        rowResult.put("message", "Missing required field: Name (Row " + (i + 1) + ")");
                        rowResult.put("email", email);
                        rowResult.put("rowNumber", i + 1);
                        if (empId != null) rowResult.put("empId", empId);
                        failureCount++;
                        details.add(rowResult);
                        continue;
                    }
                    if (clientName == null || clientName.trim().isEmpty()) {
                        rowResult.put("status", "FAILURE");
                        rowResult.put("message", "Missing required field: Client Name (Row " + (i + 1) + ")");
                        rowResult.put("email", email);
                        rowResult.put("name", name);
                        rowResult.put("rowNumber", i + 1);
                        if (empId != null) rowResult.put("empId", empId);
                        failureCount++;
                        details.add(rowResult);
                        continue;
                    }
                    if (deployedDate == null) {
                        rowResult.put("status", "FAILURE");
                        rowResult.put("message", "Missing required field: Deployed Date (format: DD-MMM-YY or YYYY-MM-DD) (Row " + (i + 1) + ")");
                        rowResult.put("email", email);
                        rowResult.put("name", name);
                        rowResult.put("clientName", clientName);
                        rowResult.put("rowNumber", i + 1);
                        if (empId != null) rowResult.put("empId", empId);
                        failureCount++;
                        details.add(rowResult);
                        continue;
                    }

                    // Find or create candidate
                    Optional<User> candidateOpt = userRepository.findByOfficialEmailOrPersonalEmail(email, email);
                    User candidate;
                    boolean isNewCandidate = false;
                    boolean hadPreviousDeployment = false;
                    boolean isHistoricalDeployment = false;
                    
                    if (candidateOpt.isEmpty()) {
                        // Create new candidate
                        candidate = new User();
                        candidate.setName(name.trim());
                        candidate.setEmail(email.trim());
                        
                        // Set official and personal emails (only if not "NA")
                        if (officialEmail != null) {
                            candidate.setOfficialEmail(officialEmail.trim());
                        }
                        if (personalEmail != null) {
                            candidate.setPersonalEmail(personalEmail.trim());
                        }
                        
                        if (contactNumber != null && !contactNumber.trim().isEmpty()) {
                            candidate.setContactNumber(contactNumber.trim());
                        }
                        
                        // Parse YOE (handles formats like "2.11 Yrs" meaning 2 years 11 months)
                        if (yoeStr != null && !yoeStr.trim().isEmpty()) {
                            try {
                                BigDecimal yoe = parseYearsOfExperience(yoeStr.trim());
                                if (yoe != null) {
                                    candidate.setYoeActual(yoe);
                                    candidate.setYoePortrayed(yoe);
                                }
                            } catch (Exception e) {
                                // Ignore invalid YOE
                            }
                        }
                        
                        // Map technology to skill set
                        if (technology != null && !technology.trim().isEmpty()) {
                            candidate.setSkillSet(mapTechnologyToSkillSet(technology.trim()));
                        }
                        
                        // Set default password (email username + @123)
                        String username = email.split("@")[0];
                        String defaultPassword = username + "@123";
                        candidate.setPassword(passwordService.encode(defaultPassword));
                        
                        candidate.setRole(UserRole.CANDIDATE);
                        candidate.setSource("B2B");
                        candidate.setCandidateStatus("DEPLOYED");
                        candidate.setNoOfInterviews(0);
                        candidate.setSystemInterviewCount(0);
                        
                        // Set empId only if provided
                        if (empId != null && !empId.trim().isEmpty()) {
                            candidate.setEmpId(empId.trim());
                        }
                        
                        candidate = userRepository.save(candidate);
                        isNewCandidate = true;
                    } else {
                        candidate = candidateOpt.get();
                        hadPreviousDeployment = "DEPLOYED".equals(candidate.getCandidateStatus());
                        
                        // Check if this is historical data (candidate is RFD/WFD/DOB but we're importing deployment history)
                        if ("RFD".equals(candidate.getCandidateStatus())
                            || "WFD".equals(candidate.getCandidateStatus())
                            || "DOB".equals(candidate.getCandidateStatus())) {
                            isHistoricalDeployment = true;
                        }
                    }
                    
                    // End previous active deployment if exists
                    Optional<DeploymentHistory> activeDeployment = deploymentHistoryRepository.findActiveDeploymentByCandidateId(candidate.getId());
                    if (activeDeployment.isPresent()) {
                        DeploymentHistory prevDeployment = activeDeployment.get();
                        prevDeployment.setEndDate(deployedDate.minusDays(1)); // End previous day
                        prevDeployment.setStatus("COMPLETED");
                        deploymentHistoryRepository.save(prevDeployment);
                    }
                    
                    // Update candidate deployment fields ONLY if not historical
                    if (!isHistoricalDeployment) {
                        // Only update empId if provided and different from current value
                        if (empId != null && !empId.trim().isEmpty()) {
                            String newEmpId = empId.trim();
                            // Only update if different from current empId
                            if (!newEmpId.equals(candidate.getEmpId())) {
                                // Check if empId is already used by another candidate
                                Optional<User> existingEmpId = userRepository.findByEmpId(newEmpId);
                                if (existingEmpId.isPresent() && !existingEmpId.get().getId().equals(candidate.getId())) {
                                    rowResult.put("status", "FAILURE");
                                    rowResult.put("message", "Emp ID " + newEmpId + " is already assigned to another candidate: " + existingEmpId.get().getName() + " (" + existingEmpId.get().getEmail() + ")");
                                    rowResult.put("email", email);
                                    rowResult.put("name", name);
                                    rowResult.put("empId", empId);
                                    rowResult.put("rowNumber", i + 1);
                                    failureCount++;
                                    details.add(rowResult);
                                    continue;
                                }
                                candidate.setEmpId(newEmpId);
                            }
                        }
                        candidate.setDeployedClientName(clientName.trim());
                        candidate.setDeployedDate(deployedDate);
                        candidate.setMentor(mentor != null && !mentor.trim().isEmpty() ? mentor.trim() : null);
                        candidate.setCandidateStatus("DEPLOYED");
                        userRepository.save(candidate);
                    }
                    
                    // Create deployment history record
                    DeploymentHistory history = new DeploymentHistory();
                    history.setCandidateId(candidate.getId());
                    history.setEmpId(empId != null && !empId.trim().isEmpty() ? empId.trim() : null);
                    history.setClientName(clientName.trim());
                    history.setDeployedDate(deployedDate);
                    
                    // If historical deployment, mark as COMPLETED with end date as today
                    if (isHistoricalDeployment) {
                        history.setEndDate(LocalDate.now());
                        history.setStatus("COMPLETED");
                    } else {
                        history.setEndDate(null); // Currently active
                        history.setStatus("ACTIVE");
                    }
                    
                    history.setMentor(mentor != null && !mentor.trim().isEmpty() ? mentor.trim() : null);
                    deploymentHistoryRepository.save(history);

                    rowResult.put("empId", empId);
                    rowResult.put("email", email);
                    rowResult.put("name", candidate.getName());
                    rowResult.put("clientName", clientName);
                    rowResult.put("deployedDate", deployedDate.toString());
                    rowResult.put("mentor", mentor);
                    rowResult.put("rowNumber", i + 1);
                    
                    if (isNewCandidate) {
                        rowResult.put("status", "SUCCESS");
                        rowResult.put("message", "New candidate created and deployed successfully");
                        successCount++;
                    } else if (isHistoricalDeployment) {
                        rowResult.put("status", "SUCCESS");
                        rowResult.put("message", "Deployment history added (candidate status unchanged)");
                        successCount++;
                    } else if (hadPreviousDeployment) {
                        rowResult.put("status", "SUCCESS");
                        rowResult.put("message", "Deployment history updated - moved to new client");
                        successCount++;
                    } else {
                        rowResult.put("status", "SUCCESS");
                        rowResult.put("message", "Candidate deployed successfully");
                        successCount++;
                    }
                    
                    details.add(rowResult);

                } catch (Exception e) {
                    rowResult.put("status", "FAILURE");
                    rowResult.put("message", "Error processing row: " + e.getMessage());
                    failureCount++;
                    details.add(rowResult);
                }
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("totalRows", totalRows);
        result.put("successCount", successCount);
        result.put("warningCount", warningCount);
        result.put("failureCount", failureCount);
        result.put("details", details);
        
        return result;
    }

    public List<User> getDeployedCandidates() {
        return userRepository.findDeployedCandidates();
    }

    public List<DeploymentHistory> getDeploymentHistory(String candidateId) {
        return deploymentHistoryRepository.findByCandidateIdOrderByDeployedDateDesc(candidateId);
    }

    public List<DeploymentHistory> getAllActiveDeployments() {
        return deploymentHistoryRepository.findAllActiveDeployments();
    }

    public List<DeploymentHistory> getAllCompletedDeployments() {
        return deploymentHistoryRepository.findAllCompletedDeployments();
    }

    @Transactional
    public User endDeployment(String candidateId, LocalDate endDate) {
        User candidate = userRepository.findById(candidateId)
                .orElseThrow(() -> new RuntimeException("Candidate not found"));
        
        String oldStatus = candidate.getCandidateStatus() != null ? candidate.getCandidateStatus() : "N/A";
        String oldClient = candidate.getDeployedClientName();
        
        // End active deployment in history
        Optional<DeploymentHistory> activeDeployment = deploymentHistoryRepository.findActiveDeploymentByCandidateId(candidateId);
        if (activeDeployment.isPresent()) {
            DeploymentHistory history = activeDeployment.get();
            history.setEndDate(endDate != null ? endDate : LocalDate.now());
            history.setStatus("COMPLETED");
            deploymentHistoryRepository.save(history);
        }
        
        // Clear deployment fields and move back to B2B (RFD status)
        candidate.setEmpId(null);
        candidate.setDeployedClientName(null);
        candidate.setDeployedDate(null);
        candidate.setMentor(null);
        candidate.setCandidateStatus("RFD");
        
        User saved = userRepository.save(candidate);
        
        // Log audit trail
        auditService.record("system", "System", "ADMIN", "DEPLOYMENT_ENDED", "DEPLOYMENT",
                candidateId, String.format("%s - %s → RFD", candidate.getName(), oldClient),
                oldStatus, "RFD");
        
        return saved;
    }

    @Transactional
    public User updateDeployment(String candidateId, String empId, String clientName, LocalDate deployedDate, String mentor) {
        User candidate = userRepository.findById(candidateId)
                .orElseThrow(() -> new RuntimeException("Candidate not found"));
        
        // End previous active deployment if exists
        Optional<DeploymentHistory> activeDeployment = deploymentHistoryRepository.findActiveDeploymentByCandidateId(candidateId);
        if (activeDeployment.isPresent()) {
            DeploymentHistory prevDeployment = activeDeployment.get();
            prevDeployment.setEndDate(deployedDate.minusDays(1));
            prevDeployment.setStatus("COMPLETED");
            deploymentHistoryRepository.save(prevDeployment);
        }
        
        candidate.setEmpId(empId);
        candidate.setDeployedClientName(clientName);
        candidate.setDeployedDate(deployedDate);
        candidate.setMentor(mentor);
        candidate.setCandidateStatus("DEPLOYED");
        userRepository.save(candidate);
        
        // Create new deployment history record
        DeploymentHistory history = new DeploymentHistory();
        history.setCandidateId(candidateId);
        history.setEmpId(empId);
        history.setClientName(clientName);
        history.setDeployedDate(deployedDate);
        history.setEndDate(null);
        history.setMentor(mentor);
        history.setStatus("ACTIVE");
        deploymentHistoryRepository.save(history);
        
        return candidate;
    }

    @Transactional
    public User clearDeployment(String candidateId) {
        return endDeployment(candidateId, LocalDate.now());
    }

    private String mapTechnologyToSkillSet(String technology) {
        String tech = technology.toLowerCase();
        if (tech.contains("java") || tech.contains("spring")) {
            return "JAVA_SB";
        } else if (tech.contains("react")) {
            return "REACT_JS";
        } else if (tech.contains("full") || tech.contains("jfsr")) {
            return "JFSR";
        }
        return "JAVA_SB";
    }

    /**
     * Parse YOE in format "2.11 Yrs" (2 years 11 months) or "3.5 Yrs" (3.5 years)
     * Converts to decimal years: 2.11 → 2.92 years (2 + 11/12)
     */
    private BigDecimal parseYearsOfExperience(String yoeStr) {
        if (yoeStr == null || yoeStr.trim().isEmpty()) {
            return null;
        }
        
        // Remove "Yrs", "Year", "years" etc.
        String cleaned = yoeStr.replaceAll("(?i)(yrs?|years?)\\s*$", "").trim();
        
        if (cleaned.isEmpty()) {
            return null;
        }
        
        try {
            // Check if format is "X.Y" where Y > 12 (indicates months, not decimal)
            if (cleaned.contains(".")) {
                String[] parts = cleaned.split("\\.");
                if (parts.length == 2) {
                    int years = Integer.parseInt(parts[0]);
                    int months = Integer.parseInt(parts[1]);
                    
                    // If months > 12, it's likely a typo, treat as decimal
                    if (months <= 12) {
                        // Convert to decimal: years + (months / 12)
                        double totalYears = years + (months / 12.0);
                        return BigDecimal.valueOf(totalYears).setScale(2, RoundingMode.HALF_UP);
                    }
                }
            }
            
            // Fallback: parse as decimal number
            return new BigDecimal(cleaned).setScale(2, RoundingMode.HALF_UP);
            
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) return null;
        
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getLocalDateTimeCellValue().toString();
                }
                return String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return null;
        }
    }

    private LocalDate getCellValueAsDate(Cell cell) {
        if (cell == null) return null;
        
        try {
            if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                return cell.getDateCellValue().toInstant()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate();
            } else if (cell.getCellType() == CellType.STRING) {
                // Try parsing string as date
                return LocalDate.parse(cell.getStringCellValue());
            }
        } catch (Exception e) {
            return null;
        }
        
        return null;
    }

}
