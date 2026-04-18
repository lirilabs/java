package com.example.LiveData.repository;

import com.example.LiveData.entity.StockData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StockRepository extends JpaRepository<StockData, String> {
    // Leave this empty. JpaRepository already provides saveAll(), save(), findById(), etc.
}