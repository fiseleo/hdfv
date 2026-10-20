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
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

import java.util.ArrayList;
import java.util.List;

/**
 * An electric arc furnace's or an electrolysis cell's recipe (0.23, hdfv:electric_furnace, hdfv:electrolysis): up to
 * four ingredients with counts (each from its own slot, nothing else in the inputs, as the alloy furnace's), a result,
 * up to two by-products, and the electricity a batch takes (kWh, from AeroCore's metallurgy: tools/recipes.py writes
 * it, MetallurgyRecipesTest checks it). The time is the energy over the machine's rated power, in work time.
 */
public record ElectricRecipe(Machine machine, List<SizedIngredient> ingredients, ItemStack result, List<ItemStack> byproducts, double kwh)
        implements Recipe<AlloyingRecipe.Input> {

    /** Which machine takes it. */
    public enum Machine { ARC_FURNACE, ELECTROLYSIS }

    /** The input slot each ingredient takes from (distinct slots), or null when the input does not hold them. */
    public int[] slots(AlloyingRecipe.Input in) {
        return new AlloyingRecipe(ingredients, result, ItemStack.EMPTY, 1).slots(in);
    }

    @Override public boolean matches(AlloyingRecipe.Input in, Level level) { return slots(in) != null; }
    @Override public ItemStack assemble(AlloyingRecipe.Input in, HolderLookup.Provider registries) { return result.copy(); }
    @Override public boolean canCraftInDimensions(int width, int height) { return true; }
    @Override public ItemStack getResultItem(HolderLookup.Provider registries) { return result; }
    @Override public RecipeSerializer<?> getSerializer() {
        return machine == Machine.ARC_FURNACE ? HdfvMaterials.ARC_FURNACE_SERIALIZER.get() : HdfvMaterials.ELECTROLYSIS_SERIALIZER.get();
    }
    @Override public RecipeType<?> getType() { return machine == Machine.ARC_FURNACE ? HdfvMaterials.ARC_FURNACE.get() : HdfvMaterials.ELECTROLYSIS.get(); }
    @Override public boolean isSpecial() { return true; }

    /** The energy a batch takes, J. */
    public double joules() { return kwh * 3.6e6; }

    static MapCodec<ElectricRecipe> codec(Machine m) {
        return RecordCodecBuilder.mapCodec(i -> i.group(
                SizedIngredient.NESTED_CODEC.listOf(1, 4).fieldOf("ingredients").forGetter(ElectricRecipe::ingredients),
                ItemStack.STRICT_CODEC.fieldOf("result").forGetter(ElectricRecipe::result),
                ItemStack.STRICT_CODEC.listOf(0, 2).optionalFieldOf("byproducts", List.of()).forGetter(ElectricRecipe::byproducts),
                Codec.doubleRange(1e-3, 1e5).fieldOf("kwh").forGetter(ElectricRecipe::kwh)).apply(i, (a, b, c, d) -> new ElectricRecipe(m, a, b, c, d)));
    }

    static StreamCodec<RegistryFriendlyByteBuf, ElectricRecipe> streamCodec(Machine m) {
        return StreamCodec.composite(
                SizedIngredient.STREAM_CODEC.apply(ByteBufCodecs.list(4)), r -> new ArrayList<>(r.ingredients()),
                ItemStack.STREAM_CODEC, ElectricRecipe::result,
                ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list(2)), r -> new ArrayList<>(r.byproducts()),
                ByteBufCodecs.DOUBLE, ElectricRecipe::kwh,
                (a, b, c, d) -> new ElectricRecipe(m, a, b, c, d));
    }

    public static final class Serializer implements RecipeSerializer<ElectricRecipe> {
        private final MapCodec<ElectricRecipe> codec;
        private final StreamCodec<RegistryFriendlyByteBuf, ElectricRecipe> stream;

        public Serializer(Machine m) {
            this.codec = ElectricRecipe.codec(m);
            this.stream = ElectricRecipe.streamCodec(m);
        }

        @Override public MapCodec<ElectricRecipe> codec() { return codec; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, ElectricRecipe> streamCodec() { return stream; }
    }
}
