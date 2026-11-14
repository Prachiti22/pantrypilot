package com.pantrypilot;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Checks the module boundaries: the ai, core and insights modules may use the
 * shared contract, but none of them may reach into another module's internals.
 */
class ModularityTest {

    @Test
    void modulesOnlyDependOnTheSharedContract() {
        ApplicationModules.of(PantryPilotApplication.class).verify();
    }
}
