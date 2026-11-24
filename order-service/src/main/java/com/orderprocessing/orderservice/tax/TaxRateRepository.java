package com.orderprocessing.orderservice.tax;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TaxRateRepository extends JpaRepository<TaxRate, String> {
}
