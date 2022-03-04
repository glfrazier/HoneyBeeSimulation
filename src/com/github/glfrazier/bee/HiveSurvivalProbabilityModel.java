package com.github.glfrazier.bee;

import java.util.Properties;

/**
 * The model that translates hive gene health (a value between zero and one) to
 * the probability that the hive will survive a winter (also a value between
 * zero and one). The property 'survivalprob.model' specifies which model to
 * use. There are two built-in models ("linear" and "sigmoid"). Each model can
 * be adjusted using model-specific properties.
 * 
 * After the model calculates the hive survival probability, it adjusts that
 * probability if the hive is being fed (the <code>isDomestic</code> parameter
 * in {@link #survivalProbability(double, boolean)}). The adjustment is
 * 
 * <pre>
 * prob += F * (1 - prob)
 * </pre>
 * 
 * where <code>F</code> is a value in the range 0 to 1 that is specified by the
 * property 'survivalprob.F'. A value of zero results in feeding the hive having
 * no impact on its ability to survive the winter, while a value of one
 * guarantees that a hive that is fed will survive the winter. The default is
 * 0.5 &mdash; being fed moves the survival probability half way towards 1.0.
 * E.g., if the unfed probability of survival is 0.3, the fed probability of
 * survival is 0.65.
 * 
 * If you wish to create your own model, code a class that extends
 * HiveSurvivalProbabilityModel. The class must have a constructor that takes,
 * as its only argument, a <code>java.util.Properties</code> object. It should
 * invoke the superclass constructor, passing the properties object, and then
 * parse the properties to set its own values. It must implement
 * {@link #survivalProbability(double, boolean)}, and it should invoke
 * {@link #adjust(double)} to take feeding into account. Remember to adjust your
 * classpath such that your HiveSurvivalProbabilityModel class is included.
 * 
 * 
 * @see #survivalProbability(double, boolean)
 * @see HiveSurvivalProbabilityModel.LinearModel
 * @see HiveSurvivalProbabilityModel.SigmoidModel
 * @see BeeHealthSimulation#createPropertiesObject(Properties, String)
 * 
 * @author Greg Frazier
 *
 */
public abstract class HiveSurvivalProbabilityModel {

	public static final String[] MODELS = { "linear", "sigmoid" };

	protected final double F;

	private HiveSurvivalProbabilityModel(Properties props) {
		F = Double.parseDouble(props.getProperty("survivalprob.F", "0.5"));
		if (F < 0 || F > 1) {
			throw new IllegalArgumentException("survivalprob.F must be in the range [0..1]. You specified " + F);
		}
	}

	protected double adjust(double p) {
		return p + F * (1.0 - p);
	}

	/**
	 * Obtain the hive survival probability model that is specified by the
	 * properties. Note that the program will terminate with an error message if the
	 * property 'survivalprob.model' is not set or specifies an unknown model.
	 * 
	 * @param props
	 * @return
	 */
	public static HiveSurvivalProbabilityModel getModel(Properties props) {
		String model = props.getProperty("survivalprob.model");
		if (model == null) {
			System.err.println("The property 'survivalprob.model' must be specified.");
			System.exit(-1);
		}
		try {
			if (model.equals("linear")) {
				return new LinearModel(props);
			}
			if (model.equals("sigmoid")) {
				return new SigmoidModel(props);
			}
			return (HiveSurvivalProbabilityModel) BeeHealthSimulation.createPropertiesObject(props, model);
		} catch (Throwable e) {
			System.err.println("When parsing the hive survival probability model properties, the exception\n\t" + e
					+ "\nwas generated.");
			System.exit(-1);
		}
		System.err.println("'survivalprob.model=" + model + "' is not supported. The supported models:");
		for (String a : MODELS) {
			System.err.println("\t" + a);
		}
		System.exit(-1);
		// This line is unreachable, but Eclipse is unhappy without it
		return null;
	}

	/**
	 * Obtain the probability of surviving the winter.
	 * 
	 * @param hiveHealth the genetic health of the hive. See
	 *                   {@link InheritanceModel#getHiveStrength(double, double[])}.
	 * @param isDomestic <code>true</code> if the hive is domestic,
	 *                   <code>false</code> if it is feral. See
	 *                   {@link Site#domestic}.
	 * @return the probability of the hive surviving winter
	 */
	public abstract double survivalProbability(double hiveHealth, boolean isDomestic);

	/**
	 * <code>survivalprob.model=linear</code> In the linear model, the probability
	 * of survival is the gene health, adjusted for the hive being fed as described
	 * in {@link HiveSurvivalProbabilityModel}.
	 * 
	 * @author Greg Frazier
	 *
	 */
	public static class LinearModel extends HiveSurvivalProbabilityModel {

		public LinearModel(Properties props) {
			super(props);
		}

		@Override
		public double survivalProbability(double hiveHealth, boolean isDomestic) {
			if (isDomestic)
				return adjust(hiveHealth);
			// else
			return hiveHealth;
		}

	}

	/**
	 * <code>survivalprob.model=sigmoid</code> The sigmoid model uses a sigmoid
	 * curve to generate the hive survival probability:
	 * 
	 * <pre>
	 * prob = 1 / (1 + e ^ (-(M*H + A))
	 * </pre>
	 * 
	 * Where:
	 * 
	 * <dl>
	 * <dt>H
	 * </dl>
	 * <dd>The genetic health of the hive, <code>0 &lt; H &lt; 1</code>. This value
	 * is passed to survivalProbability.</dd>
	 * <dt>M</dt>
	 * <dd>Controls the steepness of the curve. It is specified by the property
	 * 'survivalprob.M'. If unspecified, 20 is the default.</dd>
	 * <dt>A</td>
	 * <dd>Shifts the curve to the left (<code>A &lt; 0</code>) or to the right
	 * (<code>A &gt; 0</code>). Specified by the property 'survivalprob.A'. If
	 * unspecified, -2 is the default.</dd>
	 * </dl>
	 * 
	 * For a description of the being-fed adjustment to the hive survival
	 * probability, see {@link HiveSurvivalProbabilityModel}.
	 * 
	 * @param props
	 */
	public static class SigmoidModel extends HiveSurvivalProbabilityModel {

		private final double M;
		private final double A;

		public SigmoidModel(Properties props) {
			super(props);
			M = Double.parseDouble(props.getProperty("survivalprob.M", "20"));
			A = Double.parseDouble(props.getProperty("survivalprob.A", "-2"));
		}

		@Override
		public double survivalProbability(double hiveHealth, boolean isDomestic) {
			double prob = 1 / (1 + Math.exp(-(M * hiveHealth + A)));
			if (isDomestic) {
				prob = adjust(prob);
			}
			return prob;
		}

	}
}
