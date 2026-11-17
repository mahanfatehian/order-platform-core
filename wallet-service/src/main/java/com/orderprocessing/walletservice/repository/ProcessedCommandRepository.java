package com.orderprocessing.walletservice.repository;

import com.orderprocessing.walletservice.model.ProcessedCommand;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedCommandRepository extends JpaRepository<ProcessedCommand, String> {
}
