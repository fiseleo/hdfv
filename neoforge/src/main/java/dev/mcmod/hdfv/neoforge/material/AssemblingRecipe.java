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
 * An assembly bench's recipe (0.23, hdfv:assembling; the user chose machines assembled at the bench from standard
 * components, with labour time and a fitter): up to 18 ingredients with counts, a result, and the fitting time in work
 * seconds (tools/recipes.py writes it by the parts' rule, Plans.assembleS: 300 s, a minute a piece, 3 s a kilogram).
 * The bench takes what the recipe needs from anywhere in its inputs; other things there are left alone.
 */
public record AssemblingRecipe(List<SizedIngredient> ingredients, ItemStack result, int seconds) implements Recipe<AssemblingRecipe.Input> {

    /** The bench's input slots. */
    public record Input(List<ItemStack> stacks) implements RecipeInput {
        @Override public ItemStack getItem(int i) { return stacks.get(i); }
        @Override public int size() { return stacks.size(); }
    }

    /** How many of each input slot the recipe takes, or null when the inputs do not hold it all (each item counted once). */
    public int[] takes(Input in) {
        int[] left = new int[in.size()], take = new int[in.size()];
        for (int i = 0; i < in.size(); i++) { left[i] = in.getItem(i).getCount(); }
        for (SizedIngredient need : ingredients) {
            int n = need.count();
            for (int i = 0; i < in.size() && n > 0; i++) {
                ItemStack s = in.getItem(i);
                if (left[i] <= 0 || s.isEmpty() || !need.ingredient().test(s)) { continue; }
                int t = Math.min(n, left[i]);
                left[i] -= t;
                take[i] += t;
                n -= t;
            }
            if (n > 0) { return null; }
        }
        return take;
    }

    @Override public boolean matches(Input in, Level level) { return takes(in) != null; }
    @Override public ItemStack assemble(Input in, HolderLookup.Provider registries) { return result.copy(); }
    @Override public boolean canCraftInDimensions(int width, int height) { return true; }
    @Override public ItemStack getResultItem(HolderLookup.Provider registries) { return result; }
    @Override public RecipeSerializer<?> getSerializer() { return HdfvMaterials.ASSEMBLING_SERIALIZER.get(); }
    @Override public RecipeType<?> getType() { return HdfvMaterials.ASSEMBLING.get(); }
    @Override public boolean isSpecial() { return true; }

    public static final MapCodec<AssemblingRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            SizedIngredient.NESTED_CODEC.listOf(1, 18).fieldOf("ingredients").forGetter(AssemblingRecipe::ingredients),
            ItemStack.STRICT_CODEC.fieldOf("result").forGetter(AssemblingRecipe::result),
            Codec.intRange(1, 360000).fieldOf("seconds").forGetter(AssemblingRecipe::seconds)).apply(i, AssemblingRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, AssemblingRecipe> STREAM_CODEC = StreamCodec.composite(
            SizedIngredient.STREAM_CODEC.apply(ByteBufCodecs.list(18)), r -> new ArrayList<>(r.ingredients()),
            ItemStack.STREAM_CODEC, AssemblingRecipe::result,
            ByteBufCodecs.VAR_INT, AssemblingRecipe::seconds,
            AssemblingRecipe::new);

    public static final class Serializer implements RecipeSerializer<AssemblingRecipe> {
        @Override public MapCodec<AssemblingRecipe> codec() { return CODEC; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, AssemblingRecipe> streamCodec() { return STREAM_CODEC; }
    }
}
