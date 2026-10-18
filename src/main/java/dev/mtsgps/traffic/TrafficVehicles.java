package dev.mtsgps.traffic;

import dev.mtsgps.nav.Position;
import mcinterface1201.WrapperEntity;
import mcinterface1201.WrapperWorld;
import minecrafttransportsimulator.baseclasses.ComputedVariable;
import minecrafttransportsimulator.baseclasses.Point3D;
import minecrafttransportsimulator.entities.instances.*;
import minecrafttransportsimulator.items.components.AItemPart;
import minecrafttransportsimulator.items.instances.*;
import minecrafttransportsimulator.mcinterface.IWrapperPlayer;
import minecrafttransportsimulator.mcinterface.InterfaceManager;
import minecrafttransportsimulator.packloading.PackParser;
import minecrafttransportsimulator.systems.ConfigSystem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import java.util.*;

/** Spawns real, assembled MTS entities and mounts a real vanilla mob in the controller seat. */
public final class TrafficVehicles {
    public static final String MARKER="mtsgps_traffic",DRIVER_TAG="mtsgps_traffic_driver";
    private static final Set<String> MODELS=Set.of("merc230","fordmustang69","scout");
    public record Spawned(EntityVehicleF_Physics car,PartSeat seat,Villager driver,double wheelbase,TrafficControl.BrakingMemory braking) {}

    public static Spawned spawn(ServerLevel level,IWrapperPlayer placingPlayer,Position at,double yaw,Random random) {
        var vehicles=new TreeMap<String,List<ItemVehicle>>();
        var parts=new ArrayList<AItemPart>();
        for(var item:PackParser.getAllPackItems()) {
            if(!"mtsofficialpack".equals(item.definition.packID)) continue;
            if(item instanceof ItemVehicle vehicle && MODELS.contains(vehicle.definition.systemName)
                    && vehicle.definition.motorized!=null && !vehicle.definition.motorized.isAircraft && !vehicle.definition.motorized.isTrailer)
                vehicles.computeIfAbsent(vehicle.definition.systemName,key->new ArrayList<>()).add(vehicle);
            if(item instanceof AItemPart part) parts.add(part);
        }
        if(vehicles.isEmpty()) throw new IllegalStateException("Official Pack road cars were not loaded");
        var models=new ArrayList<>(vehicles.values());
        var paints=models.get(random.nextInt(models.size()));
        var item=paints.get(random.nextInt(paints.size()));
        // A null constructor player avoids MTS's first-tick placement boost: the road records chassis height already.
        var car=new EntityVehicleF_Physics(WrapperWorld.getWrapperFor(level),null,item,null);
        Villager mob=null;
        try {
            car.position.set(at.x(),at.y()+.15,at.z()); car.prevPosition.set(car.position);
            car.orientation.setToAngles(new Point3D(0,yaw,0)); car.prevOrientation.set(car.orientation);
            car.motion.set(0,0,0); car.prevMotion.set(car.motion);
            car.getOrCreateVariable(MARKER).setTo(1,false);
            car.world.spawnEntity(car);
            car.addPartsPostAddition(placingPlayer,null);
            // Wheel/seat slots on these actual pack vehicles are intentionally empty when placed by a player.
            for(int i=0;i<car.definition.parts.size();i++) {
                var slot=car.definition.parts.get(i);
                if(car.partsInSlots.get(i)!=null || slot.isSpare) continue;
                boolean wheel=slot.types!=null && slot.types.contains("ground_wheel");
                boolean seat=slot.isController && slot.types!=null && slot.types.contains("seat");
                boolean engine=slot.types!=null && slot.types.contains("engine_car");
                if(!wheel && !seat && !engine) continue;
                var candidates=new ArrayList<AItemPart>();
                for(var part:parts) {
                    if((wheel && part instanceof ItemPartGroundDevice && part.definition.ground!=null && part.definition.ground.isWheel
                            || seat && part instanceof ItemPartSeat || engine && part instanceof ItemPartEngine)
                            && part.isPartValidForPackDef(slot,car.subDefinition,!slot.bypassSlotMinMax)) candidates.add(part);
                }
                if(candidates.isEmpty()) throw new IllegalStateException("No compatible traffic part for "+item.definition.systemName+" slot "+i);
                var part=candidates.get(random.nextInt(candidates.size()));
                if(car.addPartFromStack(part.getNewStack(null),placingPlayer,i,false,true)==null)
                    throw new IllegalStateException("Traffic part could not be installed");
            }
            car.updatePartList();
            PartSeat driverSeat=null; int wheels=0; double minZ=Double.POSITIVE_INFINITY,maxZ=Double.NEGATIVE_INFINITY;
            for(var part:car.allParts) {
                if(part instanceof PartSeat seat && seat.placementDefinition.isController) driverSeat=seat;
                if(part instanceof PartGroundDevice && !part.placementDefinition.isSpare) {
                    wheels++; minZ=Math.min(minZ,part.placementDefinition.pos.z); maxZ=Math.max(maxZ,part.placementDefinition.pos.z);
                }
            }
            if(driverSeat==null || wheels<4 || car.engines.isEmpty()) throw new IllegalStateException("Traffic car is missing driving parts");
            String fuel="";
            double potency=Double.NEGATIVE_INFINITY;
            var fuels=ConfigSystem.settings.fuel.fuels.get(car.engines.get(0).definition.engine.fuelType);
            if(fuels!=null) for(var entry:fuels.entrySet()) {
                if(InterfaceManager.coreInterface.isFluidValid(entry.getKey()) && entry.getValue()>potency) {
                    fuel=entry.getKey(); potency=entry.getValue();
                }
            }
            if(fuel.isEmpty()) throw new IllegalStateException("No registered fuel for the selected traffic engine");
            // Clients simulate MTS engines too; give them real tank contents instead of relying on a server-only creative flag.
            car.fuelTank.manuallySet(fuel,"",car.fuelTank.getMaxLevel());
            car.isCreative=true; car.electricPower=12;
            set(car.parkingBrakeVar,0); set(car.brakeVar,1); set(car.throttleVar,0);
            set(car.headLightVar,1);
            for(var engine:car.engines) {
                set(engine.isAutomaticVar,1); set(engine.currentGearVar,1); engine.autoStartEngine();
            }
            mob=EntityType.VILLAGER.create(level);
            if(mob==null) throw new IllegalStateException("Villager could not be created");
            mob.moveTo(at.x(),at.y()+1,at.z(),(float)-yaw,0);
            mob.setNoAi(true); mob.setPersistenceRequired(); mob.addTag(DRIVER_TAG);
            if(!level.addFreshEntity(mob) || !driverSeat.setRider(WrapperEntity.getWrapperFor(mob),true))
                throw new IllegalStateException("Villager could not enter the driver seat");
            return new Spawned(car,driverSeat,mob,Math.max(1.5,maxZ-minZ),new TrafficControl.BrakingMemory());
        } catch(RuntimeException failure) {
            if(mob!=null) mob.discard();
            if(car.isValid) car.remove();
            throw failure;
        }
    }
    public static void set(ComputedVariable variable,double value) {
        if(Math.abs(variable.currentValue-value)>.005) variable.setTo(value,true);
    }
    public static void remove(Spawned spawned) {
        spawned.driver().discard();
        if(spawned.car().isValid) spawned.car().remove();
    }
    private TrafficVehicles() {}
}
