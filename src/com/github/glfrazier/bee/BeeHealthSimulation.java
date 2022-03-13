package com.github.glfrazier.bee;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.util.Properties;
import java.util.Random;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Simulate the "health" of honeybee hives, both domestic and feral. The
 * simulator models the world as a {@link Grid} of {@link Site}s. (The grid
 * wraps around, so technically it is a torus.) Each site is either domestic or
 * feral. If it is domestic, the site may host multiple {@link Hive}s; if it is
 * feral, the site will only have a single {@link Hive}. The simulation breaks
 * each year into three actions:
 * <ol>
 * <li>{@link Site#overWinter()}, where hives may perish;</li>
 * <li>{@link Site#replaceDeadHives()}, where domestic sites replace dead hives
 * by acquiring a mated queen from a queen-breeder and wild sites are
 * repopulated by a swarm from a nearby hive; and</li>
 * <li>{@link Site#requeenIfOld()}, where domestic sites replace aging queens
 * with a mated queen acquired from a queen-breeder.</li>
 * </ol>
 * 
 * The simulation is designed to explore the impact of domestic honeybee
 * husbandry practices on both domestic and feral hive health, with a focus on
 * four husbandry practices:
 * <ol>
 * <li>The feeding of hives (and other practices that increase the probability
 * of a hive suriving);</li>
 * <li>The co-location hives, with 20+ co-located hives not unusual and
 * commercial operatings having up to 100 co-located hives;</li>
 * <li>The purchase of queens from a small number of professional queen
 * breeders; and</li>
 * <li>The unselective care/breeding of hives.</li>
 * 
 * This simulation does not attempt to model bee genetics. Rather, it leverages
 * two basic tenets of genetics and evolution:
 * <ul>
 * <li>Genetic traits result in a probability of survival; and</li>
 * <li>Offspring inherit a combination of their parents' genes (or, in the case
 * of drones, just their mother's genes);</li>
 * </ul>
 * So, genes are modeled as a probability of surviving the winter. The hive's
 * probability of survival is a combination of the queen and the drones'
 * survival probabilities, and a child queen's survival strength is selected
 * from a normal distribution centered on a combination of the mother queen and
 * one of the drone's survival strengths.
 * 
 * See the documentation for {@link #main(String[])} for a brief summary of how
 * to run the simulation, or the README at
 * https://github.com/glfrazier/BeeHealthSimulation.git for more comprehensive
 * documentation of the simulation.
 * 
 * @author Greg Frazier
 *
 */
public class BeeHealthSimulation implements Serializable, Runnable {

	private static final long serialVersionUID = 1L;

	public static final Logger LOGGER = Logger.getAnonymousLogger();

	private Random random;
	private Grid grid;
	private int edgeLength;
	private int simLength;

	private int year;

	/**
	 * The model that controls how workers and queens inherit traits from their
	 * parents.
	 */
	private InheritanceModel iModel;

	private Statistics stats;

	private Properties props;

	/** Set by the property feral_uses_domestic_survival_model */
	public boolean feralUsesDomesticSurvivalModel;

	/** Set by the property survivalprob.F */
	public double feedingFactor;

	public BeeHealthSimulation() {
		random = new Random();
	}

	/**
	 * Initialize the simulation using properties in the <code>props</code>
	 * argument. There are no default values: if a required property is not present,
	 * the simulation terminates with an appropriate error message.
	 * 
	 * @param props the properties that configure the simulation
	 */
	public void initialize(Properties props) {
		LOGGER.fine("Entered simulation initialization.");
		this.props = props;
		String seedStr = props.getProperty("seed");
		long seed = 0;
		if (seedStr == null || seedStr.equals("")) {
			seed = System.currentTimeMillis();
			props.setProperty("seed", Long.toString(seed));
		} else {
			seed = Long.parseLong(seedStr);
		}
		random.setSeed(seed);
		feralUsesDomesticSurvivalModel = getBooleanProperty("feral_uses_domestic_survival_model");
		feedingFactor = getProbabilityProperty("survivalprob.F");
		iModel = new InheritanceModel(props);
		stats = new Statistics(props, this);
		edgeLength = Integer.parseInt(props.getProperty("edge_length"));
		simLength = Integer.parseInt(props.getProperty("sim_length"));
		if (props.containsKey("seed")) {
			random.setSeed(Long.parseLong(props.getProperty("seed")));
		}
		grid = new Grid(edgeLength, this, random);
		LOGGER.fine("Grid constructed.");
		grid.initialize(random, props);
		// This is a light hack. The simulator, in the results directory, creates
		// a file for every property whose name begins "name" or "desc". The filename
		// is "<property_name>.txt", and in the file is the value of that property.
		// To make it easy to see how many hives were created at the beginning of the
		// simulation (e.g., to visually confirm that the properties are being
		// appropriately set), we create a property named
		// "desc_number_of_hives_<#hives>", and so have a file whose name includes the
		// number of hives.
		props.setProperty("desc_number_of_hives_" + stats.getHivesCreatedThisYear(),
				"" + stats.getHivesCreatedThisYear());
		LOGGER.fine("Completed simulation initialization.");
	}

	/**
	 * Run the simulation.
	 */
	public void run() {
		stats.startSimulation();
		// Record the statistics of the initial system, before it has processed any
		// years
		for (Site site : grid) {
			stats.hivesAtEndOfSummer(site);
		}
		stats.endOfSummer();

		// Now simulate the years.
		int progressInterval = -1;
		if (props.containsKey("progress_interval")) {
			progressInterval = getIntProperty(props, "progress_interval");
		}
		for (year = 0; year < simLength; year++) {
			boolean verbose = (progressInterval > 0 && year % progressInterval == 0);
			if (verbose) {
				System.out.println("Processing year " + year);
			}
			process(verbose);
			if (verbose) {
				System.out.println("Completed processing year " + year);
			}
		}
		try {
			stats.endSimulation();
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(-1);
		}
	}

	public int currentYear() {
		return year;
	}

	public InheritanceModel getSimulationInheritanceModel() {
		return iModel;
	}

	public Statistics getSimulationStatistics() {
		return stats;
	}

	/**
	 * Process a year of the simulation. The years go from fall to fall.
	 */
	public void process(boolean verbose) {
		if (verbose) {
			System.out.println("\tOver-wintering the sites.");
		}
		for (Site site : grid) {
			// Over-winter each hive at the site. Each hive will either die or be ready to
			// swarm the next summer.
			site.overWinter();
		}
		for (Site site : grid) {
			stats.hivesAtEndOfWinter(site);
		}
		stats.endOfWinter();
		if (verbose) {
			System.out.println("\tCompleted over-wintering. Replace dead hives and do some requeening.");
		}
		int numberOfThreads = 20;
		if (props.containsKey("threads")) {
			numberOfThreads = getIntProperty("threads");
		}
		final int NUMBER_OF_THREADS = numberOfThreads;
//		System.out.println("NUMBER_OF_THREADS=" + NUMBER_OF_THREADS);
//		System.out.println("Sites and their random numbers:");
//		for(Site s : grid) {
//			System.out.println("\t" + s.toString() + ":" + s.random.nextInt());
//		}
//		System.out.println("Hives and their random numbers:");
//		for(Site s : grid) {
//			for(Hive h : s.syncCopyHives()) {
//				System.out.println("\t" + h + ":" + h.random.nextInt());
//			}
//		}
		Thread[] threads = new Thread[NUMBER_OF_THREADS];
		for (int i = 0; i < NUMBER_OF_THREADS; i++) {
			final int TID = i;
			threads[TID] = new Thread("Worker Thread " + TID) {
				public void run() {
					for (int i = TID; i < grid.size(); i += NUMBER_OF_THREADS) {
						Site site = grid.getSite(i);
						if (site.domestic) {
							site.replaceDeadHivesOrRequeenLiveHives();
						}
					}
				}
			};
			threads[TID].start();
		}
		for (int i = 0; i < threads.length; i++) {
			try {
				threads[i].join();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				System.exit(-1);
			}
		}
		if (verbose) {
			System.out.println("\tCompleted requeening. Swarm if appropriate.");
		}
		for (Site site : grid) {
			site.swarmIfAppropriate();
		}
		for (Site site : grid) {
			stats.hivesAtEndOfSummer(site);
		}
		stats.endOfSummer();
	}

	// Some utilities

	/**
	 * Calls {@link #getProbabilityProperty(Properties, String)}, passing the
	 * Properties object that the sim was constructed with.
	 * 
	 * @param propName the name of the property
	 * @return the value of the property
	 */
	public double getProbabilityProperty(String propName) {
		return getProbabilityProperty(props, propName);
	}

	/**
	 * Parse a property that specifies a probability.
	 * 
	 * @param props    the properties
	 * @param propName the name of the property
	 * @return the value of the property
	 */
	public static double getProbabilityProperty(Properties props, String propName) {
		String probStr = props.getProperty(propName);
		if (probStr == null) {
			System.err.println("'" + propName + "' was not specified in the provided properties; it is required.");
			System.exit(-1);
		}
		try {
			double prob = Double.parseDouble(probStr);
			if (prob < 0 || prob > 1) {
				System.err.println("'" + propName
						+ "' is a probability, and so must be in the range [0..1]. You specified " + prob);
				System.exit(-1);
			}
			return prob;
		} catch (NumberFormatException e) {
			System.err.println("The value for the property '" + propName
					+ "' is not a number. It is a probability, and so must be a number in the range [0..1].");
			System.exit(-1);
		}
		// unreachable code
		return 0;
	}

	/**
	 * Obtain a String property.
	 * 
	 * @param props    the properties
	 * @param propName the name of the property
	 * @return the value of the property
	 */
	public static String getProperty(Properties props, String propName) {
		String value = props.getProperty(propName);
		if (value == null) {
			System.err.println("'" + propName + "' was not specified in the provided properties; it is required.");
			System.exit(-1);
		}
		return value;
	}

	/**
	 * Parse a property that specifies a number.
	 * 
	 * @param props    the properties
	 * @param propName the name of the property
	 * @return the value of the property
	 */
	public static double getDoubleProperty(Properties props, String propName) {
		String dStr = props.getProperty(propName);
		if (dStr == null) {
			System.err.println("'" + propName + "' was not specified in the provided properties; it is required.");
			System.exit(-1);
		}
		try {
			double d = Double.parseDouble(dStr);
			return d;
		} catch (NumberFormatException e) {
			System.err.println("The value for the property '" + propName + "' is not a number. It is '" + dStr + "'");
			e.printStackTrace();
			System.exit(-1);
		}
		// unreachable code
		return 0;
	}

	/**
	 *
	 * 
	 * @param propName the name of the property
	 * @return the value of the property
	 */
	public boolean getBooleanProperty(String propName) {
		return getBooleanProperty(props, propName);
	}

	public static boolean getBooleanProperty(Properties props, String propName) {
		if (!props.containsKey(propName)) {
			throw new IllegalArgumentException("Property <" + propName + "> is not in the properties.");
		}
		String s = props.getProperty(propName);
		try {
			return Boolean.parseBoolean(s);
		} catch (Throwable t) {
			LOGGER.severe("The property <" + propName + "> must have a boolean value (true/false). It has: " + s);
			System.exit(-1);
		}
		// unreachable code
		return false;
	}

	/**
	 * Calls {@link #getIntProperty(Properties, String)}, passing the Properties
	 * object that the sim was constructed with.
	 * 
	 * @param propName the name of the property
	 * @return the value of the property
	 */
	public int getIntProperty(String propName) {
		return getIntProperty(props, propName);
	}

	/**
	 * Parse a property that specifies an integer.
	 * 
	 * @param props    the properties
	 * @param propName the name of the property
	 * @return the value of the property
	 */
	public static int getIntProperty(Properties props, String propName) {
		String iStr = props.getProperty(propName);
		if (iStr == null) {
			System.err.println("'" + propName + "' was not specified in the provided properties; it is required.");
			System.exit(-1);
		}
		try {
			int i = Integer.parseInt(iStr);
			return i;
		} catch (NumberFormatException e) {
			System.err
					.println("The value for the property '" + propName + "' is not an integer number---it is " + iStr);
			System.exit(-1);
		}
		// unreachable code
		return 0;
	}

	@SuppressWarnings("rawtypes")
	private static Class[] argTypes = { java.util.Properties.class };

	/**
	 * Return an object from a class that has a constructor that takes a
	 * java.util.Properties argument. Exits the process with an error message if
	 * there is no such a class or if the class does not have an appropriate
	 * constructor.
	 */
	public static Object createPropertiesObject(Properties props, String classname) {
		try {
			Class<?> objectClass = (Class<?>) Class.forName(classname);
			Constructor<?> objectConstructor = objectClass.getConstructor(argTypes);
			Object o = objectConstructor.newInstance(props);
			return o;
		} catch (Exception e) {
			System.err.println("Failed to construct an object of class " + classname + ": " + e);
			e.printStackTrace();
			System.exit(-1);
		}
		// unreachable code
		return null;
	}

	/**
	 * The arguments to the simulation are properties: name/value pairs separated by
	 * an equal sign. The typical way to run a simulation is to create a properties
	 * file, formatted in accordance to <code>java.util.Properties</code>, and then
	 * include the argument "properties_file=<filename>" in the argument list. That
	 * properties file may, as one if its properties, specify a second properties
	 * file to be loaded. Properties specified on the command line take precedence
	 * over properties in a file, and properties encountered in the first file take
	 * precedence over those specified in subsequent files. Thus,
	 * 
	 * <pre>
	 * java -cp bin com.github.glfrazier.bee.BeeHealthSimulation properties_file=propertyfiles/defaultvalues.prop sim_length=10
	 * </pre>
	 * 
	 * will use the properties specified in defaultvalues.prop except for the
	 * sim_length property, which will have the value 10.
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		ConsoleHandler handler = new ConsoleHandler();
		handler.setLevel(Level.ALL);
		LOGGER.addHandler(handler);
		Properties props = new Properties();
		for (String arg : args) {
			if (!arg.contains("=")) {
				System.err.println("Command line arguments must be in the form of 'name=value'.");
				System.exit(-1);
			}
			String[] tokens = arg.split("=");
			if (tokens.length == 1) {
				String name = tokens[0];
				tokens = new String[2];
				tokens[0] = name;
				tokens[1] = "";
			}
			props.setProperty(tokens[0].trim(), tokens[1].trim());
		}
		if (props.containsKey("logging")) {
			String level = props.getProperty("logging");
			LOGGER.setLevel(Level.parse(level));
		}
		while (props.containsKey("properties_file")) {
			String fname = props.getProperty("properties_file");
			LOGGER.fine("Loading properties from '" + fname + "'.");
			props.remove("properties_file");
			Properties tmp = props;
			props = new Properties();
			try {
				props.load(new FileInputStream(fname));
			} catch (Exception e) {
				System.err.println("Failed to load properties from '" + tmp.getProperty("properties_file") + "':" + e);
			}
			LOGGER.fine("Finished loading properties from '" + fname + "'.");
			props.putAll(tmp);
		}
		if (props.containsKey("logging")) {
			String level = props.getProperty("logging");
			LOGGER.setLevel(Level.parse(level));
		}
		LOGGER.fine("Inputs have been parsed.");
		BeeHealthSimulation sim = new BeeHealthSimulation();
		sim.initialize(props);
		sim.run();
	}

	public Properties getProperties() {
		return props;
	}

	public Grid getGrid() {
		return grid;
	}

	public double cappedNormal(Random rand, double mean, double stddev) {
		double max = getProbabilityProperty("max_g");
		double d = rand.nextGaussian() * stddev + mean;
		if (d > mean + stddev)
			d = mean + stddev;
		if (d > max)
			d = max;
		if (d < mean - stddev)
			d = mean - stddev;
		return d;
	}

	public String getProperty(String propName) {
		return getProperty(props, propName);
	}

}
