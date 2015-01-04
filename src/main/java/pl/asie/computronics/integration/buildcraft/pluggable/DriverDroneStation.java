package pl.asie.computronics.integration.buildcraft.pluggable;

import buildcraft.api.transport.IPipeTile;
import buildcraft.api.transport.pluggable.PipePluggable;
import li.cil.oc.api.Network;
import li.cil.oc.api.internal.Drone;
import li.cil.oc.api.machine.Arguments;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.prefab.ManagedEnvironment;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * @author Vexatos
 */
public class DriverDroneStation extends ManagedEnvironment {

	protected final Drone drone;

	protected boolean isDocking = false;
	protected boolean isDocked = false;
	protected IPipeTile pipe;
	protected ForgeDirection side = ForgeDirection.UNKNOWN;
	private int[] pipevec;

	public DriverDroneStation(Drone drone) {
		this.drone = drone;
		this.setNode(Network.newNode(this, Visibility.Neighbors).
			withComponent("docking").
			create());
	}

	@Override
	public boolean canUpdate() {
		return true;
	}

	@Override
	public void update() {
		if((isDocking || isDocked) && pipe == null && pipevec != null) {
			TileEntity tile = drone.world().getTileEntity(pipevec[0], pipevec[1], pipevec[2]);
			if(tile instanceof IPipeTile) {
				pipe = (IPipeTile) tile;
			} else {
				isDocked = false;
				isDocking = false;
				side = null;
			}
		}
		li.cil.oc.common.entity.Drone droneEntity = (li.cil.oc.common.entity.Drone) drone;
		if(isDocking && droneEntity.motionX == 0 && droneEntity.motionY == 0 && droneEntity.motionZ == 0) {
			isDocking = false;
			isDocked = true;
		}
	}

	@Callback(doc = "function(slot:number[,maxAmount:number]):number; drops an item into the attached pipe if docked; Returns the amount of items dropped on success, 0 and an error message otherwise")
	public Object[] dropItem(Context context, Arguments args) {
		if(!isDocked || pipe == null) {
			if(isDocking) {
				return new Object[] { 0, "drone is still docking" };
			}
			return new Object[] { 0, "drone is not docked" };
		}
		if(!(pipe.getPipeType() == IPipeTile.PipeType.ITEM)) {
			return new Object[] { 0, "pipe is not an item pipe" };
		}
		int count = args.count() > 1 ? Math.max(0, Math.min(64, args.checkInteger(1))) : 64;
		li.cil.oc.common.entity.Drone droneEntity = (li.cil.oc.common.entity.Drone) drone;
		ItemStack stack = ((IInventory) droneEntity.inventory()).getStackInSlot(args.checkInteger(0));
		if(stack != null && stack.getItem() != null) {
			stack = ((IInventory) droneEntity.inventory()).decrStackSize(args.checkInteger(0), count);
			pipe.injectItem(stack, true, side);
			return new Object[] { stack.stackSize };
		}
		return new Object[] { 0, "slot is empty" };
	}

	@Callback(doc = "function():boolean; Makes the drone start docking with a docking station; Always tries to dock with a station below it first")
	public Object[] dock(Context context, Arguments args) {
		int x = (int) Math.floor(drone.xPosition());
		int y = (int) Math.floor(drone.yPosition());
		int z = (int) Math.floor(drone.zPosition());
		ForgeDirection side = ForgeDirection.UP;
		double targetY = y;
		World world = drone.world();
		DroneStationPluggable station = tryGetStation(world, x, y - 1, z, side);
		if(station != null) {
			targetY = y - 1 + station.getBoundingBox(side).maxY;
		} else {
			side = ForgeDirection.DOWN;
			station = tryGetStation(world, x, y + 1, z, side);
			if(station != null) {
				targetY = y + 1 - station.getBoundingBox(side).minY;
			}
		}
		if(station != null && drone instanceof li.cil.oc.common.entity.Drone) {
			li.cil.oc.common.entity.Drone droneEntity = (li.cil.oc.common.entity.Drone) drone;
			if(droneEntity.motionX != 0 || droneEntity.motionY != 0 || droneEntity.motionZ != 0) {
				return new Object[] { false, "drone is still moving" };
			}
			droneEntity.targetY_$eq((float) targetY);
			this.side = side;
			isDocking = true;
			return new Object[] { true };
		}
		return new Object[] { false };
	}

	@Callback(doc = "function():boolean; Releases the drone if docked")
	public Object[] release(Context context, Arguments args) {
		if(!isDocked || pipe == null) {
			if(isDocking) {
				return new Object[] { 0, "drone is still docking" };
			}
			return new Object[] { 0, "drone is not docked" };
		}

		int y = (int) Math.floor(drone.yPosition());
		double targetY;
		DroneStationPluggable station = (DroneStationPluggable) pipe.getPipePluggable(side);
		if(side == ForgeDirection.UP) {
			targetY = y + 1 - station.getBoundingBox(ForgeDirection.UP).maxY;
		} else {
			targetY = y - 1 + station.getBoundingBox(ForgeDirection.DOWN).maxY;
		}
		if(drone instanceof li.cil.oc.common.entity.Drone) {
			li.cil.oc.common.entity.Drone droneEntity = (li.cil.oc.common.entity.Drone) drone;
			droneEntity.targetY_$eq((float) targetY);
			isDocking = false;
			isDocked = false;
			pipe = null;
			side = null;
			return new Object[] { true };
		}
		return new Object[] { false };
	}

	private DroneStationPluggable tryGetStation(World world, int x, int y, int z, ForgeDirection side) {
		TileEntity tile = world.getTileEntity(x, y, z);
		if(tile != null && tile instanceof IPipeTile) {
			PipePluggable pluggable = ((IPipeTile) tile).getPipePluggable(side);
			if(pluggable != null && pluggable instanceof DroneStationPluggable) {
				this.pipe = (IPipeTile) tile;
				return (DroneStationPluggable) pluggable;
			}
		}
		return null;
	}

	@Override
	public void save(NBTTagCompound nbt) {
		super.save(nbt);
		nbt.setBoolean("drone:docking", isDocking);
		nbt.setBoolean("drone:docked", isDocked);
		if((isDocked || isDocking) && pipe != null) {
			nbt.setInteger("drone:dockX", pipe.x());
			nbt.setInteger("drone:dockY", pipe.y());
			nbt.setInteger("drone:dockZ", pipe.z());
			if(side != null) {
				nbt.setInteger("drone:side", side.ordinal());
			}
		}
	}

	@Override
	public void load(NBTTagCompound nbt) {
		super.load(nbt);
		isDocking = nbt.getBoolean("drone:docking");
		isDocked = nbt.getBoolean("drone:docked");
		if((isDocked || isDocking)) {
			pipevec = new int[] {
				nbt.getInteger("drone:dockX"),
				nbt.getInteger("drone:dockY"),
				nbt.getInteger("drone:dockZ") };
			side = ForgeDirection.getOrientation(nbt.getInteger("drone:side"));
		}
	}
}