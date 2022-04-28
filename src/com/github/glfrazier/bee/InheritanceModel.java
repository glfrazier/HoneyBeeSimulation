package com.github.glfrazier.bee;

import java.io.Serializable;
import java.util.Properties;
import java.util.Random;

/**
 * The child gene health is selected from a normal distribution around a point
 * determined by the parents' genes. Options:
 * <ul>
 * <li>The Mode:
 * <ul>
 * <li>ONE_PARENT (one of the parents' genes is selected as the mean)</li>
 * <li>AVG_OF_PARENTS (the mean is the average of the parents' genes)</li>
 * </ul>
 * </li>
 * <li>The variance (sigma-squared) of the distribution. Almost all children
 * will be within 5*var of the mean. So, a variance of 0.001 will have most
 * children within 0.005 of the mean.</li>
 * </ul>
 * 
 * @author Greg Frazier
 *
 */
public class InheritanceModel implements Serializable {

	private static final long serialVersionUID = 1L;

	static enum Mode {
		ONE_PARENT, AVERAGE
	};

	Mode mode;
	double stddev;
	private double maxStrength;

	public InheritanceModel(Properties props) {
		mode = Mode.valueOf(BeeHealthSimulation.getProperty(props, "inheritance_mode"));
		stddev = BeeHealthSimulation.getDoubleProperty(props, "stddev_g");
		maxStrength = BeeHealthSimulation.getProbabilityProperty(props, "max_g");
	}

	/**
	 * Get a child queen from the specified queen and drone. The hive's inheritance
	 * model determines how the base gene strength is calculated. We then use
	 * {@link Random#nextGaussian()} and the specified standard deviation to
	 * determine how the child's gene drifts relative to the parent(s).
	 * 
	 * @param queen the queen's gene "strength".
	 * @param drone the drone's gene "strength".
	 * @param rand  the random number generator.
	 * @return the gene strength of the new queen.
	 */
	public double getChildQueen(double queen, double drone, Random rand) {
		double mean = (mode == Mode.ONE_PARENT ? (rand.nextBoolean() ? queen : drone) : (queen + drone) / 2);
		double result = mean + stddev * rand.nextGaussian();
		if (result > maxStrength) {
			result = maxStrength;
		}
		if (result < 0) {
			result = 0;
		}
		return result;
	}

	/**
	 * Randomly select one of the drones, and then get an offspring between that
	 * drone and the queen.
	 */
	public double getChildQueen(double queen, double[] drones, Random rand, boolean dead) {
		if (dead) {
			System.err.println("An attempt was made to obtain a child queen from a dead hive.");
			System.exit(-1);
		}
		try {
			int index = rand.nextInt(drones.length);
			return getChildQueen(queen, drones[index], rand);
		} catch (Throwable t) {
			t.printStackTrace();
			System.err.println("queen=" + queen + ", drones=" + drones + ", rand=" + rand + ", dead=" + dead);
			System.exit(-1);
		}
		return 0;
	}

	/**
	 * Obtain the gene strength of the hive, assuming that each drone is equally
	 * likely to father a worker.
	 * 
	 * @param queenGene
	 * @param droneGenes
	 * @return
	 */
	public double getHiveStrength(double queenGene, double[] droneGenes) {
		double total = 0;
		for (int i = 0; i < droneGenes.length; i++) {
			total += queenGene + droneGenes[i];
		}
		return total / (2 * droneGenes.length);
	}
}
