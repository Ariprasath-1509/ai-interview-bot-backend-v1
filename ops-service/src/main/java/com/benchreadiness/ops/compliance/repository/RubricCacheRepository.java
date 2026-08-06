package com.benchreadiness.ops.compliance.repository;

import com.benchreadiness.ops.compliance.entity.RubricCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RubricCacheRepository extends JpaRepository<RubricCache, String> {
    Optional<RubricCache> findByCacheKey(String cacheKey);
}
