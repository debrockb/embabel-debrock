package com.matoe.repository;

import com.matoe.entity.ItineraryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ItineraryRepository extends JpaRepository<ItineraryEntity, String> {

    List<ItineraryEntity> findAllByOrderByCreatedAtDesc();

    List<ItineraryEntity> findByDestinationContainingIgnoreCaseOrderByCreatedAtDesc(String destination);
}
