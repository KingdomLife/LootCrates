package com.patrickzhong.lootcrates;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.block.Chest;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

/**
 * 
 * @author Patrick Zhong
 *
 */
public class LootCrates extends JavaPlugin implements Listener{
	
	File configFile;
	File locEmptyFile;
	File locUsedFile;
	FileConfiguration config = null;
	FileConfiguration locEmpty = null;
	FileConfiguration locUsed = null;
	public static Plugin plugin;
	
	private String prefix = ChatColor.DARK_GRAY + "" + ChatColor.BOLD + "[" + ChatColor.AQUA + "LootCrates" + ChatColor.DARK_GRAY + "" + ChatColor.BOLD + "] ";
	
	public void onEnable(){
		plugin = this;
		this.getServer().getPluginManager().registerEvents(this, this);   // Registers plugin to listen events
		
		try{
            if(!getDataFolder().exists())
            	getDataFolder().mkdir();                                  // No data folder; creating one
            configFile = new File(getDataFolder(), "config.yml");
            locEmptyFile = new File(getDataFolder(), "locationempty.yml");
            locUsedFile = new File(getDataFolder(), "locationused.yml");
            if (!configFile.exists()){                                    // Creates new config.yml if not in existence
            	configFile.createNewFile();
            	config = YamlConfiguration.loadConfiguration(configFile); // Loads configuration from file
        		config.addDefault("ItemHighestLevel", 70);                // Highest level of an item
        		config.addDefault("ItemMaxLevelDifference", 10);          // Max level difference of an item
        		config.addDefault("AllowNewCrateLocations", true);        // Whether adding locations is allowed
        		config.addDefault("CrateRespawnDelay", 30);               // Delay for crate respawn (seconds)
        		config.addDefault("CrateUpgradeDelay", 10);               // Delay for crate upgrade (seconds)
        		config.options().copyDefaults(true);                      // Implements defaults
        		saveC();
            }
            
            if(!locEmptyFile.exists())                                    // Creates new locationempty.yml if not in existence
            	locEmptyFile.createNewFile();
            
            
            if(!locUsedFile.exists())                                     // Creates new locationused.yml if not in existence
            	locUsedFile.createNewFile();
            
        } catch (IOException e){
            e.printStackTrace();
        }
		
		primeUpgrades();
		primeRespawns();
		
		getLogger().info("LootCrates enabled!");
	}
	
	
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args){
		
		Player player = (Player) sender;                                  // Player who sends the command
		
		if(cmd.getName().equalsIgnoreCase("lcadd")){
			if(args.length < 1){
				messageP(player, ChatColor.RED+"Not enough arguments");
				return false;
			}
			
			refreshC();
			if(config.getBoolean("AllowNewCrateLocations")){              // Checks if adding is allowed
				Location loc;
				
				if(args[0].equals("here")){
					loc = player.getLocation();                           // Sets location to player's location
				}else {
					String[] coords = args[0].split(",");                 // Parses string location
					loc = new Location(player.getWorld(), Double.parseDouble(coords[0]), Double.parseDouble(coords[1]), Double.parseDouble(coords[2]));
				}
				
				spawnCrate(loc);                                          // Spawn a new crate
				messageP(player, ChatColor.GREEN+"Successfully added crate at "+loc.getWorld().getName()+","+loc.getX()+","+loc.getY()+","+loc.getZ());
			}else
				messageP(player, ChatColor.RED+"Adding new crates is disabled in config.");
			
			return true;
			
		}
		
		return false;
	}
	
	private void spawnCrate(Location loc){
		refreshLU();
		
		Block block = loc.getBlock();                                     // Gets block at location and turns into a chest with crate metadata
		block.setType(Material.CHEST);
		block.setMetadata("isCrate", new FixedMetadataValue(this, true));
		
		int delay = config.getInt("CrateUpgradeDelay");                   // Gets upgrade delay and item max level difference
		int maxLevelDiff = config.getInt("ItemMaxLevelDifference");
		
		Crate crate = new Crate(loc, (int)Math.floor(Math.random()*(maxLevelDiff/2+1)+maxLevelDiff/2)*-1, delay); // New crate with random level
		Date now = new Date();
		crate.spawnTime = 0;
		crate.upgradeTime = now.getTime() + config.getInt("CrateUpgradeDelay")*1000; // Sets upgrade target time in milliseconds
		
		locUsed.set(serializeLoc(block.getLocation()), crate.toString()); // Sets string crate to string location and saves file
		saveLU();
		
		setTimer(crate, "upgrade");                                       // Sets a timer for upgrading
		
	}
	
	private void respawn(Crate crate){
		refreshLE();
		refreshLU();
		refreshC();
		
		Block block = crate.loc.getBlock();
		block.setType(Material.CHEST);
		block.setMetadata("isCrate", new FixedMetadataValue(this, true));
		
		String sLoc = serializeLoc(block.getLocation());                 // Gets serialized location
		int maxLevelDiff = config.getInt("ItemMaxLevelDifference");
		locEmpty.set(sLoc, null);                                        // Deletes crate from Empty file
		
		crate.alive = true;                                              // Sets crate to alive, assigns random level and upgrade target time
		Date now = new Date();
		crate.spawnTime = 0;
		crate.level = (int)Math.floor(Math.random()*(maxLevelDiff/2+1)+maxLevelDiff/2)*-1;
		crate.upgradeTime = now.getTime() + config.getInt("CrateUpgradeDelay")*1000;
		
		locUsed.set(sLoc, crate.toString());                             // Sets string crate to serialized location
		
		saveLE();
		saveLU();
		
		setTimer(crate, "upgrade");                                      // Sets timer for upgrading
	}
	
	@EventHandler
	public void onInvClose(InventoryCloseEvent ev){
		InventoryHolder holder = ev.getInventory().getHolder();
		if(holder instanceof Chest && ((Chest)holder).hasMetadata("isCrate") && ((Chest)holder).getMetadata("isCrate").size() == 1 && ((Chest)holder).getMetadata("isCrate").get(0).asBoolean()){
			refreshLE();                                                 // Closed chest is a crate
			refreshLU();
			refreshC();
			
			Chest chest = (Chest)holder;                                 // Deletes crate from Used list
			String sLoc = serializeLoc(chest.getLocation());
			Crate crate = new Crate(locUsed.getString(sLoc)); 
			locUsed.set(sLoc, null);
			
			crate.alive = false;                                         // Sets crate to dead, assigns spawn target time in milliseconds
			Date now = new Date();
			crate.spawnTime = now.getTime() + config.getInt("CrateRespawnDelay")*1000;
			crate.upgradeTime = 0;
			
			locEmpty.set(sLoc, crate.toString());
			
			saveLE();
			saveLU();
			chest.removeMetadata("isCrate", this);                       // Removes crate metadata from block
			Block c = chest.getWorld().getBlockAt(chest.getLocation());  // Block representing chest
			c.setType(Material.AIR);                                     // Removes block
			setTimer(crate, "respawn");                                  // Sets timer for respawn
			messageP((Player)ev.getPlayer(), ChatColor.YELLOW+"You have looted a level "+crate.level+" crate.");
		}
	}
	
	private void upgrade(Crate crate){
		refreshLU();
		String sLoc = serializeLoc(crate.loc.getBlock().getLocation()); // Accurate serialized location
		String crateString = locUsed.getString(sLoc);                   // Gets string crate, null if not in Used
		if(crateString == null)                                         // Crate is not in Used list; thus must be dead
			return;
		crate = new Crate(crateString);                                 // Creates Crate representation using serialized crate
		
		refreshC();
		int lvlUp = (int)Math.floor(Math.random()*3);                   // Random level up
		int maxDiff = config.getInt("ItemMaxLevelDifference");
		if(crate.level+lvlUp >= maxDiff)                                // Levels up crate
			crate.level = maxDiff;
		else
			crate.level += lvlUp;
		crate.upgradeTime = (new Date()).getTime() + config.getInt("CrateUpgradeDelay")*1000; // Assigns next target upgrade time
		setTimer(crate, "upgrade");                                     // Sets timer for upgrading
		locUsed.set(sLoc, crate.toString());                            // Sets new crate into file with serialized location key
		getLogger().info("Crate upgraded.");
		saveLU();
		
	}
	
	private BukkitTask setTimer(Crate nonFCrate, String whatToDo){      // Sets delayed task to upgrade/respawn crate
		refreshC();
		final Crate crate = nonFCrate;
		int delay;
		BukkitTask timer;
		
		if(whatToDo.equals("upgrade")){
			delay = config.getInt("CrateUpgradeDelay");
			
			timer = new BukkitRunnable(){
		    	public void run(){
		    		upgrade(crate);
		    	}
	        }.runTaskLater(this, delay*20);                             // Convert seconds to ticks
		}
		else {
			delay = config.getInt("CrateRespawnDelay");
			
			timer = new BukkitRunnable(){
		    	public void run(){
		    		respawn(crate);
		    	}
	        }.runTaskLater(this, delay*20);
		}
        
        return timer;
	}
	
	private void primeUpgrades(){                                      // Handles catching up on upgrading after plugin startup
		locUsed = YamlConfiguration.loadConfiguration(locUsedFile);
		Set<String> used = locUsed.getKeys(false);                     // All used serialized locations
		for(String sCrate : used){
			Crate c = new Crate(locUsed.getString(sCrate));
			c.loc.getBlock().setMetadata("isCrate", new FixedMetadataValue(this, true)); // Renews crate metadata
			long time = (new Date()).getTime();
			if(time >= c.upgradeTime)                                  // Past upgrade time; upgrade.
				upgrade(c);
			else {                                                     // Upgrade is in future; sets timer for upgrading
				long delay = (c.upgradeTime-time)/1000;
				final Crate cr = c;
				new BukkitRunnable(){
			    	public void run(){
			    		upgrade(cr);
			    	}
		        }.runTaskLater(this, (int)delay*20);
			}
		}
	}
	
	private void primeRespawns(){                                      // Handles catching up on respawns after plugin startup
		refreshLE();
		Set<String> empty = locEmpty.getKeys(false);
		for(String sCrate : empty){
			Crate c = new Crate(locEmpty.getString(sCrate));
			long time = (new Date()).getTime();
			if(time >= c.spawnTime)
				respawn(c);
			else {
				long delay = (c.spawnTime-time)/1000;
				final Crate cr = c;
				new BukkitRunnable(){
			    	public void run(){
			    		respawn(cr);
			    	}
		        }.runTaskLater(this, (int)delay*20);
			}
		}
	}
	
	private String serializeLoc(Location loc){                         // Returns a string representation of a location that is easier to use and store
		return (loc.getWorld().getName()+","+loc.getX()+","+loc.getY()+","+loc.getZ()).replace('.', ';');
	}
	
	private void messageP(Player player, String message){              // Messages a player with a specified message
		player.sendMessage(prefix+message);
	}
	
	private void messageAll(String message){                           // Messages all players with a specified message
		for(Player player : Bukkit.getServer().getOnlinePlayers())
			player.sendMessage(prefix+message);
		getLogger().info(prefix+message);
	}
	
	private void refreshC(){                                           // Refreshes config.yml configuration to receive any changes from console
		config = YamlConfiguration.loadConfiguration(configFile);
	}
	
	private void refreshLE(){
		locEmpty = YamlConfiguration.loadConfiguration(locEmptyFile);  // Refreshes locationempty.yml configuration to receive any changes from console
	}
	
	private void refreshLU(){                                          // Refreshes locationused.yml configuration to receive any changes from console
		locUsed = YamlConfiguration.loadConfiguration(locUsedFile);
	}
	
	private void saveC(){                                              // Saves config.yml
		try {
			config.save(configFile);
		} catch(IOException e) {
			  e.printStackTrace();
		}
	}
	
	private void saveLE(){                                             // Saves locationempty.yml
		try {
			locEmpty.save(locEmptyFile);
		} catch(IOException e) {
			  e.printStackTrace();
		}
	}
	
	private void saveLU(){                                             // Saves locationused.yml
		try {
			locUsed.save(locUsedFile);
		} catch(IOException e) {
			  e.printStackTrace();
		}
	}
}
