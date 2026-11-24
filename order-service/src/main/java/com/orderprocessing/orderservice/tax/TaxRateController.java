package com.orderprocessing.orderservice.tax;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/tax-rates")
public class TaxRateController {
    private final TaxRateService taxRates;

    public TaxRateController(TaxRateService taxRates) {
        this.taxRates = taxRates;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> setIran(@RequestBody SetTaxRateRequest request) {
        TaxTable table = taxRates.setIranRate(request.rateBps());
        return Map.of("country", TaxTable.COUNTRY, "rateBps", table.rateBps());
    }

    public record SetTaxRateRequest(int rateBps) {
    }
}
