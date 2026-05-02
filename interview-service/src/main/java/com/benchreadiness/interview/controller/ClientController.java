package com.benchreadiness.interview.controller;

import com.benchreadiness.interview.dto.ClientDTO;
import com.benchreadiness.interview.entity.Client;
import com.benchreadiness.interview.service.ClientService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/recruiter/clients")
public class ClientController {
    
    private final ClientService clientService;
    
    public ClientController(ClientService clientService) {
        this.clientService = clientService;
    }
    
    @GetMapping
    public ResponseEntity<List<ClientDTO>> getAllClients(@RequestHeader("X-User-Role") String userRole) {
        // ADMIN, SUPER_ADMIN, and RECRUITER can view clients
        if (!"ADMIN".equals(userRole) && !"SUPER_ADMIN".equals(userRole) && !"RECRUITER".equals(userRole)) {
            return ResponseEntity.status(403).build();
        }
        List<ClientDTO> clients = clientService.getAllClients();
        return ResponseEntity.ok(clients);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<ClientDTO> getClientById(@PathVariable UUID id,
                                                   @RequestHeader("X-User-Role") String userRole) {
        // ADMIN, SUPER_ADMIN, and RECRUITER can view client details
        if (!"ADMIN".equals(userRole) && !"SUPER_ADMIN".equals(userRole) && !"RECRUITER".equals(userRole)) {
            return ResponseEntity.status(403).build();
        }
        ClientDTO client = clientService.getClientById(id);
        return ResponseEntity.ok(client);
    }
    
    @PostMapping
    public ResponseEntity<ClientDTO> createClient(@RequestBody ClientDTO clientDTO,
                                                  @RequestHeader("X-User-Role") String userRole) {
        // ADMIN, SUPER_ADMIN, and RECRUITER can create clients
        if (!"ADMIN".equals(userRole) && !"SUPER_ADMIN".equals(userRole) && !"RECRUITER".equals(userRole)) {
            return ResponseEntity.status(403).build();
        }
        ClientDTO createdClient = clientService.createClient(clientDTO);
        return ResponseEntity.ok(createdClient);
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<ClientDTO> updateClient(@PathVariable UUID id, 
                                                  @RequestBody ClientDTO clientDTO,
                                                  @RequestHeader("X-User-Role") String userRole) {
        // ADMIN, SUPER_ADMIN, and RECRUITER can update clients
        if (!"ADMIN".equals(userRole) && !"SUPER_ADMIN".equals(userRole) && !"RECRUITER".equals(userRole)) {
            return ResponseEntity.status(403).build();
        }
        ClientDTO updatedClient = clientService.updateClient(id, clientDTO);
        return ResponseEntity.ok(updatedClient);
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteClient(@PathVariable UUID id,
                                            @RequestHeader("X-User-Role") String userRole) {
        // Only ADMIN and SUPER_ADMIN can delete clients
        if (!"ADMIN".equals(userRole) && !"SUPER_ADMIN".equals(userRole)) {
            return ResponseEntity.status(403).build();
        }
        clientService.deleteClient(id);
        return ResponseEntity.noContent().build();
    }
}