package com.ssok.server.domain.space.repository;

import com.ssok.server.domain.space.entity.Space;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpaceRepository extends JpaRepository<Space, Long> {
}
