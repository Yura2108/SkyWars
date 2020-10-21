package com.walrusone.skywars.api;

import com.walrusone.skywars.game.GameMap;
import com.walrusone.skywars.game.GamePlayer;
import java.util.List;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class GameStartEvent extends Event {
    public static final HandlerList handlers = new HandlerList();
    public static GameMap gameMap;
    public static int gameIndex;
    public static List<GamePlayer> gamePlayers;

    public GameStartEvent(GameMap map, int gameI, List<GamePlayer> gPlayers) {
        gameMap = map;
        gameIndex = gameI;
        gamePlayers = gPlayers;
    }

    public GameMap getGameMap() {
        return gameMap;
    }

    public int getGameIndex() {
        return gameIndex;
    }

    public List<GamePlayer> getGamePlayers() {
        return gamePlayers;
    }

    public HandlerList getHandlers() {
        return handlers;
    }
    public static HandlerList getHandlerList()
    {
        return handlers;
    }
}
