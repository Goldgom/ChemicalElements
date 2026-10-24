package org.goldgom.chemicalelements.api.fluid;
import java.math.BigDecimal;
import java.util.Optional;
import org.goldgom.chemicalelements.api.chem.Composition;
import org.goldgom.chemicalelements.api.tx.*;
public interface SolutionAccess {
 Optional<SolutionState> snapshot(SolutionLocation location);
 ChemistryTransaction previewTransfer(SolutionLocation from,SolutionLocation to,BigDecimal litres);
 default TransactionResult commit(ChemistryTransaction plan){return plan.commit();}
 ChemistryTransaction dissolve(SolutionLocation target,Composition solid,BigDecimal seconds);
}