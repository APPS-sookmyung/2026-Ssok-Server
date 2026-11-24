package com.ssok.server.domain.library.repository;

import com.ssok.server.domain.library.entity.Library;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LibraryRepository extends JpaRepository<Library, Long> {
    Optional<Library> findByIdAndSpaceId(Long id, Long spaceId);
    List<Library> findAllBySpaceIdOrderByIdAsc(Long spaceId);
    List<Library> findBySpaceIdAndNameContainingIgnoreCaseOrderByIdAsc(Long spaceId, String query);
    boolean existsBySpaceIdAndNameKeyAndIdNot(Long spaceId, String key, Long id);
}
