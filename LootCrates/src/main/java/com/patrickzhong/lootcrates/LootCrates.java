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
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.block.Chest;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import com.patrickzhong.kingdomlifeapi.KingdomLifeAPI;

import net.minecraft.server.v1_8_R3.EnumParticle;
import net.minecraft.server.v1_8_R3.PacketPlayOutWorldParticles;
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
	//private KingdomLifeAPI kLifeAPI;
	final EnumParticle[] particles = {EnumParticle.CLOUD, EnumParticle.CRIT_MAGIC, EnumParticle.SPELL_WITCH, EnumParticle.VILLAGER_HAPPY};
	
	HashMap<Player,Crate> openCrates = new HashMap<Player,Crate>();
	
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
        		config.addDefault("DebugLogging", false);        // Whether debug messages show
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
		startParticles();
		
		/*new BukkitRunnable(){
			public void run(){
				if (!setUpKingdomLifeAPI() ) {
		            getLogger().severe(String.format("[%s] - Disabled due to no KingdomLifeAPI found!", getDescription().getName()));
		            getServer().getPluginManager().disablePlugin(plugin);
		            return;
		        }
				getLogger().info("LootCrates enabled!");
			}
		}.runTaskLater(plugin, 1);
		*/
	}
	
	/*private boolean setUpKingdomLifeAPI(){
		if (getServer().getPluginManager().getPlugin("KingdomLifeAPI") == null) {
            return false;
        }
		
		//getLogger().info((getServer().getPluginManager().getPlugin("KingdomLifeAPI") == null)+"");
		//getLogger().info(getServer().getPluginManager().getPlugins().toString());
        RegisteredServiceProvider<KingdomLifeAPI> rsp = getServer().getServicesManager().getRegistration(KingdomLifeAPI.class);
        if (rsp == null) {
            return false;
        }
        kLifeAPI = rsp.getProvider();
        return kLifeAPI != null;
	}*/
	
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
				messageP(player, ChatColor.GREEN+"Successfully added crate at "+loc.getWorld().getName()+","+loc.getBlockX()+","+loc.getBlockY()+","+loc.getBlockZ());
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
	public void onInvOpen(InventoryOpenEvent ev){
		InventoryHolder holder = ev.getInventory().getHolder();
		if(holder instanceof Chest && ((Chest)holder).hasMetadata("isCrate") && ((Chest)holder).getMetadata("isCrate").size() == 1 && ((Chest)holder).getMetadata("isCrate").get(0).asBoolean()){
			//And now for the MAGIC
			refreshC();
			
			ev.setCancelled(true);
			
			Chest chest = (Chest)holder;
			String sLoc = serializeLoc(chest.getLocation());
			Crate crate = new Crate(locUsed.getString(sLoc)); 
			Player player = (Player)ev.getPlayer();
			
			openCrates.put(player, crate);
			
			String uuid = player.getUniqueId().toString();
			String type = KingdomLifeAPI.type(uuid);
			int level = KingdomLifeAPI.level(uuid, type) + crate.level;
			if(level < 0)
				level = 0;
			else {
				int maxLevel = config.getInt("ItemHighestLevel");
				if(level > maxLevel)
					level = maxLevel;
			}
			
			String[] rarityArr = {"Common","Uncommon","Unique","Rare"};
			int ranRar  = (int)Math.floor(Math.random()*4);
			
			List<ItemStack> items = KingdomLifeAPI.getItems(type, rarityArr[ranRar], level+"");
			ItemStack item = items.get((int)Math.floor(Math.random()*items.size()));
			
			int shards = (int)Math.floor(Math.random()*5+1) + level * 4;
			ItemStack shardStack = new ItemStack(Material.PRISMARINE_SHARD, shards);
			ItemMeta im = shardStack.getItemMeta();
			List<String> lores = im.getLore();
			if(lores == null)
				lores = new ArrayList<String>();
			lores.add(ChatColor.GRAY+"Worth: 1/64th Emerald");
			lores.add("");
			lores.add(ChatColor.GREEN+"Currency");
			im.setDisplayName(ChatColor.GREEN+"Emerald Shard");
			im.setLore(lores);
			shardStack.setItemMeta(im);
			
			int tier = (int)Math.floor(((double)crate.level)/5.0);
			
			Inventory spectate = Bukkit.createInventory(ev.getPlayer(), 4, "Tier "+tier+" Loot Crate");
	        
			spectate.setItem(0, item);
			spectate.setItem(1, shardStack);

			player.openInventory(spectate);
		}
		
	}
	
	@EventHandler
	public void onInvClose(InventoryCloseEvent ev){
		//InventoryHolder holder = ev.getInventory().getHolder();
		//if(holder instanceof Chest && ((Chest)holder).hasMetadata("isCrate") && ((Chest)holder).getMetadata("isCrate").size() == 1 && ((Chest)holder).getMetadata("isCrate").get(0).asBoolean()){
		Player player = (Player)ev.getPlayer();
		if(openCrates.containsKey(player)){
			refreshLE();                                                 // Closed chest is a crate
			refreshLU();
			refreshC();
			
			Crate crate = openCrates.remove(player); 
			String sLoc = serializeLoc(crate.loc);
			Chest chest = (Chest)crate.loc.getBlock();                   // Deletes crate from Used list
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
			if(config.getBoolean("DebugLogging"))                        // Checks if debug logging is on
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
		if(config.getBoolean("DebugLogging"))
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
	
	private void startParticles(){
		new BukkitRunnable(){
			public void run(){
				locUsed = YamlConfiguration.loadConfiguration(locUsedFile);
				Set<String> used = locUsed.getKeys(false);                     // All used serialized locations
				for(String sCrate : used){
					String[] arr = sCrate.replace(";", ".").split(",");
					final Location loc = new Location(Bukkit.getServer().getWorld(arr[0]), Double.parseDouble(arr[1]), Double.parseDouble(arr[2]), Double.parseDouble(arr[3]));
					final double x = loc.getBlockX() + 0.125 + 0.5;
					final double y = loc.getBlockY() + 0.125;
					final double z = loc.getBlockZ() + 0.125 + 0.5;
					final double maxRadius = 2.0;
					final Double[] time = {0.0};
					
					new BukkitRunnable(){
						public void run(){
							double radius = radius(time[0], maxRadius);
							
							for(double i = 0; i < Math.PI*2; i+=Math.PI/20){
								float newX = (float)(xLoc(i, radius) + x);
								float newY = (float)(y+maxRadius-time[0]);
								float newZ = (float)(zLoc(i, radius) + z);
								
								PacketPlayOutWorldParticles packet= new PacketPlayOutWorldParticles(EnumParticle.VILLAGER_HAPPY, true, newX, newY, newZ, 0f, 0f, 0f, 0f, 1);
								for(Player player : Bukkit.getServer().getOnlinePlayers()){
									((CraftPlayer)player).getHandle().playerConnection.sendPacket(packet);
								}
							}
							
							time[0] += 0.2;
							if(radius >= maxRadius)
								this.cancel();
						}
					}.runTaskTimer(plugin, 0, 2);
					
				}
			}
		}.runTaskTimer(this, 0, 100);
	}
	
	private double xLoc(double time, double radius){
		return Math.sin(time) * radius;
	}
	
	private double zLoc(double time, double radius){
		return Math.cos(time) * radius;
	}
	
	private double radius(double time, double maxRadius){
		return Math.sqrt(Math.pow(maxRadius,2) - Math.pow(time-maxRadius, 2));
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
