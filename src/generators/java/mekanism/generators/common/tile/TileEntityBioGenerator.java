package mekanism.generators.common.tile;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import mekanism.api.TileNetworkList;
import mekanism.common.FluidSlot;
import mekanism.common.MekanismItem;
import mekanism.common.base.FluidHandlerWrapper;
import mekanism.common.base.IComparatorSupport;
import mekanism.common.base.IFluidHandlerWrapper;
import mekanism.api.sustained.ISustainedData;
import mekanism.common.base.LazyOptionalHelper;
import mekanism.common.util.ChargeUtils;
import mekanism.common.util.ItemDataUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.PipeUtils;
import mekanism.generators.common.GeneratorTags;
import mekanism.generators.common.GeneratorsBlock;
import mekanism.generators.common.config.MekanismGeneratorsConfig;
import net.minecraft.inventory.container.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.Direction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.IFluidTank;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;

public class TileEntityBioGenerator extends TileEntityGenerator implements IFluidHandlerWrapper, ISustainedData, IComparatorSupport {

    private static final String[] methods = new String[]{"getEnergy", "getOutput", "getMaxEnergy", "getEnergyNeeded", "getBioFuel", "getBioFuelNeeded"};
    private static IFluidTank[] ALL_TANKS = new IFluidTank[0];
    /**
     * The FluidSlot biofuel instance for this generator.
     */
    public FluidSlot bioFuelSlot = new FluidSlot(24000, -1);

    private int currentRedstoneLevel;

    public TileEntityBioGenerator() {
        super(GeneratorsBlock.BIO_GENERATOR, MekanismGeneratorsConfig.generators.bioGeneration.get() * 2);
    }

    @Override
    public void onUpdate() {
        super.onUpdate();

        if (!getInventory().get(0).isEmpty()) {
            ChargeUtils.charge(1, this);
            FluidStack fluidStack = FluidUtil.getFluidContained(getInventory().get(0)).orElse(FluidStack.EMPTY);
            if (fluidStack.isEmpty()) {
                int fuel = getFuel(getInventory().get(0));
                if (fuel > 0) {
                    int fuelNeeded = bioFuelSlot.MAX_FLUID - bioFuelSlot.fluidStored;
                    if (fuel <= fuelNeeded) {
                        bioFuelSlot.fluidStored += fuel;
                        if (!getInventory().get(0).getItem().getContainerItem(getInventory().get(0)).isEmpty()) {
                            getInventory().set(0, getInventory().get(0).getItem().getContainerItem(getInventory().get(0)));
                        } else {
                            getInventory().get(0).shrink(1);
                        }
                    }
                }
            } else if (fluidStack.getFluid().getTags().contains(GeneratorTags.BIO_ETHANOL)) {
                FluidUtil.getFluidHandler(getInventory().get(0)).ifPresent(handler -> {
                    FluidStack drained = handler.drain(bioFuelSlot.MAX_FLUID - bioFuelSlot.fluidStored, FluidAction.EXECUTE);
                    if (!drained.isEmpty()) {
                        bioFuelSlot.fluidStored += drained.getAmount();
                    }
                });
            }
        }
        if (canOperate()) {
            if (!world.isRemote) {
                setActive(true);
            }
            bioFuelSlot.setFluid(bioFuelSlot.fluidStored - 1);
            setEnergy(getEnergy() + MekanismGeneratorsConfig.generators.bioGeneration.get());
        } else if (!world.isRemote) {
            setActive(false);
        }
        if (!world.isRemote) {
            int newRedstoneLevel = getRedstoneLevel();
            if (newRedstoneLevel != currentRedstoneLevel) {
                world.updateComparatorOutputLevel(pos, getBlockType());
                currentRedstoneLevel = newRedstoneLevel;
            }
        }
    }

    @Override
    public boolean isItemValidForSlot(int slotID, @Nonnull ItemStack itemstack) {
        if (slotID == 0) {
            if (getFuel(itemstack) > 0) {
                return true;
            }
            return new LazyOptionalHelper<>(FluidUtil.getFluidContained(itemstack)).matches(fluid -> fluid.getFluid().getTags().contains(GeneratorTags.BIO_ETHANOL));
        } else if (slotID == 1) {
            return ChargeUtils.canBeCharged(itemstack);
        }
        return true;
    }

    @Override
    public boolean canOperate() {
        return getEnergy() < getBaseStorage() && bioFuelSlot.fluidStored > 0 && MekanismUtils.canFunction(this);
    }

    @Override
    public void read(CompoundNBT nbtTags) {
        super.read(nbtTags);
        bioFuelSlot.fluidStored = nbtTags.getInt("bioFuelStored");
    }

    @Nonnull
    @Override
    public CompoundNBT write(CompoundNBT nbtTags) {
        super.write(nbtTags);
        nbtTags.putInt("bioFuelStored", bioFuelSlot.fluidStored);
        return nbtTags;
    }

    public int getFuel(ItemStack itemstack) {
        return MekanismItem.BIO_FUEL.itemMatches(itemstack) ? 200 : 0;
    }

    /**
     * Gets the scaled fuel level for the GUI.
     *
     * @param i - multiplier
     *
     * @return Scaled fuel level
     */
    public int getScaledFuelLevel(int i) {
        return bioFuelSlot.fluidStored * i / bioFuelSlot.MAX_FLUID;
    }

    @Nonnull
    @Override
    public int[] getSlotsForFace(@Nonnull Direction side) {
        return side == getRightSide() ? new int[]{1} : new int[]{0};
    }

    @Override
    public void handlePacketData(PacketBuffer dataStream) {
        super.handlePacketData(dataStream);
        if (world.isRemote) {
            bioFuelSlot.fluidStored = dataStream.readInt();
        }
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        super.getNetworkedData(data);
        data.add(bioFuelSlot.fluidStored);
        return data;
    }

    @Override
    public String[] getMethods() {
        return methods;
    }

    @Override
    public Object[] invoke(int method, Object[] arguments) throws NoSuchMethodException {
        switch (method) {
            case 0:
                return new Object[]{getEnergy()};
            case 1:
                return new Object[]{output};
            case 2:
                return new Object[]{getBaseStorage()};
            case 3:
                return new Object[]{getBaseStorage() - getEnergy()};
            case 4:
                return new Object[]{bioFuelSlot.fluidStored};
            case 5:
                return new Object[]{bioFuelSlot.MAX_FLUID - bioFuelSlot.fluidStored};
            default:
                throw new NoSuchMethodException();
        }
    }

    @Override
    public int fill(Direction from, @Nonnull FluidStack resource, FluidAction fluidAction) {
        int fuelNeeded = bioFuelSlot.MAX_FLUID - bioFuelSlot.fluidStored;
        int fuelTransfer = Math.min(resource.getAmount(), fuelNeeded);
        if (fluidAction.execute()) {
            bioFuelSlot.setFluid(bioFuelSlot.fluidStored + fuelTransfer);
        }
        return fuelTransfer;
    }

    @Override
    public boolean canFill(Direction from, @Nonnull FluidStack fluid) {
        return from != getDirection() && fluid.getFluid().getTags().contains(GeneratorTags.BIO_ETHANOL);
    }

    @Override
    public IFluidTank[] getTankInfo(Direction from) {
        return PipeUtils.EMPTY;
    }

    @Override
    public void writeSustainedData(ItemStack itemStack) {
        ItemDataUtils.setInt(itemStack, "fluidStored", bioFuelSlot.fluidStored);
    }

    @Override
    public void readSustainedData(ItemStack itemStack) {
        bioFuelSlot.setFluid(ItemDataUtils.getInt(itemStack, "fluidStored"));
    }

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> capability, @Nullable Direction side) {
        if (side != getDirection() && capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            return CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.orEmpty(capability, LazyOptional.of(() -> new FluidHandlerWrapper(this, side)));
        }
        return super.getCapability(capability, side);
    }

    @Override
    public IFluidTank[] getAllTanks() {
        return ALL_TANKS;
    }

    @Override
    public int getRedstoneLevel() {
        return Container.calcRedstoneFromInventory(this);
    }
}