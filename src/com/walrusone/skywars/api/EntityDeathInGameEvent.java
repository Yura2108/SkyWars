package com.walrusone.skywars.api;

import com.walrusone.skywars.game.Game;
import com.walrusone.skywars.game.GamePlayer;
import org.bukkit.Location;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.EntityDamageEvent;

public class EntityDeathInGameEvent extends Event {
    public static final HandlerList handlers = new HandlerList();
    public static Game game;
    public static Location loc;
    public static GamePlayer entity;
    public static EntityDamageEvent.DamageCause damageCause;


    public EntityDeathInGameEvent(Game g, Location l, GamePlayer e, EntityDamageEvent.DamageCause d){
        this.game = g;
        this.loc = l;
        this.entity = e;
        this.damageCause = d;
    }

    public static Game getGame() {
        return game;
    }

    public static Location getLoc() {
        return loc;
    }

    public static GamePlayer getEntity() {
        return entity;
    }

    public static EntityDamageEvent.DamageCause getDamageCause() {
        return damageCause;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }
    public static HandlerList getHandlerList()
    {
        return handlers;
    }
}
