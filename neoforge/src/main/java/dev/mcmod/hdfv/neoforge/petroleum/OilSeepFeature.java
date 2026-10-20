package dev.mcmod.hdfv.neoforge.petroleum;

import dev.mcmod.hdfv.petroleum.OilFields;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Oil seeps (0.8): where a field lies under the ground, now and then a patch of soaked ground shows it (the way the
 * first oil fields were found). A chunk over the inner part of a field gets a seep a quarter of the time, a few
 * blocks of oil-soaked soil in place of the dirt, grass, sand or gravel on top.
 */
public final class OilSeepFeature extends Feature<NoneFeatureConfiguration> {
    public static final double CHANCE = 0.25, INNER = 0.7;

    public OilSeepFeature() { super(NoneFeatureConfiguration.CODEC); }

    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> c) {
        WorldGenLevel level = c.level();
        var r = c.random();
        if (r.nextDouble() >= CHANCE) { return false; }
        BlockPos o = c.origin();
        int x = (o.getX() & ~15) + r.nextInt(16), z = (o.getZ() & ~15) + r.nextInt(16);
        OilFields.Field f = OilFields.at(level.getSeed(), x, z);
        if (f == null) { return false; }
        double dx = x + 0.5 - f.centreX(), dz = z + 0.5 - f.centreZ();
        if (dx * dx + dz * dz > INNER * INNER * f.radiusM() * f.radiusM()) { return false; }
        boolean any = false;
        for (int i = 0; i < 6; i++) {
            int px = x + r.nextInt(3) - 1, pz = z + r.nextInt(3) - 1;
            int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, px, pz) - 1;
            BlockPos p = new BlockPos(px, y, pz);
            BlockState s = level.getBlockState(p);
            if (s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(net.minecraft.world.level.block.Blocks.GRAVEL)) {
                level.setBlock(p, HdfvPetroleum.OIL_SEEP.get().defaultBlockState(), Block.UPDATE_CLIENTS);
                any = true;
            }
        }
        return any;
    }
}
