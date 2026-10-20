package dev.mcmod.hdfv.neoforge.power;

import dev.mcmod.hdfv.power.PowerPlant;
import dev.mcmod.hdfv.power.ShaftLine;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.Locale;

/** A treadle (0.8): someone standing on it (a player or a villager) pedals 75 W into the line (a person's legs, sustained; HumanPower). */
public final class TreadleBlockEntity extends PowerBlockEntity implements ShaftEnd {
    private boolean pedalled;
    private int clock;
    private double lastW, rpm;

    public TreadleBlockEntity(BlockPos pos, BlockState s) { super(HdfvPower.TREADLE_ENTITY.get(), pos, s); }

    public boolean pedalled() { return pedalled; }

    @Override public ShaftLine.End lineEnd(double lineRpm) { return new ShaftLine.End(pedalled ? PowerPlant.TREADLE_W : 0, 0, 0.05); }

    @Override public void settle(double lineRpm, double givenW, double gotW, double standLoadW) { rpm = lineRpm; lastW = givenW; }

    @Override public void tick() {
        if (level == null) { return; }
        if (clock++ % 10 == 0) {
            AABB on = new AABB(worldPosition.above()).inflate(0.1, 0, 0.1);
            pedalled = !level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, on,
                    e -> e.isAlive() && !e.isSpectator() && (e instanceof Player || e instanceof net.minecraft.world.entity.npc.Villager)).isEmpty();
        }
        ShaftNetworks.tick(level, worldPosition);
        dev.mcmod.hdfv.neoforge.sound.SoundSync.report(this, pedalled && rpm > 5 ? dev.mcmod.hdfv.sound.MachineSound.treadle(rpm, true) : null);
    }

    @Override public Component status() {
        return Component.translatable("hdfv.power.treadle", String.format(Locale.ROOT, "%.0f", lastW), String.format(Locale.ROOT, "%.0f", rpm));
    }
}
