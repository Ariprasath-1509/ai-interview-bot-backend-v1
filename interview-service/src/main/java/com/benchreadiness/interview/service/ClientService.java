package com.benchreadiness.interview.service;

import com.benchreadiness.interview.client.ObserverServiceClient;
import com.benchreadiness.interview.dto.ClientDTO;
import com.benchreadiness.interview.entity.Client;
import com.benchreadiness.interview.repository.ClientRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ClientService {
    
    private final ClientRepository clientRepository;
    private final ObserverServiceClient observerServiceClient;
    
    public ClientService(ClientRepository clientRepository, ObserverServiceClient observerServiceClient) {
        this.clientRepository = clientRepository;
        this.observerServiceClient = observerServiceClient;
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
    
    public ClientDTO createClient(ClientDTO clientDTO) {
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
            // Log error but don't fail client creation
            System.err.println("Failed to send client creation notification: " + e.getMessage());
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
        
        Client savedClient = clientRepository.save(client);
        return convertToDTO(savedClient);
    }
    
    public ClientDTO markRecruitmentReviewed(UUID id) {
        Client client = clientRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Client not found with id: " + id));
        
        client.setRecruitmentReviewed(true);
        client.setUpdatedAt(LocalDateTime.now());
        
        Client savedClient = clientRepository.save(client);
        return convertToDTO(savedClient);
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
                client.getUpdatedAt()
        );
    }
}