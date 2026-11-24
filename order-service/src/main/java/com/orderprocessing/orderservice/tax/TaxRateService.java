package com.orderprocessing.orderservice.tax;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaxRateService {
    private final TaxRateRepository rates;

    public TaxRateService(TaxRateRepository rates) {
        this.rates = rates;
    }

    @Transactional(readOnly = true)
    public TaxTable current() {
        return rates.findById(TaxTable.COUNTRY)
                .map(row -> new TaxTable(row.getRateBps()))
                .orElseGet(TaxTable::none);
    }

    @Transactional
    public TaxTable setIranRate(int rateBps) {
        TaxTable checked = new TaxTable(rateBps);
        TaxRate row = rates.findById(TaxTable.COUNTRY).orElseGet(TaxRate::new);
        row.setCountry(TaxTable.COUNTRY);
        row.setRateBps(checked.rateBps());
        rates.save(row);
        return checked;
    }
}
