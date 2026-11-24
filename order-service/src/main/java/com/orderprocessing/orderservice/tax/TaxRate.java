package com.orderprocessing.orderservice.tax;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "tax_rates")
public class TaxRate {
    @Id
    @Column(name = "country", nullable = false, length = 2)
    private String country;

    @Column(name = "rate_bps", nullable = false)
    private int rateBps;
}
