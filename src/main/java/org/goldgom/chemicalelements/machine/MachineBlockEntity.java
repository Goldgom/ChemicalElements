package org.goldgom.chemicalelements.machine;

import java.math.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.goldgom.chemicalelements.api.chem.Composition;
import org.goldgom.chemicalelements.api.recipe.*;

/** One persistent owner: reservations never copy material to a second inventory.
 * Input slots are frozen while running; cancellation merely unlocks the same instances. */
public final class MachineBlockEntity extends BlockEntity implements MenuProvider {
 public static final int MAX_SIDE=9,OUTPUT_SLOTS=9;
 private int side=5,elapsed,duration;
 private boolean running,dropped;
 private long revision,controlMutationRevision;
 public final MachineProjection projection=new MachineProjection(this);
 long controlMutationRevision(){return controlMutationRevision;}
 @Override public void setChanged(){controlMutationRevision=Math.incrementExact(controlMutationRevision);super.setChanged();}
 void projectionDirty(){super.setChanged();}
 MachineInventory ownedInventory(){return inventory;}
 MachineTransactions.ControlImage controlImage(){return new MachineTransactions.ControlImage(running?"running":"idle",elapsed,duration,Map.of("side",Integer.toString(side),"dropped",Boolean.toString(dropped),"job_revision",Long.toString(revision),"catalog_hash",catalogHash,"operation",operation.toString(),"message",message,"thermal_millij",Long.toString(power.thermalMilliJ()),"pressure_pa",Integer.toString(power.pressurePa())));}
 private String catalogHash="",message="idle";
 private UUID operation=UUID.randomUUID();
 /** Persistent owner identity, distinct from a menu session or the job/display revision. */
 private UUID incarnation=UUID.randomUUID();
 private final Set<MachineMenu> transactionMenus=Collections.newSetFromMap(new IdentityHashMap<>());
 public UUID incarnation(){return incarnation;}
 void trackMenu(MachineMenu menu){transactionMenus.add(menu);}
 void untrackMenu(MachineMenu menu){transactionMenus.remove(menu);}
 private void invalidateMenus(){for(var menu:List.copyOf(transactionMenus))menu.invalidateTransactionSession();transactionMenus.clear();}
 @Override public void setRemoved(){invalidateMenus();projection.lifecycle();super.setRemoved();}
 @Override public void onChunkUnloaded(){invalidateMenus();projection.lifecycle();super.onChunkUnloaded();}
 @Override public void clearRemoved(){super.clearRemoved();projection.lifecycle();}
 @Override public void onLoad(){super.onLoad();projection.lifecycle();}
 private SynthesisResult preview;
 public final MachinePower power=new MachinePower(this::setChanged);
 public final MaterialTank tank=new MaterialTank(org.goldgom.chemicalelements.api.chem.MolAmount.parseDecimal("10000"),this::setChanged);
 private final MachineInventory inventory=new MachineInventory(this::setChanged);
 public final ContainerData data=new ContainerData(){
  public int get(int i){return switch(i){case 0->elapsed;case 1->duration;case 2->running?1:0;case 3->side;case 4->preview==null?2:preview.status().ordinal();case 5->tank.fillBasisPoints();default->0;};}
  public void set(int i,int value){} public int getCount(){return 6;}
 };
 public MachineBlockEntity(BlockPos pos,BlockState state){super(MachineBootstrap.CONTROLLER.get(),pos,state);}
 public Container inventory(){return inventory;} public int side(){return side;} public boolean running(){return running;}
 public String message(){return message;} public long revision(){return revision;}
 public MachineKind kind(){return ((MachineBlock)getBlockState().getBlock()).kind();}
 public void configureGrid(int width){if(width<1||width>MAX_SIDE)throw new IllegalArgumentException("grid range 1..9");if(running||!inventory.isEmpty())throw new IllegalStateException("grid must be empty");if(side!=width)invalidateMenus();side=width;setChanged();}
 public boolean inputSlot(int index){return index<side*side;}
 public boolean mayEdit(int index){return !running&&inputSlot(index);}
 private SynthesisResult evaluate(MachineServices.Runtime services){
  var catalog=services.catalog().get();var slots=new ArrayList<Composition>();
  for(int i=0;i<side*side;i++){
   var stack=inventory.getItem(i);if(stack.isEmpty()){slots.add(Composition.EMPTY);continue;}
   var ledger=services.stacks().snapshot(stack);
   if(ledger.isEmpty())return new SynthesisResult(SynthesisResult.Status.UNMODELED,Optional.empty(),List.of("Input instance ledger unavailable"));
   if(ledger.get().instances().size()!=stack.getCount())return new SynthesisResult(SynthesisResult.Status.UNMODELED,Optional.empty(),List.of("Input ledger/count mismatch; inputs retained"));
   Composition value=Composition.EMPTY;for(var instance:ledger.get().instances())value=value.add(fullComposition(instance));
   slots.add(value);
  }
  var aggregate=slots.stream().reduce(Composition.EMPTY,Composition::add);
  // Actual energy/thermal ports must be connected before non-ambient setpoints can be claimed.
  var conditions=kind().conditions(aggregate,power.temperature(),power.pressure(),power.available(),Set.of());
  return services.synthesis().evaluate(new SynthesisRequest(slots,side,side,conditions,catalog.epoch()),catalog);
 }
 public void start(){
  if(level==null||level.isClientSide||running)return;
  var structure=MultiblockValidator.inspect(level,worldPosition,kind(),MachineBlock.quarterTurns(getBlockState()));
  if(structure.state()!=MultiblockValidator.State.VALID){message="Structure: "+structure.summary();setChanged();return;}
  // Pure materialize previews are not journal commits. Keep production off until B installs
  // its durable prepare/commit/cancel/recover owner path (no production switch in this port).
  if(!productionEnabled()){message="paused: durable machine transactions unavailable";setChanged();return;}
  var services=MachineServices.current();if(services.isEmpty()){message="UNMODELED: chemistry services not connected";setChanged();return;}
  preview=evaluate(services.get());message=String.join("; ",preview.reasons());
  if(preview.status()!=SynthesisResult.Status.READY){setChanged();return;}
  for(int i=MAX_SIDE*MAX_SIDE;i<inventory.getContainerSize();i++)if(!inventory.getItem(i).isEmpty()){message="Output must be empty";setChanged();return;}
  var p=preview.proposal().orElseThrow();duration=Math.max(1,p.durationSeconds().multiply(BigDecimal.valueOf(20)).setScale(0,RoundingMode.CEILING).intValueExact());
  operation=UUID.randomUUID();catalogHash=services.get().catalog().get().contentHash();elapsed=0;running=true;revision++;setChanged();
 }
 public void cancel(){if(level!=null&&level.isClientSide)return;running=false;elapsed=0;duration=0;preview=null;message="cancelled; original instances retained";revision++;setChanged();}
 public static void serverTick(Level level,BlockPos pos,BlockState state,MachineBlockEntity machine){machine.tick();machine.syncVisual();}
 private void syncVisual(){
  if(level==null||level.isClientSide)return;
  int visual=message.startsWith("Structure:")||message.startsWith("paused: structure")?3:message.startsWith("paused:")?2:running?1:0;
  var state=getBlockState();if(state.getValue(MachineBlock.VISUAL)!=visual)level.setBlock(worldPosition,state.setValue(MachineBlock.VISUAL,visual),3);
 }
 private static boolean productionEnabled(){return false;}
 private void tick(){
  if(!productionEnabled())return;
  power.tick(kind());
  MachineTransport.tick(this);
  if(!running)return;
  var structure=MultiblockValidator.inspect(level,worldPosition,kind(),MachineBlock.quarterTurns(getBlockState()));
  if(structure.state()!=MultiblockValidator.State.VALID){message="paused: structure "+structure.summary();return;}
  var runtime=MachineServices.current();if(runtime.isEmpty()){message="paused: services unavailable";return;}
  var services=runtime.get();if(!catalogHash.equals(services.catalog().get().contentHash())){cancel();message="registry changed: reservation released";return;}
  if(preview==null)preview=evaluate(services);
  if(preview.status()!=SynthesisResult.Status.READY){message="paused: "+String.join("; ",preview.reasons());return;}
  if(elapsed<duration){message="processing";elapsed++;setChanged();return;}
  try{
   // Revalidate on the commit tick, including any changed physical conditions.
   var fresh=evaluate(services);if(fresh.status()!=SynthesisResult.Status.READY){preview=fresh;message="paused: conditions changed";return;}
   var input=new ArrayList<ItemStack>();for(int i=0;i<side*side;i++)input.add(inventory.getItem(i).copy());
   var output=List.copyOf(services.settlement().materialize(operation,List.copyOf(input),fresh.proposal().orElseThrow(),services.catalog().get()));
   if(output.size()>OUTPUT_SLOTS){message="paused: output capacity";return;}
   for(var stack:output)if(stack.isEmpty()||stack.getCount()>stack.getMaxStackSize())throw new IllegalStateException("invalid output stack");
   var snapshot=services.catalog().get();
   Composition before=Composition.EMPTY,after=Composition.EMPTY;
   var identities=new HashSet<UUID>();
   var inputInstances=new ArrayList<org.goldgom.chemicalelements.api.instance.InstanceRecord>();
   var outputInstances=new ArrayList<org.goldgom.chemicalelements.api.instance.InstanceRecord>();
   for(var stack:input)if(!stack.isEmpty()){
    var ledger=services.stacks().snapshot(stack).orElseThrow(()->new IllegalStateException("missing input ledger"));ledger.validateCount(stack.getCount());inputInstances.addAll(ledger.instances());
    for(var instance:ledger.instances())before=before.add(fullComposition(instance));
   }
   for(var stack:output){
    var ledger=services.stacks().snapshot(stack).orElseThrow(()->new IllegalStateException("missing product ledger"));ledger.validateCount(stack.getCount());outputInstances.addAll(ledger.instances());
    for(var instance:ledger.instances())if(!identities.add(instance.id()))throw new IllegalStateException("duplicate product identity");
    for(var instance:ledger.instances())after=after.add(fullComposition(instance));
   }
   if(!before.inventory(snapshot).equals(after.inventory(snapshot)))throw new IllegalStateException("materialization failed element/charge conservation; inputs retained");
   MaterialStateBalance.requireUnchanged(inputInstances,outputInstances);
   for(int i=MAX_SIDE*MAX_SIDE;i<inventory.getContainerSize();i++)if(!inventory.getItem(i).isEmpty()){message="paused: output occupied";return;}
   int energyCost=fresh.proposal().orElseThrow().energyJ().max(BigDecimal.ZERO).setScale(0,RoundingMode.CEILING).intValueExact();
   // materialize is only a preview, never a durable transaction authorization.
   // No electrical spend or inventory clear is allowed on this legacy path.
   message="paused: durable machine commit required";setChanged();
  }catch(RuntimeException failure){message="paused: "+failure.getMessage();setChanged();}
 }
 private static Composition fullComposition(org.goldgom.chemicalelements.api.instance.InstanceRecord instance){
  var value=instance.remaining().add(instance.adsorbed()).add(instance.poreSolution().dissolved()).add(instance.poreSolution().suspended());
  if(!instance.poreSolution().waterMol().isZero())value=value.add(new Composition(Map.of(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("chemicalelements","water"),instance.poreSolution().waterMol())));
  return value;
 }
 public void dropContentsOnce(){if(dropped||level==null||level.isClientSide)return;dropped=true;Containers.dropContents(level,worldPosition,inventory);inventory.clearContent();setChanged();}
 @Override public Component getDisplayName(){return Component.translatable("block.chemicalelements."+kind().id());}
 @Override public AbstractContainerMenu createMenu(int id,Inventory playerInventory,Player player){return new MachineMenu(id,playerInventory,this);}
 @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){super.saveAdditional(tag,registries);tag.putInt("schema_version",1);ContainerHelper.saveAllItems(tag,inventory.items(),registries);tag.putInt("side",side);tag.putBoolean("running",running);tag.putInt("elapsed",elapsed);tag.putInt("duration",duration);tag.putLong("revision",revision);tag.putString("catalog_hash",catalogHash);tag.putUUID("operation",operation);tag.putUUID("owner_incarnation",incarnation);tag.putString("message",message);power.save(tag);tag.put("material_tank",tank.save());projection.save(tag);}
 @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){super.loadAdditional(tag,registries);if(tag.getInt("schema_version")!=1)throw new IllegalStateException("Unsupported machine schema");if(tag.contains("Items")){var items=NonNullList.withSize(MAX_SIDE*MAX_SIDE+OUTPUT_SLOTS,ItemStack.EMPTY);ContainerHelper.loadAllItems(tag,items,registries);inventory.publish(items);}else{var legacy=new SimpleContainer(MAX_SIDE*MAX_SIDE+OUTPUT_SLOTS);legacy.fromTag(tag.getList("inventory",10),registries);var items=NonNullList.withSize(MAX_SIDE*MAX_SIDE+OUTPUT_SLOTS,ItemStack.EMPTY);for(int i=0;i<items.size();i++)items.set(i,legacy.getItem(i));inventory.publish(items);}side=tag.getInt("side");if(side<1||side>MAX_SIDE)throw new IllegalStateException("Invalid machine grid");running=tag.getBoolean("running");elapsed=tag.getInt("elapsed");duration=tag.getInt("duration");if(elapsed<0||duration<0||elapsed>duration)throw new IllegalStateException("Invalid job progress");revision=tag.getLong("revision");catalogHash=tag.getString("catalog_hash");operation=tag.hasUUID("operation")?tag.getUUID("operation"):UUID.randomUUID();invalidateMenus();incarnation=tag.hasUUID("owner_incarnation")?tag.getUUID("owner_incarnation"):incarnation;message=tag.getString("message");power.load(tag);if(tag.contains("material_tank"))tank.load(tag.getCompound("material_tank"));preview=null;projection.load(tag);setChanged();}
}
