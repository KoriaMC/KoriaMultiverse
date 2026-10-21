package fr.koriamc.multiverse.world;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;

import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Générateur de monde vide (aucun bloc). Utilisable à la création avec
 * "generateur": "VIDE", ou dans bukkit.yml avec KoriaMultiverse:VIDE.
 */
public final class VoidGenerator extends ChunkGenerator {

    private final int spawnHeight;

    public VoidGenerator(int spawnHeight) {
        this.spawnHeight = spawnHeight;
    }

    @Override
    public ChunkData generateChunkData(World world, Random random, int x, int z, BiomeGrid biome) {
        for (int bx = 0; bx < 16; bx++) {
            for (int bz = 0; bz < 16; bz++) {
                biome.setBiome(bx, bz, Biome.PLAINS);
            }
        }
        return createChunkData(world);
    }

    @Override
    public List<BlockPopulator> getDefaultPopulators(World world) {
        return Collections.emptyList();
    }

    @Override
    public boolean canSpawn(World world, int x, int z) {
        return true;
    }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, 0.5, spawnHeight + 1, 0.5);
    }
}
