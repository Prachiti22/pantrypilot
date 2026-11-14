package com.pantrypilot.core.stub;

import com.pantrypilot.shared.InventoryQuery;
import com.pantrypilot.shared.PendingChangeService;
import com.pantrypilot.shared.ProposedChange;
import com.pantrypilot.shared.StockItem;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

/**
 * PLACEHOLDER for Prachiti's confirm-before-apply queue and inventory. It keeps
 * proposals in memory and lets them be confirmed or rejected, but confirming
 * does not change any inventory: there is no ledger here yet.
 */
@Service
public class StubPendingChanges implements PendingChangeService, InventoryQuery {

    public enum State {
        PENDING, CONFIRMED, REJECTED
    }

    public record Pending(long id, State state, Instant submittedAt, ProposedChange change) {
    }

    private final Map<Long, Pending> pending = new ConcurrentHashMap<>();
    private final AtomicLong ids = new AtomicLong();

    @Override
    public long submit(ProposedChange change) {
        long id = ids.incrementAndGet();
        pending.put(id, new Pending(id, State.PENDING, Instant.now(), change));
        return id;
    }

    @Override
    public List<StockItem> available(long householdId, long userId) {
        return List.of();
    }

    public List<Pending> forHousehold(long householdId) {
        return pending.values().stream()
                .filter(p -> p.change().householdId() == householdId)
                .sorted(Comparator.comparingLong(Pending::id).reversed())
                .toList();
    }

    public Optional<Pending> find(long id) {
        return Optional.ofNullable(pending.get(id));
    }

    /** @return the updated entry, or empty when the id is unknown or it was already decided */
    public Optional<Pending> decide(long id, State decision) {
        Pending updated = pending.computeIfPresent(id, (key, current) -> current.state() == State.PENDING
                ? new Pending(current.id(), decision, current.submittedAt(), current.change())
                : current);
        return Optional.ofNullable(updated).filter(p -> p.state() == decision);
    }
}
