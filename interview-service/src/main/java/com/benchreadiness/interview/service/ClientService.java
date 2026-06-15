package com.benchreadiness.interview.service;

import com.benchreadiness.interview.client.DocumentServiceClient;
import com.benchreadiness.interview.client.ObserverServiceClient;
import com.benchreadiness.interview.dto.ClientDTO;
import com.benchreadiness.interview.dto.PositionRequirementDTO;
import com.benchreadiness.interview.dto.SkillRequirementDTO;
import com.benchreadiness.interview.entity.Client;
import com.benchreadiness.interview.entity.PositionRequirement;
import com.benchreadiness.interview.entity.SkillRequirement;
import com.benchreadiness.interview.repository.ClientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ClientService {

    public record JdFileDownload(String filename, byte[] data) {}

    private static final Logger log = LoggerFactory.getLogger(ClientService.class);

    private final ClientRepository clientRepository;
    private final ObserverServiceClient observerServiceClient;
    private final DocumentServiceClient documentServiceClient;

    public ClientService(ClientRepository clientRepository,
                         ObserverServiceClient observerServiceClient,
                         DocumentServiceClient documentServiceClient) {
        this.clientRepository = clientRepository;
        this.observerServiceClient = observerServiceClient;
        this.documentServiceClient = documentServiceClient;
    }

    public List<ClientDTO> getAllClients() {
        return clientRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public ClientDTO getClientById(UUID id) {
        Client client = clientRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Client not found with id: " + id));
        return convertToDTO(client);
    }

    public String getDocId(UUID clientId) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new RuntimeException("Client not found with id: " + clientId));
        return client.getDocId();
    }

    /**
     * Stored JD binary for download (not included in {@link ClientDTO}).
     */
    public Optional<JdFileDownload> getJdFileDownload(UUID id) {
        Client client = clientRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Client not found with id: " + id));
        byte[] bytes = client.getJdFile();
        if (bytes == null || bytes.length == 0) {
            return Optional.empty();
        }
        String filename = client.getJdFileName() != null && !client.getJdFileName().isBlank()
                ? client.getJdFileName()
                : "job-description";
        return Optional.of(new JdFileDownload(filename, bytes));
    }

    public ClientDTO createClient(ClientDTO clientDTO, MultipartFile jdFile) {
        Client client = new Client();
        client.setClientName(clientDTO.getClientName());
        client.setJdRole(clientDTO.getJdRole());
        client.setJdDescription(clientDTO.getJdDescription());
        client.setPositionsVacant(clientDTO.getPositionsVacant());
        client.setMarketCandidatesNeeded(clientDTO.getMarketCandidatesNeeded());
        client.setBenchB2bCandidatesNeeded(clientDTO.getBenchB2bCandidatesNeeded());
        client.setStatus(Client.ClientStatus.valueOf(clientDTO.getStatus()));
        client.setBenchReviewed(false);
        client.setRecruitmentReviewed(false);
        client.setCreatedAt(LocalDateTime.now());
        client.setUpdatedAt(LocalDateTime.now());

        // Handle skill requirements
        if (clientDTO.getSkillRequirements() != null && !clientDTO.getSkillRequirements().isEmpty()) {
            for (SkillRequirementDTO skillReqDTO : clientDTO.getSkillRequirements()) {
                SkillRequirement skillReq = new SkillRequirement(client, skillReqDTO.getSkillSet());
                
                for (PositionRequirementDTO posReqDTO : skillReqDTO.getPositions()) {
                    PositionRequirement posReq = new PositionRequirement(
                        skillReq, 
                        posReqDTO.getCandidatesNeeded(),
                        posReqDTO.getMinYoeRequired(),
                        posReqDTO.getSource()
                    );
                    skillReq.addPosition(posReq);
                }
                
                client.addSkillRequirement(skillReq);
            }
        }

        applyJdFile(client, jdFile);

        Client savedClient = clientRepository.save(client);

        // Send notification to all admins and recruiters
        try {
            Map<String, Object> notificationRequest = new HashMap<>();
            notificationRequest.put("clientId", savedClient.getId().toString());
            notificationRequest.put("clientName", savedClient.getClientName());
            notificationRequest.put("jdRole", savedClient.getJdRole());
            notificationRequest.put("jdDescription", savedClient.getJdDescription());
            notificationRequest.put("positionsVacant", savedClient.getPositionsVacant());
            notificationRequest.put("benchB2bCandidatesNeeded", savedClient.getBenchB2bCandidatesNeeded());
            notificationRequest.put("marketCandidatesNeeded", savedClient.getMarketCandidatesNeeded());
            notificationRequest.put("skillRequirementsSummary", buildSkillRequirementsSummary(savedClient));
            notificationRequest.put("jdFileName", savedClient.getJdFileName());
            observerServiceClient.notifyClientCreated(notificationRequest);
        } catch (Exception e) {
            log.error("Failed to send client creation notification: {}", e.getMessage());
        }

        return convertToDTO(savedClient);
    }

    public ClientDTO updateClient(UUID id, ClientDTO clientDTO, MultipartFile jdFile) {
        Client client = clientRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Client not found with id: " + id));

        client.setClientName(clientDTO.getClientName());
        client.setJdRole(clientDTO.getJdRole());
        client.setJdDescription(clientDTO.getJdDescription());
        client.setPositionsVacant(clientDTO.getPositionsVacant());
        client.setMarketCandidatesNeeded(clientDTO.getMarketCandidatesNeeded());
        client.setBenchB2bCandidatesNeeded(clientDTO.getBenchB2bCandidatesNeeded());
        client.setStatus(Client.ClientStatus.valueOf(clientDTO.getStatus()));
        client.setUpdatedAt(LocalDateTime.now());

        // Handle skill requirements update
        if (clientDTO.getSkillRequirements() != null) {
            // Clear existing skill requirements
            client.getSkillRequirements().clear();
            
            // Add new skill requirements
            for (SkillRequirementDTO skillReqDTO : clientDTO.getSkillRequirements()) {
                SkillRequirement skillReq = new SkillRequirement(client, skillReqDTO.getSkillSet());
                
                for (PositionRequirementDTO posReqDTO : skillReqDTO.getPositions()) {
                    PositionRequirement posReq = new PositionRequirement(
                        skillReq, 
                        posReqDTO.getCandidatesNeeded(),
                        posReqDTO.getMinYoeRequired(),
                        posReqDTO.getSource()
                    );
                    skillReq.addPosition(posReq);
                }
                
                client.addSkillRequirement(skillReq);
            }
        }

        applyJdFile(client, jdFile);

        Client savedClient = clientRepository.save(client);
        return convertToDTO(savedClient);
    }

    private void applyJdFile(Client client, MultipartFile jdFile) {
        if (jdFile == null || jdFile.isEmpty()) {
            return;
        }
        try {
            byte[] fileBytes = jdFile.getBytes();
            client.setJdFile(fileBytes);
            client.setJdFileName(jdFile.getOriginalFilename());

            // Upload to document service with proper error handling
            String docId = documentServiceClient.uploadDocument(fileBytes, jdFile.getOriginalFilename());
            client.setDocId(docId);
            log.info("JD file uploaded to document service. docId={}, fileName={}, size={} bytes", 
                    docId, jdFile.getOriginalFilename(), fileBytes.length);
            
            // Verify upload by checking status (optional but recommended)
            try {
                Map<String, Object> status = documentServiceClient.getDocumentStatus(docId);
                if (status != null) {
                    log.info("Document service confirmed upload. docId={}, status={}", docId, status.get("status"));
                } else {
                    log.warn("Document service upload succeeded but status check returned null. docId={}", docId);
                }
            } catch (Exception statusEx) {
                log.warn("Could not verify document upload status. docId={}, error={}", docId, statusEx.getMessage());
            }
        } catch (Exception e) {
            log.error("Failed to upload JD file to document service: {}", e.getMessage(), e);
            // Fallback: still store in PostgreSQL even if document service fails
            try {
                client.setJdFile(jdFile.getBytes());
                client.setJdFileName(jdFile.getOriginalFilename());
                // Clear docId since upload failed
                client.setDocId(null);
                log.warn("JD file stored in PostgreSQL only (document service upload failed). fileName={}", 
                        jdFile.getOriginalFilename());
            } catch (Exception ex) {
                log.error("Failed to read file bytes: {}", ex.getMessage());
            }
        }
    }

    public void deleteClient(UUID id) {
        if (!clientRepository.existsById(id)) {
            throw new RuntimeException("Client not found with id: " + id);
        }
        clientRepository.deleteById(id);
    }

    public List<ClientDTO> getPendingClientsForBench() {
        return clientRepository.findByBenchReviewedFalseAndBenchB2bCandidatesNeededGreaterThan(0)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<ClientDTO> getPendingClientsForRecruitment() {
        return clientRepository.findByRecruitmentReviewedFalseAndMarketCandidatesNeededGreaterThan(0)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public ClientDTO markBenchReviewed(UUID id) {
        Client client = clientRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Client not found with id: " + id));
        client.setBenchReviewed(true);
        client.setUpdatedAt(LocalDateTime.now());
        return convertToDTO(clientRepository.save(client));
    }

    public ClientDTO markRecruitmentReviewed(UUID id) {
        Client client = clientRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Client not found with id: " + id));
        client.setRecruitmentReviewed(true);
        client.setUpdatedAt(LocalDateTime.now());
        return convertToDTO(clientRepository.save(client));
    }

    private ClientDTO convertToDTO(Client client) {
        List<SkillRequirementDTO> skillRequirements = new ArrayList<>();
        
        // Handle null or empty skill requirements for backward compatibility
        if (client.getSkillRequirements() != null && !client.getSkillRequirements().isEmpty()) {
            skillRequirements = client.getSkillRequirements().stream()
                    .map(this::convertSkillRequirementToDTO)
                    .collect(Collectors.toList());
        }
                
        return new ClientDTO(
                client.getId(),
                client.getClientName(),
                client.getJdRole(),
                client.getJdDescription(),
                client.getPositionsVacant(),
                client.getMarketCandidatesNeeded(),
                client.getBenchB2bCandidatesNeeded(),
                client.getStatus().name(),
                client.getBenchReviewed(),
                client.getRecruitmentReviewed(),
                client.getCreatedAt(),
                client.getUpdatedAt(),
                client.getDocId(),
                client.getJdFileName(),
                skillRequirements
        );
    }
    
    private SkillRequirementDTO convertSkillRequirementToDTO(SkillRequirement skillReq) {
        SkillRequirementDTO dto = new SkillRequirementDTO(skillReq.getSkillSet());
        dto.setId(skillReq.getId());
        
        List<PositionRequirementDTO> positions = new ArrayList<>();
        if (skillReq.getPositions() != null && !skillReq.getPositions().isEmpty()) {
            positions = skillReq.getPositions().stream()
                    .map(this::convertPositionRequirementToDTO)
                    .collect(Collectors.toList());
        }
        dto.setPositions(positions);
        
        return dto;
    }
    
    private PositionRequirementDTO convertPositionRequirementToDTO(PositionRequirement posReq) {
        PositionRequirementDTO dto = new PositionRequirementDTO(
            posReq.getCandidatesNeeded(),
            posReq.getMinYoeRequired(),
            posReq.getSource()
        );
        dto.setId(posReq.getId());
        return dto;
    }

    private String buildSkillRequirementsSummary(Client client) {
        if (client.getSkillRequirements() == null || client.getSkillRequirements().isEmpty()) {
            return "No specific skill requirements listed.";
        }
        StringBuilder summary = new StringBuilder();
        for (SkillRequirement skillReq : client.getSkillRequirements()) {
            summary.append("• ").append(skillReq.getSkillSet()).append('\n');
            if (skillReq.getPositions() != null) {
                for (PositionRequirement posReq : skillReq.getPositions()) {
                    summary.append("  - ")
                        .append(posReq.getCandidatesNeeded() != null ? posReq.getCandidatesNeeded() : 0)
                        .append(" candidates, ")
                        .append(posReq.getMinYoeRequired() != null ? posReq.getMinYoeRequired() : 0)
                        .append("+ years experience, source: ")
                        .append(posReq.getSource() != null ? posReq.getSource() : "N/A")
                        .append('\n');
                }
            }
        }
        return summary.toString().trim();
    }
}
