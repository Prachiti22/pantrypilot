package com.pantrypilot.core.stub;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** PLACEHOLDER review-queue endpoints so the AI module can be demoed end to end. */
@RestController
@RequestMapping("/api/stub/pending")
public class StubPendingController {

    private final StubPendingChanges pending;

    public StubPendingController(StubPendingChanges pending) {
        this.pending = pending;
    }

    @GetMapping
    public List<StubPendingChanges.Pending> list(@RequestParam long householdId) {
        return pending.forHousehold(householdId);
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<StubPendingChanges.Pending> confirm(@PathVariable long id) {
        return decide(id, StubPendingChanges.State.CONFIRMED);
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<StubPendingChanges.Pending> reject(@PathVariable long id) {
        return decide(id, StubPendingChanges.State.REJECTED);
    }

    private ResponseEntity<StubPendingChanges.Pending> decide(long id, StubPendingChanges.State decision) {
        if (pending.find(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return pending.decide(id, decision)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(409).build());
    }
}
