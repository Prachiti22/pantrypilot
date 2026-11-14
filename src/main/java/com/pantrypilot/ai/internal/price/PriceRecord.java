package com.pantrypilot.ai.internal.price;

import com.pantrypilot.shared.PriceEstimate;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/** Cached ordering-out price of one dish in one city. */
@Entity
@Table(name = "ai_price_estimate")
public class PriceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dish_key", nullable = false)
    private String dishKey;

    @Column(name = "city_key", nullable = false)
    private String cityKey;

    @Column(name = "price_per_serving", nullable = false)
    private BigDecimal pricePerServing;

    @Column(nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PriceEstimate.Basis basis;

    @Column(name = "sample_count", nullable = false)
    private int sampleCount;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PriceRecord() {
    }

    PriceRecord(String dishKey, String cityKey) {
        this.dishKey = dishKey;
        this.cityKey = cityKey;
    }

    void set(BigDecimal pricePerServing, String currency, PriceEstimate.Basis basis, int sampleCount) {
        this.pricePerServing = pricePerServing;
        this.currency = currency;
        this.basis = basis;
        this.sampleCount = sampleCount;
        this.updatedAt = Instant.now();
    }

    BigDecimal pricePerServing() {
        return pricePerServing;
    }

    String currency() {
        return currency;
    }

    PriceEstimate.Basis basis() {
        return basis;
    }

    int sampleCount() {
        return sampleCount;
    }
}
