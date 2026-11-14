package com.pantrypilot.shared;

import java.util.List;

/** Provided by the core module (Prachiti). Used by the insights module. */
public interface InventoryQuery {

    /** Current stock of a household, leaving out other members' private items. */
    List<StockItem> available(long householdId, long userId);
}
