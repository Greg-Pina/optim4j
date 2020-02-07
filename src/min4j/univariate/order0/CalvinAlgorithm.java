package min4j.univariate.order0;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * An Adaptive Univariate Global Optimization Algorithm and Its Convergence Rate
 * under the Wiener Measure by James Calvin
 *
 * @author Michael
 */
public final class CalvinAlgorithm extends DerivativeFreeOptimizer {

	// ==========================================================================
	// FIELDS
	// ==========================================================================
	private final double myLambda;

	// ==========================================================================
	// CONSTRUCTORS
	// ==========================================================================
	/**
	 *
	 * @param absoluteTolerance
	 * @param maxEvaluations
	 * @param lambdaParam
	 */
	public CalvinAlgorithm(final double absoluteTolerance, final int maxEvaluations, final double lambdaParam) {
		super(absoluteTolerance, 0.0, maxEvaluations);
		myLambda = lambdaParam;
	}

	/**
	 *
	 * @param absoluteTolerance
	 * @param maxEvaluations
	 */
	public CalvinAlgorithm(final double absoluteTolerance, final int maxEvaluations) {
		this(absoluteTolerance, maxEvaluations, 16.0);
	}

	// ==========================================================================
	// IMPLEMENTATIONS
	// ==========================================================================
	@Override
	public double optimize(final Function<? super Double, Double> f, final double a, double b) {

		// prepare variables
		final int[] fevals = new int[1];

		// call main subroutine
		final double result = optimize(f, a, b, myTol, myLambda, myMaxEvals, fevals);
		myEvals = fevals[0];
		return result;
	}

	// ==========================================================================
	// HELPER METHODS
	// ==========================================================================
	private static double optimize(final Function<? super Double, Double> func, final double a, final double b,
			final double tolerance, final double lambda, final int fmax, final int[] evals) {
		final Function<Double, Double> obj = x -> func.apply(a + x * (b - a));
		final double topt = calvin(obj, tolerance, lambda, fmax, evals);
		if (topt != topt) {
			return Double.NaN;
		}
		return a + topt * (b - a);
	}

	private static double calvin(final Function<Double, Double> func, final double tolerance, final double lambda,
			final int fmax, final int[] evals) {

		// initialize the partition
		final List<Double> t = new ArrayList<>(fmax);
		t.add(0.0);
		t.add(0.5);
		t.add(1.0);

		// initialize the function evaluations at the endpoints
		final List<Double> f = new ArrayList<>(fmax);
		f.add(func.apply(0.0));
		f.add(func.apply(0.5));
		f.add(func.apply(1.0));

		// initialize the tracking parameters
		double tau = 0.5;
		double gtau = Math.sqrt(-lambda * tau * Math.log(tau));
		double min = min(f);
		evals[0] = f.size();

		// main loop
		for (int n = 2; n <= fmax; ++n) {

			// find out which interval to split
			double rhomax = Double.NEGATIVE_INFINITY;
			int imax = -1;
			for (int i = 1; i <= n; ++i) {
				final double num = t.get(i) - t.get(i - 1);
				final double den1 = f.get(i - 1) - min + gtau;
				final double den2 = f.get(i) - min + gtau;
				final double rho = num / (den1 * den2);
				if (rho > rhomax) {
					rhomax = rho;
					imax = i;
				}
			}

			// split the interval at imin
			final double left = t.get(imax - 1);
			final double rght = t.get(imax);
			final double tmid = 0.5 * (left + rght);
			final double fmid = func.apply(tmid);
			t.add(imax, tmid);
			f.add(imax, fmid);
			++evals[0];

			// update tracking parameters
			tau = Math.min(tau, tmid - left);
			tau = Math.min(tau, rght - tmid);
			gtau = Math.sqrt(-lambda * tau * Math.log(tau));
			min = Math.min(min, fmid);

			// check convergence
			if (tau <= tolerance) {
				final int imin = argmin(f);
				if (imin >= 0) {
					return t.get(imin);
				} else {
					break;
				}
			}
		}
		return Double.NaN;
	}

	private static final <T extends Comparable<T>> int argmin(final Iterable<? extends T> data) {
		int k = 0;
		int imin = -1;
		T min = null;
		for (final T t : data) {
			if (k == 0 || t.compareTo(min) < 0) {
				min = t;
				imin = k;
			}
			++k;
		}
		return imin;
	}

	private static final <T extends Comparable<T>> T min(final Iterable<? extends T> data) {
		int k = 0;
		T min = null;
		for (final T t : data) {
			if (k == 0 || t.compareTo(min) < 0) {
				min = t;
			}
			++k;
		}
		return min;
	}
}
