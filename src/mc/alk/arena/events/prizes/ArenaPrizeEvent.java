package mc.alk.arena.events.prizes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import mc.alk.arena.competition.Competition;
import mc.alk.arena.events.BAEvent;
import mc.alk.arena.objects.teams.Team;

import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

public class ArenaPrizeEvent extends BAEvent {
	final Competition competition;
	final Collection<Team> teams;

	Integer exp;
	Double money;
	List<ItemStack> items;
	List<PotionEffect> effects;
	List<Reward> rewards;

	public ArenaPrizeEvent(Competition competition, Collection<Team> teams){
		this.competition = competition;
		this.teams = teams;
	}
	public Integer getExp() {
		return exp;
	}
	public void setExp(Integer exp) {
		this.exp = exp;
	}
	public Double getMoney() {
		return money;
	}
	public void setMoney(Double money) {
		this.money = money;
	}
	public List<ItemStack> getItems() {
		return items;
	}
	public void setItems(List<ItemStack> items) {
		this.items = items;
	}
	public List<PotionEffect> getEffects() {
		return effects;
	}
	public void setEffects(List<PotionEffect> effects) {
		this.effects = effects;
	}
	public List<Reward> getRewards() {
		return rewards;
	}
	public void setRewards(List<Reward> rewards) {
		this.rewards = rewards;
	}
	public void addReward(Reward reward){
		if (this.rewards == null){
			this.rewards = new ArrayList<Reward>();}
		this.rewards.add(reward);
	}
	public Collection<Team> getTeams() {
		return teams;
	}
}