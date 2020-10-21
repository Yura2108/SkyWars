package com.walrusone.skywars.game;

import com.walrusone.skywars.SkyWarsReloaded;
import com.walrusone.skywars.api.EntityDeathInGameEvent;
import com.walrusone.skywars.api.GameEndEvent;
import com.walrusone.skywars.api.GameStartEvent;
import com.walrusone.skywars.utilities.BungeeUtil;
import com.walrusone.skywars.utilities.EmptyChest;
import com.walrusone.skywars.utilities.GlassColor;
import com.walrusone.skywars.utilities.Messaging;
import com.walrusone.skywars.utilities.StringUtils;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Effect;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.plugin.PluginManager;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.util.Vector;

public class Game
{
    private List<GamePlayer> gPlayers = new ArrayList();
    private List<GamePlayer> spectators = new ArrayList();
    private Map<GamePlayer, Integer> kills = new HashMap();
    private boolean forceEnd = false;
    private int fireworksCount = 0;
    private int count = 0;
    private int gameLength = 0;
    private boolean thunderStorm = false;
    private int nextStrike = 5;
    private int strikeCounter = 0;
    private int sanityChecker = 0;
    private GameState gameState;
    private String mapName;
    private World mapWorld;
    private GameMap gameMap;
    private int gameNumber;
    private Scoreboard scoreboard;
    private Objective objective;
    private int min;
    private int max;
    private int minPlayers;
    private int numberOfSpawns;
    private boolean shutdown = false;
    private Map<Integer, GamePlayer> availableSpawns = new HashMap();
    private Map<String, Integer> scoreboardData = new HashMap();
    private Location specSpawn;

    public Game(int gameNumber, String map)
    {
        this.gameNumber = gameNumber;
        if (SkyWarsReloaded.getCfg().signJoinMode()) {
            this.mapName = map;
        }
        int size = SkyWarsReloaded.getCfg().getMaxMapSize() / 2;
        this.min = (0 - size);
        this.max = (0 + size);
        if (SkyWarsReloaded.getCfg().getSpawn() == null)
        {
            SkyWarsReloaded.get().getLogger().info("�� ������ ���������� ����� � ���� ����� � ������� /SWR SETSPAWN ������ ��� �������� ������");
            endGame();
        }
        this.gameState = GameState.PREGAME;
        getGameMap();
        getScoreBoard();
    }

    private void getGameMap()
    {
        if (!SkyWarsReloaded.getCfg().signJoinMode())
        {
            ArrayList<String> shuffleMaps = SkyWarsReloaded.getMC().getMaps();
            Collections.shuffle(shuffleMaps);
            int numberOfMaps = shuffleMaps.size();
            int random = (int)(Math.random() * numberOfMaps) + 1;
            this.mapName = ((String)shuffleMaps.get(random - 1));
        }
        this.gameMap = SkyWarsReloaded.getMC().getMap(this.mapName);
        boolean gameLoad = this.gameMap.loadMap(this.gameNumber);
        this.mapWorld = SkyWarsReloaded.get().getServer().getWorld(this.mapName + "_" + this.gameNumber);
        if (gameLoad)
        {
            this.numberOfSpawns = this.gameMap.getSpawns().size();
            if (SkyWarsReloaded.getCfg().spectatingEnabled())
            {
                double x = ((Location)this.gameMap.getSpawns().get(Integer.valueOf(1))).getX() + 0.5D;
                double y = ((Location)this.gameMap.getSpawns().get(Integer.valueOf(1))).getY();
                double z = ((Location)this.gameMap.getSpawns().get(Integer.valueOf(1))).getZ() + 0.5D;
                this.specSpawn = new Location(this.mapWorld, x, y, z);
            }
            this.minPlayers = (this.numberOfSpawns * SkyWarsReloaded.getCfg().getMinPercentPlayers() / 100);
            if (this.minPlayers < 1) {
                this.minPlayers = 1;
            }
            for (int i = 1; i <= this.numberOfSpawns; i++) {
                this.availableSpawns.put(Integer.valueOf(i), null);
            }
            createSpawnPlatform();
        }
        else
        {
            endGame();
        }
    }

    public void addPlayer(GamePlayer gPlayer)
    {
        if ((this.gPlayers.size() >= this.numberOfSpawns) || (this.gPlayers.contains(gPlayer))) {
            return;
        }
        if (gPlayer.getP() != null)
        {
            this.gPlayers.add(gPlayer);
            this.kills.put(gPlayer, Integer.valueOf(0));
            gPlayer.setGame(this.gameNumber);
            gPlayer.setInGame(true);
            if (SkyWarsReloaded.getCfg().bungeeEnabled()) {
                BungeeUtil.sendSignUpdateRequest(this);
            }
            if (SkyWarsReloaded.getCfg().signJoinMode()) {
                SkyWarsReloaded.getGC().updateSign(this.gameNumber);
            }
            SkyWarsReloaded.getInvC().add(gPlayer.getP());
            preparePlayerForLobby(gPlayer);
            if ((SkyWarsReloaded.getCfg().resetTimerEnabled()) &&
                    (SkyWarsReloaded.getCfg().getResetTimerThreshold() >= this.gPlayers.size() / this.numberOfSpawns)) {
                this.count = 0;
            }
        }
    }

    private void preparePlayerForLobby(final GamePlayer gPlayer)
    {
        int spawn = getAvailableSpawn();
        Location location = new Location(this.mapWorld, ((Location)this.gameMap.getSpawns().get(Integer.valueOf(spawn))).getX() + 0.5D, ((Location)this.gameMap.getSpawns().get(Integer.valueOf(spawn))).getY(), ((Location)this.gameMap.getSpawns().get(Integer.valueOf(spawn))).getZ() + 0.5D);
        this.availableSpawns.put(Integer.valueOf(spawn), gPlayer);
        gPlayer.getP().teleport(location, PlayerTeleportEvent.TeleportCause.PLUGIN);
        SkyWarsReloaded.get().getServer().getScheduler().scheduleSyncDelayedTask(SkyWarsReloaded.get(), new Runnable()
        {
            public void run()
            {
                if (gPlayer.getP() != null)
                {
                    gPlayer.getP().getInventory().clear();
                    gPlayer.getP().getInventory().setHelmet(null);
                    gPlayer.getP().getInventory().setChestplate(null);
                    gPlayer.getP().getInventory().setLeggings(null);
                    gPlayer.getP().getInventory().setBoots(null);
                    gPlayer.getP().setLevel(0);
                    gPlayer.getP().setExp(0.0F);
                    gPlayer.getP().setHealth(20.0D);
                    gPlayer.getP().setFoodLevel(20);
                    gPlayer.getP().setFlying(false);
                    gPlayer.setOpVote(0);
                    gPlayer.setWeatherVote(0);
                    gPlayer.setTimeVote(0);
                    gPlayer.setJumpVote(0);
                    SkyWarsReloaded.getNMS().sendTitle(gPlayer.getP(), 20, 60, 20, new Messaging.MessageFormatter().setVariable("mapname", Game.this.mapName).formatNoColor("titles.joinGameTitle"), SkyWarsReloaded.getMessaging().getMessage("titles.joinGameSubtitle"));
                    for (PotionEffect effect : gPlayer.getP().getActivePotionEffects()) {
                        gPlayer.getP().removePotionEffect(effect.getType());
                    }
                    gPlayer.getP().setGameMode(GameMode.ADVENTURE);
                    Game.this.scoreboardData.put(gPlayer.getP().getName(), Integer.valueOf(1));
                    gPlayer.getP().setScoreboard(SkyWarsReloaded.get().getServer().getScoreboardManager().getNewScoreboard());
                    gPlayer.getP().setScoreboard(Game.this.scoreboard);
                    Game.this.updateScoreboard();
                    gPlayer.getP().getInventory().setItem(SkyWarsReloaded.getCfg().getExitItemSlot(), SkyWarsReloaded.getCfg().getExitGameItem());
                    if (SkyWarsReloaded.getCfg().optionsMenuEnabled()) {
                        gPlayer.getP().getInventory().setItem(SkyWarsReloaded.getCfg().getOptionsItemSlot(), SkyWarsReloaded.getCfg().getOptionsItem());
                    }
                    if (SkyWarsReloaded.getCfg().kitsEnabled()) {
                        gPlayer.getP().getInventory().setItem(SkyWarsReloaded.getCfg().getKitMenuItemSlot(), SkyWarsReloaded.getCfg().getKitMenuItem());
                    }
                    String color = gPlayer.getGlass();
                    if (color == null) {
                        color = "normal";
                    }
                    if (!color.equalsIgnoreCase("normal"))
                    {
                        GlassColor colorGlass = SkyWarsReloaded.getGLC().getByColor(color);
                        if (colorGlass != null) {
                            Game.this.setGlass(colorGlass.getMaterial(), colorGlass.getData(), gPlayer);
                        } else {
                            Game.this.setGlass(Material.GLASS, gPlayer);
                        }
                    }
                    else
                    {
                        Game.this.setGlass(Material.GLASS, gPlayer);
                    }
                }
            }
        }, 5L);
        sendGameMessage(new Messaging.MessageFormatter()
                .withPrefix()
                .setVariable("number", Integer.toString(this.gPlayers.size()))
                .setVariable("total", Integer.toString(this.numberOfSpawns))
                .setVariable("player", gPlayer.getP().getName())
                .format("game.lobby-join"));
        playSound(SkyWarsReloaded.getCfg().getJoinSound());
    }

    private int getAvailableSpawn()
    {
        for (Iterator localIterator = this.availableSpawns.keySet().iterator(); localIterator.hasNext();)
        {
            int spawn = ((Integer)localIterator.next()).intValue();
            if (this.availableSpawns.get(Integer.valueOf(spawn)) == null) {
                return spawn;
            }
        }
        return 1;
    }

    private void fillChests()
    {
        int votesForBasic = 0;
        int votesForNormal = 0;
        int votesForOP = 0;

        String vote = "normal";
        for (GamePlayer gPlayer : getPlayers()) {
            if (gPlayer.getOpVote() == 1) {
                votesForBasic++;
            } else if (gPlayer.getOpVote() == 2) {
                votesForNormal++;
            } else if (gPlayer.getOpVote() == 3) {
                votesForOP++;
            }
        }
        if ((votesForNormal >= votesForBasic) && (votesForNormal >= votesForOP)) {
            vote = "normal";
        } else if (votesForOP >= votesForBasic) {
            vote = "op";
        } else {
            vote = "basic";
        }
        for (EmptyChest eChest : this.gameMap.getChests().values())
        {
            int x = eChest.getX();
            int y = eChest.getY();
            int z = eChest.getZ();
            Location loc = new Location(this.mapWorld, x, y, z);
            Chest chest = (Chest)loc.getBlock().getState();
            SkyWarsReloaded.getCC().populateChest(chest, vote);
        }
        for (EmptyChest eChest : this.gameMap.getDoubleChests().values())
        {
            int x = eChest.getX();
            int y = eChest.getY();
            int z = eChest.getZ();
            Location loc = new Location(this.mapWorld, x, y, z);
            Chest chest = (Chest)loc.getBlock().getState();
            InventoryHolder ih = chest.getInventory().getHolder();
            DoubleChest dc = (DoubleChest)ih;
            SkyWarsReloaded.getCC().populateDoubleChest(dc, vote);
        }
    }

    public void prepareForStart()
    {
        if (this.gameState == GameState.PREGAME) {
            if (this.gPlayers.size() >= this.minPlayers)
            {
                if (this.count == 0) {
                    sendGameMessage(new Messaging.MessageFormatter()
                            .withPrefix()
                            .setVariable("time", Integer.toString(SkyWarsReloaded.getCfg().preGameTimer()))
                            .format("game.countdown"));
                } else if ((this.count > 0) && (this.count < SkyWarsReloaded.getCfg().preGameTimer()))
                {
                    if ((SkyWarsReloaded.getCfg().preGameTimer() - this.count) % 5 == 0) {
                        sendGameMessage(new Messaging.MessageFormatter()
                                .withPrefix()
                                .setVariable("time", Integer.toString(SkyWarsReloaded.getCfg().preGameTimer() - this.count))
                                .format("game.countdown-continue"));
                    } else if (SkyWarsReloaded.getCfg().preGameTimer() - this.count < 5) {
                        sendGameMessage(new Messaging.MessageFormatter()
                                .withPrefix()
                                .setVariable("time", Integer.toString(SkyWarsReloaded.getCfg().preGameTimer() - this.count))
                                .format("game.countdown-continue"));
                    }
                }
                else if (this.count >= SkyWarsReloaded.getCfg().preGameTimer()) {
                    startGame();
                }
                this.count += 1;
            }
            else if (this.gPlayers.size() < this.minPlayers)
            {
                this.count = 0;
            }
        }
        if ((this.gameState == GameState.PREGAME) || (this.gameState == GameState.PLAYING))
        {
            this.sanityChecker += 1;
            if (this.sanityChecker == 5)
            {
                String world = this.mapWorld.getName();
                for (GamePlayer gPlayer : getPlayers()) {
                    if (gPlayer.getP() != null)
                    {
                        if (!gPlayer.getP().getWorld().getName().equalsIgnoreCase(world)) {
                            if (gPlayer.getGame() != null)
                            {
                                Game game = gPlayer.getGame();
                                if (game == this)
                                {
                                    deletePlayer(gPlayer, true, false);
                                }
                                else
                                {
                                    this.gPlayers.remove(gPlayer);
                                    this.kills.remove(gPlayer);
                                    checkForWinner();
                                }
                            }
                            else
                            {
                                this.gPlayers.remove(gPlayer);
                                this.kills.remove(gPlayer);
                                checkForWinner();
                            }
                        }
                    }
                    else
                    {
                        this.gPlayers.remove(gPlayer);
                        this.kills.remove(gPlayer);
                        checkForWinner();
                    }
                }
            }
            else if (this.sanityChecker > 5)
            {
                this.sanityChecker = 0;
            }
        }
        int y;
        if (this.thunderStorm) {
            if (this.strikeCounter == this.nextStrike)
            {
                int hitPlayer = getRandomNum(100, 1);
                if (hitPlayer <= SkyWarsReloaded.getCfg().getStrikeChance())
                {
                    int size = this.gPlayers.size();
                    Player player = ((GamePlayer)this.gPlayers.get(getRandomNum(size - 1, 0))).getP();
                    this.mapWorld.strikeLightning(player.getLocation());
                }
                else
                {
                    int x = getRandomNum(this.max, this.min);
                    int z = getRandomNum(this.max, this.min);
                    y = getRandomNum(50, 20);
                    this.mapWorld.strikeLightningEffect(new Location(this.mapWorld, x, y, z));
                }
                this.nextStrike = getRandomNum(20, 3);
                this.strikeCounter = 0;
            }
            else
            {
                this.strikeCounter += 1;
            }
        }
        if ((this.gameState == GameState.PLAYING) && (!this.forceEnd))
        {
            this.gameLength += 1;
            if (SkyWarsReloaded.getCfg().getMaxGameLength() - this.gameLength == 300)
            {
                sendGameMessage(new Messaging.MessageFormatter()
                        .withPrefix()
                        .setVariable("time", "5")
                        .format("game.gameEndingTimer"));
            }
            else if (SkyWarsReloaded.getCfg().getMaxGameLength() - this.gameLength == 120)
            {
                sendGameMessage(new Messaging.MessageFormatter()
                        .withPrefix()
                        .setVariable("time", "2")
                        .format("game.gameEndingTimer"));
            }
            else if (SkyWarsReloaded.getCfg().getMaxGameLength() - this.gameLength == 60)
            {
                sendGameMessage(new Messaging.MessageFormatter()
                        .withPrefix()
                        .setVariable("time", "1")
                        .format("game.gameEndingTimer"));
            }
            else if (SkyWarsReloaded.getCfg().getMaxGameLength() - this.gameLength <= 0)
            {
                this.forceEnd = true;
                sendGameMessage(new Messaging.MessageFormatter()
                        .withPrefix()
                        .format("game.forceGameEnd"));
                int highest = 0;
                GamePlayer winner = null;
                for (GamePlayer gplayer : this.kills.keySet()) {
                    if (((Integer)this.kills.get(gplayer)).intValue() >= highest) {
                        winner = gplayer;
                    }
                }
                for (GamePlayer gplayer : getPlayers()) {
                    if (winner != gplayer) {
                        gplayer.getP().teleport(new Location(this.mapWorld, 0.0D, -64.0D, 0.0D), PlayerTeleportEvent.TeleportCause.PLUGIN);
                    }
                }
            }
        }
    }

    public void startGame()
    {
        this.gameState = GameState.PLAYING;
        if (SkyWarsReloaded.getCfg().bungeeEnabled()) {
            BungeeUtil.sendSignUpdateRequest(this);
        }
        if (SkyWarsReloaded.getCfg().signJoinMode()) {
            SkyWarsReloaded.getGC().updateSign(this.gameNumber);
        }
        Bukkit.getPluginManager().callEvent(new GameStartEvent(gameMap, gameNumber, gPlayers));
        fillChests();
        removeSpawnHousing();
        for (GamePlayer gPlayer : getPlayers()) {
            if (gPlayer.getP() != null)
            {
                gPlayer.getP().setGameMode(GameMode.SURVIVAL);
                gPlayer.getP().getInventory().remove(SkyWarsReloaded.getCfg().getKitMenuItem());
                gPlayer.getP().getInventory().remove(SkyWarsReloaded.getCfg().getOptionsItem());
                gPlayer.getP().getInventory().remove(SkyWarsReloaded.getCfg().getExitGameItem());
                gPlayer.getP().getOpenInventory().close();
                gPlayer.getP().setHealth(20.0D);
                gPlayer.getP().setFoodLevel(20);
                gPlayer.getP().addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 100, 5));
                if (gPlayer.hasKitSelected())
                {
                    SkyWarsReloaded.getKC().populateInventory(gPlayer.getP().getInventory(), gPlayer.getSelectedKit());
                    SkyWarsReloaded.getKC().givePotionEffects(gPlayer, gPlayer.getSelectedKit());
                    gPlayer.setKitSelected(false);
                }
            }
        }
        if (SkyWarsReloaded.getCfg().timeVoteEnabled()) {
            setTime();
        }
        if (SkyWarsReloaded.getCfg().jumpVoteEnabled()) {
            setJump();
        }
        if (SkyWarsReloaded.getCfg().weatherVoteEnabled()) {
            setWeather();
        }
    }

    public void endGame()
    {
        this.gameState = GameState.ENDING;
        if ((SkyWarsReloaded.getCfg().bungeeEnabled()) && (!this.shutdown)) {
            BungeeUtil.sendSignUpdateRequest(this);
        }
        if ((SkyWarsReloaded.getCfg().signJoinMode()) && (!this.shutdown)) {
            SkyWarsReloaded.getGC().updateSign(this.gameNumber);
        }
        for (GamePlayer gplayer : getPlayers()) {
            deletePlayer(gplayer, false, false);
        }
        if (SkyWarsReloaded.getCfg().spectatingEnabled()) {
            for (GamePlayer gPlayer : getSpectators()) {
                removeSpectator(gPlayer);
            }
        }
        for (Player player : this.mapWorld.getPlayers()) {
            if (player != null) {
                player.teleport(SkyWarsReloaded.getCfg().getSpawn(), PlayerTeleportEvent.TeleportCause.PLUGIN);
            }
        }
        for (Entity entity : this.mapWorld.getEntities()) {
            if (entity != null) {
                entity.remove();
            }
        }
        if ((!SkyWarsReloaded.getCfg().spectatingEnabled()) && (!this.shutdown)) {
            SkyWarsReloaded.get().getServer().getScheduler().scheduleSyncDelayedTask(SkyWarsReloaded.get(), new Runnable()
            {
                public void run()
                {
                    Game.this.deleteGame();
                }
            }, 20 * SkyWarsReloaded.getCfg().getTimeAfterGame());
        } else {
            deleteGame();
        }
    }

    private void getScoreBoard()
    {
        if (this.scoreboard != null) {
            resetScoreboard();
        }
        ScoreboardManager manager = SkyWarsReloaded.get().getServer().getScoreboardManager();
        this.scoreboard = manager.getNewScoreboard();
        this.objective = this.scoreboard.registerNewObjective("info", "dummy");
        this.objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        String leaderboard = new Messaging.MessageFormatter().setVariable("mapname", this.mapName.toUpperCase()).format("game.scoreboard-title");
        this.objective.setDisplayName(leaderboard);
    }

    private void updateScoreboard()
    {
        if (this.objective != null) {
            this.objective.unregister();
        }
        this.objective = this.scoreboard.registerNewObjective("info", "dummy");
        this.objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        String leaderboard = new Messaging.MessageFormatter().setVariable("mapname", this.mapName.toUpperCase()).format("game.scoreboard-title");
        this.objective.setDisplayName(leaderboard);
        if (SkyWarsReloaded.getCfg().usePlayerNames())
        {
            for (String name : this.scoreboardData.keySet()) {
                if (((Integer)this.scoreboardData.get(name)).intValue() == 0)
                {
                    Score score = this.objective.getScore(ChatColor.RED + name);
                    score.setScore(((Integer)this.scoreboardData.get(name)).intValue());
                }
                else
                {
                    Score score = this.objective.getScore(ChatColor.GREEN + name);
                    score.setScore(((Integer)this.scoreboardData.get(name)).intValue());
                }
            }
        }
        else
        {
            Score score = this.objective.getScore(new Messaging.MessageFormatter().format("game.scoreboard-players"));
            score.setScore(getPlayers().size());
        }
    }

    private void resetScoreboard()
    {
        if (this.objective != null) {
            this.objective.unregister();
        }
        if (this.scoreboard != null) {
            this.scoreboard = null;
        }
    }

    public void deletePlayer(final GamePlayer gplayer, boolean playerQuit, boolean hardQuit)
    {
        if (this.gPlayers.contains(gplayer))
        {
            this.gPlayers.remove(gplayer);
            this.kills.remove(gplayer);
        }
        if (playerQuit) {
            playerQuit(gplayer);
        }
        if (!hardQuit) {
            preparePlayerForExit(gplayer);
        }
        if ((this.gameState == GameState.PREGAME) || (this.gameState == GameState.PLAYING))
        {
            if ((SkyWarsReloaded.getCfg().bungeeEnabled()) && (!this.shutdown)) {
                BungeeUtil.sendSignUpdateRequest(this);
            }
            if ((SkyWarsReloaded.getCfg().signJoinMode()) && (!this.shutdown)) {
                SkyWarsReloaded.getGC().updateSign(this.gameNumber);
            }
        }
        if ((SkyWarsReloaded.getCfg().bungeeEnabled()) && (!this.shutdown) && (!hardQuit))
        {
            if (gplayer.getP() != null)
            {
                gplayer.getP().teleport(SkyWarsReloaded.getCfg().getSpawn(), PlayerTeleportEvent.TeleportCause.PLUGIN);
                SkyWarsReloaded.get().getServer().getScheduler().scheduleSyncDelayedTask(SkyWarsReloaded.get(), new Runnable()
                {
                    public void run()
                    {
                        SkyWarsReloaded.getInvC().restoreInventory(gplayer.getP());
                        BungeeUtil.connectToServer(gplayer.getP(), SkyWarsReloaded.getCfg().getLobbyServer());
                    }
                }, 5L);
            }
        }
        else if (gplayer.getP() != null)
        {
            gplayer.getP().teleport(SkyWarsReloaded.getCfg().getSpawn(), PlayerTeleportEvent.TeleportCause.PLUGIN);
            if (!this.shutdown) {
                SkyWarsReloaded.get().getServer().getScheduler().scheduleSyncDelayedTask(SkyWarsReloaded.get(), new Runnable()
                {
                    public void run()
                    {
                        if (gplayer.getP() != null)
                        {
                            SkyWarsReloaded.getInvC().restoreInventory(gplayer.getP());
                            SkyWarsReloaded.getScore().getScoreboard(gplayer.getP());
                        }
                    }
                }, 5L);
            }
        }
        if (this.gameState == GameState.PLAYING) {
            checkForWinner();
        }
    }

    public void shutdown()
    {
        this.shutdown = true;
    }

    private void playerQuit(GamePlayer gPlayer)
    {
        gPlayer.setInGame(false);
        GamePlayer killer = gPlayer.getTagged().getPlayer();
        if (this.gameState == GameState.PLAYING)
        {
            this.scoreboardData.put(gPlayer.getP().getName(), Integer.valueOf(0));
            updateScoreboard();
            if (!gPlayer.isSpectating()) {
                if ((System.currentTimeMillis() - gPlayer.getTagged().getTime().longValue() < 10000L) && (killer != gPlayer))
                {
                    if (killer != gPlayer)
                    {
                        killer.setKills(killer.getKills() + 1);
                        this.kills.put(killer, Integer.valueOf(((Integer)this.kills.get(killer)).intValue() + 1));
                        gPlayer.setDeaths(gPlayer.getDeaths() + 1);
                        int killTotal = SkyWarsReloaded.getCfg().getKillValue();
                        if (killer.getP() != null) {
                            if (killer.getP().hasPermission("swr.vip")) {
                                killTotal = SkyWarsReloaded.getCfg().getKillValue() * SkyWarsReloaded.getCfg().getVIPMultiplier();
                            } else {
                                killTotal = SkyWarsReloaded.getCfg().getKillValue();
                            }
                        }
                        killer.setScore(killer.getScore() + killTotal);
                        gPlayer.setScore(gPlayer.getScore() - SkyWarsReloaded.getCfg().getDeathValue());
                        addBalance(killer, killTotal);
                        removeBalance(gPlayer, SkyWarsReloaded.getCfg().getDeathValue());
                        sendGameMessage(new Messaging.MessageFormatter()
                                .withPrefix()
                                .setVariable("player", gPlayer.getName())
                                .setVariable("player_score", StringUtils.formatScore(-SkyWarsReloaded.getCfg().getDeathValue()))
                                .setVariable("killer", killer.getName())
                                .setVariable("killer_score", StringUtils.formatScore(killTotal))
                                .format("game.death.quit-while-tagged"));
                    }
                }
                else
                {
                    gPlayer.setScore(gPlayer.getScore() - SkyWarsReloaded.getCfg().getLeaveValue());
                    removeBalance(gPlayer, SkyWarsReloaded.getCfg().getLeaveValue());
                    sendGameMessage(new Messaging.MessageFormatter()
                            .withPrefix()
                            .setVariable("player", gPlayer.getName())
                            .setVariable("score", StringUtils.formatScore(-SkyWarsReloaded.getCfg().getLeaveValue()))
                            .format("game.left-the-game"));
                }
            }
        }
        else if (this.gameState == GameState.PREGAME)
        {
            this.scoreboardData.remove(gPlayer.getP().getName());
            updateScoreboard();
            for (Iterator localIterator = this.availableSpawns.keySet().iterator(); localIterator.hasNext();)
            {
                int spawn = ((Integer)localIterator.next()).intValue();
                if (this.availableSpawns.get(Integer.valueOf(spawn)) == gPlayer) {
                    this.availableSpawns.put(Integer.valueOf(spawn), null);
                }
            }
            sendGameMessage(new Messaging.MessageFormatter()
                    .withPrefix()
                    .setVariable("number", Integer.toString(this.gPlayers.size()))
                    .setVariable("total", Integer.toString(this.numberOfSpawns))
                    .setVariable("player", gPlayer.getName())
                    .format("game.lobby-leave"));
            playSound(SkyWarsReloaded.getCfg().getLeaveSound());
        }
    }

    private void preparePlayerForExit(GamePlayer gplayer)
    {
        if (gplayer.getP() != null)
        {
            gplayer.setInGame(false);
            gplayer.setKitSelected(false);
            gplayer.getP().getInventory().clear();
            gplayer.setOpVote(0);
            gplayer.setWeatherVote(0);
            gplayer.setTimeVote(0);
            gplayer.setJumpVote(0);
            gplayer.getP().setScoreboard(SkyWarsReloaded.get().getServer().getScoreboardManager().getNewScoreboard());
            for (PotionEffect effect : gplayer.getP().getActivePotionEffects()) {
                gplayer.getP().removePotionEffect(effect.getType());
            }
            gplayer.getP().setFireTicks(0);
            gplayer.setGame(-1);
        }
    }

    public Boolean playerExists(GamePlayer name)
    {
        for (GamePlayer gPlayer : this.gPlayers) {
            if (name.getUUID().toString().equalsIgnoreCase(gPlayer.getUUID().toString())) {
                return Boolean.valueOf(true);
            }
        }
        return Boolean.valueOf(false);
    }

    public ArrayList<GamePlayer> getPlayers()
    {
        ArrayList<GamePlayer> players = new ArrayList();
        for (GamePlayer g : this.gPlayers) {
            players.add(g);
        }
        return players;
    }

    public ArrayList<GamePlayer> getSpectators()
    {
        ArrayList<GamePlayer> players = new ArrayList();
        for (GamePlayer g : this.spectators) {
            players.add(g);
        }
        return players;
    }

    public void deleteGame()
    {
        SkyWarsReloaded.getWC().deleteWorld(this.mapName + "_" + this.gameNumber);
        deleteWorldGuardFolder(this.mapName + "_" + this.gameNumber);
        SkyWarsReloaded.getGC().deleteGame(this.gameNumber);
    }

    private void deleteWorldGuardFolder(String name)
    {
        File workingDirectory = new File(SkyWarsReloaded.get().getServer().getWorldContainer().getAbsolutePath());
        workingDirectory = new File(workingDirectory, "/plugins/WorldGuard/worlds/");
        File[] contents = workingDirectory.listFiles();
        if (contents != null)
        {
            File[] arrayOfFile1;
            int j = (arrayOfFile1 = contents).length;
            for (int i = 0; i < j; i++)
            {
                File file = arrayOfFile1[i];
                if ((file.isDirectory()) && (file.getName().matches(name))) {
                    SkyWarsReloaded.getWC().deleteWorld(file);
                }
            }
        }
    }

    public GameState getState()
    {
        return this.gameState;
    }

    public Boolean isFull()
    {
        if (this.gPlayers.size() >= this.numberOfSpawns) {
            return Boolean.valueOf(true);
        }
        return Boolean.valueOf(false);
    }

    public void createSpawnPlatform()
    {
        for (Iterator localIterator = this.gameMap.getSpawns().keySet().iterator(); localIterator.hasNext();)
        {
            int spawn = ((Integer)localIterator.next()).intValue();
            int x = ((Location)this.gameMap.getSpawns().get(Integer.valueOf(spawn))).getBlockX();
            int y = ((Location)this.gameMap.getSpawns().get(Integer.valueOf(spawn))).getBlockY();
            int z = ((Location)this.gameMap.getSpawns().get(Integer.valueOf(spawn))).getBlockZ();

            this.mapWorld.getBlockAt(x, y, z).setType(Material.GLASS);
            this.mapWorld.getBlockAt(x, y + 1, z + 1).setType(Material.GLASS);
            this.mapWorld.getBlockAt(x, y + 1, z - 1).setType(Material.GLASS);
            this.mapWorld.getBlockAt(x + 1, y + 1, z).setType(Material.GLASS);
            this.mapWorld.getBlockAt(x - 1, y + 1, z).setType(Material.GLASS);
            this.mapWorld.getBlockAt(x, y + 2, z + 1).setType(Material.GLASS);
            this.mapWorld.getBlockAt(x, y + 2, z - 1).setType(Material.GLASS);
            this.mapWorld.getBlockAt(x + 1, y + 2, z).setType(Material.GLASS);
            this.mapWorld.getBlockAt(x - 1, y + 2, z).setType(Material.GLASS);
            this.mapWorld.getBlockAt(x, y + 3, z + 1).setType(Material.GLASS);
            this.mapWorld.getBlockAt(x, y + 3, z - 1).setType(Material.GLASS);
            this.mapWorld.getBlockAt(x + 1, y + 3, z).setType(Material.GLASS);
            this.mapWorld.getBlockAt(x - 1, y + 3, z).setType(Material.GLASS);
            this.mapWorld.getBlockAt(x, y + 4, z).setType(Material.GLASS);
        }
    }

    public void removeSpawnHousing()
    {
        for (Iterator localIterator = this.gameMap.getSpawns().keySet().iterator(); localIterator.hasNext();)
        {
            int spawn = ((Integer)localIterator.next()).intValue();
            int x = ((Location)this.gameMap.getSpawns().get(Integer.valueOf(spawn))).getBlockX();
            int y = ((Location)this.gameMap.getSpawns().get(Integer.valueOf(spawn))).getBlockY();
            int z = ((Location)this.gameMap.getSpawns().get(Integer.valueOf(spawn))).getBlockZ();

            this.mapWorld.getBlockAt(x, y, z).setType(Material.AIR);
            this.mapWorld.getBlockAt(x, y + 1, z + 1).setType(Material.AIR);
            this.mapWorld.getBlockAt(x, y + 1, z - 1).setType(Material.AIR);
            this.mapWorld.getBlockAt(x + 1, y + 1, z).setType(Material.AIR);
            this.mapWorld.getBlockAt(x - 1, y + 1, z).setType(Material.AIR);
            this.mapWorld.getBlockAt(x, y + 2, z + 1).setType(Material.AIR);
            this.mapWorld.getBlockAt(x, y + 2, z - 1).setType(Material.AIR);
            this.mapWorld.getBlockAt(x + 1, y + 2, z).setType(Material.AIR);
            this.mapWorld.getBlockAt(x - 1, y + 2, z).setType(Material.AIR);
            this.mapWorld.getBlockAt(x, y + 3, z + 1).setType(Material.AIR);
            this.mapWorld.getBlockAt(x, y + 3, z - 1).setType(Material.AIR);
            this.mapWorld.getBlockAt(x + 1, y + 3, z).setType(Material.AIR);
            this.mapWorld.getBlockAt(x - 1, y + 3, z).setType(Material.AIR);
            this.mapWorld.getBlockAt(x, y + 4, z).setType(Material.AIR);
        }
    }

    public static enum GameState
    {
        PREGAME,  PLAYING,  ENDING
    }

    public void setState(GameState gState)
    {
        this.gameState = gState;
    }

    public void onPlayerDeath(final GamePlayer target, final EntityDamageEvent.DamageCause dCause, final Location loc)
    {
        Bukkit.getPluginManager().callEvent(new EntityDeathInGameEvent(target.getGame(), loc, target, dCause));
        this.gPlayers.remove(target);
        this.kills.remove(target);
        this.scoreboardData.put(target.getP().getName(), Integer.valueOf(0));
        updateScoreboard();
        preparePlayerForExit(target);
        if (SkyWarsReloaded.getCfg().spectatingEnabled())
        {
            target.setSpectating(true);
            target.setSpecGame(this.gameNumber);
        }
        final Game game = this;

        SkyWarsReloaded.get().getServer().getScheduler().scheduleSyncDelayedTask(SkyWarsReloaded.get(), new Runnable()
        {
            public void run()
            {
                if (target.getP() != null) {
                    SkyWarsReloaded.getNMS().respawnPlayer(target.getP());
                }
                if (!SkyWarsReloaded.getCfg().spectatingEnabled()) {
                    Game.this.deletePlayer(target, false, false);
                }
                target.setDeaths(target.getDeaths() + 1);
                Game.this.mapWorld.playEffect(loc, Effect.MOBSPAWNER_FLAMES, 4);
                if (System.currentTimeMillis() - target.getTagged().getTime().longValue() < 10000L)
                {
                    GamePlayer killer = target.getTagged().getPlayer();
                    if (killer != target)
                    {
                        killer.setKills(killer.getKills() + 1);
                        if (Game.this.kills.get(killer) != null) {
                            Game.this.kills.put(killer, Integer.valueOf(((Integer)Game.this.kills.get(killer)).intValue() + 1));
                        }
                        int killTotal = SkyWarsReloaded.getCfg().getKillValue();
                        if (killer.getP() != null) {
                            if (killer.getP().hasPermission("swr.vip")) {
                                killTotal = SkyWarsReloaded.getCfg().getKillValue() * SkyWarsReloaded.getCfg().getVIPMultiplier();
                            } else {
                                killTotal = SkyWarsReloaded.getCfg().getKillValue();
                            }
                        }
                        killer.setScore(killer.getScore() + killTotal);
                        target.setScore(target.getScore() - SkyWarsReloaded.getCfg().getDeathValue());
                        Game.this.addBalance(killer, killTotal);
                        Game.this.removeBalance(target, SkyWarsReloaded.getCfg().getDeathValue());
                        target.setTagged(target);
                        if (target.getP() != null)
                        {
                            target.getP().sendMessage(Game.this.getDeathMessage(dCause, true, target, killer));
                            target.getP().playSound(target.getP().getLocation(), SkyWarsReloaded.getCfg().getDeathSound(), 1.0F, 1.0F);
                        }
                        Game.this.sendGameMessage(getDeathMessage(dCause, false, target, target));
                        Game.this.playSound(SkyWarsReloaded.getCfg().getDeathSound());
                    }
                }
                else
                {
                    target.setScore(target.getScore() - SkyWarsReloaded.getCfg().getDeathValue());
                    Game.this.removeBalance(target, SkyWarsReloaded.getCfg().getDeathValue());
                    Game.this.sendGameMessage(getDeathMessage(dCause, false, target, target));
                    Game.this.playSound(SkyWarsReloaded.getCfg().getDeathSound());
                }
                target.setGamesPlayed(target.getGamesPlayed() + 1);
                if ((Game.this.gameState == Game.GameState.PREGAME) || (Game.this.gameState == Game.GameState.PLAYING))
                {
                    if ((SkyWarsReloaded.getCfg().bungeeEnabled()) && (!Game.this.shutdown)) {
                        BungeeUtil.sendSignUpdateRequest(game);
                    }
                    if ((SkyWarsReloaded.getCfg().signJoinMode()) && (!Game.this.shutdown)) {
                        SkyWarsReloaded.getGC().updateSign(Game.this.gameNumber);
                    }
                }
                Game.this.checkForWinner();
            }
        }, 1L);
    }

    public void checkForWinner()
    {
        if (this.gameState == GameState.PLAYING) {
            if (getPlayers().size() == 1)
            {
                this.gameState = GameState.ENDING;
                if (SkyWarsReloaded.getCfg().bungeeEnabled()) {
                    BungeeUtil.sendSignUpdateRequest(this);
                }
                if (SkyWarsReloaded.getCfg().signJoinMode()) {
                    SkyWarsReloaded.getGC().updateSign(this.gameNumber);
                }
                GamePlayer gPlayer = (GamePlayer)getPlayers().get(0);
                this.gPlayers.remove(gPlayer);
                this.kills.remove(gPlayer);
                preparePlayerForExit(gPlayer);
                gPlayer.setWins(gPlayer.getWins() + 1);
                gPlayer.setGamesPlayed(gPlayer.getGamesPlayed() + 1);
                playSound(SkyWarsReloaded.getCfg().getWinSound());
                if (gPlayer.getP() != null)
                {
                    Location loc = gPlayer.getP().getEyeLocation();
                    launchFireworkDisplay(this.mapWorld, loc);
                }
                int winTotal = SkyWarsReloaded.getCfg().getWinValue();
                if (gPlayer.getP() != null) {
                    if (gPlayer.getP().hasPermission("swr.vip")) {
                        winTotal = SkyWarsReloaded.getCfg().getWinValue() * SkyWarsReloaded.getCfg().getVIPMultiplier();
                    } else {
                        winTotal = SkyWarsReloaded.getCfg().getWinValue();
                    }
                }
                gPlayer.setScore(gPlayer.getScore() + winTotal);
                addBalance(gPlayer, winTotal);
                Bukkit.getServer().getPluginManager().callEvent(new GameEndEvent(gPlayer.getP(), this.mapName));
                if (SkyWarsReloaded.getCfg().spectatingEnabled())
                {
                    if (gPlayer.getP() != null)
                    {
                        gPlayer.spectateMode(true, this, gPlayer.getP().getLocation(), this.shutdown);
                        sendGameMessage(new Messaging.MessageFormatter().withPrefix()
                                .setVariable("time", Integer.toString(SkyWarsReloaded.getCfg().getTimeAfterGame()))
                                .format("game.gameEnding"));
                    }
                    SkyWarsReloaded.get().getServer().getScheduler().scheduleSyncDelayedTask(SkyWarsReloaded.get(), new Runnable()
                    {
                        public void run()
                        {
                            Game.this.endGame();
                        }
                    }, 20 * SkyWarsReloaded.getCfg().getTimeAfterGame());
                }
                else
                {
                    deletePlayer(gPlayer, false, false);
                    endGame();
                }
                if (SkyWarsReloaded.getCfg().WinBroadcastDisabled()) {
                    sendGameMessage(new Messaging.MessageFormatter().setVariable("player", gPlayer.getName())
                            .withPrefix()
                            .setVariable("player_score", StringUtils.formatScore(winTotal))
                            .setVariable("mapname", this.mapName)
                            .format("game.win"));
                } else {
                    for (GamePlayer gamePlayer : SkyWarsReloaded.getPC().getAll()) {
                        if (gamePlayer.getP() != null) {
                            gamePlayer.getP().sendMessage(new Messaging.MessageFormatter().setVariable("player", gPlayer.getName())
                                    .withPrefix()
                                    .setVariable("player_score", StringUtils.formatScore(winTotal))
                                    .setVariable("mapname", this.mapName)
                                    .format("game.win"));
                        }
                    }
                }
            }
            else if (getPlayers().size() < 1)
            {
                this.gameState = GameState.ENDING;
                if (SkyWarsReloaded.getCfg().bungeeEnabled()) {
                    BungeeUtil.sendSignUpdateRequest(this);
                }
                if (SkyWarsReloaded.getCfg().signJoinMode()) {
                    SkyWarsReloaded.getGC().updateSign(this.gameNumber);
                }
                if (SkyWarsReloaded.getCfg().spectatingEnabled())
                {
                    sendGameMessage(new Messaging.MessageFormatter().withPrefix()
                            .setVariable("time", Integer.toString(SkyWarsReloaded.getCfg().getTimeAfterGame()))
                            .format("game.gameEnding"));
                    SkyWarsReloaded.get().getServer().getScheduler().scheduleSyncDelayedTask(SkyWarsReloaded.get(), new Runnable()
                    {
                        public void run()
                        {
                            Game.this.endGame();
                        }
                    }, 20 * SkyWarsReloaded.getCfg().getTimeAfterGame());
                }
                else
                {
                    endGame();
                }
            }
        }
    }

    private void sendGameMessage(String message)
    {
        for (GamePlayer gPlayer : getPlayers()) {
            if (gPlayer.getP() != null) {
                gPlayer.getP().sendMessage(message);
            }
        }
        if (SkyWarsReloaded.getCfg().spectatingEnabled()) {
            for (GamePlayer spectator : getSpectators()) {
                if (spectator.getP() != null) {
                    spectator.getP().sendMessage(message);
                }
            }
        }
    }

    public void playSound(Sound sound)
    {
        for (GamePlayer gamePlayer : getPlayers()) {
            if (gamePlayer.getP() != null) {
                gamePlayer.getP().playSound(gamePlayer.getP().getLocation(), sound, 1.0F, 1.0F);
            }
        }
        for (GamePlayer gamePlayer : getSpectators()) {
            if ((!this.gPlayers.contains(gamePlayer)) &&
                    (gamePlayer.getP() != null)) {
                gamePlayer.getP().playSound(gamePlayer.getP().getLocation(), sound, 1.0F, 1.0F);
            }
        }
    }

    private String getDeathMessage(EntityDamageEvent.DamageCause dCause, boolean withHelp, GamePlayer target, GamePlayer killer)
    {
        String first = "";
        int killTotal = SkyWarsReloaded.getCfg().getKillValue();
        if (killer.getP() != null) {
            if (killer.getP().hasPermission("swr.vip")) {
                killTotal = SkyWarsReloaded.getCfg().getKillValue() * SkyWarsReloaded.getCfg().getVIPMultiplier();
            } else {
                killTotal = SkyWarsReloaded.getCfg().getKillValue();
            }
        }
        String second = new Messaging.MessageFormatter()
                .setVariable("killer", killer.getName())
                .setVariable("killer_score", StringUtils.formatScore(killTotal))
                .format("game.death.killer-section");
        if ((dCause.equals(EntityDamageEvent.DamageCause.BLOCK_EXPLOSION)) || (dCause.equals(EntityDamageEvent.DamageCause.ENTITY_EXPLOSION)))
        {
            first =

                    new Messaging.MessageFormatter().withPrefix().setVariable("player", target.getName()).setVariable("player_score", StringUtils.formatScore(-SkyWarsReloaded.getCfg().getDeathValue())).format("game.death.explosion");
        }
        else if (dCause.equals(EntityDamageEvent.DamageCause.DROWNING))
        {
            first =

                    new Messaging.MessageFormatter().withPrefix().setVariable("player", target.getName()).setVariable("player_score", StringUtils.formatScore(-SkyWarsReloaded.getCfg().getDeathValue())).format("game.death.drowning");
        }
        else if ((dCause.equals(EntityDamageEvent.DamageCause.FIRE)) || (dCause.equals(EntityDamageEvent.DamageCause.FIRE_TICK)))
        {
            first =

                    new Messaging.MessageFormatter().withPrefix().setVariable("player", target.getName()).setVariable("player_score", StringUtils.formatScore(-SkyWarsReloaded.getCfg().getDeathValue())).format("game.death.fire");
        }
        else if (dCause.equals(EntityDamageEvent.DamageCause.ENTITY_ATTACK))
        {
            first =

                    new Messaging.MessageFormatter().withPrefix().setVariable("player", target.getName()).setVariable("player_score", StringUtils.formatScore(-SkyWarsReloaded.getCfg().getDeathValue())).setVariable("killer", killer.getName()).setVariable("killer_score", StringUtils.formatScore(killTotal)).format("game.death.pvp");
            second = "";
        }
        else if (dCause.equals(EntityDamageEvent.DamageCause.FALLING_BLOCK))
        {
            first =

                    new Messaging.MessageFormatter().withPrefix().setVariable("player", target.getName()).setVariable("player_score", StringUtils.formatScore(-SkyWarsReloaded.getCfg().getDeathValue())).format("game.death.falling-block");
        }
        else if (dCause.equals(EntityDamageEvent.DamageCause.LAVA))
        {
            first =

                    new Messaging.MessageFormatter().withPrefix().setVariable("player", target.getName()).setVariable("player_score", StringUtils.formatScore(-SkyWarsReloaded.getCfg().getDeathValue())).format("game.death.lava");
        }
        else if (dCause.equals(EntityDamageEvent.DamageCause.PROJECTILE))
        {
            first =

                    new Messaging.MessageFormatter().withPrefix().setVariable("player", target.getName()).setVariable("player_score", StringUtils.formatScore(-SkyWarsReloaded.getCfg().getDeathValue())).setVariable("killer", killer.getName()).setVariable("killer_score", StringUtils.formatScore(killTotal)).format("game.death.projectile");
            second = "";
        }
        else if (dCause.equals(EntityDamageEvent.DamageCause.SUFFOCATION))
        {
            first =

                    new Messaging.MessageFormatter().withPrefix().setVariable("player", target.getName()).setVariable("player_score", StringUtils.formatScore(-SkyWarsReloaded.getCfg().getDeathValue())).format("game.death.suffocation");
        }
        else if (dCause.equals(EntityDamageEvent.DamageCause.VOID))
        {
            first =

                    new Messaging.MessageFormatter().withPrefix().setVariable("player", target.getName()).setVariable("player_score", StringUtils.formatScore(-SkyWarsReloaded.getCfg().getDeathValue())).format("game.death.void");
        }
        else
        {
            first =

                    new Messaging.MessageFormatter().withPrefix().setVariable("player", target.getName()).setVariable("player_score", StringUtils.formatScore(-SkyWarsReloaded.getCfg().getDeathValue())).format("game.death.general");
        }
        if (withHelp) {
            return first + second;
        }
        return first + "!";
    }

    public int getGameNumber()
    {
        return this.gameNumber;
    }

    public int getNumberOfSpawns()
    {
        return this.numberOfSpawns;
    }

    public boolean containsPlayer(Player player)
    {
        for (GamePlayer gPlayer : this.gPlayers) {
            if (gPlayer.getP() == player) {
                return true;
            }
        }
        return false;
    }

    public World getMapWorld()
    {
        return this.mapWorld;
    }

    public void addSpectator(GamePlayer gPlayer)
    {
        this.spectators.add(gPlayer);
    }

    public Location getSpawn()
    {
        return this.specSpawn;
    }

    public String getMapName()
    {
        return this.mapName;
    }

    public Scoreboard getScoreboard()
    {
        return this.scoreboard;
    }

    public void launchFireworkDisplay(final World w, final Location loc)
    {
        Firework fw = (Firework)w.spawn(loc.clone().add(new Vector(getRandomNum(5, -5), 1, getRandomNum(5, -5))), Firework.class);
        FireworkMeta meta = fw.getFireworkMeta();
        FireworkEffect effect = SkyWarsReloaded.getNMS().getFireworkEffect(getRandomColor(), getRandomColor(), getRandomColor(), getRandomColor(), getRandomColor(), getRandomType());
        meta.addEffect(effect);
        meta.setPower(getRandomNum(4, 1));
        fw.setFireworkMeta(meta);
        this.fireworksCount += 1;
        if (this.fireworksCount < (SkyWarsReloaded.getCfg().getTimeAfterGame() - 5) * 4) {
            SkyWarsReloaded.get().getServer().getScheduler().scheduleSyncDelayedTask(SkyWarsReloaded.get(), new Runnable()
            {
                public void run()
                {
                    Game.this.launchFireworkDisplay(w, loc);
                }
            }, 5L);
        }
    }

    public int getRandomNum(int max, int min)
    {
        Random rand = new Random();
        int ii = min + rand.nextInt(max - min + 1);
        return ii;
    }

    public FireworkEffect.Type getRandomType()
    {
        int type = getRandomNum(5, 1);
        switch (type)
        {
            case 1:
                return FireworkEffect.Type.STAR;
            case 2:
                return FireworkEffect.Type.CREEPER;
            case 3:
                return FireworkEffect.Type.BURST;
            case 4:
                return FireworkEffect.Type.BALL_LARGE;
            case 5:
                return FireworkEffect.Type.BALL;
        }
        return FireworkEffect.Type.STAR;
    }

    public Color getRandomColor()
    {
        int color = getRandomNum(17, 1);
        switch (color)
        {
            case 1:
                return Color.AQUA;
            case 2:
                return Color.BLACK;
            case 3:
                return Color.BLUE;
            case 4:
                return Color.FUCHSIA;
            case 5:
                return Color.GRAY;
            case 6:
                return Color.GREEN;
            case 7:
                return Color.LIME;
            case 8:
                return Color.MAROON;
            case 9:
                return Color.NAVY;
            case 10:
                return Color.OLIVE;
            case 11:
                return Color.ORANGE;
            case 12:
                return Color.PURPLE;
            case 13:
                return Color.RED;
            case 14:
                return Color.SILVER;
            case 15:
                return Color.TEAL;
            case 16:
                return Color.WHITE;
            case 17:
                return Color.YELLOW;
        }
        return Color.RED;
    }

    public void removeSpectator(GamePlayer gPlayer)
    {
        this.spectators.remove(gPlayer);
        if (SkyWarsReloaded.getCfg().bungeeEnabled())
        {
            if (gPlayer.getP() != null)
            {
                gPlayer.spectateMode(false, this, SkyWarsReloaded.getCfg().getSpawn(), this.shutdown);
                BungeeUtil.connectToServer(gPlayer.getP(), SkyWarsReloaded.getCfg().getLobbyServer());
            }
        }
        else if (gPlayer.getP() != null) {
            gPlayer.spectateMode(false, this, SkyWarsReloaded.getCfg().getSpawn(), this.shutdown);
        }
        if (gPlayer.getP() != null) {
            SkyWarsReloaded.getScore().getScoreboard(gPlayer.getP());
        }
    }

    public void sendSpectatorMessage(Player player, String message)
    {
        GamePlayer sender = SkyWarsReloaded.getPC().getPlayer(player.getUniqueId());
        int score = sender.getScore();
        String sValue;
        if (score < 0) {
            sValue = ChatColor.RED + "(-" + score + ")";
        } else {
            sValue = ChatColor.GREEN + "(+" + score + ")";
        }
        String name = player.getDisplayName();
        for (GamePlayer gPlayer : getSpectators()) {
            if (gPlayer.getP() != null) {
                gPlayer.getP().sendMessage(new Messaging.MessageFormatter()
                        .setVariable("score", sValue)
                        .setVariable("player", name)
                        .setVariable("message", message)
                        .format("spectatorchat"));
            }
        }
    }

    private void setTime()
    {
        String time = getTime();
        World world = SkyWarsReloaded.get().getServer().getWorld(this.mapName + "_" + this.gameNumber);
        if (time.equalsIgnoreCase("dawn")) {
            world.setTime(0L);
        } else if (time.equalsIgnoreCase("noon")) {
            world.setTime(6000L);
        } else if (time.equalsIgnoreCase("dusk")) {
            world.setTime(12000L);
        } else if (time.equalsIgnoreCase("midnight")) {
            world.setTime(18000L);
        }
    }

    private String getTime()
    {
        int votesForDawn = 0;
        int votesForNoon = 0;
        int votesForDusk = 0;
        int votesForMidnight = 0;
        for (GamePlayer gPlayer : getPlayers()) {
            if (gPlayer.getTimeVote() == 1) {
                votesForDawn++;
            } else if (gPlayer.getTimeVote() == 2) {
                votesForNoon++;
            } else if (gPlayer.getTimeVote() == 3) {
                votesForDusk++;
            } else if (gPlayer.getTimeVote() == 4) {
                votesForMidnight++;
            }
        }
        if ((votesForDawn >= votesForNoon) && (votesForDawn >= votesForDusk) && (votesForDawn >= votesForMidnight)) {
            return "dawn";
        }
        if ((votesForNoon >= votesForDusk) && (votesForNoon >= votesForMidnight)) {
            return "noon";
        }
        if (votesForDusk >= votesForMidnight) {
            return "dusk";
        }
        return "midnight";
    }

    private void setWeather()
    {
        String weather = getWeather();
        World world = SkyWarsReloaded.get().getServer().getWorld(this.mapName + "_" + this.gameNumber);
        if (weather.equalsIgnoreCase("sunny"))
        {
            world.setStorm(false);
            world.setWeatherDuration(Integer.MAX_VALUE);
        }
        else if (weather.equalsIgnoreCase("rain"))
        {
            world.setStorm(true);
            world.setWeatherDuration(Integer.MAX_VALUE);
        }
        else if (weather.equalsIgnoreCase("thunder storm"))
        {
            world.setStorm(true);
            world.setThundering(true);
            world.setThunderDuration(Integer.MAX_VALUE);
            world.setWeatherDuration(Integer.MAX_VALUE);
            this.thunderStorm = true;
        }
        else if (weather.equalsIgnoreCase("snow"))
        {
            for (int x = this.min; x < this.max; x++) {
                for (int z = this.min; z < this.max; z++) {
                    world.setBiome(x, z, Biome.ICE_PLAINS);
                }
            }
            world.setStorm(true);
            world.setWeatherDuration(Integer.MAX_VALUE);
            List<Chunk> chunks = getChunks();
            SkyWarsReloaded.getNMS().updateChunks(this.mapWorld, chunks);
            world.setStorm(true);
            world.setWeatherDuration(Integer.MAX_VALUE);
        }
    }

    private List<Chunk> getChunks()
    {
        int minX = this.min;
        int minZ = this.min;
        int maxX = this.max;
        int maxZ = this.max;
        int minY = 0;
        int maxY = 0;
        Block min = this.mapWorld.getBlockAt(minX, minY, minZ);
        Block max = this.mapWorld.getBlockAt(maxX, maxY, maxZ);
        Chunk cMin = min.getChunk();
        Chunk cMax = max.getChunk();
        List<Chunk> chunks = new ArrayList();
        for (int cx = cMin.getX(); cx < cMax.getX(); cx++) {
            for (int cz = cMin.getZ(); cz < cMax.getZ(); cz++)
            {
                Chunk currentChunk = this.mapWorld.getChunkAt(cx, cz);
                chunks.add(currentChunk);
            }
        }
        return chunks;
    }

    private String getWeather()
    {
        int votesForSunny = 0;
        int votesForRain = 0;
        int votesForThunder = 0;
        int votesForSnow = 0;
        for (GamePlayer gPlayer : getPlayers()) {
            if (gPlayer.getWeatherVote() == 1) {
                votesForSunny++;
            } else if (gPlayer.getWeatherVote() == 2) {
                votesForRain++;
            } else if (gPlayer.getWeatherVote() == 3) {
                votesForThunder++;
            } else if (gPlayer.getWeatherVote() == 4) {
                votesForSnow++;
            }
        }
        if ((votesForSunny >= votesForRain) && (votesForSunny >= votesForThunder) && (votesForSunny >= votesForSnow)) {
            return "sunny";
        }
        if ((votesForRain >= votesForThunder) && (votesForRain >= votesForSnow)) {
            return "rain";
        }
        if (votesForThunder >= votesForSnow) {
            return "thunder storm";
        }
        return "snow";
    }

    private void setJump()
    {
        String jump = getJump();
        if (!jump.equalsIgnoreCase("normal")) {
            if (jump.equalsIgnoreCase("high jump")) {
                for (GamePlayer gPlayer : getPlayers()) {
                    if (gPlayer.getP() != null) {
                        gPlayer.getP().addPotionEffect(new PotionEffect(PotionEffectType.JUMP, Integer.MAX_VALUE, SkyWarsReloaded.getCfg().getHighJumpValue()));
                    }
                }
            } else if (jump.equalsIgnoreCase("super jump")) {
                for (GamePlayer gPlayer : getPlayers()) {
                    if (gPlayer.getP() != null) {
                        gPlayer.getP().addPotionEffect(new PotionEffect(PotionEffectType.JUMP, Integer.MAX_VALUE, SkyWarsReloaded.getCfg().getSuperJumpValue()));
                    }
                }
            } else if (jump.equalsIgnoreCase("god jump")) {
                for (GamePlayer gPlayer : getPlayers()) {
                    if (gPlayer.getP() != null) {
                        gPlayer.getP().addPotionEffect(new PotionEffect(PotionEffectType.JUMP, Integer.MAX_VALUE, SkyWarsReloaded.getCfg().getGodJumpValue()));
                    }
                }
            }
        }
    }

    private String getJump()
    {
        int votesForNormal = 0;
        int votesForHigh = 0;
        int votesForSuper = 0;
        int votesForGod = 0;
        for (GamePlayer gPlayer : getPlayers()) {
            if (gPlayer.getJumpVote() == 1) {
                votesForNormal++;
            } else if (gPlayer.getJumpVote() == 2) {
                votesForHigh++;
            } else if (gPlayer.getJumpVote() == 3) {
                votesForSuper++;
            } else if (gPlayer.getJumpVote() == 4) {
                votesForGod++;
            }
        }
        if ((votesForNormal >= votesForHigh) && (votesForNormal >= votesForSuper) && (votesForNormal >= votesForGod)) {
            return "normal";
        }
        if ((votesForHigh >= votesForSuper) && (votesForHigh >= votesForGod)) {
            return "high jump";
        }
        if (votesForSuper >= votesForGod) {
            return "super jump";
        }
        return "god jump";
    }

    private void removeBalance(GamePlayer p, int x)
    {
        p.setBalance(p.getBalance() - x);
    }

    private void addBalance(GamePlayer p, int x)
    {
        p.setBalance(p.getBalance() + x);
    }

    public int getPlayerSpawn(GamePlayer gPlayer)
    {
        for (Iterator localIterator = this.availableSpawns.keySet().iterator(); localIterator.hasNext();)
        {
            int spawn = ((Integer)localIterator.next()).intValue();
            if (this.availableSpawns.get(Integer.valueOf(spawn)) == gPlayer) {
                return spawn;
            }
        }
        return -1;
    }

    public void setGlass(Material color, GamePlayer gPlayer)
    {
        if ((containsPlayer(gPlayer.getP())) && (this.gameState == GameState.PREGAME))
        {
            Material material = color;

            int spawn = getPlayerSpawn(gPlayer);
            if (spawn != -1)
            {
                int x = ((Location)this.gameMap.getSpawns().get(Integer.valueOf(spawn))).getBlockX();
                int y = ((Location)this.gameMap.getSpawns().get(Integer.valueOf(spawn))).getBlockY();
                int z = ((Location)this.gameMap.getSpawns().get(Integer.valueOf(spawn))).getBlockZ();

                this.mapWorld.getBlockAt(x, y, z).setType(material);
                this.mapWorld.getBlockAt(x, y + 1, z + 1).setType(material);
                this.mapWorld.getBlockAt(x, y + 1, z - 1).setType(material);
                this.mapWorld.getBlockAt(x + 1, y + 1, z).setType(material);
                this.mapWorld.getBlockAt(x - 1, y + 1, z).setType(material);
                this.mapWorld.getBlockAt(x, y + 2, z + 1).setType(material);
                this.mapWorld.getBlockAt(x, y + 2, z - 1).setType(material);
                this.mapWorld.getBlockAt(x + 1, y + 2, z).setType(material);
                this.mapWorld.getBlockAt(x - 1, y + 2, z).setType(material);
                this.mapWorld.getBlockAt(x, y + 3, z + 1).setType(material);
                this.mapWorld.getBlockAt(x, y + 3, z - 1).setType(material);
                this.mapWorld.getBlockAt(x + 1, y + 3, z).setType(material);
                this.mapWorld.getBlockAt(x - 1, y + 3, z).setType(material);
                this.mapWorld.getBlockAt(x, y + 4, z).setType(material);
            }
        }
    }

    public void setGlass(Material color, byte data, GamePlayer gPlayer)
    {
        if ((containsPlayer(gPlayer.getP())) && (this.gameState == GameState.PREGAME))
        {
            Material material = color;

            int spawn = getPlayerSpawn(gPlayer);
            if (spawn != -1)
            {
                int x = ((Location)this.gameMap.getSpawns().get(Integer.valueOf(spawn))).getBlockX();
                int y = ((Location)this.gameMap.getSpawns().get(Integer.valueOf(spawn))).getBlockY();
                int z = ((Location)this.gameMap.getSpawns().get(Integer.valueOf(spawn))).getBlockZ();

                this.mapWorld.getBlockAt(x, y, z).setType(material);
                this.mapWorld.getBlockAt(x, y, z).setData(data);
                this.mapWorld.getBlockAt(x, y + 1, z + 1).setType(material);
                this.mapWorld.getBlockAt(x, y + 1, z + 1).setData(data);
                this.mapWorld.getBlockAt(x, y + 1, z - 1).setType(material);
                this.mapWorld.getBlockAt(x, y + 1, z - 1).setData(data);
                this.mapWorld.getBlockAt(x + 1, y + 1, z).setType(material);
                this.mapWorld.getBlockAt(x + 1, y + 1, z).setData(data);
                this.mapWorld.getBlockAt(x - 1, y + 1, z).setType(material);
                this.mapWorld.getBlockAt(x - 1, y + 1, z).setData(data);
                this.mapWorld.getBlockAt(x, y + 2, z + 1).setType(material);
                this.mapWorld.getBlockAt(x, y + 2, z + 1).setData(data);
                this.mapWorld.getBlockAt(x, y + 2, z - 1).setType(material);
                this.mapWorld.getBlockAt(x, y + 2, z - 1).setData(data);
                this.mapWorld.getBlockAt(x + 1, y + 2, z).setType(material);
                this.mapWorld.getBlockAt(x + 1, y + 2, z).setData(data);
                this.mapWorld.getBlockAt(x - 1, y + 2, z).setType(material);
                this.mapWorld.getBlockAt(x - 1, y + 2, z).setData(data);
                this.mapWorld.getBlockAt(x, y + 3, z + 1).setType(material);
                this.mapWorld.getBlockAt(x, y + 3, z + 1).setData(data);
                this.mapWorld.getBlockAt(x, y + 3, z - 1).setType(material);
                this.mapWorld.getBlockAt(x, y + 3, z - 1).setData(data);
                this.mapWorld.getBlockAt(x + 1, y + 3, z).setType(material);
                this.mapWorld.getBlockAt(x + 1, y + 3, z).setData(data);
                this.mapWorld.getBlockAt(x - 1, y + 3, z).setType(material);
                this.mapWorld.getBlockAt(x - 1, y + 3, z).setData(data);
                this.mapWorld.getBlockAt(x, y + 4, z).setType(material);
                this.mapWorld.getBlockAt(x, y + 4, z).setData(data);
            }
        }
    }
}
