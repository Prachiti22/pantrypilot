/**
 * The team contract. Every type the three modules exchange lives here, and a
 * module may depend on this package but never on another module's internals.
 * Changing anything in this package needs agreement from all three members.
 */
@org.springframework.modulith.ApplicationModule(type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package com.pantrypilot.shared;
