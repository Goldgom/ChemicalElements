package org.goldgom.chemicalelements.thermal;

import java.math.*;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import org.goldgom.chemicalelements.api.chem.*;
import org.goldgom.chemicalelements.api.instance.InstanceRecord;

/** Read-only plans. Neither this plan nor a caller-provided reference authorizes a transaction.
 * B must re-read the complete expected instance and heat-owner state, compare epoch/reference,
 * and atomically settle all participants. No FE conversion, volume/EOS or world projection. */
public final class EnthalpyPlanner {
    private static final BigDecimal ZERO=BigDecimal.ZERO;
    private EnthalpyPlanner() {}
    public record Request(ResourceLocation modelId, long catalogEpoch, String energyReference,
            BigDecimal pressurePa, BigDecimal targetK, BigDecimal heatingBudgetJ, BigDecimal coolingBudgetJ) {}
    public record Plan(String status, String reason, InstanceRecord expected, long catalogEpoch,
            String catalogHash, PhaseLibrary expectedLibrary, ResourceLocation modelId, String energyReference, PhaseModel.Domain domain,
            Composition allocation, BigDecimal temperatureK, BigDecimal materialEnergyJ,
            BigDecimal heatSourceDebitJ, BigDecimal heatSinkCreditJ) {
        public boolean ready(){return status.equals("READY");}
        /** Reject stale/full-state mismatch before any caller spends power or projects a carrier. */
        public boolean matches(InstanceRecord authoritative, PhaseLibrary current, String reference) {
            return ready() && current == expectedLibrary && expected.equals(authoritative) && current.catalog().epoch()==catalogEpoch
                    && current.catalog().contentHash().equals(catalogHash) && Objects.equals(reference,energyReference);
        }
    }
    public static Plan plan(InstanceRecord input, Request r, PhaseLibrary library) {
        Objects.requireNonNull(input);Objects.requireNonNull(r);Objects.requireNonNull(library);
        var m=library.get(r.modelId());
        if(r.catalogEpoch()!=library.catalog().epoch())return reject("STALE_CATALOG","Catalog epoch changed",input,r,library,m);
        if(r.pressurePa()==null || r.pressurePa().signum()<=0)return reject("UNMODELED","Explicit positive Pa required",input,r,library,m);
        if(r.targetK()==null || r.targetK().signum()<=0 || r.heatingBudgetJ()==null || r.coolingBudgetJ()==null
                || r.heatingBudgetJ().signum()<0 || r.coolingBudgetJ().signum()<0)return reject("INVALID_REQUEST","Invalid temperature/heat budgets",input,r,library,m);
        if(m==null)return reject("UNKNOWN","No model for requested identity",input,r,library,null);
        if(!m.hasEnthalpy())return reject(m.kind()==PhaseModel.Kind.UNKNOWN?"UNKNOWN":"UNMODELED",
                "No complete enthalpy reference/Cp model: "+m.reason(),input,r,library,m);
        if(!Objects.equals(r.energyReference(),m.energyReference()))return reject("UNMODELED","Legacy or different energy reference; no implicit migration of AMBIENT zero",input,r,library,m);
        if(!input.poreSolution().equals(org.goldgom.chemicalelements.api.fluid.SolutionState.empty()) || !input.adsorbed().equals(Composition.EMPTY))
            return reject("UNMODELED","Pore/adsorbed mixture heat is not modeled",input,r,library,m);
        var amounts=input.remaining().amounts();
        if(amounts.isEmpty() || amounts.keySet().stream().anyMatch(id->!id.equals(m.lowSpecies())&&!id.equals(m.highSpecies())))
            return reject("UNMODELED","Only the exact pure chemical identity and declared phase pair is modeled",input,r,library,m);
        BigDecimal initialK=input.thermal().temperatureK();
        if(!m.domain().contains(initialK,r.pressurePa()) || !m.domain().contains(r.targetK(),r.pressurePa()))
            return reject("UNMODELED","Outside declared isobaric T/P domain",input,r,library,m);
        MolAmount low=amounts.getOrDefault(m.lowSpecies(),MolAmount.ZERO), high=amounts.getOrDefault(m.highSpecies(),MolAmount.ZERO);
        if((!low.isZero() && initialK.compareTo(m.transitionK())>0) || (!high.isZero() && initialK.compareTo(m.transitionK())<0))
            return reject("UNMODELED","Input phase outside equilibrium branch (no metastability model)",input,r,library,m);
        BigDecimal initialH=low.decimal().multiply(m.molarEnthalpy(false,initialK)).add(high.decimal().multiply(m.molarEnthalpy(true,initialK)));
        if(initialH.compareTo(input.thermal().energyJ())!=0)return reject("ENERGY_MISMATCH","Stored energy does not equal declared reference enthalpy",input,r,library,m);
        if(!validNuclides(input,library))return reject("NUCLIDE_MISMATCH","Isotope amounts exceed their actual element atoms (or unknown Z)",input,r,library,m);
        int direction=r.targetK().compareTo(initialK);
        if(direction==0)return reject("NO_CHANGE","Same temperature; latent plateau direction must be selected by a target across boundary",input,r,library,m);
        boolean heating=direction>0;
        BigDecimal budget=heating?r.heatingBudgetJ():r.coolingBudgetJ();
        BigDecimal temperature=initialK, spent=ZERO;
        MolAmount total=low.add(high);
        boolean crossing=heating ? r.targetK().compareTo(m.transitionK())>0 && !low.isZero()
                : r.targetK().compareTo(m.transitionK())<0 && !high.isZero();
        BigDecimal firstTarget=crossing?m.transitionK():r.targetK();
        // A two-phase plateau cannot sensibly move before finishing the applicable latent conversion.
        if(amounts.size()>1 && !crossing)return reject("UNMODELED","Two-phase plateau needs target across its boundary",input,r,library,m);
        BigDecimal cp=high.isZero()?m.cpLow():m.cpHigh();
        BigDecimal sensible=total.decimal().multiply(cp).multiply(firstTarget.subtract(temperature).abs());
        if(sensible.compareTo(budget)>0)return reject("INSUFFICIENT_HEAT","Cannot reach requested sensible step; all input retained",input,r,library,m);
        spent=sensible;temperature=firstTarget;
        if(crossing) {
            MolAmount convertible=heating?low:high;
            BigInteger quanta=budget.subtract(spent).movePointRight(MolAmount.SCALE)
                    .divideToIntegralValue(m.latentJMol()).toBigInteger().min(convertible.quanta());
            var moved=new MolAmount(quanta);
            spent=spent.add(moved.decimal().multiply(m.latentJMol()));
            if(heating){low=low.subtract(moved);high=high.add(moved);}else{high=high.subtract(moved);low=low.add(moved);}
            if(moved.equals(convertible)) {
                BigDecimal next=total.decimal().multiply(heating?m.cpHigh():m.cpLow()).multiply(r.targetK().subtract(temperature).abs());
                // Exact target only; unspent sub-step heat remains with its finite source/sink, never discarded.
                if(next.compareTo(budget.subtract(spent))<=0){spent=spent.add(next);temperature=r.targetK();}
            }
        }
        if(spent.signum()==0)return reject("NO_CHANGE","No affordable mol quantum; budget retained",input,r,library,m);
        Composition allocation=new Composition(Map.of(m.lowSpecies(),low,m.highSpecies(),high));
        BigDecimal finalH=low.decimal().multiply(m.molarEnthalpy(false,temperature)).add(high.decimal().multiply(m.molarEnthalpy(true,temperature)));
        BigDecimal delta=finalH.subtract(initialH), signedSpent=heating?spent:spent.negate();
        if(delta.compareTo(signedSpent)!=0 || !allocation.totalMol().equals(input.remaining().totalMol())
                || !allocation.inventory(library.catalog()).equals(input.remaining().inventory(library.catalog()))
                || allocation.mass(library.catalog()).compareTo(input.remaining().mass(library.catalog()))!=0)
            return reject("CONSERVATION_FAILURE","Exact energy/mol/element/charge/mass check failed",input,r,library,m);
        // Entire isotope inventory remains on the SAME instance; no invented per-phase isotope split.
        return new Plan("READY","Pure equilibrium constant-Cp approximation; exact latent quanta; isotope inventory unchanged on same instance; requires atomic finite heat-owner settlement",
                input,library.catalog().epoch(),library.catalog().contentHash(),library,m.id(),m.energyReference(),m.domain(),allocation,temperature,finalH,
                heating?spent:ZERO,heating?ZERO:spent);
    }
    private static boolean validNuclides(InstanceRecord input,PhaseLibrary library) {
        var atoms=input.remaining().inventory(library.catalog()).atoms();var sums=new HashMap<Integer,BigInteger>();
        for(var entry:input.nuclides().amounts().entrySet()) {
            if(entry.getKey().z()==0)return false;
            sums.merge(entry.getKey().z(),entry.getValue().quanta(),BigInteger::add);
        }
        for(var e:sums.entrySet()) {
            var element=library.catalog().elements().values().stream().filter(d->d.atomicNumber()==e.getKey()).findFirst();
            if(element.isEmpty() || e.getValue().compareTo(atoms.getOrDefault(element.get().id(),BigInteger.ZERO))>0)return false;
        }
        return true;
    }
    private static Plan reject(String status,String reason,InstanceRecord input,Request r,PhaseLibrary lib,PhaseModel m) {
        return new Plan(status,reason,input,lib.catalog().epoch(),lib.catalog().contentHash(),lib,r.modelId(),r.energyReference(),m==null?null:m.domain(),
                input.remaining(),input.thermal().temperatureK(),input.thermal().energyJ(),ZERO,ZERO);
    }
    /** Reuse existing limited water planner. This is latent-only, never a complete enthalpy READY plan. */
    public static PhaseProcess.Plan legacyWater(Composition input,ResourceLocation modelId,BigDecimal kelvin,
            BigDecimal pressurePa,BigDecimal heatingJ,BigDecimal coolingJ,MolAmount capacity,PhaseLibrary lib) {
        var m=lib.get(modelId);
        if(m==null || m.kind()!=PhaseModel.Kind.LEGACY_WATER_LATENT || input.amounts().size()!=1)
            return new PhaseProcess.Plan("UNMODELED",input,Composition.EMPTY,ZERO,ZERO,"Missing legacy adapter or non-pure input; use DiluteWaterPhase only within its existing domain");
        return PhaseProcess.apply(input,PhaseLibrary.legacy(m.legacyOperation()),kelvin,pressurePa,heatingJ,coolingJ,capacity,lib.catalog());
    }
}
