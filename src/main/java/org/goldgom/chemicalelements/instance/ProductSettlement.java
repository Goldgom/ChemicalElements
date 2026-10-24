package org.goldgom.chemicalelements.instance;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.goldgom.chemicalelements.api.chem.Composition;
import org.goldgom.chemicalelements.api.chem.MolAmount;
import org.goldgom.chemicalelements.api.data.RegistrySnapshot;
import org.goldgom.chemicalelements.api.fluid.SolutionState;
import org.goldgom.chemicalelements.api.instance.InstanceRecord;
import org.goldgom.chemicalelements.api.instance.Provenance;
import org.goldgom.chemicalelements.api.nuclear.NuclideInventory;
import org.goldgom.chemicalelements.api.reaction.ReactionProposal;
import org.goldgom.chemicalelements.api.thermal.ThermalState;
import org.goldgom.chemicalelements.machine.MachineServices;

/**
 * B's implementation of D's {@link MachineServices.Settlement}: turns a validated reaction proposal over
 * the controller's input instances into output stacks. Pure preview (inputs untouched); the controller
 * performs the single state swap. Matter balance: {@code sum(inputs) - consumed + products + byproducts}
 * must be non-negative per species and element/charge-equal to the inputs; otherwise it throws and the
 * controller keeps the inputs. Output identities derive from the operation id (idempotent replay).
 * <p>Carriers: a species whose atoms are a single element with charge 0 goes to that element's sample
 * item; every other species (compounds, ions, unconverted residue) goes to the registered residue carrier
 * item {@value #RESIDUE_CARRIER}. If that item is not registered, materialization is refused explicitly.
 */
public final class ProductSettlement implements MachineServices.Settlement {
    public static final ProductSettlement INSTANCE = new ProductSettlement();
    public static final String RESIDUE_CARRIER = "chemicalelements:material_sample";
    static final ResourceLocation WATER = ResourceLocation.fromNamespaceAndPath("chemicalelements", "water");

    private ProductSettlement() {}

    @Override
    public List<ItemStack> materialize(UUID operation, List<ItemStack> input, ReactionProposal proposal, RegistrySnapshot snapshot) {
        if (proposal.registryEpoch() != snapshot.epoch()) throw new IllegalStateException("stale proposal epoch");
        var instances = new ArrayList<InstanceRecord>();
        for (var s : input) {
            if (s.isEmpty()) continue;
            var l = StackLedgers.get(s).orElseThrow(() -> new IllegalStateException("input without instance ledger"));
            if (l.instances().size() != s.getCount()) throw new IllegalStateException("input ledger/count mismatch");
            instances.addAll(l.instances());
        }
        Composition total = Composition.EMPTY;
        BigDecimal energy = BigDecimal.ZERO, tw = BigDecimal.ZERO, w = BigDecimal.ZERO;
        var nuclides = new TreeMap<org.goldgom.chemicalelements.api.nuclear.NuclideId, MolAmount>();
        for (var r : instances) {
            total = total.add(LedgerOps.fullComposition(r, WATER));
            energy = energy.add(r.thermal().energyJ());
            var m = r.remaining().totalMol().decimal().max(BigDecimal.ONE.movePointLeft(18));
            tw = tw.add(r.thermal().temperatureK().multiply(m));
            w = w.add(m);
            r.nuclides().amounts().forEach((k, v) -> nuclides.merge(k, v, MolAmount::add));
        }
        Composition after;
        try {
            after = total.subtract(proposal.consumed()).add(proposal.products()).add(proposal.byproducts());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("proposal consumes more than the reserved inputs", ex);
        }
        if (!total.inventory(snapshot).equals(after.inventory(snapshot)))
            throw new IllegalStateException("proposal is not element/charge balanced against inputs");
        // reaction enthalpy is supplied/removed by the machine power ledger; stored heat stays with matter
        var temperature = w.signum() == 0 ? ThermalState.AMBIENT.temperatureK() : tw.divide(w, MathContext.DECIMAL64);
        var parents = instances.stream().map(InstanceRecord::id).toList();
        var groups = new TreeMap<String, TreeMap<ResourceLocation, MolAmount>>();
        for (var e : after.amounts().entrySet()) groups.computeIfAbsent(carrier(e.getKey(), snapshot), k -> new TreeMap<>()).put(e.getKey(), e.getValue());
        var origin = new Provenance("machine_reaction", proposal.rule().toString(), 0, snapshot.epoch(),
                Map.of("operation", operation.toString(), "basis", proposal.basis()));
        var out = new ArrayList<ItemStack>();
        int index = 0;
        var weights = new ArrayList<java.math.BigInteger>();
        for (int i = 0; i < Math.max(1, groups.size()); i++) weights.add(java.math.BigInteger.ONE);
        var energyParts = LedgerOps.splitDecimal(energy, weights);
        for (var g : groups.entrySet()) {
            Item item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(g.getKey()))
                    .orElseThrow(() -> new IllegalStateException("UNMODELED carrier: register " + g.getKey()));
            var nuc = index == 0 ? new NuclideInventory(nuclides) : NuclideInventory.EMPTY;
            var rec = new InstanceRecord(LedgerOps.derivedId("product|" + operation, parents, index), new Composition(g.getValue()), origin, 0,
                    new ThermalState(energyParts.get(index), temperature, Map.of(), 0), nuc, SolutionState.empty(), Composition.EMPTY);
            var stack = new ItemStack(item, 1);
            StackLedgers.set(stack, LedgerOps.of(0, List.of(rec)));
            out.add(stack);
            index++;
        }
        if (out.isEmpty() && !instances.isEmpty()) throw new IllegalStateException("reaction leaves no matter; refusing to delete inputs");
        return out;
    }

    static String carrier(ResourceLocation species, RegistrySnapshot snapshot) {
        var d = snapshot.species().get(species);
        if (d != null && d.charge() == 0 && d.atoms().size() == 1) {
            var element = d.atoms().keySet().iterator().next();
            var item = ResourceLocation.fromNamespaceAndPath(element.getNamespace(), "element_" + element.getPath());
            if (BuiltInRegistries.ITEM.containsKey(item)) return item.toString();
        }
        return RESIDUE_CARRIER;
    }
}
