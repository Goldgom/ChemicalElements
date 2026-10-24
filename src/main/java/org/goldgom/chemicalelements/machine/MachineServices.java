package org.goldgom.chemicalelements.machine;

import java.util.*;
import java.util.function.Supplier;
import net.minecraft.world.item.ItemStack;
import org.goldgom.chemicalelements.api.data.RegistrySnapshot;
import org.goldgom.chemicalelements.api.instance.StackChemistry;
import org.goldgom.chemicalelements.api.recipe.*;
import org.goldgom.chemicalelements.api.reaction.ReactionProposal;

/** Wired once by the integration owner. No fall-back template chemistry or client authority. */
public final class MachineServices {
 private MachineServices(){}
 public interface Settlement {
  /** Pure preview. Complete output inventory includes products, residues and all returned containers.
   * Implementor must preserve instance ledgers, check element/charge balance and idempotent operation id.
   * Inputs stay exclusively in the controller until its single state swap. Never mutate passed snapshots. */
  List<ItemStack> materialize(UUID operation,List<ItemStack> input,ReactionProposal proposal,RegistrySnapshot snapshot);
 }
 public record Runtime(StackChemistry stacks,SynthesisEngine synthesis,Supplier<RegistrySnapshot> catalog,Settlement settlement){public Runtime{Objects.requireNonNull(stacks);Objects.requireNonNull(synthesis);Objects.requireNonNull(catalog);Objects.requireNonNull(settlement);}}
 private static volatile Runtime runtime;
 public static void install(Runtime value){runtime=Objects.requireNonNull(value);}
 /** Integration entry point after B's real stack/settlement owner is ready. No fallback ledger,
  * output factory or registry copy is created here. Each engine read captures one atomic bundle. */
 public static void installProduction(StackChemistry stacks,Settlement settlement){
  Objects.requireNonNull(stacks);Objects.requireNonNull(settlement);
  var catalog=org.goldgom.chemicalelements.data.AtomicChemistryCatalog.INSTANCE;
  var synthesis=org.goldgom.chemicalelements.reaction.ModeledSynthesisEngine.atomic(()->{
   var bundle=catalog.bundle();
   return new org.goldgom.chemicalelements.reaction.ModeledSynthesisEngine.VersionedLibrary(bundle.epoch(),bundle.reactions());
  });
  install(new Runtime(stacks,synthesis,catalog::snapshot,settlement));
 }
 public static Optional<Runtime> current(){return Optional.ofNullable(runtime);}
}
