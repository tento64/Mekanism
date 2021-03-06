package mekanism.api.datagen.recipe.builder;

import com.google.gson.JsonObject;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import mcp.MethodsReturnNonnullByDefault;
import mekanism.api.MekanismAPI;
import mekanism.api.SerializerHelper;
import mekanism.api.annotations.FieldsAreNonnullByDefault;
import mekanism.api.datagen.recipe.MekanismRecipeBuilder;
import mekanism.api.gas.GasStack;
import mekanism.api.recipes.inputs.FluidStackIngredient;
import mekanism.api.recipes.inputs.GasStackIngredient;
import net.minecraft.advancements.Advancement;
import net.minecraft.util.ResourceLocation;

@FieldsAreNonnullByDefault
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class FluidGasToGasRecipeBuilder extends MekanismRecipeBuilder<FluidGasToGasRecipeBuilder> {

    private final GasStackIngredient gasInput;
    private final FluidStackIngredient fluidInput;
    private final GasStack output;

    protected FluidGasToGasRecipeBuilder(FluidStackIngredient fluidInput, GasStackIngredient gasInput, GasStack output) {
        super(new ResourceLocation(MekanismAPI.MEKANISM_MODID, "washing"));
        this.fluidInput = fluidInput;
        this.gasInput = gasInput;
        this.output = output;
    }

    public static FluidGasToGasRecipeBuilder washing(FluidStackIngredient fluidInput, GasStackIngredient gasInput, GasStack output) {
        if (output.isEmpty()) {
            throw new IllegalArgumentException("This washing recipe requires a non empty gas output.");
        }
        return new FluidGasToGasRecipeBuilder(fluidInput, gasInput, output);
    }

    @Override
    protected FluidGasToGasRecipeResult getResult(ResourceLocation id) {
        return new FluidGasToGasRecipeResult(id, fluidInput, gasInput, output, advancementBuilder,
              new ResourceLocation(id.getNamespace(), "recipes/" + id.getPath()), serializerName);
    }

    public static class FluidGasToGasRecipeResult extends RecipeResult {

        private final GasStackIngredient gasInput;
        private final FluidStackIngredient fluidInput;
        private final GasStack output;

        public FluidGasToGasRecipeResult(ResourceLocation id, FluidStackIngredient fluidInput, GasStackIngredient gasInput, GasStack output,
              Advancement.Builder advancementBuilder, ResourceLocation advancementId, ResourceLocation serializerName) {
            super(id, advancementBuilder, advancementId, serializerName);
            this.fluidInput = fluidInput;
            this.gasInput = gasInput;
            this.output = output;
        }

        @Override
        public void serialize(@Nonnull JsonObject json) {
            json.add("fluidInput", fluidInput.serialize());
            json.add("gasInput", gasInput.serialize());
            json.add("output", SerializerHelper.serializeGasStack(output));
        }
    }
}