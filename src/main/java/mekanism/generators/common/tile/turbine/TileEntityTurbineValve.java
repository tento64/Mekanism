package mekanism.generators.common.tile.turbine;

import ic2.api.energy.EnergyNet;
import ic2.api.energy.event.EnergyTileLoadEvent;
import ic2.api.energy.event.EnergyTileUnloadEvent;
import ic2.api.energy.tile.IEnergyTile;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import mekanism.api.Coord4D;
import mekanism.common.base.FluidHandlerWrapper;
import mekanism.common.base.IComparatorSupport;
import mekanism.common.base.IEnergyWrapper;
import mekanism.common.base.IFluidHandlerWrapper;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.capabilities.CapabilityWrapperManager;
import mekanism.common.config.MekanismConfig;
import mekanism.common.integration.MekanismHooks;
import mekanism.common.integration.computer.IComputerIntegration;
import mekanism.common.integration.forgeenergy.ForgeEnergyIntegration;
import mekanism.common.integration.ic2.IC2Integration;
import mekanism.common.tile.gas_tank.TileEntityGasTank.GasMode;
import mekanism.common.util.CableUtils;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.PipeUtils;
import mekanism.generators.common.GeneratorsBlock;
import mekanism.generators.common.content.turbine.TurbineFluidTank;
import net.minecraft.util.Direction;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fml.common.Optional.Method;

public class TileEntityTurbineValve extends TileEntityTurbineCasing implements IFluidHandlerWrapper, IEnergyWrapper, IComputerIntegration, IComparatorSupport {

    private static final String[] methods = new String[]{"isFormed", "getSteam", "getFlowRate", "getMaxFlow", "getSteamInput"};
    public boolean ic2Registered = false;
    public TurbineFluidTank fluidTank;
    private CapabilityWrapperManager<IEnergyWrapper, ForgeEnergyIntegration> forgeEnergyManager = new CapabilityWrapperManager<>(IEnergyWrapper.class, ForgeEnergyIntegration.class);
    private int currentRedstoneLevel;

    public TileEntityTurbineValve() {
        super(GeneratorsBlock.TURBINE_VALVE);
        fluidTank = new TurbineFluidTank(this);
    }

    @Override
    public void onUpdate() {
        super.onUpdate();

        if (!ic2Registered && MekanismUtils.useIC2()) {
            register();
        }

        if (!world.isRemote) {
            if (structure != null) {
                CableUtils.emit(this);
            }
            int newRedstoneLevel = getRedstoneLevel();
            if (newRedstoneLevel != currentRedstoneLevel) {
                world.updateComparatorOutputLevel(pos, getBlockType());
                currentRedstoneLevel = newRedstoneLevel;
            }
        }
    }

    @Override
    public boolean canOutputEnergy(Direction side) {
        return structure != null && !structure.locations.contains(Coord4D.get(this).offset(side));
    }

    @Override
    public boolean canReceiveEnergy(Direction side) {
        return false;
    }

    @Method(modid = MekanismHooks.IC2_MOD_ID)
    public void register() {
        if (!world.isRemote) {
            IEnergyTile registered = EnergyNet.instance.getTile(world, getPos());
            if (registered != this) {
                if (registered != null && ic2Registered) {
                    MinecraftForge.EVENT_BUS.post(new EnergyTileUnloadEvent(registered));
                    ic2Registered = false;
                } else {
                    MinecraftForge.EVENT_BUS.post(new EnergyTileLoadEvent(this));
                    ic2Registered = true;
                }
            }
        }
    }

    @Method(modid = MekanismHooks.IC2_MOD_ID)
    public void deregister() {
        if (!world.isRemote) {
            IEnergyTile registered = EnergyNet.instance.getTile(world, getPos());
            if (registered != null && ic2Registered) {
                MinecraftForge.EVENT_BUS.post(new EnergyTileUnloadEvent(registered));
                ic2Registered = false;
            }
        }
    }

    @Override
    public double getMaxOutput() {
        return structure != null ? structure.getEnergyCapacity() : 0;
    }

    @Override
    public void onAdded() {
        super.onAdded();
        if (MekanismUtils.useIC2()) {
            register();
        }
    }

    @Override
    public void onChunkUnloaded() {
        if (MekanismUtils.useIC2()) {
            deregister();
        }
        super.onChunkUnloaded();
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (MekanismUtils.useIC2()) {
            deregister();
        }
    }

    @Override
    @Method(modid = MekanismHooks.IC2_MOD_ID)
    public int addEnergy(int amount) {
        return 0;
    }

    @Override
    @Method(modid = MekanismHooks.IC2_MOD_ID)
    public double getDemandedEnergy() {
        return 0;
    }

    @Override
    @Method(modid = MekanismHooks.IC2_MOD_ID)
    public double injectEnergy(Direction direction, double amount, double voltage) {
        return amount;
    }

    @Override
    @Method(modid = MekanismHooks.IC2_MOD_ID)
    public void drawEnergy(double amount) {
        if (structure != null) {
            double toDraw = Math.min(IC2Integration.fromEU(amount), getMaxOutput());
            setEnergy(Math.max(getEnergy() - toDraw, 0));
        }
    }

    @Override
    public double acceptEnergy(Direction side, double amount, boolean simulate) {
        return 0;
    }

    @Override
    public double pullEnergy(Direction side, double amount, boolean simulate) {
        double toGive = Math.min(getEnergy(), amount);
        if (toGive < 0.0001 || (side != null && !canOutputEnergy(side))) {
            return 0;
        }
        if (!simulate) {
            setEnergy(getEnergy() - toGive);
        }
        return toGive;
    }

    @Override
    public FluidTankInfo[] getTankInfo(Direction from) {
        return ((!world.isRemote && structure != null) || (world.isRemote && clientHasStructure)) ? new FluidTankInfo[]{fluidTank.getInfo()} : PipeUtils.EMPTY;
    }

    @Override
    public FluidTankInfo[] getAllTanks() {
        return getTankInfo(null);
    }

    @Override
    public int fill(Direction from, @Nonnull FluidStack resource, boolean doFill) {
        int filled = fluidTank.fill(resource, doFill);
        if (doFill) {
            structure.newSteamInput += filled;
        }
        if (filled < structure.getFluidCapacity() && structure.dumpMode != GasMode.IDLE) {
            filled = Math.min(structure.getFluidCapacity(), resource.amount);
        }
        return filled;
    }

    @Override
    public boolean canFill(Direction from, @Nonnull FluidStack fluid) {
        if (fluid.getFluid() == FluidRegistry.getFluid("steam")) {
            return (!world.isRemote && structure != null) || (world.isRemote && clientHasStructure);
        }
        return false;
    }

    @Nonnull
    @Override
    public String getName() {
        return LangUtils.localize("gui.industrialTurbine");
    }

    @Override
    public String[] getMethods() {
        return methods;
    }

    @Override
    public Object[] invoke(int method, Object[] arguments) throws NoSuchMethodException {
        if (method == 0) {
            return new Object[]{structure != null};
        } else {
            if (structure == null) {
                return new Object[]{"Unformed"};
            }
            switch (method) {
                case 1:
                    return new Object[]{structure.fluidStored != null ? structure.fluidStored.amount : 0};
                case 2:
                    return new Object[]{structure.clientFlow};
                case 3:
                    double rate = structure.lowerVolume * (structure.clientDispersers * MekanismConfig.current().generators.turbineDisperserGasFlow.val());
                    rate = Math.min(rate, structure.vents * MekanismConfig.current().generators.turbineVentGasFlow.val());
                    return new Object[]{rate};
                case 4:
                    return new Object[]{structure.lastSteamInput};
            }
        }
        throw new NoSuchMethodException();
    }

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> capability, @Nullable Direction side) {
        if ((!world.isRemote && structure != null) || (world.isRemote && clientHasStructure)) {
            if (capability == Capabilities.ENERGY_STORAGE_CAPABILITY) {
                return Capabilities.ENERGY_STORAGE_CAPABILITY.orEmpty(capability, LazyOptional.of(() -> this));
            }
            if (capability == Capabilities.ENERGY_OUTPUTTER_CAPABILITY) {
                return Capabilities.ENERGY_OUTPUTTER_CAPABILITY.orEmpty(capability, LazyOptional.of(() -> this));
            }
            if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
                return CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.orEmpty(capability, LazyOptional.of(() -> new FluidHandlerWrapper(this, side)));
            }
            if (capability == CapabilityEnergy.ENERGY) {
                return CapabilityEnergy.ENERGY.orEmpty(capability, LazyOptional.of(() -> forgeEnergyManager.getWrapper(this, getDirection())));
            }
        }
        return super.getCapability(capability, side);
    }

    @Override
    public int getRedstoneLevel() {
        return MekanismUtils.redstoneLevelFromContents(fluidTank.getFluidAmount(), fluidTank.getCapacity());
    }
}