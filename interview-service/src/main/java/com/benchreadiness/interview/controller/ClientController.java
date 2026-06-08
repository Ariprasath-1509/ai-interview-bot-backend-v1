package com.benchreadiness.interview.controller;

import com.benchreadiness.interview.client.DocumentServiceClient;
import com.benchreadiness.interview.dto.ClientDTO;
import com.benchreadiness.interview.service.ClientService;
import com.benchreadiness.interview.service.MatchingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
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
    private final DocumentServiceClient documentServiceClient;

    public ClientController(ClientService clientService, MatchingService matchingService, 
                           ObjectMapper objectMapper, DocumentServiceClient documentServiceClient) {
        this.clientService = clientService;
        this.matchingService = matchingService;
        this.objectMapper = objectMapper;
        this.documentServiceClient = documentServiceClient;
    }

    @GetMapping("/for-interview")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'RECRUITER')")
    public ResponseEntity<Map<String, Object>> getClientsForInterview(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String userRole,
            @RequestParam(required = false) String candidateSkillSet,
            @RequestParam(required = false) Double candidateYoe) {
        
        List<ClientDTO> allClients = clientService.getAllClients();

        // If a specific candidate's skill+YOE is provided, filter and rank for that candidate
        if (candidateSkillSet != null && !candidateSkillSet.isBlank()) {
            double yoe = candidateYoe != null ? candidateYoe : 0.0;
            List<ClientDTO> matched = new ArrayList<>();
            List<ClientDTO> partial = new ArrayList<>();

            for (ClientDTO client : allClients) {
                MatchResult result = matchClientToCandidate(client, candidateSkillSet, yoe);
                if (result == MatchResult.MATCHED) matched.add(client);
                else if (result == MatchResult.YOE_SHORT) partial.add(client);
                // SKILL_MISMATCH clients are excluded entirely
            }

            // matched (skill + yoe) first, then partial (skill matches but yoe short)
            List<ClientDTO> clientsToReturn = new ArrayList<>();
            clientsToReturn.addAll(matched);
            clientsToReturn.addAll(partial);

            return ResponseEntity.ok(Map.of(
                "clients", clientsToReturn,
                "hasMatchingClients", !matched.isEmpty(),
                "message", matched.isEmpty() && partial.isEmpty()
                    ? "No client positions found matching " + candidateSkillSet + " skill set."
                    : matched.size() + " position(s) match skill + YOE" + (partial.isEmpty() ? "" : ", " + partial.size() + " match skill only"),
                "totalClients", clientsToReturn.size()
            ));
        }
        
        // No candidate context — original behaviour: show clients that have any eligible candidates
        List<ClientDTO> clientsWithMatches = new ArrayList<>();
        List<ClientDTO> clientsWithoutMatches = new ArrayList<>();
        
        for (ClientDTO client : allClients) {
            boolean hasMatches = quickCheckForMatches(client);
            if (hasMatches) clientsWithMatches.add(client);
            else clientsWithoutMatches.add(client);
        }
        
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
        
        return ResponseEntity.ok(Map.of(
            "clients", clientsToReturn,
            "hasMatchingClients", hasMatchingClients,
            "message", message,
            "totalClients", clientsToReturn.size(),
            "totalClientsWithMatches", clientsWithMatches.size(),
            "totalClientsWithoutMatches", clientsWithoutMatches.size()
        ));
    }

    private enum MatchResult { MATCHED, YOE_SHORT, SKILL_MISMATCH }

    /**
     * Match a client against a specific candidate's skillSet + YOE.
     * MATCHED   = client has a skill requirement matching candidateSkillSet AND yoe >= minYoeRequired
     * YOE_SHORT = skill matches but candidate yoe is below minimum (still show, ranked lower)
     * SKILL_MISMATCH = no skill requirement matches — exclude entirely
     */
    private MatchResult matchClientToCandidate(ClientDTO client, String candidateSkillSet, double candidateYoe) {
        if (client.getSkillRequirements() == null || client.getSkillRequirements().isEmpty()) {
            // Legacy client with no skill requirements — include as partial
            return MatchResult.YOE_SHORT;
        }
        boolean skillFound = false;
        boolean yoeMet = false;
        for (var skillReq : client.getSkillRequirements()) {
            if (skillReq.getSkillSet() != null && skillReq.getSkillSet().equals(candidateSkillSet)) {
                skillFound = true;
                for (var pos : skillReq.getPositions()) {
                    double minYoe = pos.getMinYoeRequired() != null ? pos.getMinYoeRequired() : 0.0;
                    if (candidateYoe >= minYoe) {
                        yoeMet = true;
                        break;
                    }
                }
                break;
            }
        }
        if (!skillFound) return MatchResult.SKILL_MISMATCH;
        return yoeMet ? MatchResult.MATCHED : MatchResult.YOE_SHORT;
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
                            matchesSkillRequirement(candidate, skillReq.getSkillSet(),
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
                new com.benchreadiness.interview.dto.SkillRequirementDTO("JAVA_SB");
            
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

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'RECRUITER')")
    public ResponseEntity<ClientDTO> updateClientJson(@PathVariable UUID id,
                                                       @RequestBody ClientDTO clientDTO) {
        return ResponseEntity.ok(clientService.updateClient(id, clientDTO, null));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'RECRUITER')")
    public ResponseEntity<ClientDTO> updateClientMultipart(
            @PathVariable UUID id,
            @RequestPart("client") String clientJson,
            @RequestPart(value = "jdFile", required = false) MultipartFile jdFile) {
        try {
            ClientDTO clientDTO = objectMapper.readValue(clientJson, ClientDTO.class);
            return ResponseEntity.ok(clientService.updateClient(id, clientDTO, jdFile));
        } catch (Exception e) {
            log.warn("updateClient multipart parse failed: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
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
    
    @GetMapping("/{id}/doc-status")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'RECRUITER')")
    public ResponseEntity<Map<String, Object>> getDocumentStatus(@PathVariable UUID id) {
        String docId = clientService.getDocId(id);
        if (docId == null) {
            return ResponseEntity.ok(Map.of(
                "error", "No docId stored for this client",
                "hasJdFile", clientService.getJdFileDownload(id).isPresent()
            ));
        }
        
        try {
            Map<String, Object> status = documentServiceClient.getDocumentStatus(docId);
            if (status == null) {
                return ResponseEntity.ok(Map.of(
                    "docId", docId,
                    "error", "Document not found in document service",
                    "hasJdFile", clientService.getJdFileDownload(id).isPresent()
                ));
            }
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                "docId", docId,
                "error", "Failed to check document service: " + e.getMessage(),
                "hasJdFile", clientService.getJdFileDownload(id).isPresent()
            ));
        }
    }

    @GetMapping("/{id}/jd-file")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'RECRUITER')")
    public ResponseEntity<byte[]> downloadClientJd(@PathVariable UUID id) {
        var jd = clientService.getJdFileDownload(id);
        if (jd.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        var file = jd.get();
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(file.filename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(file.data().length)
                .body(file.data());
    }
}
