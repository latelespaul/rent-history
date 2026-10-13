package com.rentrix.rentrixserver.repository;

import com.rentrix.rentrixserver.entity.Flat;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FlatRepository extends JpaRepository<Flat, Long>, JpaSpecificationExecutor<Flat> {
	
	// we will use this to avoid N+1 issue
	@EntityGraph(attributePaths = {"owner", "createdBy", "address"})
	Page<Flat> findAll(Specification<Flat> spec, Pageable pageable);
	
	Page<Flat> findByCreatedById(Long userId, Pageable pageable);
	
	@EntityGraph(attributePaths = {"owner", "owner.userDetail",
		"createdBy", "createdBy.userDetail",
		"images"})
	@Query("SELECT f FROM Flat f WHERE f.id = :id")
	Optional<Flat> findByIdWithImages(@Param("id") Long id);
	
}