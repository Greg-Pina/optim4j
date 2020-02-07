package min4j.multivariate.unconstrained.order0.cmaes;

import java.util.Arrays;
import java.util.function.Function;

import min4j.multivariate.unconstrained.order0.GradientFreeOptimizer;
import min4j.multivariate.unconstrained.order0.cmaes.AbstractCmaesOptimizer.AbstractCmaesFactory;

/**
 * Auger, Anne, and Nikolaus Hansen. "A restart CMA evolution strategy with
 * increasing population size." Evolutionary Computation, 2005. The 2005 IEEE
 * Congress on. Vol. 2. IEEE, 2005.
 * 
 * Ilya Loshchilov, Marc Schoenauer, and Mich�le Sebag. "Black-box Optimization
 * Benchmarking of NIPOP-aCMA-ES and NBIPOP-aCMA-ES on the BBOB-2012 Noiseless
 * Testbed." Genetic and Evolutionary Computation Conference (GECCO-2012), ACM
 * Press : 269-276. July 2012.
 * 
 * Loshchilov, Ilya, Marc Schoenauer, and Michele Sebag. "Alternative restart
 * strategies for CMA-ES." International Conference on Parallel Problem Solving
 * from Nature. Springer, Berlin, Heidelberg, 2012.
 * 
 * Liao, Tianjun, and Thomas St�tzle. "Bounding the population size of
 * IPOP-CMA-ES on the noiseless BBOB testbed." Proceedings of the 15th annual
 * conference companion on Genetic and evolutionary computation. ACM, 2013.
 * 
 * @author Michael
 */
public final class IPopCmaesAlgorithm extends GradientFreeOptimizer {

	// ==========================================================================
	// FIELDS
	// ==========================================================================
	private final double myCmaesTol, mySigmaRef, mySigmaDec;
	private final int myMaxEvals;
	private final boolean myPrint;
	private final boolean myAdaptiveMaxLambda;
	private final AbstractCmaesFactory myCmaesFactory;

	private int myLambda, myMaxLambda, myMaxEv, myIt;
	private double mySigma, myFxBest, myFx, myFxOld;
	private double[] myXBest, myX, myXStart, myXGuess;
	private AbstractCmaesOptimizer myCmaes;

	private Function<? super double[], Double> myFunc;
	private int myD;

	// ==========================================================================
	// CONSTRUCTORS
	// ==========================================================================
	/**
	 *
	 * @param tolerance
	 * @param cmaesTolerance
	 * @param sigma0
	 * @param maxEvaluations
	 * @param maxPopulationSize
	 * @param printOutput
	 * @param cmaesFactory
	 * @param sigmaDecayFactor
	 */
	public IPopCmaesAlgorithm(final double tolerance, final double cmaesTolerance, final double sigma0,
			final int maxEvaluations, final int maxPopulationSize, final boolean printOutput,
			final AbstractCmaesFactory cmaesFactory, final double sigmaDecayFactor) {

		// implements IPOP-CMAES with population size limit
		super(tolerance);
		myCmaesTol = cmaesTolerance;
		mySigmaRef = sigma0;
		myMaxEvals = maxEvaluations;
		myPrint = printOutput;
		myMaxLambda = maxPopulationSize;
		myAdaptiveMaxLambda = false;
		myCmaesFactory = cmaesFactory;
		mySigmaDec = sigmaDecayFactor;
	}

	/**
	 *
	 * @param tolerance
	 * @param cmaesTolerance
	 * @param sigma0
	 * @param maxEvaluations
	 * @param printOutput
	 * @param cmaesFactory
	 * @param sigmaDecayFactor
	 */
	public IPopCmaesAlgorithm(final double tolerance, final double cmaesTolerance, final double sigma0,
			final int maxEvaluations, final boolean printOutput, final AbstractCmaesFactory cmaesFactory,
			final double sigmaDecayFactor) {

		// implements IPOP-CMAES with adaptive parameter limit
		super(tolerance);
		myCmaesTol = cmaesTolerance;
		mySigmaRef = sigma0;
		myMaxEvals = maxEvaluations;
		myPrint = printOutput;
		myAdaptiveMaxLambda = true;
		myCmaesFactory = cmaesFactory;
		mySigmaDec = sigmaDecayFactor;
	}

	/**
	 *
	 * @param tolerance
	 * @param cmaesTolerance
	 * @param sigma0
	 * @param maxEvaluations
	 * @param printOutput
	 */
	public IPopCmaesAlgorithm(final double tolerance, final double cmaesTolerance, final double sigma0,
			final int maxEvaluations, final boolean printOutput) {

		// implements NIPOP-aCMA-ES
		this(tolerance, cmaesTolerance, sigma0, maxEvaluations, Integer.MAX_VALUE, printOutput,
				new ActiveCmaesAlgorithm.ActiveCmaesFactory(), 1.6);
	}

	// ==========================================================================
	// IMPLEMENTATIONS
	// ==========================================================================
	@Override
	public final void initialize(final Function<? super double[], Double> func, final double[] guess) {

		// initialize domain
		myFunc = func;
		myD = guess.length;
		myXGuess = guess;

		// initialize sigma
		mySigma = mySigmaRef;

		// initialize lambda
		myLambda = 4 + (int) Math.floor(3.0 * Math.log(myD));

		// initialize max lambda as per Liao et al. (2013)
		if (myAdaptiveMaxLambda) {
			myMaxLambda = 10 * myD * myD;
		}

		// initialize number of function evaluations
		myMaxEv = (int) (100.0 + 50.0 * (myD + 3) * (myD + 3) / Math.sqrt(myLambda)) * myLambda;
		myMaxEv = Math.min(myMaxEv, myMaxEvals);

		// create new optimizer
		myCmaes = myCmaesFactory.createCmaStrategy(myCmaesTol, myLambda, mySigma, myMaxEv);
		myXStart = Arrays.copyOf(myXGuess, myD);

		// run initial CMAES algorithm
		myX = myCmaes.optimize(myFunc, myXStart);
		myFx = myFunc.apply(myX);

		// initialize counters
		myEvals = myCmaes.countEvaluations() + 1;
		myIt = 1;

		// initialize best points
		myXBest = myX;
		myFxBest = myFx;
		myFxOld = Double.NaN;

		// print output
		if (myPrint) {
			System.out.println("Run\tBudget\tMaxBudget\tPop\tSigma\tF\tBestF");
			System.out.println(myIt + "\t" + myEvals + "\t" + myMaxEv + "\t" + myLambda + "\t" + mySigma + "\t" + myFx
					+ "\t" + myFxBest);
		}
	}

	@Override
	public final void iterate() {

		// increase population size
		// we apply the maximum bound in Liao et al. (2013) and reset the lambda to
		// its initial value when reached
		myLambda <<= 1;
		if (myLambda >= myMaxLambda) {
			myLambda = 4 + (int) Math.floor(3.0 * Math.log(myD));
		}

		// adjust the sigma based on Loshchilov et al. (2012)
		mySigma /= mySigmaDec;
		mySigma = Math.max(mySigma, 0.01 * mySigmaRef);

		// set budget
		myMaxEv = (int) (100 + 50 * (myD + 3) * (myD + 3) / Math.sqrt(myLambda)) * myLambda;
		myMaxEv = Math.min(myMaxEv, myMaxEvals - myEvals);

		// set the guess
		for (int i = 0; i < myD; ++i) {
			myXStart[i] = myXGuess[i] + mySigmaRef * RAND.nextGaussian();
		}

		// create new optimizer
		myCmaes = myCmaesFactory.createCmaStrategy(myCmaesTol, myLambda, mySigma, myMaxEv);

		// run CMAES again
		myX = myCmaes.optimize(myFunc, myXStart);
		myFx = myFunc.apply(myX);

		// increment counters
		myEvals += myCmaes.countEvaluations() + 1;
		++myIt;

		// update best point
		if (myFx < myFxBest) {
			myFxOld = myFxBest;
			myXBest = myX;
			myFxBest = myFx;
		}

		// print output
		if (myPrint) {
			System.out.println(myIt + "\t" + myEvals + "\t" + myMaxEv + "\t" + myLambda + "\t" + mySigma + "\t" + myFx
					+ "\t" + myFxBest);
		}
	}

	@Override
	public double[] optimize(final Function<? super double[], Double> func, final double[] guess) {
		initialize(func, guess);
		while (myEvals < myMaxEvals) {
			iterate();

			// check convergence
			if (myFx != myFxOld) {
				final double ftol = RELEPS * 0.5 * Math.abs(myFx + myFxOld);
				if (Math.abs(myFx - myFxOld) <= myTol + ftol) {
					break;
				}
			}
		}
		return myXBest;
	}
}
