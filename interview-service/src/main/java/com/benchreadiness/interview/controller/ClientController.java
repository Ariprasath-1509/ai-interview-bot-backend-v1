package com.benchreadiness.interview.controller;

import com.benchreadiness.interview.dto.ClientDTO;
import com.benchreadiness.interview.service.ClientService;
import com.benchreadiness.interview.service.MatchingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/recruiter/clients")
public class ClientController {

    private static final Logger log = LoggerFactory.getLogger(ClientController.class);

    private final ClientService clientService;
    private final MatchingService matchingService;
    private final ObjectMapper objectMapper;

    public ClientController(ClientService clientService, MatchingService matchingService, ObjectMapper objectMapper) {
        this.clientService = clientService;
        this.matchingService = matchingService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/for-interview")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'RECRUITER')")
    public ResponseEntity<Map<String, Object>> getClientsForInterview(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole) {
        
        List<ClientDTO> allClients = clientService.getAllClients();
        
        // Quick rule-based check: does client have ANY matching candidates?
        // No AI calls - just skill + YOE + status filtering
        List<ClientDTO> clientsWithMatches = new ArrayList<>();
        List<ClientDTO> clientsWithoutMatches = new ArrayList<>();
        
        for (ClientDTO client : allClients) {
            boolean hasMatches = quickCheckForMatches(client);
            
            if (hasMatches) {
                clientsWithMatches.add(client);
            } else {
                clientsWithoutMatches.add(client);
            }
        }
        
        // Return clients with matches first, or all if none have matches
        List<ClientDTO> clientsToReturn;
        String message;
        boolean hasMatchingClients = !clientsWithMatches.isEmpty();
        
        if (hasMatchingClients) {
            clientsToReturn = clientsWithMatches;
            message = "Showing clients with potential candidate matches (based on skill + experience)";
        } else {
            clientsToReturn = allClients;
            message = "No matching candidates found. Showing all clients.";
        }
        
        Map<String, Object> response = Map.of(
            "clients", clientsToReturn,
            "hasMatchingClients", hasMatchingClients,
            "message", message,
            "totalClients", clientsToReturn.size(),
            "totalClientsWithMatches", clientsWithMatches.size(),
            "totalClientsWithoutMatches", clientsWithoutMatches.size()
        );
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Quick rule-based check: Does this client have ANY candidates matching:
     * 1. Skill requirement
     * 2. YOE requirement (using yoePortrayed)
     * 3. Status = RFD with at least 1 completed interview
     * 
     * NO AI CALLS - just database filtering for speed
     */
    private boolean quickCheckForMatches(ClientDTO client) {
        try {
            // Get all RFD candidates with at least 1 interview
            List<Map<String, Object>> allCandidates = matchingService.getAllEligibleCandidates();
            
            // Check if client has skill requirements
            if (client.getSkillRequirements() != null && !client.getSkillRequirements().isEmpty()) {
                // New skill-based matching
                for (var skillReq : client.getSkillRequirements()) {
                    for (var posReq : skillReq.getPositions()) {
                        // Check if ANY candidate matches this skill + YOE + source
                        boolean hasMatch = allCandidates.stream().anyMatch(candidate -> 
                            matchesSkillRequirement(candidate, skillReq.getSkillSet().name(), 
                                                   posReq.getMinYoeRequired(), posReq.getSource())
                        );
                        
                        if (hasMatch) {
                            return true; // Found at least one match
                        }
                    }
                }
                return false;
            } else {
                // Legacy: check if ANY candidate matches the source
                if (client.getBenchB2bCandidatesNeeded() != null && client.getBenchB2bCandidatesNeeded() > 0) {
                    boolean hasMatch = allCandidates.stream()
                        .anyMatch(c -> "BENCH".equals(c.get("source")) || "B2B".equals(c.get("source")));
                    if (hasMatch) return true;
                }
                
                if (client.getMarketCandidatesNeeded() != null && client.getMarketCandidatesNeeded() > 0) {
                    boolean hasMatch = allCandidates.stream()
                        .anyMatch(c -> "MARKET".equals(c.get("source")));
                    if (hasMatch) return true;
                }
                
                return false;
            }
        } catch (Exception e) {
            log.warn("Failed to check matches for client {}: {}", client.getClientName(), e.getMessage());
            return false; // On error, assume no matches
        }
    }
    
    /**
     * Check if candidate matches skill requirement:
     * - Skill set matches
     * - YOE portrayed >= minimum required
     * - Source matches (BENCH/B2B or MARKET)
     */
    private boolean matchesSkillRequirement(Map<String, Object> candidate, String requiredSkill, 
                                           Double minYoeRequired, String requiredSource) {
        // 1. Check skill match
        String candidateSkill = (String) candidate.get("skillSet");
        if (!requiredSkill.equals(candidateSkill)) {
            return false;
        }
        
        // 2. Check YOE match (use yoePortrayed for client matching)
        Double yoePortrayed = candidate.get("yoePortrayed") != null ? 
            ((Number) candidate.get("yoePortrayed")).doubleValue() : 0.0;
        if (yoePortrayed < minYoeRequired) {
            return false;
        }
        
        // 3. Check source match
        String candidateSource = (String) candidate.get("source");
        if ("BENCH_B2B".equals(requiredSource)) {
            return "BENCH".equals(candidateSource) || "B2B".equals(candidateSource);
        } else if ("MARKET".equals(requiredSource)) {
            return "MARKET".equals(candidateSource);
        }
        
        return false;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'RECRUITER')")
    public ResponseEntity<List<ClientDTO>> getAllClients() {
        return ResponseEntity.ok(clientService.getAllClients());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'RECRUITER')")
    public ResponseEntity<ClientDTO> getClientById(@PathVariable UUID id) {
        return ResponseEntity.ok(clientService.getClientById(id));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'RECRUITER')")
    public ResponseEntity<ClientDTO> createClient(
            @RequestPart("client") String clientJson,
            @RequestPart(value = "jdFile", required = false) MultipartFile jdFile) {
        try {
            ClientDTO clientDTO = objectMapper.readValue(clientJson, ClientDTO.class);
            ClientDTO created = clientService.createClient(clientDTO, jdFile);
            return ResponseEntity.ok(created);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'RECRUITER')")
    public ResponseEntity<ClientDTO> createClientJson(@RequestBody ClientDTO clientDTO) {
        // Ensure backward compatibility - if no skill requirements provided, 
        // create default ones based on legacy fields
        if ((clientDTO.getSkillRequirements() == null || clientDTO.getSkillRequirements().isEmpty()) &&
            (clientDTO.getBenchB2bCandidatesNeeded() > 0 || clientDTO.getMarketCandidatesNeeded() > 0)) {
            
            clientDTO = createLegacySkillRequirements(clientDTO);
        }
        
        ClientDTO created = clientService.createClient(clientDTO, null);
        return ResponseEntity.ok(created);
    }
    
    private ClientDTO createLegacySkillRequirements(ClientDTO clientDTO) {
        List<com.benchreadiness.interview.dto.SkillRequirementDTO> skillRequirements = new ArrayList<>();
        
        // Create a default JAVA_SB skill requirement if candidates are needed
        if (clientDTO.getBenchB2bCandidatesNeeded() > 0 || clientDTO.getMarketCandidatesNeeded() > 0) {
            com.benchreadiness.interview.dto.SkillRequirementDTO skillReq = 
                new com.benchreadiness.interview.dto.SkillRequirementDTO(com.benchreadiness.interview.entity.SkillSet.JAVA_SB);
            
            List<com.benchreadiness.interview.dto.PositionRequirementDTO> positions = new ArrayList<>();
            
            if (clientDTO.getBenchB2bCandidatesNeeded() > 0) {
                positions.add(new com.benchreadiness.interview.dto.PositionRequirementDTO(
                    clientDTO.getBenchB2bCandidatesNeeded(), 3.0, "BENCH_B2B"));
            }
            
            if (clientDTO.getMarketCandidatesNeeded() > 0) {
                positions.add(new com.benchreadiness.interview.dto.PositionRequirementDTO(
                    clientDTO.getMarketCandidatesNeeded(), 3.0, "MARKET"));
            }
            
            skillReq.setPositions(positions);
            skillRequirements.add(skillReq);
        }
        
        clientDTO.setSkillRequirements(skillRequirements);
        return clientDTO;
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'RECRUITER')")
    public ResponseEntity<ClientDTO> updateClient(@PathVariable UUID id,
                                                  @RequestBody ClientDTO clientDTO) {
        return ResponseEntity.ok(clientService.updateClient(id, clientDTO));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Void> deleteClient(@PathVariable UUID id) {
        clientService.deleteClient(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/doc-id")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'RECRUITER')")
    public ResponseEntity<Map<String, String>> getDocId(@PathVariable UUID id) {
        String docId = clientService.getDocId(id);
        if (docId == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("docId", docId));
    }
}
