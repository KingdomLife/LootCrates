package com.patrickzhong.lootcrates;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;

/**
 * 
 * @author Patrick
 * A object representation of a crate
 *
 */
public class Crate {
	Location loc;
	int level;
	boolean alive;
	long spawnTime;
	long upgradeTime;
	
	public Crate(Location location, int level, int upgradeT){ // Creates a crate representation with specified properties
		loc = location;
		this.level = level;
		alive = true;
	}
	
	public Crate(String serializedCrate){                     // Creates a crate representation using a serialized crate
		String[] arr = serializedCrate.split(",");
		loc = new Location(Bukkit.getServer().getWorld(arr[0]), Double.parseDouble(arr[1]), Double.parseDouble(arr[2]), Double.parseDouble(arr[3]));
		level = Integer.parseInt(arr[4]);
		alive = Boolean.parseBoolean(arr[5]);
		spawnTime = Long.parseLong(arr[6]);
		upgradeTime = Long.parseLong(arr[7]);
	}
	
	public String toString(){                                 // Returns the serialization of this crate
		return loc.getWorld().getName()+","+loc.getX()+","+loc.getY()+","+loc.getZ()+","+level+","+alive+","+spawnTime+","+upgradeTime;
	}
}
