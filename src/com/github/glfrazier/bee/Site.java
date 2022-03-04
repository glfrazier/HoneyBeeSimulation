package com.github.glfrazier.bee;

import static com.github.glfrazier.bee.BeeHealthSimulation.LOGGER;
import static java.util.logging.Level.FINEST;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;

public class Site implements Iterable<Hive>, Serializable {

	private static final long serialVersionUID = 1L;

	/** Reference to the grid */
	private Grid grid;

	/** Where this site is in the grid (x) */
	int x;
	/** Where this site is in the grid (y) */
	int y;
	/**
	 * The number of hives at this site. If wild, it is 1. If domestic, randomly
	 * selected from an exponential distribution.
	 */
	int numberOfHives;
	/** The Hive(s) at this site. */
	List<Hive> hives;
	/** Track whether this site has been initialized yet. */
	public boolean initialized = false;
	/** True if this is a domestic site. (Kept bees.) */
	boolean domestic;

	boolean queenBreeder;

	/** The sites random number generator */
	Random random;

	private int matingFlightDistance;

	private int droneParticipationDistance;

	private int minDrones;

	private int maxDrones;

	private int swarmDistance;

	private int maxDomesticAge;

	public Site(int x, int y, long seed, Grid grid) {
		this.x = x;
		this.y = y;
		this.grid = grid;
		this.hives = new ArrayList<>();
		this.random = new Random();
		this.random.setSeed(seed);
	}

	public void setQueenBreeder() {
		queenBreeder = true;
	}

	@Override
	public Iterator<Hive> iterator() {
		return hives.iterator();
	}

	private static final boolean IF_DEAD = true;
	private static final boolean IF_TOO_OLD = true;
	private static final boolean NOT_IF_DEAD = false;
	private static final boolean NOT_IF_TOO_OLD = false;

	public void replaceDeadHives() {
		replaceHives(IF_DEAD, NOT_IF_TOO_OLD);
	}

	private void replaceHives(boolean ifDead, boolean ifTooOld) {
		if (LOGGER.getLevel() == FINEST) {
			LOGGER.finest(this + " entered replaceHives, ifDead=" + ifDead + ", ifTooOld=" + ifTooOld
					+ ", hives.size()=" + hives.size());
		}
		// A map to hold the replacement hives
		Map<Hive,Hive> replacementHives = new HashMap<>();
		// Look at each hive at this site
		for (Iterator<Hive> iter = hives.iterator(); iter.hasNext();) {
			Hive hive = iter.next();
			// If the hive is dead, replace it
			if ((ifDead && hive.dead) || (ifTooOld && (hive.age > maxDomesticAge))) {
				if (domestic) {
					// If this is a domestic site, the keeper buys a new hive
					replacementHives.put(hive, grid.purchaseMatedQueen(this, random));
				}
				// If this is a wild site, the dead hive is replaced by a neighboring swarm, if
				// one exists
				else {
					List<Hive> nbrHives = grid.getNeighborhoodHives(this, swarmDistance);
					nbrHives.remove(hive);
					for (Hive nbrHive : nbrHives) {
						Hive h = nbrHive.swarm(this);
						if (h != null) {
							// If h is not null, then nbrHive successfully swarmed and is replacing this
							// hive.
							replacementHives.put(hive, h);
							break;
						}
					}
				}
			}
		}
		for(Hive h : replacementHives.keySet()) {
			hives.remove(h);
			hives.add(replacementHives.get(h));
		}
		if (LOGGER.getLevel() == FINEST) {
			LOGGER.finest(this + " leaving replaceHives, ifDead=" + ifDead + ", ifTooOld=" + ifTooOld
					+ ", hives.size()=" + hives.size());
		}
	}

	/**
	 * Initialize this site, getting parameters from the properties and using random
	 * when stochastic decisions must be made.
	 * 
	 * @param props
	 */
	public synchronized void initialize(Properties props) {
		LOGGER.finest("Initializing " + this);
		if (initialized) {
			LOGGER.warning("Attempted to initialize " + this + " multiple times.");
			return;
		}

		int numberOfHives = 1;

		// Domestic vs. Feral
		double pDomestic = BeeHealthSimulation.getProbabilityProperty(props, "prob_domestic");
		domestic = random.nextDouble() < pDomestic;

		// If domestic, the number of hives is randomly selected from a range
		if (domestic) {
			String numberOfHivesDistro = BeeHealthSimulation.getProperty(props, "number_of_hives_distribution");
			if (numberOfHivesDistro.equals("three-way-norm")) {
				double m0 = BeeHealthSimulation.getDoubleProperty(props, "number_of_hives_m0");
				double m1 = BeeHealthSimulation.getDoubleProperty(props, "number_of_hives_m1");
				double m2 = BeeHealthSimulation.getDoubleProperty(props, "number_of_hives_m2");
				if (m0 + m1 + m2 != 1.0) {
					System.err
							.println("In the 'three-way-norm' number-of-hives distribution, m1+m2+m3 must equal 1.0.");
					System.exit(-1);
				}
				numberOfHives = threeWayNorm(m0, m1, m2);
			} else if (numberOfHivesDistro.equals("linear")) {
				int min = BeeHealthSimulation.getIntProperty(props, "number_of_hives_min");
				int max = BeeHealthSimulation.getIntProperty(props, "number_of_hives_max");
				numberOfHives = random.nextInt(1 + max - min) + min;
			} else {
				System.err.println("The 'number_of_hives_distro' <" + numberOfHivesDistro + "> is not supported.");
				System.exit(-1);
			}
		}

		// Intialize the hive(s)
		finishInitialize(props, numberOfHives);

	}

	void finishInitialize(Properties props, int numberOfHives) {
		if (numberOfHives == 0) {
			LOGGER.warning(this + " is bein initialized with zero hives.");
		}
		LOGGER.finest("Finish Initializing " + this);

		maxDomesticAge = BeeHealthSimulation.getIntProperty(props, "requeen_age");
		minDrones = BeeHealthSimulation.getIntProperty(props, "min_drones");
		maxDrones = BeeHealthSimulation.getIntProperty(props, "max_drones");
		matingFlightDistance = BeeHealthSimulation.getIntProperty(props, "mating_flight_distance");
		droneParticipationDistance = BeeHealthSimulation.getIntProperty(props, "drone_participation_distance");
		swarmDistance = BeeHealthSimulation.getIntProperty(props, "swarm_distance");

		// Initialize each hive at this site. Note that ALL HIVES BEGIN WITH EQUALLY
		// ROBUST GENES. Which is not the same as identical genes.
		for (int i = 0; i < numberOfHives; i++) {
			double q = BeeHealthSimulation.getProbabilityProperty(props, "initial_gene");
			int minDrones = BeeHealthSimulation.getIntProperty(props, "min_drones");
			int maxDrones = BeeHealthSimulation.getIntProperty(props, "max_drones");
			int droneCount = random.nextInt(1 + maxDrones - minDrones) + minDrones;
			double[] d = new double[droneCount];
			for (int j = 0; j < d.length; j++) {
				d[j] = q;
			}
			Hive h = new Hive(q, d, this, random.nextLong());
			hives.add(h);
		}
		initialized = true;
	}

	private int threeWayNorm(double m0, double m1, double m2) {
		int mult = 0;
		double r = random.nextDouble();
		if (r < m0) {
			mult = 2;
		} else if (r < m0 + m1) {
			mult = 10;
		} else {
			mult = 25;
		}
		double d = random.nextGaussian();
		d = Math.abs(d);
		int x = (int) (mult * d) + 1;
		return x;
	}

	public synchronized boolean isInitialized() {
		return initialized;
	}

	/**
	 * Return an array of drone genes that the queen acquired during her mating
	 * flight.
	 * 
	 * @param hive the hive from which the queen is flying; no drones from this hive
	 *             will be included.
	 * @return <code>null</code> if there are no drones in the vicinity, else an
	 *         array of drone genes
	 */
	public double[] matingFlight(Hive hive) {
		Direction[] dirs = Direction.getRandomDirectionArray(hive.random);
		List<Hive> droneProvidingHives = null;
		for (Direction d : dirs) {
			Site s = grid.getSiteInDirection(this, d, matingFlightDistance);
			droneProvidingHives = grid.getNeighborhoodHives(s, droneParticipationDistance);
			droneProvidingHives.remove(hive);
			if (!droneProvidingHives.isEmpty()) {
				break;
			}
		}
		if (droneProvidingHives.isEmpty()) {
			return null;
		}
		List<Double> flyingDrones = new ArrayList<>();
		for (Hive h : droneProvidingHives) {
			flyingDrones.add(h.getBabyDrone()); // Note that baby drones have genetic strength == queen
		}
		// Use the hive's randomness to decide how many drones the mating flight gets
		int droneCount = hive.random.nextInt(1 + maxDrones - minDrones) + minDrones;
		double[] drones = new double[droneCount];
		for (int i = 0; i < drones.length; i++) {
			// We do not remove the drone from the list after it is selected. Each drone
			// in the flyingDrones list is actually many (many!) drones; the queen may
			// mate with multiple drones from the same hive.
			drones[i] = flyingDrones.get(hive.random.nextInt(flyingDrones.size()));
		}
		return drones;
	}

	public Grid getGrid() {
		return grid;
	}

	/**
	 * Over-winter each hive at the site.
	 * 
	 * @see Hive#overWinter()
	 */
	public void overWinter() {
		for (Hive hive : hives) {
			// After a winter, a hive will either be dead or able to swarm the next summer.
			hive.overWinter();
		}
	}

	@Override
	public String toString() {
		String soubrequet = !initialized ? "(?)" : domestic ? "(D)" : "(F)";
		return "Site[" + x + "," + y + "]" + soubrequet;
	}

	public void requeenIfOld() {
		replaceHives(NOT_IF_DEAD, IF_TOO_OLD);
	}

	public static String getStateCSVHeader() {
		return "x,y,domestic,queen breeder,number of hives, number of live hives, number of dead hives, avg live hive strength, max live hive strength, min live hive strength";
	}

	public String getStateCSV() {
		int dead = 0;
		double totalStrength = 0;
		double minStrength = Double.MAX_VALUE;
		double maxStrength = 0;
		for (Hive h : hives) {
			if (h.dead) {
				dead++;
			} else {
				double s = h.iModel.getHiveStrength(h.queenGene, h.droneGenes);
				totalStrength += s;
				if (s < minStrength) {
					minStrength = s;
				}
				if (s > maxStrength) {
					maxStrength = s;
				}
			}
		}
		double avgStrength = 0;
		if (totalStrength != 0) {
			avgStrength = totalStrength / (hives.size() - dead);
		}
		StringBuffer result = new StringBuffer();
		result.append(x).append(',').append(y).append(',').append(domestic).append(',').append(queenBreeder).append(',')
				.append(hives.size()).append(',').append(hives.size() - dead).append(',').append(dead).append(',')
				.append(avgStrength).append(',').append(maxStrength).append(',').append(minStrength);
		return result.toString();
	}

}
