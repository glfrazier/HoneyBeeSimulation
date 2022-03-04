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

public class BeeHealthSimulation implements Serializable {

	private static final long serialVersionUID = 1L;

	public static final Logger LOGGER = Logger.getAnonymousLogger();

	private Random random;
	private Grid grid;
	private int edgeLength;
	private int simLength;

	private InheritanceModel iModel;
	private Statistics stats;

	private Properties props;

	public BeeHealthSimulation() {
		random = new Random();
	}

	public void initialize() {
		initialize(System.getProperties());
	}

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
		iModel = new InheritanceModel(props);
		stats = new Statistics(props, this);
		edgeLength = Integer.parseInt(props.getProperty("edge_length"));
		simLength = Integer.parseInt(props.getProperty("sim_length"));
		if (props.containsKey("seed")) {
			random.setSeed(Long.parseLong(props.getProperty("seed")));
		}
		grid = new Grid(edgeLength, random.nextLong(), this, random);
		LOGGER.fine("Grid constructed.");
		grid.initialize(random, props);
		LOGGER.fine("Completed simulation initialization.");
	}

	public void run() {
		stats.startSimulation();
		// Record the statistics of the initial system, before it has processed any
		// years
		for (Site site : grid) {
			stats.hivesAtEndOfSummer(site);
		}
		stats.endOfSummer();

		// Now simulate the years.
		for (int i = 0; i < simLength; i++) {
			process();
		}
		try {
			stats.endSimulation();
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(-1);
		}
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
	public void process() {
		for (Site site : grid) {
			// Over-winter each hive at the site. Each hive will either die or be ready to
			// swarm the next summer.
			site.overWinter();
		}
		for (Site site : grid) {
			stats.hivesAtEndOfWinter(site);
		}
		stats.endOfWinter();
		for (Site site : grid) {
			// It is spring. Dead domestic hives will be replaced by purchasing a
			// queen from a queen breeder. Dead wild hives will be replaced by a swarm from
			// a nearby hive, if there is a nearby have that is ready to swarm and has not
			// already done so this summer. (Note: if there are no neighboring swarms
			// available, a dead wild hive won't be replaced.)
			site.replaceDeadHives();
		}
		for (Site site : grid) {
			// It is early summer. Bee keepers with two-year-old queens re-queen their
			// hives. This includes queen breeders. Note that we do this AFTER replacing
			// dead hives, so swarming has already taken place.
			if (site.domestic) {
				site.requeenIfOld();
			}
		}
		for (Site site : grid) {
			stats.hivesAtEndOfSummer(site);
		}
		stats.endOfSummer();
	}

	// Some utilities

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

//	/**
//	 * Parse a property that specifies a probability and has a default value.
//	 * 
//	 * @param props        the properties
//	 * @param propName     the name of the property
//	 * @param defaultValue the default value of the property, in string form
//	 * @return the value of the property
//	 */
//	public static double getProbabilityProperty(Properties props, String propName, String defaultValue) {
//		try {
//			double d = Double.parseDouble(defaultValue);
//			if (d < 0 || d > 1) {
//				throw new IllegalArgumentException(defaultValue + " is outside the range [0..1].");
//			}
//		} catch (NumberFormatException e) {
//			System.err.println(
//					"The default value for the property '" + propName + "' is not a number! It is " + defaultValue);
//			throw e;
//		}
//		String probStr = props.getProperty(propName, defaultValue);
//		try {
//			double prob = Double.parseDouble(probStr);
//			if (prob < 0 || prob > 1) {
//				System.err.println("'" + propName
//						+ "' is a probability, and so must be in the range [0..1]. You specified " + prob);
//				System.exit(-1);
//			}
//			return prob;
//		} catch (NumberFormatException e) {
//			System.err.println("The value for the property '" + propName
//					+ "' is not a number. It is a probability, and so must be a number in the range [0..1].");
//			System.exit(-1);
//		}
//		// unreachable code
//		return 0;
//	}

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

//	/**
//	 * Parse a property that specifies a number that has a default value.
//	 * 
//	 * @param props        the properties
//	 * @param propName     the name of the property
//	 * @param defaultValue the default value of the property, in string form
//	 * @return the value of the property
//	 */
//	public static double getDoubleProperty(Properties props, String propName, String defaultValue) {
//		try {
//			Double.parseDouble(defaultValue);
//		} catch (NumberFormatException e) {
//			System.err.println(
//					"The default value for the property '" + propName + "' is not a number! It is " + defaultValue);
//			throw e;
//		}
//		String dStr = props.getProperty(propName, defaultValue);
//		try {
//			double prob = Double.parseDouble(dStr);
//			return prob;
//		} catch (NumberFormatException e) {
//			System.err.println("The value for the property '" + propName + "' is not a number---it is " + dStr);
//			System.exit(-1);
//		}
//		// unreachable code
//		return 0;
//	}

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
			System.err.println("The value for the property '" + propName + "' is not a number---it is " + iStr);
			System.exit(-1);
		}
		// unreachable code
		return 0;
	}

//	/**
//	 * Parse a property that specifies a number that has a default value.
//	 * 
//	 * @param props        the properties
//	 * @param propName     the name of the property
//	 * @param defaultValue the default value of the property, in string form
//	 * @return the value of the property
//	 */
//	public static int getIntProperty(Properties props, String propName, String defaultValue) {
//		try {
//			Integer.parseInt(defaultValue);
//		} catch (NumberFormatException e) {
//			System.err.println(
//					"The default value for the property '" + propName + "' is not an integer! It is " + defaultValue);
//			throw e;
//		}
//		String iStr = props.getProperty(propName, defaultValue);
//		try {
//			int i = Integer.parseInt(iStr);
//			return i;
//		} catch (NumberFormatException e) {
//			System.err.println("The value for the property '" + propName + "' is not a number. It is " + iStr);
//			System.exit(-1);
//		}
//		// unreachable code
//		return 0;
//	}

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
			props.setProperty(tokens[0].trim(), tokens[1].trim());
			if (props.containsKey("logging")) {
				String level = props.getProperty("logging");
				LOGGER.setLevel(Level.parse(level));
			}
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

}
