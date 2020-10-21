//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package com.walrusone.skywars;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import com.walrusone.skywars.api.NMS;
import com.walrusone.skywars.api.PlaceHolders;
import com.walrusone.skywars.commands.CmdManager;
import com.walrusone.skywars.config.Config;
import com.walrusone.skywars.controllers.ChestController;
import com.walrusone.skywars.controllers.GameController;
import com.walrusone.skywars.controllers.GlassController;
import com.walrusone.skywars.controllers.InventoryController;
import com.walrusone.skywars.controllers.KitController;
import com.walrusone.skywars.controllers.MapController;
import com.walrusone.skywars.controllers.ParticleController;
import com.walrusone.skywars.controllers.PlayerController;
import com.walrusone.skywars.controllers.ProjectileController;
import com.walrusone.skywars.controllers.ScoreboardController;
import com.walrusone.skywars.controllers.ShopController;
import com.walrusone.skywars.controllers.WorldController;
import com.walrusone.skywars.dataStorage.DataStorage;
import com.walrusone.skywars.dataStorage.Database;
import com.walrusone.skywars.game.Game;
import com.walrusone.skywars.game.GameMap;
import com.walrusone.skywars.listeners.IconMenuController;
import com.walrusone.skywars.listeners.LobbyListener;
import com.walrusone.skywars.listeners.PingListener;
import com.walrusone.skywars.listeners.PlayerListener;
import com.walrusone.skywars.listeners.ProjectileListener;
import com.walrusone.skywars.listeners.SignListener;
import com.walrusone.skywars.listeners.SpectatorListener;
import com.walrusone.skywars.runnables.CheckForMinPlayers;
import com.walrusone.skywars.runnables.SavePlayers;
import com.walrusone.skywars.utilities.BungeeUtil;
import com.walrusone.skywars.utilities.LoggerFilter;
import com.walrusone.skywars.utilities.Messaging;
import com.walrusone.skywars.utilities.SaveDefaultMaps;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.logging.Logger;
import net.milkbowl.vault.chat.Chat;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;


public class SkyWarsReloaded extends JavaPlugin implements PluginMessageListener {
    private static SkyWarsReloaded instance;
    private GameController gc;
    private MapController mc;
    private WorldController wc;
    private DataStorage ds;
    private Database db;
    private Config config;
    private InventoryController invc;
    private PlayerController pc;
    private ProjectileController projc;
    private ChestController cc;
    private KitController kc;
    private IconMenuController ic;
    private GlassController glc;
    private ParticleController pec;
    private ShopController sc;
    private ScoreboardController score;
    private Messaging messaging;
    private boolean finishedStartup;
    private static final Logger log = Logger.getLogger("Minecraft");
    public static Economy econ = null;
    public static Chat chat = null;
    private NMS nmsHandler;

    public SkyWarsReloaded() {
    }

    public void onEnable() {
        instance = this;
        String packageName = this.getServer().getClass().getPackage().getName();
        String version = packageName.substring(packageName.lastIndexOf(46) + 1);

        try {
            Class<?> clazz = Class.forName("com.walrusone.skywars.nms." + version + ".NMSHandler");
            if (NMS.class.isAssignableFrom(clazz)) {
                this.nmsHandler = (NMS)clazz.getConstructor().newInstance();
            }
        } catch (Exception var7) {
            var7.printStackTrace();
            this.getLogger().severe("Could not find support for this CraftBukkit version.");
            this.getLogger().info("Check for updates at https://www.spigotmc.org/resources/skywarsreloaded-updated.36796/");
            this.setEnabled(false);
            return;
        }

        this.getLogger().info("Loading support for " + version);
        //this.getConfig().options().copyDefaults(true);
        //this.saveDefaultConfig();
        //this.saveConfig();
        //this.reloadConfig();
        this.messaging = new Messaging(this);
        this.config = new Config();
        if (this.config.bungeeEnabled()) {
            this.getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
            this.getServer().getMessenger().registerIncomingPluginChannel(this, "BungeeCord", this);
            Bukkit.getPluginManager().registerEvents(new PingListener(), this);
        }

        boolean sqlEnabled = this.getConfig().getBoolean("sqldatabase.enabled");
        if (sqlEnabled) {
            this.getSWRDatabase();
        }

        if (this.config.usingExternalEcomony() && !this.setupEconomy()) {
            log.severe(String.format("[%s] - Disabling SkyWarsReloaded: No Economy Plugin Found!", this.getDescription().getName()));
            this.getServer().getPluginManager().disablePlugin(this);
        } else {
            this.setupChat();
            boolean saveDefaultMaps = this.getConfig().getBoolean("resaveDefaultMaps");
            if (saveDefaultMaps) {
                SaveDefaultMaps.saveDefaultMaps();
                this.getConfig().set("resaveDefaultMaps", false);
                this.saveConfig();
            }

            if (this.config.logFilterEnabled()) {
                this.getServer().getLogger().setFilter(new LoggerFilter());
            }

            this.wc = new WorldController();
            this.mc = new MapController();
            this.gc = new GameController();
            this.ds = new DataStorage();
            this.pc = new PlayerController();
            this.invc = new InventoryController();
            this.cc = new ChestController();
            this.kc = new KitController();
            this.ic = new IconMenuController();
            this.sc = new ShopController();
            this.glc = new GlassController();
            this.pec = new ParticleController();
            if (this.config.trailEffectsEnabled()) {
                Bukkit.getPluginManager().registerEvents(new ProjectileListener(), this);
                this.projc = new ProjectileController();
            }

            this.score = new ScoreboardController();
            this.getCommand("swr").setExecutor(new CmdManager());
            this.getCommand("global").setExecutor(this);
            Location spawn;
            World world;
            if (this.config.daylightCycleDisabled()) {
                spawn = getCfg().getSpawn();
                if (spawn != null) {
                    world = spawn.getWorld();
                    world.setTime(6000L);
                    world.setGameRuleValue("doDaylightCycle", "false");
                }
            }

            if (this.config.weatherDisabled()) {
                spawn = getCfg().getSpawn();
                if (spawn != null) {
                    world = spawn.getWorld();
                    world.setStorm(false);
                    world.setThundering(false);
                }
            }

            Bukkit.getPluginManager().registerEvents(new PlayerListener(), this);
            Bukkit.getPluginManager().registerEvents(new LobbyListener(), this);
            Bukkit.getPluginManager().registerEvents(new SignListener(), this);
            Bukkit.getPluginManager().registerEvents(this.ic, this);
            if (this.config.spectatingEnabled()) {
                Bukkit.getPluginManager().registerEvents(new SpectatorListener(), this);
            }

            Bukkit.getScheduler().scheduleSyncRepeatingTask(this, new CheckForMinPlayers(), 20L, 20L);
            int saveInterval = this.getConfig().getInt("sqldatabase.saveInterval");
            Bukkit.getScheduler().scheduleSyncRepeatingTask(this, new SavePlayers(), 0L, (long)(1200 * saveInterval));
            if (this.config.bungeeEnabled()) {
                Game var10 = this.gc.createGame();
            }

            if (this.config.signJoinMode()) {
                this.gc.signJoinLoad();
            }

            this.finishedStartup = true;
            if (Bukkit.getPluginManager().isPluginEnabled("PlaceHolderAPI")) {
                new PlaceHolders(this);
            }

        }
    }

    public void onDisable() {
        if (this.finishedStartup) {
            this.gc.shutdown();
            this.pc.shutdown();
            this.invc.save();
            this.deleteWorlds();
        }

        instance = null;
        this.wc = null;
        this.ds = null;
        this.pc = null;
        this.ic = null;
        this.score = null;
        this.messaging = null;
        this.config = null;
        this.cc = null;
        this.mc = null;
        this.kc = null;
        this.sc = null;
        this.glc = null;
        this.pec = null;
        this.projc = null;
        this.invc = null;
        this.db = null;
        this.gc = null;
    }

    public void reload() {
        this.finishedStartup = false;
        this.reloadConfig();
        this.saveConfig();
        this.gc.shutdown();
        this.invc.save();
        this.messaging = null;
        this.messaging = new Messaging(this);
        this.config = null;
        this.config = new Config();
        this.cc = null;
        this.cc = new ChestController();
        this.mc = null;
        this.mc = new MapController();
        this.kc = null;
        this.kc = new KitController();
        this.sc = null;
        this.sc = new ShopController();
        this.glc = null;
        this.glc = new GlassController();
        this.pec = null;
        this.pec = new ParticleController();
        if (getCfg().trailEffectsEnabled()) {
            this.projc = null;
            this.projc = new ProjectileController();
        }

        this.invc = null;
        this.invc = new InventoryController();
        this.db = null;
        boolean sqlEnabled = this.getConfig().getBoolean("sqldatabase.enabled");
        if (sqlEnabled) {
            this.getSWRDatabase();
        }

        this.gc = null;
        this.gc = new GameController();
        if (this.config.bungeeEnabled()) {
            Game var2 = this.gc.createGame();
        }

        if (this.config.signJoinMode()) {
            this.gc.signJoinLoad();
        }

        this.finishedStartup = true;
    }

    private void deleteWorlds() {
        Iterator var1 = this.mc.getRegisteredMaps().iterator();

        while(var1.hasNext()) {
            GameMap map = (GameMap)var1.next();
            this.wc.deleteWorld(map.getName());
        }

    }

    public static boolean deleteFolder(File file) {
        if (!file.exists()) {
            return false;
        } else {
            boolean result = true;
            if (file.isDirectory()) {
                File[] contents = file.listFiles();
                if (contents != null) {
                    File[] var3 = contents;
                    int var4 = contents.length;

                    for(int var5 = 0; var5 < var4; ++var5) {
                        File f = var3[var5];
                        result = result && deleteFolder(f);
                    }
                }
            }

            return result && file.delete();
        }
    }

    public boolean loadingEnded() {
        return this.finishedStartup;
    }

    public static SkyWarsReloaded get() {
        return instance;
    }

    public static GameController getGC() {
        return instance.gc;
    }

    public static WorldController getWC() {
        return instance.wc;
    }

    public static Messaging getMessaging() {
        return instance.messaging;
    }

    public static MapController getMC() {
        return instance.mc;
    }

    public static DataStorage getDS() {
        return instance.ds;
    }

    public static Database getDB() {
        return instance.db;
    }

    public static PlayerController getPC() {
        return instance.pc;
    }

    public static ChestController getCC() {
        return instance.cc;
    }

    public static ProjectileController getProjC() {
        return instance.projc;
    }

    public static KitController getKC() {
        return instance.kc;
    }

    public static IconMenuController getIC() {
        return instance.ic;
    }

    public static ShopController getSC() {
        return instance.sc;
    }

    public static InventoryController getInvC() {
        return instance.invc;
    }

    public static GlassController getGLC() {
        return instance.glc;
    }

    public static ParticleController getPEC() {
        return instance.pec;
    }

    public static ScoreboardController getScore() {
        return instance.score;
    }

    public static Config getCfg() {
        return instance.config;
    }

    public static NMS getNMS() {
        return instance.nmsHandler;
    }

    private boolean setupEconomy() {
        if (this.getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        } else {
            RegisteredServiceProvider<Economy> rsp = this.getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp == null) {
                return false;
            } else {
                econ = (Economy)rsp.getProvider();
                return econ != null;
            }
        }
    }

    private void setupChat() {
        RegisteredServiceProvider<Chat> chatProvider = this.getServer().getServicesManager().getRegistration(Chat.class);
        if (chatProvider != null) {
            chat = chatProvider.getProvider();
        }

    }

    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (channel.equals("BungeeCord")) {
            ByteArrayDataInput in = ByteStreams.newDataInput(message);
            String subchannel = in.readUTF();
            if (subchannel.equals("SkyWarsReloadedRequest")) {
                short len = in.readShort();
                byte[] msgbytes = new byte[len];
                in.readFully(msgbytes);
                Game game = this.gc.getGame(1);
                if (game != null) {
                    BungeeUtil.sendSignUpdateRequest(game);
                } else {
                    System.out.println("Game " + game + " couldn't be found, please fix your setup.");
                }
            }

        }
    }

    private void getSWRDatabase() {
        try {
            this.db = new Database();
        } catch (ClassNotFoundException var17) {
            var17.printStackTrace();
        } catch (SQLException var18) {
            var18.printStackTrace();
        }

        try {
            this.db.createTables();
        } catch (IOException var15) {
            var15.printStackTrace();
        } catch (SQLException var16) {
            var16.printStackTrace();
        }

        Connection conn = this.db.getConnection();

        DatabaseMetaData metadata;
        ResultSet resultSet;
        try {
            metadata = conn.getMetaData();
            resultSet = metadata.getTables((String)null, (String)null, "swreloaded_player", (String[])null);
            if (resultSet.next()) {
                resultSet = metadata.getColumns((String)null, (String)null, "swreloaded_player", "playername");
                if (!resultSet.next()) {
                    try {
                        this.db.addColumn("playername");
                    } catch (IOException var13) {
                        var13.printStackTrace();
                    }
                }
            }
        } catch (SQLException var14) {
            var14.printStackTrace();
        }

        try {
            metadata = conn.getMetaData();
            resultSet = metadata.getTables((String)null, (String)null, "swreloaded_player", (String[])null);
            if (resultSet.next()) {
                resultSet = metadata.getColumns((String)null, (String)null, "swreloaded_player", "balance");
                if (!resultSet.next()) {
                    try {
                        this.db.addColumn("balance");
                    } catch (IOException var11) {
                        var11.printStackTrace();
                    }
                }
            }
        } catch (SQLException var12) {
            var12.printStackTrace();
        }

        try {
            metadata = conn.getMetaData();
            resultSet = metadata.getTables((String)null, (String)null, "swreloaded_player", (String[])null);
            if (resultSet.next()) {
                resultSet = metadata.getColumns((String)null, (String)null, "swreloaded_player", "glasscolor");
                if (!resultSet.next()) {
                    try {
                        this.db.addColumn("glasscolor");
                    } catch (IOException var9) {
                        var9.printStackTrace();
                    }
                }
            }
        } catch (SQLException var10) {
            var10.printStackTrace();
        }

        try {
            metadata = conn.getMetaData();
            resultSet = metadata.getTables((String)null, (String)null, "swreloaded_player", (String[])null);
            if (resultSet.next()) {
                resultSet = metadata.getColumns((String)null, (String)null, "swreloaded_player", "effect");
                if (!resultSet.next()) {
                    try {
                        this.db.addColumn("effect");
                    } catch (IOException var7) {
                        var7.printStackTrace();
                    }
                }
            }
        } catch (SQLException var8) {
            var8.printStackTrace();
        }

        try {
            metadata = conn.getMetaData();
            resultSet = metadata.getTables((String)null, (String)null, "swreloaded_player", (String[])null);
            if (resultSet.next()) {
                resultSet = metadata.getColumns((String)null, (String)null, "swreloaded_player", "traileffect");
                if (!resultSet.next()) {
                    try {
                        this.db.addColumn("traileffect");
                    } catch (IOException var5) {
                        var5.printStackTrace();
                    }
                }
            }
        } catch (SQLException var6) {
            var6.printStackTrace();
        }

    }
}
