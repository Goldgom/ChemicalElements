package org.goldgom.chemicalelements.api.tx;
public interface ChemistryTransaction {
 /** Pure simulation; must not allocate identities, advance RNG or dirty persistence. */
 TransactionResult preview();
 TransactionResult validate();
 TransactionResult commit();
}