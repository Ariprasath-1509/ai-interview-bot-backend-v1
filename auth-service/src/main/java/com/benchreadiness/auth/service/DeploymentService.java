package com.benchreadiness.auth.service;

import com.benchreadiness.auth.dto.DeploymentBulkRequest;
import com.benchreadiness.auth.entity.CandidateStatus;
import com.benchreadiness.auth.entity.User;
import com.benchreadiness.auth.repository.UserRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@Service
public class DeploymentService {

    private final UserRepository userRepository;

    public DeploymentService(UserRepository userRepository) {
        this.userRepository = userRepository;
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
                    // Parse row data
                    String empId = getCellValueAsString(row.getCell(0));
                    String email = getCellValueAsString(row.getCell(1));
                    String clientName = getCellValueAsString(row.getCell(2));
                    LocalDate deployedDate = getCellValueAsDate(row.getCell(3));
                    String mentor = getCellValueAsString(row.getCell(4));

                    // Validate required fields (empId is optional, can be null)
                    if (email == null || email.trim().isEmpty()) {
                        rowResult.put("status", "FAILURE");
                        rowResult.put("message", "Missing required field: Email");
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

                    // Find candidate by email (match against officialEmail OR personalEmail)
                    Optional<User> candidateOpt = userRepository.findByOfficialEmailOrPersonalEmail(email, email);
                    
                    if (candidateOpt.isEmpty()) {
                        rowResult.put("status", "FAILURE");
                        rowResult.put("email", email);
                        rowResult.put("message", "Candidate not found in system");
                        failureCount++;
                        details.add(rowResult);
                        continue;
                    }

                    User candidate = candidateOpt.get();
                    
                    // Check if already deployed
                    boolean alreadyDeployed = candidate.getCandidateStatus() == CandidateStatus.DEPLOYED;
                    
                    // Update deployment fields (empId can be null)
                    candidate.setEmpId(empId != null && !empId.trim().isEmpty() ? empId.trim() : null);
                    candidate.setDeployedClientName(clientName.trim());
                    candidate.setDeployedDate(deployedDate);
                    candidate.setMentor(mentor != null && !mentor.trim().isEmpty() ? mentor.trim() : null);
                    candidate.setCandidateStatus(CandidateStatus.DEPLOYED);
                    
                    userRepository.save(candidate);

                    rowResult.put("empId", empId);
                    rowResult.put("email", email);
                    rowResult.put("name", candidate.getName());
                    
                    if (alreadyDeployed) {
                        rowResult.put("status", "WARNING");
                        rowResult.put("message", "Candidate already deployed, data updated");
                        warningCount++;
                    } else {
                        rowResult.put("status", "SUCCESS");
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

    @Transactional
    public User updateDeployment(String candidateId, String empId, String clientName, LocalDate deployedDate, String mentor) {
        User candidate = userRepository.findById(candidateId)
                .orElseThrow(() -> new RuntimeException("Candidate not found"));
        
        candidate.setEmpId(empId);
        candidate.setDeployedClientName(clientName);
        candidate.setDeployedDate(deployedDate);
        candidate.setMentor(mentor);
        candidate.setCandidateStatus(CandidateStatus.DEPLOYED);
        
        return userRepository.save(candidate);
    }

    @Transactional
    public User clearDeployment(String candidateId) {
        User candidate = userRepository.findById(candidateId)
                .orElseThrow(() -> new RuntimeException("Candidate not found"));
        
        candidate.setEmpId(null);
        candidate.setDeployedClientName(null);
        candidate.setDeployedDate(null);
        candidate.setMentor(null);
        // Optionally reset status to previous state (e.g., RFD)
        candidate.setCandidateStatus(CandidateStatus.RFD);
        
        return userRepository.save(candidate);
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
