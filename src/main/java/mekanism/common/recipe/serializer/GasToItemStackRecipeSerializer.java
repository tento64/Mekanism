package mekanism.common.recipe.serializer;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import javax.annotation.Nonnull;
import mekanism.api.SerializerHelper;
import mekanism.api.recipes.GasToItemStackRecipe;
import mekanism.api.recipes.inputs.GasStackIngredient;
import mekanism.common.Mekanism;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipeSerializer;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.JSONUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistryEntry;

public class GasToItemStackRecipeSerializer<T extends GasToItemStackRecipe> extends ForgeRegistryEntry<IRecipeSerializer<?>> implements IRecipeSerializer<T> {

    private final IFactory<T> factory;

    public GasToItemStackRecipeSerializer(IFactory<T> factory) {
        this.factory = factory;
    }

    @Nonnull
    @Override
    public T read(@Nonnull ResourceLocation recipeId, @Nonnull JsonObject json) {
        JsonElement input = JSONUtils.isJsonArray(json, "input") ? JSONUtils.getJsonArray(json, "input") :
                            JSONUtils.getJsonObject(json, "input");
        GasStackIngredient inputIngredient = GasStackIngredient.deserialize(input);
        ItemStack output = SerializerHelper.getItemStack(json, "output");
        return this.factory.create(recipeId, inputIngredient, output);
    }

    @Override
    public T read(@Nonnull ResourceLocation recipeId, @Nonnull PacketBuffer buffer) {
        try {
            GasStackIngredient inputIngredient = GasStackIngredient.read(buffer);
            ItemStack output = buffer.readItemStack();
            return this.factory.create(recipeId, inputIngredient, output);
        } catch (Exception e) {
            Mekanism.logger.error("Error reading gas to itemstack recipe from packet.", e);
            throw e;
        }
    }

    @Override
    public void write(@Nonnull PacketBuffer buffer, @Nonnull T recipe) {
        try {
            recipe.write(buffer);
        } catch (Exception e) {
            Mekanism.logger.error("Error writing gas to itemstack recipe to packet.", e);
            throw e;
        }
    }

    public interface IFactory<T extends GasToItemStackRecipe> {

        T create(ResourceLocation id, GasStackIngredient input, ItemStack output);
    }
}