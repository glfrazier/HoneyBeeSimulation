package com.github.glfrazier.bee;

import static com.github.glfrazier.bee.BeeHealthSimulation.LOGGER;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Level;

public class Statistics implements Serializable {

	private static final long serialVersionUID = 1L;

	public static final String DEFAULT_BASE_DIR = ".";
	public static final String DEFAULT_CHECKPOINT_DIR = "checkpoints";
	public static final String DEFAULT_RESULTS_DIR = "results";

	private BeeHealthSimulation sim;
	private List<PerYearStatistics> statistics;
	private PerYearStatistics thisYearStats;
	private int thisYear;

	private File baseDir;
	private File checkpointDir;
	private File resultsDir;

	public Statistics(Properties props, BeeHealthSimulation sim) {
		this.sim = sim;
		statistics = new ArrayList<>();
		thisYearStats = new PerYearStatistics();
		thisYear = 0;
		initialize(props);
	}

	private void initialize(Properties props) {
		String baseDirStr = props.getProperty("base_dir", DEFAULT_BASE_DIR);
		baseDir = new File(baseDirStr);
		String checkpointDirStr = props.getProperty("checkpoint_dir", DEFAULT_CHECKPOINT_DIR);
		checkpointDir = new File(baseDir, checkpointDirStr);
		String resultsDirStr = props.getProperty("results_dir", DEFAULT_RESULTS_DIR);
		resultsDir = new File(baseDir, resultsDirStr);
		resultsDir.mkdirs();
		File subdir = null;
		for (int i = 0; true; i++) {
			String subdirName = String.format("%03d", i);
			subdir = new File(resultsDir, subdirName);
			if (!subdir.exists()) {
				subdir.mkdir();
				break;
			}
		}
		resultsDir = subdir;
	}

	/**
	 * Write the statistics to disk.
	 * 
	 * @throws IOException if statistics cannot be saved
	 */
	public void endSimulation() throws IOException {
		Properties props = sim.getProperties();
		do {
			File f = new File(resultsDir, "sites.csv");
			PrintStream out = new PrintStream(new FileOutputStream(f));
			out.println(Site.getStateCSVHeader());
			for (Site s : sim.getGrid()) {
				out.println(s.getStateCSV());
			}
			out.close();
		} while (false);
		for (Object key : props.keySet()) {
			String name = key.toString();
			if (name.startsWith("name") || name.startsWith("desc")) {
				File f = new File(resultsDir, name + ".txt");
				PrintStream out = new PrintStream(new FileOutputStream(f));
				out.println(props.getProperty(name));
				out.close();
			}
		}
		try {
			Field[] fields = PerYearStatistics.class.getDeclaredFields();
			for (Field field : fields) {
				String name = field.getName();
				if (!name.contains("omestic") && !name.contains("eral")) {
					continue;
				}
				if (name.startsWith("max") || name.startsWith("min")) {
					continue;
				}
				boolean isTotaledValue = false;
				if (name.startsWith("total")) {
					isTotaledValue = true;
				}
				boolean domestic = false;
				if (name.contains("omestic")) {
					domestic = true;
				}
				double[] values = new double[statistics.size()];
				double[] mins = null;
				double[] maxes = null;
				if (name.startsWith("total")) {
					mins = new double[statistics.size()];
					maxes = new double[statistics.size()];
					isTotaledValue = true;
				}
				int index = 0;
				for (PerYearStatistics s : statistics) {
					values[index] = field.getDouble(s);
					if (isTotaledValue) {
						if (domestic) {
							values[index] /= s.domesticLiveHives;
						} else {
							values[index] /= s.feralLiveHives;
						}
					}
					if (isTotaledValue) {
						Field minField = PerYearStatistics.class.getField(name.replace("total", "min"));
						mins[index] = minField.getDouble(s);
						Field maxField = PerYearStatistics.class.getField(name.replace("total", "max"));
						maxes[index] = maxField.getDouble(s);
					}
					index++;
				}
				if (isTotaledValue) {
					String avgName = name.replace("total", "avg");
					saveData(avgName, values, mins, maxes);
				} else {
					saveData(name, values);
				}
			}
		} catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.exit(-1);
		}
	}

	private void saveData(String name, double[] values, double[] mins, double[] maxes) throws IOException {
		File f = new File(resultsDir, name + ".csv");
		PrintStream out = new PrintStream(new FileOutputStream(f));
		for (int i = 0; i < values.length; i++) {
			out.println(String.format("%d \t%.4f  \t%.4f \t%.4f", i, values[i], mins[i], maxes[i]));
		}
		out.close();
	}

	private void saveData(String name, double[] values) throws IOException {
		File f = new File(resultsDir, name + ".csv");
		PrintStream out = new PrintStream(new FileOutputStream(f));
		for (int i = 0; i < values.length; i++) {
			out.println(String.format("%d \t%.4f", i, values[i]));
		}
		out.close();
	}

	public void endOfWinter() {
		// Do whatever aggregation needs to be done at the end of a winter.
	}

	public void endOfSummer() {
		// Do whatever aggregation needs to be done at the end of a summer.
		LOGGER.fine("Ending summer " + thisYear);
		LOGGER.fine("\tdomesticLiveHives=" + thisYearStats.domesticLiveHives);
		LOGGER.fine("\tdomesticDeadHives=" + thisYearStats.domesticDeadHives);
		if (thisYearStats.domesticSwarms != thisYearStats.domesticSwarmsThatCouldNotFindSite
				+ thisYearStats.domesticSwarmsThatFoundSite) {
			LOGGER.severe("In year " + thisYear + ", domesticSwarms(" + thisYearStats.domesticSwarms
					+ ") != domesticSwarmsThatCouldNotFindSite (" + thisYearStats.domesticSwarmsThatCouldNotFindSite
					+ ") + domesticSwarmsThatFoundSite (" + thisYearStats.domesticSwarmsThatFoundSite + ")");
			System.exit(-1);
		}
		if (thisYearStats.feralSwarms != thisYearStats.feralSwarmsThatCouldNotFindSite
				+ thisYearStats.feralSwarmsThatFoundSite) {
			LOGGER.severe("In year " + thisYear + ", feralSwarms(" + thisYearStats.feralSwarms
					+ ") != feralSwarmsThatCouldNotFindSite (" + thisYearStats.feralSwarmsThatCouldNotFindSite
					+ ") + feralSwarmsThatFoundSite (" + thisYearStats.feralSwarmsThatFoundSite + ")");
			System.exit(-1);
		}
		statistics.add(thisYearStats);
		thisYearStats = new PerYearStatistics();
		thisYear++;
		if (thisYear != statistics.size()) {
			System.err.println("ERROR: thisYear does not agree with statistics.");
			System.exit(-1);
		}
	}

	private class PerYearStatistics {
		public int domesticHivesCreated;
		public int domesticDeadHives;
		public int domesticLiveHives;
		public int domesticKilledByWinter;
		public int domesticDiedOfOldAge;
		public int domesticMatingFlightFailures;
		public int domesticSwarms;
		public int domesticSwarmsThatCouldNotFindSite;
		public int domesticSwarmsThatFoundSite;
		public int domesticHiveRequeened;
		public double totalDomesticQueenStrength;
		public int feralHivesCreated;
		public int feralDeadHives;
		public int feralLiveHives;
		public int feralKilledByWinter;
		public int feralDiedOfOldAge;
		public int feralMatingFlightFailures;
		public int feralSwarms;
		public int feralSwarmsThatCouldNotFindSite;
		public int feralSwarmsThatFoundSite;
		public double totalFeralQueenStrength;
		public int totalDomesticDrones;
		public int totalFeralDrones;
		public double totalDomesticHiveStrength;
		public double totalFeralHiveStrength;
		public int domesticEowDeadHives;
		public int domesticEowLiveHives;
		public int feralEowDeadHives;
		public int feralEowLiveHives;
		public double minDomesticQueenStrength;
		public double maxDomesticQueenStrength;
		public double minDomesticHiveStrength;
		public double maxDomesticHiveStrength;
		public double minFeralQueenStrength;
		public double maxFeralQueenStrength;
		public int minDomesticDrones;
		public int maxDomesticDrones;
		public int minFeralDrones;
		public int maxFeralDrones;
		public double minFeralHiveStrength;
		public double maxFeralHiveStrength;

		public PerYearStatistics() {
			for (Field field : PerYearStatistics.class.getDeclaredFields()) {
				try {
					if (field.getName().startsWith("min")) {
						if (field.getType().equals(double.class)) {
							field.set(this, Double.MAX_VALUE);
						}
						if (field.getType().equals(int.class)) {
							field.set(this, Integer.MAX_VALUE);
						}
					}
				} catch (Throwable t) {
					System.err.println(
							"Got exception " + t + " while setting the field " + field + " to a large minimum value.");
					System.exit(-1);
				}
			}
		}
	}

	public void hivesAtEndOfSummer(Site site) {
		List<Hive> hives = site.syncCopyHives();
		if (LOGGER.getLevel() == Level.FINEST) {
			LOGGER.finest("Logging site " + site + " at end of summer, year " + thisYear);
			for (Hive h : hives) {
				LOGGER.finest("\tHive is " + (h.dead ? "dead" : "alive"));
			}
		}
		for (Hive h : hives) {
			if (site.domestic) {
				if (h.dead) {
					thisYearStats.domesticDeadHives++;
				} else {
//					if (LOGGER.getLevel() == Level.FINEST) {
//						LOGGER.finest(h + " is alive, queen strength = " + h.queenGene);
//					}
					thisYearStats.domesticLiveHives++;

					thisYearStats.totalDomesticQueenStrength += h.queenGene;
					if (h.queenGene < thisYearStats.minDomesticQueenStrength)
						thisYearStats.minDomesticQueenStrength = h.queenGene;
					if (h.queenGene > thisYearStats.maxDomesticQueenStrength)
						thisYearStats.maxDomesticQueenStrength = h.queenGene;

					thisYearStats.totalDomesticDrones += h.droneGenes.length;
					if (thisYearStats.minDomesticDrones > h.droneGenes.length)
						thisYearStats.minDomesticDrones = h.droneGenes.length;
					if (thisYearStats.maxDomesticDrones < h.droneGenes.length)
						thisYearStats.maxDomesticDrones = h.droneGenes.length;

					thisYearStats.totalDomesticHiveStrength += h.getHiveStrength();
					if (thisYearStats.minDomesticHiveStrength > h.getHiveStrength())
						thisYearStats.minDomesticHiveStrength = h.getHiveStrength();
					if (thisYearStats.maxDomesticHiveStrength < h.getHiveStrength())
						thisYearStats.maxDomesticHiveStrength = h.getHiveStrength();
				}
			} else {
				if (h.dead) {
					thisYearStats.feralDeadHives++;
				} else {
					thisYearStats.feralLiveHives++;

					thisYearStats.totalFeralQueenStrength += h.queenGene;
					if (h.queenGene < thisYearStats.minFeralQueenStrength)
						thisYearStats.minFeralQueenStrength = h.queenGene;
					if (h.queenGene > thisYearStats.maxFeralQueenStrength)
						thisYearStats.maxFeralQueenStrength = h.queenGene;

					thisYearStats.totalFeralDrones += h.droneGenes.length;
					if (thisYearStats.minFeralDrones > h.droneGenes.length)
						thisYearStats.minFeralDrones = h.droneGenes.length;
					if (thisYearStats.maxFeralDrones < h.droneGenes.length)
						thisYearStats.maxFeralDrones = h.droneGenes.length;

					thisYearStats.totalFeralHiveStrength += h.getHiveStrength();
					if (thisYearStats.minFeralHiveStrength > h.getHiveStrength())
						thisYearStats.minFeralHiveStrength = h.getHiveStrength();
					if (thisYearStats.maxFeralHiveStrength < h.getHiveStrength())
						thisYearStats.maxFeralHiveStrength = h.getHiveStrength();
				}
			}
		}
	}

	public void hivesAtEndOfWinter(Site site) {
		List<Hive> hives = site.syncCopyHives();
		for (Hive h : hives) {
			if (site.domestic) {
				if (h.dead) {
					thisYearStats.domesticEowDeadHives++;
				} else {
					thisYearStats.domesticEowLiveHives++;
				}
			} else {
				if (h.dead) {
					thisYearStats.feralEowDeadHives++;
				} else {
					thisYearStats.feralEowLiveHives++;
				}
			}
		}
	}

	/**
	 * 
	 */
	public void startSimulation() {
		try {
			Properties props = sim.getProperties();
			do {
				File f = new File(resultsDir, "properties.txt");
				PrintStream out = new PrintStream(new FileOutputStream(f));
				SortedSet<String> keys = new TreeSet<>();
				for (Object key : props.keySet()) {
					keys.add(key.toString());
				}
				for (String key : keys) {
					out.print(key);
					out.print("=");
					out.println(props.getProperty(key));
				}
				out.close();
			} while (false);
			do {
				File f = new File(resultsDir, "sim_start_sites.csv");
				PrintStream out = new PrintStream(new FileOutputStream(f));
				out.println(Site.getStateCSVHeader());
				for (Site s : sim.getGrid()) {
					out.println(s.getStateCSV());
				}
				out.close();
			} while (false);
		} catch (Exception e) {
			System.err.println("Encountered a problem recording the state of the simulation at its start.");
			System.exit(-1);
		}
	}

	public void newHiveCreated(boolean domestic) {
		if (domestic) {
			thisYearStats.domesticHivesCreated++;
		} else {
			thisYearStats.feralHivesCreated++;
		}
	}

	public int getHivesCreatedThisYear() {
		return thisYearStats.domesticHivesCreated + thisYearStats.feralHivesCreated;
	}

	public void failedToSurviveWinter(boolean domestic) {
		if (domestic) {
			thisYearStats.domesticKilledByWinter++;
		} else {
			thisYearStats.feralKilledByWinter++;
		}
	}

	public void diedOfOldAge(boolean domestic) {
		if (domestic) {
			thisYearStats.domesticDiedOfOldAge++;
		} else {
			thisYearStats.feralDiedOfOldAge++;
		}
	}

	public void matingFlightFailed(boolean domestic) {
		if (domestic) {
			thisYearStats.domesticMatingFlightFailures++;
		} else {
			thisYearStats.feralMatingFlightFailures++;
		}
	}

	public void swarming(boolean domestic) {
		if (domestic) {
			thisYearStats.domesticSwarms++;
		} else {
			thisYearStats.feralSwarms++;
		}
	}

	public void swarmCouldNotFindSite(boolean domestic) {
		if (domestic) {
			thisYearStats.domesticSwarmsThatCouldNotFindSite++;
		} else {
			thisYearStats.feralSwarmsThatCouldNotFindSite++;
		}
	}

	public void swarmFoundSite(boolean domestic) {
		if (domestic) {
			thisYearStats.domesticSwarmsThatFoundSite++;
		} else {
			thisYearStats.feralSwarmsThatFoundSite++;
		}
	}

	public void hiveIsRequeened() {
		thisYearStats.domesticHiveRequeened++;
	}

}
