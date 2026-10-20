package dev.mcmod.hdfv.neoforge.material;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

import java.util.ArrayList;
import java.util.List;

/**
 * An alloy furnace's recipe (0.7, hdfv:alloying): up to four ingredients with counts (each from its own slot), a
 * result and an optional by-product, and how many ticks it takes. Coke and coal tar from coal, the light metals
 * reduced from their ores with coke (simplified: aluminium's electrolysis needs no power yet), the design alloys from
 * base ingots, resins, rubber and fibres from coal tar.
 */
public record AlloyingRecipe(List<SizedIngredient> ingredients, ItemStack result, ItemStack byproduct, int ticks) implements Recipe<AlloyingRecipe.Input> {

    /** The furnace's four input slots. */
    public record Input(List<ItemStack> stacks) implements RecipeInput {
        @Override public ItemStack getItem(int i) { return stacks.get(i); }
        @Override public int size() { return stacks.size(); }
    }

    /** The input slot each ingredient takes from (distinct slots), or null when the input does not hold them. */
    public int[] slots(Input in) {
        int[] used = new int[ingredients.size()];
        boolean[] taken = new boolean[in.size()];
        for (int k = 0; k < ingredients.size(); k++) {
            SizedIngredient need = ingredients.get(k);
            int found = -1;
            for (int i = 0; i < in.size() && found < 0; i++) {
                ItemStack s = in.getItem(i);
                if (!taken[i] && !s.isEmpty() && need.ingredient().test(s) && s.getCount() >= need.count()) { found = i; }
            }
            if (found < 0) { return null; }
            taken[found] = true;
            used[k] = found;
        }
        // nothing else may be in the inputs
        for (int i = 0; i < in.size(); i++) { if (!taken[i] && !in.getItem(i).isEmpty()) { return null; } }
        return used;
    }

    @Override public boolean matches(Input in, Level level) { return slots(in) != null; }
    @Override public ItemStack assemble(Input in, HolderLookup.Provider registries) { return result.copy(); }
    @Override public boolean canCraftInDimensions(int width, int height) { return true; }
    @Override public ItemStack getResultItem(HolderLookup.Provider registries) { return result; }
    @Override public RecipeSerializer<?> getSerializer() { return HdfvMaterials.ALLOYING_SERIALIZER.get(); }
    @Override public RecipeType<?> getType() { return HdfvMaterials.ALLOYING.get(); }
    @Override public boolean isSpecial() { return true; }

    public static final MapCodec<AlloyingRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            SizedIngredient.NESTED_CODEC.listOf(1, 4).fieldOf("ingredients").forGetter(AlloyingRecipe::ingredients),
            ItemStack.STRICT_CODEC.fieldOf("result").forGetter(AlloyingRecipe::result),
            ItemStack.OPTIONAL_CODEC.optionalFieldOf("byproduct", ItemStack.EMPTY).forGetter(AlloyingRecipe::byproduct),
            Codec.intRange(1, 72000).fieldOf("ticks").forGetter(AlloyingRecipe::ticks)).apply(i, AlloyingRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, AlloyingRecipe> STREAM_CODEC = StreamCodec.composite(
            SizedIngredient.STREAM_CODEC.apply(ByteBufCodecs.list(4)), r -> new ArrayList<>(r.ingredients()),
            ItemStack.STREAM_CODEC, AlloyingRecipe::result,
            ItemStack.OPTIONAL_STREAM_CODEC, AlloyingRecipe::byproduct,
            ByteBufCodecs.VAR_INT, AlloyingRecipe::ticks,
            AlloyingRecipe::new);

    public static final class Serializer implements RecipeSerializer<AlloyingRecipe> {
        @Override public MapCodec<AlloyingRecipe> codec() { return CODEC; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, AlloyingRecipe> streamCodec() { return STREAM_CODEC; }
    }
}
