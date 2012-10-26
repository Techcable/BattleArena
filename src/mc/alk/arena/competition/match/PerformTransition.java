package mc.alk.arena.competition.match;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Random;

import mc.alk.arena.BattleArena;
import mc.alk.arena.Defaults;
import mc.alk.arena.controllers.ArenaClassController;
import mc.alk.arena.controllers.MoneyController;
import mc.alk.arena.controllers.PlayerStoreController;
import mc.alk.arena.controllers.TeleportController;
import mc.alk.arena.controllers.WorldGuardInterface;
import mc.alk.arena.objects.ArenaClass;
import mc.alk.arena.objects.ArenaPlayer;
import mc.alk.arena.objects.MatchState;
import mc.alk.arena.objects.TransitionOptions;
import mc.alk.arena.objects.TransitionOptions.TransitionOption;
import mc.alk.arena.objects.teams.Team;
import mc.alk.arena.util.DisguiseInterface;
import mc.alk.arena.util.EffectUtil;
import mc.alk.arena.util.InventoryUtil;
import mc.alk.arena.util.MessageUtil;
import mc.alk.arena.util.TeamUtil;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.potion.PotionEffect;

public class PerformTransition {

	static Random rand = new Random();
	//	public static boolean debug = false;

	/**
	 * Perform a transition
	 * @param Match, which match to perform the transition on
	 * @param transition: which transition are we doing
	 * @param teams: which teams to affect
	 * @param onlyInArena: only perform the actions on people still in the arena
	 */
	public static void transition(Match am, MatchState transition, Collection<Team> teams, boolean onlyInArena){
		if (teams == null)
			return;
		for (Team t: teams)
			transition(am, transition,t, onlyInArena);
	}

	public static boolean transition(Match am, final MatchState transition, Team team, boolean onlyInMatch) {
		//		final Set<ArenaPlayer> validPlayers = team.getPlayers();
		final TransitionOptions mo = am.tops.getOptions(transition);
		//		System.out.println("doing effects for " + transition + "  " + team.getName() + "  " + mo );
		if (mo == null)
			return true;
		for (ArenaPlayer p : team.getPlayers()){
			transition(am, transition,p,team, onlyInMatch);
		}
		return true;
	}

	public static boolean transition(final Match am, final MatchState transition, final ArenaPlayer p,
			final Team team, final boolean onlyInMatch) {
		if (Defaults.DEBUG_TRANSITIONS) System.out.println("transition "+am.arena.getName()+"  " + transition + " p= " +p.getName() +
				" ops="+am.tops.getOptions(transition) +"  inArena="+am.insideArena(p));

		final TransitionOptions mo = am.tops.getOptions(transition);
		if (mo == null){
			return true;}
		/// Options that don't affect players first
		/// Clear the area
		if (mo.shouldClearRegion() && WorldGuardInterface.hasWorldGuard() && am.getArena().hasRegion())
			WorldGuardInterface.clearRegion(am.getArena().getRegionWorld(), am.getArena().getRegion());

		final boolean teleportIn = mo.shouldTeleportIn();
		final boolean teleportWaitRoom = mo.shouldTeleportWaitRoom();

		final boolean insideArena = am.insideArena(p);
		/// If the flag onlyInMatch is set, we should leave if the player isnt inside.  disregard if we are teleporting people in
		if (!(teleportIn || teleportWaitRoom) && onlyInMatch && !insideArena){
			return true;}
		final boolean teleportOut = mo.shouldTeleportOut();

		final boolean wipeOnFirstEnter = !insideArena && mo.hasOption(TransitionOption.CLEARINVENTORYONFIRSTENTER);
		final boolean wipeInventory = mo.clearInventory() || wipeOnFirstEnter;

		List<PotionEffect> effects = mo.getEffects()!=null ? new ArrayList<PotionEffect>(mo.getEffects()) : null;
		final Integer health = mo.getHealth();
		final Integer hunger = mo.getHunger();
		final String disguiseAllAs = mo.getDisguiseAllAs();
		final Boolean undisguise = mo.undisguise();
		final int teamIndex = am.indexOf(team);
		boolean playerReady = p.isOnline();
		final boolean dead = !p.isOnline() || p.isDead();
		if (teleportWaitRoom){ /// Teleport waiting room
			if ( (insideArena || am.checkReady(p, team, mo, true)) && !dead){
				/// EnterWaitRoom is supposed to happen before the teleport in event, but it depends on the result of a teleport
				/// Since we cant really tell the eventual result.. do our best guess
				am.enterWaitRoom(p);
				final Location l = jitter(am.getWaitRoomSpawn(teamIndex,false),team.getPlayerIndex(p));
				TeleportController.teleportPlayer(p.getPlayer(), l, true, true, false);
				PlayerStoreController.setGameMode(p.getPlayer(), GameMode.SURVIVAL);
			} else {
				playerReady = false;
			}
		}

		/// Teleport In
		if (teleportIn && transition != MatchState.ONSPAWN){ /// only tpin, respawn tps happen elsewhere
			if ((insideArena || am.checkReady(p, team, mo, true)) && !dead){
				/// enterArena is supposed to happen before the teleport in Event, but it depends on the result of a teleport
				/// Since we cant really tell the eventual result.. do our best guess
				am.enterArena(p);
				final Location l = jitter(am.getTeamSpawn(teamIndex,false),team.getPlayerIndex(p));
				TeleportController.teleportPlayer(p.getPlayer(), l, true, true, false);
				PlayerStoreController.setGameMode(p.getPlayer(), GameMode.SURVIVAL);
			} else {
				playerReady = false;
			}
		}

		/// Only do if player is online options
		if (playerReady && !dead){
			boolean storeAll = mo.hasOption(TransitionOption.STOREALL);
			if (storeAll || mo.hasOption(TransitionOption.STOREGAMEMODE)){ am.psc.storeGamemode(p);}
			if (storeAll || mo.hasOption(TransitionOption.STOREEXPERIENCE)){ am.psc.storeExperience(p);}
			if (storeAll || mo.hasOption(TransitionOption.STOREITEMS)) { am.psc.storeItems(p);}
			if (storeAll || mo.hasOption(TransitionOption.STOREHEROCLASS)){am.psc.storeArenaClass(p);}
			if (wipeInventory){ InventoryUtil.clearInventory(p.getPlayer()); }
			if (health != null) p.setHealth(health);
			if (hunger != null) p.setFoodLevel(hunger);
			try{if (mo.deEnchant() != null && mo.deEnchant()) EffectUtil.deEnchantAll(p.getPlayer());} catch (Exception e){}
			if (DisguiseInterface.enabled() && undisguise != null && undisguise) {DisguiseInterface.undisguise(p.getPlayer());}
			if (DisguiseInterface.enabled() && disguiseAllAs != null) {DisguiseInterface.disguisePlayer(p.getPlayer(), disguiseAllAs);}
			if (mo.getMoney() != null) {MoneyController.add(p.getName(), mo.getMoney());}
			if (mo.getExperience() != null) {p.getPlayer().giveExp(mo.getExperience());}
			if (mo.hasOption(TransitionOption.WOOLTEAMS) && am.getParams().getMinTeamSize() >1){
				if (insideArena){
					TeamUtil.setTeamHead(teamIndex, p);}
				am.woolTeams= true;
			} else if (mo.hasOption(TransitionOption.ALWAYSWOOLTEAMS)){
				if (insideArena){
					TeamUtil.setTeamHead(teamIndex, p);}
				am.woolTeams= true;
			}
			if (mo.hasOption(TransitionOption.REMOVEPERMS)){ removePerms(p, mo.getRemovePerms());}
			if (mo.hasOption(TransitionOption.ADDPERMS)){ addPerms(p, mo.getAddPerms(), 0);}
			if (mo.hasOption(TransitionOption.GIVECLASS)){
				final ArenaClass ac = getArenaClass(mo,teamIndex);
				if (ac != null){ /// Give class items and effects
					ArenaClassController.giveClass(p.getPlayer(), ac);}
			}
			if (mo.hasOption(TransitionOption.GIVEITEMS)){
				giveItems(transition, p, mo.getGiveItems(),teamIndex, am.woolTeams, insideArena);}

			try{if (effects != null)
				EffectUtil.enchantPlayer(p.getPlayer(), effects);} catch (Exception e){}

			String prizeMsg = mo.getPrizeMsg(null);
			if (prizeMsg != null)
				MessageUtil.sendMessage(p,"&eYou have been given \n"+prizeMsg);
			if (teleportIn){
				transition(am, MatchState.ONSPAWN, p, team, false);
			}
		}

		/// Teleport out, need to do this at the end so that all the onCancel/onComplete options are completed first
		if (teleportOut ){ /// Lets not teleport people out who are already out(like dead ppl)
			Location loc = null;
			if (mo.hasOption(TransitionOption.TELEPORTTO))
				loc = mo.getTeleportToLoc();
			else if (mo.hasOption(TransitionOption.TELEPORTONARENAEXIT))
				loc = mo.getTeleportToLoc();
			else
				loc = am.oldlocs.get(p.getName());
			if (insideArena || !onlyInMatch){
				TeleportController.teleportPlayer(p.getPlayer(), loc, false, false, wipeInventory);
				am.leaveArena(p);
			}
			/// If players are outside of the match, but need requirements, warn them
		} else if (transition == MatchState.ONPRESTART && !insideArena){
			/// Warn players about requirements
			if (!am.tops.playerReady(p)){
				MessageUtil.sendMessage(p, am.tops.getRequiredString(p,"&eRemember you still need the following"));}
		}
		/// Restore their exp and items.. Has to happen AFTER teleport
		boolean restoreAll = mo.hasOption(TransitionOption.RESTOREALL);
		if (restoreAll || mo.hasOption(TransitionOption.RESTOREGAMEMODE)){ am.psc.restoreGamemode(p);}
		if (restoreAll || mo.hasOption(TransitionOption.RESTOREEXPERIENCE)) { am.psc.restoreExperience(p);}
		if (restoreAll || mo.hasOption(TransitionOption.RESTOREITEMS)){
			if (am.woolTeams){
				TeamUtil.removeTeamHead(teamIndex, p.getPlayer());}
			if (Defaults.DEBUG_TRANSITIONS)System.out.println("   "+transition+" transition restoring items "+insideArena);
			am.psc.restoreItems(p);
		}
		if (restoreAll || mo.hasOption(TransitionOption.RESTOREHEROCLASS)){am.psc.restoreHeroClass(p);}

		return true;
	}

	private static void removePerms(ArenaPlayer p, List<String> perms) {
		if (perms == null || perms.isEmpty())
			return;
		/// TODO complete
	}

	private static void addPerms(ArenaPlayer p, List<String> perms, int ticks) {
		if (perms == null || perms.isEmpty())
			return;
		PermissionAttachment attachment = p.getPlayer().addAttachment(BattleArena.getSelf(),ticks);
		for (String perm: perms){
			attachment.setPermission(perm, true);}
	}

	private static void giveItems(final MatchState ms, final ArenaPlayer p, final List<ItemStack> items,
			final int teamIndex,final boolean woolTeams, final boolean insideArena) {
		if (woolTeams && insideArena){
			TeamUtil.setTeamHead(teamIndex, p);}
		if (Defaults.DEBUG_TRANSITIONS)System.out.println("   "+ms+" transition giving items to " + p.getName());
		InventoryUtil.addItemsToInventory(p.getPlayer(),items,woolTeams);
	}
	private static ArenaClass getArenaClass(TransitionOptions mo, final int teamIndex) {
		Map<Integer,ArenaClass> classes = mo.getClasses();
		if (classes.containsKey(teamIndex)){
			return classes.get(teamIndex);
		} else if (classes.containsKey(ArenaClass.DEFAULT)){
			return classes.get(ArenaClass.DEFAULT);
		}
		return null;
	}
	private static Location jitter(final Location teamSpawn, int index) {
		if (index == 0)
			return teamSpawn;
		index = index % 6;
		Location loc = teamSpawn.clone();

		switch(index){
		case 0: break;
		case 1: loc.setX(loc.getX()-1); break;
		case 2:	loc.setX(loc.getX()+1); break;
		case 3:	loc.setZ(loc.getZ()-1); break;
		case 4:	loc.setZ(loc.getZ()+1); break;
		case 5:
			loc.setX(loc.getX() + rand.nextDouble()-0.5);
			loc.setZ(loc.getZ() + rand.nextDouble()-0.5);
		}
		return loc;
	}

}