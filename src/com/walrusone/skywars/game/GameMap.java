package com.walrusone.skywars.game;

import com.google.common.collect.Maps;
import com.walrusone.skywars.SkyWarsReloaded;
import com.walrusone.skywars.controllers.WorldController;
import com.walrusone.skywars.utilities.EmptyChest;
import java.io.File;
import java.util.Map;
import org.bukkit.Chunk;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Beacon;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.inventory.InventoryHolder;

public class GameMap {
    private File source;
    private File rootDirectory;
    private String name;
    private Map<Integer, Location> spawnPoints = Maps.newHashMap();
    private Map<Integer, EmptyChest> chests = Maps.newHashMap();
    private Map<Integer, EmptyChest> doubleChests = Maps.newHashMap();
    private Block min;
    private Block max;
    private int minX;
    private int minZ;
    private int minY = 0;
    private int maxX;
    private int maxZ;
    private int maxY = 0;

    public GameMap(String name, File filepath) {
        int size = SkyWarsReloaded.getCfg().getMaxMapSize();
        int max = size / 2;
        int min = -size / 2;
        this.minX = min;
        this.minZ = min;
        this.maxX = max;
        this.maxZ = max;
        this.source = filepath;
        this.name = name.toLowerCase();
        String root = SkyWarsReloaded.get().getServer().getWorldContainer().getAbsolutePath();
        this.rootDirectory = new File(root);
        this.ChunkIterator();
    }

    public String getName() {
        return this.name;
    }

    public boolean containsSpawns() {
        if (!this.name.equalsIgnoreCase("lobby") && this.spawnPoints.size() >= 2) {
            return true;
        } else {
            return this.name.equalsIgnoreCase("lobby") && this.spawnPoints.size() >= 1;
        }
    }

    public Map<Integer, Location> getSpawns() {
        return this.spawnPoints;
    }

    public Map<Integer, EmptyChest> getChests() {
        return this.chests;
    }

    public Map<Integer, EmptyChest> getDoubleChests() {
        return this.doubleChests;
    }

    public Block getMin() { return min; }

    public Block getMax() { return max; }

    public boolean loadMap(int gNumber) {
        WorldController wc = SkyWarsReloaded.getWC();
        String mapName = this.name + "_" + gNumber;
        boolean mapExists = false;
        File target = new File(this.rootDirectory, mapName);
        if (target.isDirectory() && target.list().length > 0) {
            mapExists = true;
        }

        if (mapExists) {
            SkyWarsReloaded.getWC().deleteWorld(mapName);
        }

        wc.copyWorld(this.source, target);
        boolean loaded = SkyWarsReloaded.getWC().loadWorld(mapName);
        if (loaded) {
            World world = SkyWarsReloaded.get().getServer().getWorld(mapName);
            world.setAutoSave(false);
            world.setThundering(false);
            world.setStorm(false);
            world.setDifficulty(Difficulty.NORMAL);
            world.setSpawnLocation(2000, 0, 2000);
            world.setTicksPerAnimalSpawns(1);
            world.setTicksPerMonsterSpawns(1);
            world.setGameRuleValue("doMobSpawning", "false");
            world.setGameRuleValue("mobGriefing", "false");
            world.setGameRuleValue("doFireTick", "false");
            world.setGameRuleValue("showDeathMessages", "false");
        }

        return loaded;
    }

    public void ChunkIterator() {
        World chunkWorld = SkyWarsReloaded.get().getServer().getWorld(this.name);
        this.min = chunkWorld.getBlockAt(this.minX, this.minY, this.minZ);
        this.max = chunkWorld.getBlockAt(this.maxX, this.maxY, this.maxZ);
        Chunk cMin = this.min.getChunk();
        Chunk cMax = this.max.getChunk();
        int countSpawns = 1;
        int countChests = 0;
        int countDChests = 0;

        for(int cx = cMin.getX(); cx < cMax.getX(); ++cx) {
            for(int cz = cMin.getZ(); cz < cMax.getZ(); ++cz) {
                Chunk currentChunk = chunkWorld.getChunkAt(cx, cz);
                currentChunk.load(true);
                BlockState[] var10 = currentChunk.getTileEntities();
                int var11 = var10.length;

                for(int var12 = 0; var12 < var11; ++var12) {
                    BlockState te = var10[var12];
                    if (te instanceof Beacon) {
                        Beacon beacon = (Beacon)te;
                        Location loc = beacon.getLocation();
                        this.spawnPoints.put(countSpawns, loc);
                        ++countSpawns;
                    } else if (te instanceof Chest) {
                        Chest chest = (Chest)te;
                        InventoryHolder ih = chest.getInventory().getHolder();
                        int x;
                        int z;
                        int y;
                        if (ih instanceof DoubleChest) {
                            x = chest.getX();
                            z = chest.getZ();
                            y = chest.getY();
                            this.doubleChests.put(countDChests, new EmptyChest(x, y, z));
                            ++countDChests;
                        } else {
                            x = chest.getX();
                            z = chest.getZ();
                            y = chest.getY();
                            this.chests.put(countChests, new EmptyChest(x, y, z));
                            ++countChests;
                        }
                    }
                }
            }
        }

    }
}
