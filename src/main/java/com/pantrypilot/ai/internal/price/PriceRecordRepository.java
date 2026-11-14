package com.pantrypilot.ai.internal.price;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface PriceRecordRepository extends JpaRepository<PriceRecord, Long> {

    Optional<PriceRecord> findByDishKeyAndCityKey(String dishKey, String cityKey);
}
