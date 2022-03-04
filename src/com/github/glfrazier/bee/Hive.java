package com.github.glfrazier.bee;

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

	public Hive(double queen, double[] drones, Site site, long seed) {
		this.queenGene = queen;
		this.droneGenes = drones;
		this.site = site;
		this.random = new Random(seed);
		this.iModel = site.getGrid().getSim().getSimulationInheritanceModel();
		this.stats = site.getGrid().getSim().getSimulationStatistics();
	}

	private boolean survivedWinter() {
		double prob = iModel.getHiveStrength(queenGene, droneGenes);
		if (site.domestic) {
			// domestic hives are fed, so probability of survival is boosted.
			// This moves the probability halfway towards 1.0 from its base value.
			prob = prob + (1 - prob) / 2;
		}
		return random.nextDouble() < prob;
	}

	/**
	 * Over-winter this hive. If it survives the winter, it will be able to breed
	 * the next year. If it doesn't... it's dead.
	 * 
	 * @param rand
	 */
	public void overWinter() {
		if (!survivedWinter()) {
			dead = true;
		} else {
			canBreed = true;
			age++;
		}
	}

	public double getBabyQueen() {
		return iModel.getChildQueen(queenGene, droneGenes, random);
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
	public synchronized Hive swarm(Site site) {
		if (dead) {
			// A dead hive cannot swarm
			return null;
		}
		if (!canBreed) {
			// A hive in its first summer cannot swarm
			return null;
		}
		// Create a new hive using this mated queen
		Hive swarm = new Hive(queenGene, droneGenes, site, random.nextLong());
		// Replace the queen in this hive with one of her daughters
		queenGene = getBabyQueen();

		// The virgin queen mates!
		droneGenes = site.matingFlight(this);
		if (droneGenes == null) {
			// The mating flight failed because there are zero hives in a radius of
			// 2*matingFlightDistance of this hive. Our simplistic approach to handling this
			// event is to say that the hive is dead.
			dead = true;
			stats.matingFlightFailed();
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

}
