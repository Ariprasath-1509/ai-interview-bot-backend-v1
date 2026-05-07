package com.benchreadiness.interview.service;

import com.benchreadiness.interview.client.DocumentServiceClient;
import com.benchreadiness.interview.client.ObserverServiceClient;
import com.benchreadiness.interview.dto.ClientDTO;
import com.benchreadiness.interview.entity.Client;
import com.benchreadiness.interview.repository.ClientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ClientService {

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

        // Handle JD file upload
        if (jdFile != null && !jdFile.isEmpty()) {
            try {
                byte[] fileBytes = jdFile.getBytes();
                client.setJdFile(fileBytes);
                client.setJdFileName(jdFile.getOriginalFilename());

                // Upload to document service and get docId
                String docId = documentServiceClient.uploadDocument(fileBytes, jdFile.getOriginalFilename());
                client.setDocId(docId);
                log.info("JD file uploaded to document service. docId={}", docId);
            } catch (Exception e) {
                log.error("Failed to upload JD file to document service: {}", e.getMessage());
                // Still save the file blob even if document service is unavailable
                try {
                    client.setJdFile(jdFile.getBytes());
                    client.setJdFileName(jdFile.getOriginalFilename());
                } catch (Exception ex) {
                    log.error("Failed to read file bytes: {}", ex.getMessage());
                }
            }
        }

        Client savedClient = clientRepository.save(client);

        // Send notification to admins
        try {
            Map<String, Object> notificationRequest = new HashMap<>();
            notificationRequest.put("clientId", savedClient.getId().toString());
            notificationRequest.put("clientName", savedClient.getClientName());
            notificationRequest.put("jdRole", savedClient.getJdRole());
            notificationRequest.put("benchB2bCandidatesNeeded", savedClient.getBenchB2bCandidatesNeeded());
            notificationRequest.put("marketCandidatesNeeded", savedClient.getMarketCandidatesNeeded());
            observerServiceClient.notifyClientCreated(notificationRequest);
        } catch (Exception e) {
            log.error("Failed to send client creation notification: {}", e.getMessage());
        }

        return convertToDTO(savedClient);
    }

    public ClientDTO updateClient(UUID id, ClientDTO clientDTO) {
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

        Client savedClient = clientRepository.save(client);
        return convertToDTO(savedClient);
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
                client.getJdFileName()
        );
    }
}
