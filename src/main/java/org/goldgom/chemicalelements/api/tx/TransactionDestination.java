package org.goldgom.chemicalelements.api.tx;

/** Identity only: not a reservation, inventory, receipt or proof of durable delivery.
 * Both destinations must use the same authoritative journal and operation UUID space.
 * Existing machine transaction ports require an explicit adapter; this type does not install one.
 */
public sealed interface TransactionDestination permits PlayerMenuDestination, MachineDestination {}
