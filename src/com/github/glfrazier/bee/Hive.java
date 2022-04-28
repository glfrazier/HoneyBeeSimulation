package com.github.glfrazier.bee;

import static com.github.glfrazier.bee.BeeHealthSimulation.LOGGER;
import java.io.Serializable;
import java.util.Random;

public class Hive implements Serializable {

	private static final long serialVersionUID = 1L;

	Site site;
	double queenGene;
	double[] droneGenes;
	InheritanceModel iModel;
	Statistics stats;
	Random random;

	/** A new hive is alive. */
	boolean dead = false;

	/** A hive cannot breed its first year. */
	boolean canBreed = false;
	int age;

	int maxHiveAge;

	int minimumRequeenAge;

	double requeenProbability;

	double probSwarm;

	private BeeHealthSimulation sim;

	public Hive(double queen, double[] drones, Site site, long seed) {
		this.queenGene = queen;
		this.droneGenes = drones;
		if (droneGenes == null) {
			throw new NullPointerException("Constructed hive with null drones!");
		}
		this.site = site;
		this.random = new Random(seed);
		finishConstruction(site.getGrid().getSim(), site.domestic);
	}

	private Hive(double q, double[] d, long seed, boolean domestic, BeeHealthSimulation sim) {
		this.queenGene = q;
		this.droneGenes = d;
		if (droneGenes == null) {
			throw new NullPointerException("Constructed hive with null drones!");
		}
		this.site = null;
		this.random = new Random(seed);
		finishConstruction(sim, domestic);
	}

	private void finishConstruction(BeeHealthSimulation sim, boolean domestic) {
		this.sim = sim;
		this.iModel = sim.getSimulationInheritanceModel();
		this.stats = sim.getSimulationStatistics();
		this.maxHiveAge = sim.getIntProperty("max_hive_age");
		this.minimumRequeenAge = sim.getIntProperty("min_requeen_age");
		this.requeenProbability = sim.getProbabilityProperty("requeen_probability");
		this.probSwarm = sim.getProbabilityProperty(domestic ? "domestic_prob_swarm" : "feral_prob_swarm");
		stats.newHiveCreated(domestic);
	}

	private boolean survivedWinter() {
		double prob = iModel.getHiveStrength(queenGene, droneGenes);
		if (site.domestic || (!site.domestic && sim.feralUsesDomesticSurvivalModel)) {
			// domestic hives are fed, so probability of survival is boosted.
			// This moves the probability halfway towards 1.0 from its base value.
			prob = prob + (1 - prob) * sim.feedingFactor;
		}
		return random.nextDouble() < prob;
	}

	/**
	 * Over-winter this hive. If the hive is dead, do nothing. If the hive is alive,
	 * it will die if it is too old ({@link #age} > {@link #maxHiveAge}) or it may
	 * die stochastically ({@link #survivedWinter()}. If this hive survives the
	 * winter, its {@link #age} is incremented and {@link #canBreed} is set to
	 * <code>true</code>.
	 * 
	 * @param rand
	 */
	public void overWinter() {
		if (dead) {
			return;
		}
		if (age >= maxHiveAge) {
			if (thisIsTheLastQueenBreeder()) {
				// the last queen breeder in the simulation is not allowed to die!
				LOGGER.info(this + " is the last queen breeder alive, and so cannot die of old age.");
				return;
			}
			dead = true;
			stats.diedOfOldAge(site.domestic);
			return;
		}
		if (!survivedWinter()) {
			if (thisIsTheLastQueenBreeder()) {
				// the last queen breeder in the simulation is not allowed to die!
				LOGGER.info(this + " is the last queen breeder alive, and so cannot fail to overwinter.");
				return;
			}
			dead = true;
			stats.failedToSurviveWinter(site.domestic);
			return;
		}
		canBreed = true;
		age++;
	}

	private boolean thisIsTheLastQueenBreeder() {
		if (!site.isQueenBreeder()) {
			return false;
		}
		// Only one queen breeder at a time can check to see if it is the last queen
		// breeder
		synchronized (site.getGrid().queenBreeders) {
			for (Site s : site.getGrid().queenBreeders) {
				for (Hive h : s.syncCopyHives()) {
					if ((h != this) && !h.dead) {
						return false;
					}
				}
			}
			return true;
		}
	}

	public double getBabyQueen() {
		return iModel.getChildQueen(queenGene, droneGenes, random, dead);
	}

	public double getBabyDrone() {
		return queenGene;
	}

	/**
	 * Create a new hive for the specified location that has the same genes as this
	 * hive; it will be returned. Replace this hive's queen with a baby queen, and
	 * "fly" the new queen to obtain a new set of drones.
	 * 
	 * This method sets canBreed to false. It is set back to true when the hive
	 * over-winters.
	 * 
	 * @return <code>null</code> if this hive is dead or !canBreed. Otherwise, it
	 *         returns a Hive with the current queen and drones.
	 */
	private Hive swarm() {
		if (dead) {
			throw new IllegalStateException("You asked a dead hive to swarm!");
		}
		if (!canBreed) {
			LOGGER.warning("You asked an unbreedable hive to swarm.");
			return null;
		}
		// Create a new hive using this mated queen
		Hive swarm = new Hive(queenGene, droneGenes, random.nextLong(), site.domestic, site.getGrid().getSim());
		// Replace the queen in this hive with one of her daughters
		queenGene = getBabyQueen();
		age = 0;

		// The virgin queen mates!
		droneGenes = site.matingFlight(this);
		if (droneGenes == null) {
			// The mating flight failed because there are zero hives in a radius of
			// 2*matingFlightDistance of this hive. Our simplistic approach to handling this
			// event is to say that the hive is dead.
			dead = true;
			stats.matingFlightFailed(site.domestic);
		}
		// Technically, I believe a hive *can* swarm multiple times in a single year.
		// But we are not doing that in this simulation. A decision to reexamine.
		canBreed = false;

		// Return the swarming hive
		return swarm;
	}

	/** Obtain this hive's site. */
	public Site getSite() {
		return site;
	}

	public double getHiveStrength() {
		return iModel.getHiveStrength(queenGene, droneGenes);
	}

	public boolean requeen() {
		if (!site.domestic) {
			LOGGER.severe("You asked whether to requeen a feral hive!");
			System.exit(-1);
		}
		// No! Allow for the possibility that domestic bee keepers do not know how old
		// their queen is. This all gets subsumed by requeenProbability.
		//
		// if (age >= maxHiveAge) {
		// return true;
		// }
		if (age >= minimumRequeenAge) {
			boolean result = random.nextDouble() < requeenProbability;
			if (result) {
				stats.hiveIsRequeened();
			}
			return result;
		}
		return false;
	}

	/**
	 * Both domestic and feral hives may swarm.
	 */
	public void swarmIfAppropriate() {
		// A dead hive cannot swarm
		if (dead) {
			return;
		}
		// A hive cannot swarm its first year.
		if (age < 1) {
			return;
		}
		// probSwarm was set appropriately per the domestic or feral nature of the hive.
		if (random.nextDouble() < probSwarm) {
			stats.swarming(site.domestic);
			// The hive will swarm regardless of whether it can find a site to live in.
			Hive swarmingBees = swarm();
			// Now let's see if there is a place for this swarm to live.
			Hive destination = site.findNearbyFeralDeadHive();
			if (destination != null) {
				stats.swarmFoundSite(site.domestic);
				destination.receiveSwarm(swarmingBees);
			} else {
				swarmingBees.dead = true;
				stats.swarmCouldNotFindSite(site.domestic);
			}
		}
	}

	private void receiveSwarm(Hive swarmingBees) {
		this.queenGene = swarmingBees.queenGene;
		this.droneGenes = swarmingBees.droneGenes;
		this.age = swarmingBees.age;
		this.dead = false;
		this.canBreed = false;
	}
	
	public String toString() {
		String prefix = (dead ? "Dead" : "Living") + (site == null ? " unaffiliated" : (site.domestic ? " Domestic" : " Feral"));
		return prefix + " hive" + (site == null ? "" : " @ " + site.toString());
	}

}
