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

	/**
	 * The model that controls how workers and queens inherit traits from their
	 * parents.
	 */
	private InheritanceModel iModel;

	private Statistics stats;

	private Properties props;

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
