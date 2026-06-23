package com.benchreadiness.auth.masterdata;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MasterDataEntryRepository extends JpaRepository<MasterDataEntry, UUID> {

    List<MasterDataEntry> findByCategoryOrderByDisplayOrderAscCodeAsc(String category);

    List<MasterDataEntry> findByCategoryAndActiveTrueOrderByDisplayOrderAscCodeAsc(String category);

    Optional<MasterDataEntry> findByCategoryAndCodeIgnoreCase(String category, String code);

    Optional<MasterDataEntry> findByCategoryAndId(String category, UUID id);

    boolean existsByCategoryAndCodeIgnoreCase(String category, String code);
}
