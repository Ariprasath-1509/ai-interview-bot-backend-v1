package com.benchreadiness.auth.service;

import com.benchreadiness.auth.dto.DeploymentBulkRequest;
import com.benchreadiness.auth.entity.*;
import com.benchreadiness.auth.repository.DeploymentHistoryRepository;
import com.benchreadiness.auth.repository.UserRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@Service
public class DeploymentService {

    private final UserRepository userRepository;
    private final DeploymentHistoryRepository deploymentHistoryRepository;
    private final PasswordEncoder passwordEncoder;

    public DeploymentService(UserRepository userRepository, 
                           DeploymentHistoryRepository deploymentHistoryRepository,
                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.deploymentHistoryRepository = deploymentHistoryRepository;
        this.passwordEncoder = passwordEncoder;
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
                
                totalRows++;
                Map<String, Object> rowResult = new HashMap<>();
                rowResult.put("row", i);

                try {
                    // Parse row data - matching actual Excel format:
                    // No. | Emp ID | Name | Contact Number | E Mail ID | Personal Mail ID | YOE | Technology | Client Name | Deployed Date | Mentor
                    String empId = getCellValueAsString(row.getCell(1));  // Column B: Emp ID
                    String name = getCellValueAsString(row.getCell(2));    // Column C: Name
                    String contactNumber = getCellValueAsString(row.getCell(3)); // Column D: Contact Number
                    String email = getCellValueAsString(row.getCell(4));   // Column E: E Mail ID
                    String personalEmail = getCellValueAsString(row.getCell(5)); // Column F: Personal Mail ID
                    String yoeStr = getCellValueAsString(row.getCell(6));  // Column G: YOE
                    String technology = getCellValueAsString(row.getCell(7)); // Column H: Technology
                    String clientName = getCellValueAsString(row.getCell(8)); // Column I: Client Name
                    LocalDate deployedDate = getCellValueAsDate(row.getCell(9)); // Column J: Deployed Date
                    String mentor = getCellValueAsString(row.getCell(10)); // Column K: Mentor

                    // Validate required fields
                    if (email == null || email.trim().isEmpty()) {
                        rowResult.put("status", "FAILURE");
                        rowResult.put("message", "Missing required field: Email");
                        failureCount++;
                        details.add(rowResult);
                        continue;
                    }
                    if (name == null || name.trim().isEmpty()) {
                        rowResult.put("status", "FAILURE");
                        rowResult.put("message", "Missing required field: Name");
                        failureCount++;
                        details.add(rowResult);
                        continue;
                    }
                    if (clientName == null || clientName.trim().isEmpty()) {
                        rowResult.put("status", "FAILURE");
                        rowResult.put("message", "Missing required field: Client Name");
                        failureCount++;
                        details.add(rowResult);
                        continue;
                    }
                    if (deployedDate == null) {
                        rowResult.put("status", "FAILURE");
                        rowResult.put("message", "Missing required field: Deployed Date");
                        failureCount++;
                        details.add(rowResult);
                        continue;
                    }

                    // Find or create candidate
                    Optional<User> candidateOpt = userRepository.findByOfficialEmailOrPersonalEmail(email, email);
                    User candidate;
                    boolean isNewCandidate = false;
                    
                    if (candidateOpt.isEmpty()) {
                        // Create new candidate
                        candidate = new User();
                        candidate.setName(name.trim());
                        candidate.setEmail(email.trim());
                        candidate.setOfficialEmail(email.trim());
                        if (personalEmail != null && !personalEmail.trim().isEmpty()) {
                            candidate.setPersonalEmail(personalEmail.trim());
                        }
                        if (contactNumber != null && !contactNumber.trim().isEmpty()) {
                            candidate.setContactNumber(contactNumber.trim());
                        }
                        
                        // Parse YOE
                        if (yoeStr != null && !yoeStr.trim().isEmpty()) {
                            try {
                                BigDecimal yoe = new BigDecimal(yoeStr.trim());
                                candidate.setYoeActual(yoe);
                                candidate.setYoePortrayed(yoe);
                            } catch (NumberFormatException e) {
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
                        candidate.setPassword(passwordEncoder.encode(defaultPassword));
                        
                        candidate.setRole(UserRole.CANDIDATE);
                        candidate.setSource(CandidateSource.B2B);
                        candidate.setCandidateStatus(CandidateStatus.DEPLOYED);
                        candidate.setNoOfInterviews(0);
                        candidate.setSystemInterviewCount(0);
                        
                        candidate = userRepository.save(candidate);
                        isNewCandidate = true;
                    } else {
                        candidate = candidateOpt.get();
                    }
                    
                    // Check if already deployed
                    boolean alreadyDeployed = candidate.getCandidateStatus() == CandidateStatus.DEPLOYED;
                    
                    // End previous active deployment if exists
                    Optional<DeploymentHistory> activeDeployment = deploymentHistoryRepository.findActiveDeploymentByCandidateId(candidate.getId());
                    if (activeDeployment.isPresent()) {
                        DeploymentHistory prevDeployment = activeDeployment.get();
                        prevDeployment.setEndDate(deployedDate.minusDays(1)); // End previous day
                        prevDeployment.setStatus("COMPLETED");
                        deploymentHistoryRepository.save(prevDeployment);
                    }
                    
                    // Update candidate deployment fields
                    candidate.setEmpId(empId != null && !empId.trim().isEmpty() ? empId.trim() : null);
                    candidate.setDeployedClientName(clientName.trim());
                    candidate.setDeployedDate(deployedDate);
                    candidate.setMentor(mentor != null && !mentor.trim().isEmpty() ? mentor.trim() : null);
                    candidate.setCandidateStatus(CandidateStatus.DEPLOYED);
                    userRepository.save(candidate);
                    
                    // Create deployment history record
                    DeploymentHistory history = new DeploymentHistory();
                    history.setCandidateId(candidate.getId());
                    history.setEmpId(empId != null && !empId.trim().isEmpty() ? empId.trim() : null);
                    history.setClientName(clientName.trim());
                    history.setDeployedDate(deployedDate);
                    history.setEndDate(null); // Currently active
                    history.setMentor(mentor != null && !mentor.trim().isEmpty() ? mentor.trim() : null);
                    history.setStatus("ACTIVE");
                    deploymentHistoryRepository.save(history);

                    rowResult.put("empId", empId);
                    rowResult.put("email", email);
                    rowResult.put("name", candidate.getName());
                    
                    if (isNewCandidate) {
                        rowResult.put("status", "SUCCESS");
                        rowResult.put("message", "New candidate created and deployed");
                        successCount++;
                    } else if (alreadyDeployed) {
                        rowResult.put("status", "WARNING");
                        rowResult.put("message", "Candidate already deployed, data updated");
                        warningCount++;
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
        candidate.setCandidateStatus(CandidateStatus.RFD);
        
        return userRepository.save(candidate);
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
        candidate.setCandidateStatus(CandidateStatus.DEPLOYED);
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

    private SkillSet mapTechnologyToSkillSet(String technology) {
        String tech = technology.toLowerCase();
        if (tech.contains("java") || tech.contains("spring")) {
            return SkillSet.JAVA_SB;
        } else if (tech.contains("react")) {
            return SkillSet.REACT_JS;
        } else if (tech.contains("full") || tech.contains("jfsr")) {
            return SkillSet.JFSR;
        }
        return SkillSet.JAVA_SB; // Default
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
