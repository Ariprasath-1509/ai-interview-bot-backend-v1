package com.benchreadiness.interview.service;

import com.benchreadiness.interview.client.AuthServiceClient;
import com.benchreadiness.interview.dto.AutoFillPreview;
import com.benchreadiness.interview.dto.ClientDTO;
import com.benchreadiness.interview.dto.CreateInterviewRequest;
import com.benchreadiness.interview.entity.Interview;
import com.benchreadiness.interview.entity.InterviewMode;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class EnhancedInterviewService {
    
    private final InterviewService interviewService;
    private final ClientService clientService;
    private final AuthServiceClient authServiceClient;
    
    public EnhancedInterviewService(InterviewService interviewService, ClientService clientService, AuthServiceClient authServiceClient) {
        this.interviewService = interviewService;
        this.clientService = clientService;
        this.authServiceClient = authServiceClient;
    }
    
    public AutoFillPreview previewAutoFill(String candidateId, String clientId) {
        AutoFillPreview preview = new AutoFillPreview();
        boolean candidateDataFound = false;
        boolean clientDataFound = false;
        
        // Preview candidate data
        if (candidateId != null && !candidateId.isBlank()) {
            try {
                Map<String, Object> candidate = authServiceClient.getUserById(candidateId);
                if (candidate != null) {
                    candidateDataFound = true;
                    
                    // Set candidate data
                    String email = (String) candidate.get("email");
                    if (email == null || email.isBlank()) {
                        email = (String) candidate.get("officialEmail");
                    }
                    if (email == null || email.isBlank()) {
                        email = (String) candidate.get("personalEmail");
                    }
                    preview.setEngineerEmail(email);
                    preview.setEngineerName((String) candidate.get("name"));
                    preview.setResumeSummary(generateResumeSummary(candidate));
                    
                    // Suggest interview mode based on experience
                    Double yoeActual = candidate.get("yoeActual") != null ? ((Number) candidate.get("yoeActual")).doubleValue() : null;
                    if (yoeActual != null) {
                        if (yoeActual >= 8) {
                            preview.setSuggestedMode(InterviewMode.L4);
                        } else if (yoeActual >= 5) {
                            preview.setSuggestedMode(InterviewMode.L3);
                        } else if (yoeActual >= 3) {
                            preview.setSuggestedMode(InterviewMode.L2);
                        } else if (yoeActual >= 1) {
                            preview.setSuggestedMode(InterviewMode.L1);
                        } else {
                            preview.setSuggestedMode(InterviewMode.SCREENING);
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Failed to preview candidate data: " + e.getMessage());
            }
        }
        
        // Preview client data
        if (clientId != null && !clientId.isBlank()) {
            try {
                ClientDTO client = clientService.getClientById(UUID.fromString(clientId));
                if (client != null) {
                    clientDataFound = true;
                    
                    preview.setJdTitle(client.getJdRole());
                    preview.setJdText(client.getJdDescription());
                    preview.setClientName(client.getClientName());
                    preview.setNotes("Client: " + client.getClientName() + " | Positions: " + client.getPositionsVacant());
                    
                    // Generate focus areas based on JD
                    CreateInterviewRequest tempRequest = new CreateInterviewRequest();
                    tempRequest.setJdTitle(client.getJdRole());
                    tempRequest.setJdText(client.getJdDescription());
                    tempRequest.setInterviewMode(preview.getSuggestedMode());
                    preview.setFocusAreas(generateFocusAreas(tempRequest));
                    
                    // Update suggested mode based on JD if not set by candidate
                    if (preview.getSuggestedMode() == null) {
                        tempRequest.setClientId(clientId);
                        preview.setSuggestedMode(suggestInterviewMode(tempRequest));
                    }
                }
            } catch (Exception e) {
                System.err.println("Failed to preview client data: " + e.getMessage());
            }
        }
        
        // Set default values if no data found
        if (preview.getSuggestedMode() == null) {
            preview.setSuggestedMode(InterviewMode.SCREENING);
        }
        if (preview.getFocusAreas() == null) {
            CreateInterviewRequest tempRequest = new CreateInterviewRequest();
            tempRequest.setInterviewMode(preview.getSuggestedMode());
            preview.setFocusAreas(generateFocusAreas(tempRequest));
        }
        
        preview.setCandidateDataFound(candidateDataFound);
        preview.setClientDataFound(clientDataFound);
        
        return preview;
    }
    
    public Interview createInterviewWithAutoFill(CreateInterviewRequest request, String userId) throws Exception {
        // Auto-fill candidate data if candidateId is provided
        if (request.getCandidateId() != null && !request.getCandidateId().isBlank()) {
            autoFillCandidateData(request);
        }
        
        // Auto-fill client/JD data if clientId is provided
        if (request.getClientId() != null && !request.getClientId().isBlank()) {
            autoFillClientData(request);
        }
        
        // Auto-suggest interview mode based on candidate experience and JD requirements
        if (request.getInterviewMode() == null) {
            request.setInterviewMode(suggestInterviewMode(request));
        }
        
        // Generate focus areas if not provided
        if (request.getFocusAreas() == null || request.getFocusAreas().isBlank()) {
            request.setFocusAreas(generateFocusAreas(request));
        }
        
        return interviewService.createInterview(request, userId);
    }
    
    private void autoFillCandidateData(CreateInterviewRequest request) {
        try {
            // Get candidate details from auth-service
            Map<String, Object> candidate = authServiceClient.getUserById(request.getCandidateId());
            
            if (candidate != null) {
                // Auto-fill engineer email and name
                if (request.getEngineerEmail() == null || request.getEngineerEmail().isBlank()) {
                    String email = (String) candidate.get("email");
                    if (email == null || email.isBlank()) {
                        email = (String) candidate.get("officialEmail");
                    }
                    if (email == null || email.isBlank()) {
                        email = (String) candidate.get("personalEmail");
                    }
                    request.setEngineerEmail(email);
                }
                
                if (request.getEngineerName() == null || request.getEngineerName().isBlank()) {
                    request.setEngineerName((String) candidate.get("name"));
                }
                
                // Generate resume summary if not provided
                if (request.getResumeSummary() == null || request.getResumeSummary().isBlank()) {
                    request.setResumeSummary(generateResumeSummary(candidate));
                }
            }
        } catch (Exception e) {
            // Log error but don't fail the interview creation
            System.err.println("Failed to auto-fill candidate data: " + e.getMessage());
        }
    }
    
    private void autoFillClientData(CreateInterviewRequest request) {
        try {
            ClientDTO client = clientService.getClientById(UUID.fromString(request.getClientId()));
            
            if (client != null) {
                // Auto-fill JD details
                if (request.getJdTitle() == null || request.getJdTitle().isBlank()) {
                    request.setJdTitle(client.getJdRole());
                }
                
                if (request.getJdText() == null || request.getJdText().isBlank()) {
                    request.setJdText(client.getJdDescription());
                }
                
                // Add client context to notes
                if (request.getNotes() == null || request.getNotes().isBlank()) {
                    request.setNotes("Client: " + client.getClientName() + " | Positions: " + client.getPositionsVacant());
                } else {
                    request.setNotes(request.getNotes() + " | Client: " + client.getClientName());
                }
            }
        } catch (Exception e) {
            // Log error but don't fail the interview creation
            System.err.println("Failed to auto-fill client data: " + e.getMessage());
        }
    }
    
    private InterviewMode suggestInterviewMode(CreateInterviewRequest request) {
        try {
            // Get candidate experience if candidateId is provided
            Double yoeActual = null;
            if (request.getCandidateId() != null) {
                Map<String, Object> candidate = authServiceClient.getUserById(request.getCandidateId());
                if (candidate != null && candidate.get("yoeActual") != null) {
                    yoeActual = ((Number) candidate.get("yoeActual")).doubleValue();
                }
            }
            
            // Analyze JD for seniority level
            String jdText = request.getJdText() != null ? request.getJdText().toLowerCase() : "";
            String jdTitle = request.getJdTitle() != null ? request.getJdTitle().toLowerCase() : "";
            String combined = jdText + " " + jdTitle;
            
            // Determine suggested mode based on JD and experience
            if (combined.contains("lead") || combined.contains("principal") || combined.contains("staff") || 
                combined.contains("architect") || (yoeActual != null && yoeActual >= 8)) {
                return InterviewMode.L4;
            } else if (combined.contains("senior") || combined.contains("sr.") || 
                      (yoeActual != null && yoeActual >= 5)) {
                return InterviewMode.L3;
            } else if (combined.contains("mid") || combined.contains("intermediate") || 
                      (yoeActual != null && yoeActual >= 3)) {
                return InterviewMode.L2;
            } else if (combined.contains("junior") || combined.contains("jr.") || 
                      (yoeActual != null && yoeActual >= 1)) {
                return InterviewMode.L1;
            } else {
                return InterviewMode.SCREENING;
            }
        } catch (Exception e) {
            return InterviewMode.SCREENING; // Default fallback
        }
    }
    
    private String generateFocusAreas(CreateInterviewRequest request) {
        StringBuilder focusAreas = new StringBuilder();
        
        String jdText = request.getJdText() != null ? request.getJdText().toLowerCase() : "";
        String jdTitle = request.getJdTitle() != null ? request.getJdTitle().toLowerCase() : "";
        String combined = jdText + " " + jdTitle;
        
        // Extract key technologies and skills
        if (combined.contains("java") || combined.contains("spring")) {
            focusAreas.append("Java & Spring Boot, ");
        }
        if (combined.contains("microservice") || combined.contains("micro-service")) {
            focusAreas.append("Microservices Architecture, ");
        }
        if (combined.contains("kafka") || combined.contains("messaging")) {
            focusAreas.append("Kafka & Messaging, ");
        }
        if (combined.contains("kubernetes") || combined.contains("k8s") || combined.contains("docker")) {
            focusAreas.append("Containerization & Orchestration, ");
        }
        if (combined.contains("aws") || combined.contains("cloud")) {
            focusAreas.append("Cloud Technologies, ");
        }
        if (combined.contains("database") || combined.contains("sql") || combined.contains("postgresql")) {
            focusAreas.append("Database Design, ");
        }
        if (combined.contains("react") || combined.contains("frontend") || combined.contains("javascript")) {
            focusAreas.append("Frontend Technologies, ");
        }
        if (combined.contains("system design") || combined.contains("architecture")) {
            focusAreas.append("System Design, ");
        }
        
        // Add default areas based on interview mode
        InterviewMode mode = request.getInterviewMode() != null ? request.getInterviewMode() : InterviewMode.SCREENING;
        switch (mode) {
            case L4:
                focusAreas.append("Technical Leadership, System Architecture, ");
                break;
            case L3:
                focusAreas.append("Advanced Problem Solving, Design Patterns, ");
                break;
            case L2:
                focusAreas.append("Applied Knowledge, Real-world Scenarios, ");
                break;
            case L1:
                focusAreas.append("Core Fundamentals, Problem Solving, ");
                break;
            default:
                focusAreas.append("Basic Concepts, Communication Skills, ");
        }
        
        // Remove trailing comma and space
        String result = focusAreas.toString();
        if (result.endsWith(", ")) {
            result = result.substring(0, result.length() - 2);
        }
        
        return result.isBlank() ? "General Technical Assessment" : result;
    }
    
    private String generateResumeSummary(Map<String, Object> candidate) {
        // First, check if candidate already has a resume summary
        String existingResumeSummary = (String) candidate.get("resumeSummary");
        if (existingResumeSummary != null && !existingResumeSummary.trim().isEmpty() && 
            !existingResumeSummary.contains("[Add specific project experience")) {
            return existingResumeSummary;
        }
        
        // If no existing summary, generate a basic one from profile data
        StringBuilder summary = new StringBuilder();
        
        String name = (String) candidate.get("name");
        String skillSet = (String) candidate.get("skillSet");
        Double yoeActual = candidate.get("yoeActual") != null ? ((Number) candidate.get("yoeActual")).doubleValue() : null;
        String batch = (String) candidate.get("batch");
        String rating = (String) candidate.get("rating");
        
        if (name != null) {
            summary.append(name).append(" - ");
        }
        
        // Add experience level description
        if (yoeActual != null) {
            if (yoeActual >= 8) {
                summary.append("Senior professional with ").append(String.format("%.1f years", yoeActual));
            } else if (yoeActual >= 5) {
                summary.append("Experienced professional with ").append(String.format("%.1f years", yoeActual));
            } else if (yoeActual >= 3) {
                summary.append("Mid-level professional with ").append(String.format("%.1f years", yoeActual));
            } else if (yoeActual >= 1) {
                summary.append("Professional with ").append(String.format("%.1f years", yoeActual));
            } else {
                summary.append("Entry-level professional with ").append(String.format("%.1f years", yoeActual));
            }
            summary.append(" of experience in ");
        }
        
        // Add skill set description
        if (skillSet != null) {
            switch (skillSet) {
                case "JAVA_SB":
                    summary.append("Java & Spring Boot development, backend systems, and microservices");
                    break;
                case "JFSR":
                    summary.append("Full Stack development with Java backend and React frontend");
                    break;
                case "REACT_JS":
                    summary.append("React.js development, modern frontend frameworks, and UI/UX");
                    break;
                default:
                    summary.append("software development and technical solutions");
            }
        } else {
            summary.append("software development");
        }
        
        // Add performance indicator if available
        if ("ASSET".equals(rating)) {
            summary.append(". Top-rated performer with proven track record");
        } else if ("MEDIUM".equals(rating)) {
            summary.append(". Consistent performer with reliable delivery");
        }
        
        // Add batch information if available
        if (batch != null && !batch.trim().isEmpty()) {
            summary.append(". Training batch: ").append(batch);
        }
        
        // Add note about resume upload
        String resumeFilename = (String) candidate.get("resumeFilename");
        if (resumeFilename != null && !resumeFilename.trim().isEmpty()) {
            summary.append(". Resume uploaded and processed for detailed background");
        } else {
            summary.append(". Resume upload recommended for detailed technical background");
        }
        
        return summary.toString();
    }
}