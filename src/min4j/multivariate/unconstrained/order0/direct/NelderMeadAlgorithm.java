package min4j.multivariate.unconstrained.order0.direct;

import java.util.Arrays;
import java.util.function.Function;

import min4j.multivariate.unconstrained.order0.GradientFreeOptimizer;

/**
 * Alan Miller
 *
 * @author Michael
 */
public final class NelderMeadAlgorithm extends GradientFreeOptimizer {

	// ==========================================================================
	// FIELDS
	// ==========================================================================
	// algorithm constants
	private final double eps = 0.001;
	private final boolean myAdaptive;
	private final int myCheckEvery;
	private final int myMaxEvals;
	private final double myRadius;

	// problem parameters
	private Function<? super double[], Double> myFunc;
	private int n;
	private double[] start;

	// algorithm temporaries
	private boolean converged;
	private int ihi, ilo, jcount, icount;
	private double ccoeff, ecoeff, rcoeff, scoeff, del, rq, y2star, ylo, ystar, ynewlo;
	private double[][] p;
	private double[] p2star, pbar, pstar, y, xmin, step;

	// ==========================================================================
	// CONSTRUCTORS
	// ==========================================================================
	/**
	 *
	 * @param tolerance
	 * @param initialRadius
	 * @param checkEvery
	 * @param maxEvaluations
	 * @param adaptive
	 */
	public NelderMeadAlgorithm(final double tolerance, final double initialRadius, final int checkEvery,
			final int maxEvaluations, final boolean adaptive) {
		super(tolerance);
		myAdaptive = adaptive;
		myCheckEvery = checkEvery;
		myMaxEvals = maxEvaluations;
		myRadius = initialRadius;
	}

	/**
	 *
	 * @param tolerance
	 * @param initialRadius
	 * @param checkEvery
	 * @param maxEvaluations
	 */
	public NelderMeadAlgorithm(final double tolerance, final double initialRadius, final int checkEvery,
			final int maxEvaluations) {
		this(tolerance, initialRadius, checkEvery, maxEvaluations, true);
	}

	// ==========================================================================
	// IMPLEMENTATIONS
	// ==========================================================================
	@Override
	public void initialize(final Function<? super double[], Double> func, final double[] guess) {

		// problem initialization
		n = guess.length;
		myFunc = func;
		start = Arrays.copyOf(guess, n);

		// parameters
		if (myAdaptive) {
			ccoeff = 0.75 - 0.5 / n;
			ecoeff = 1.0 + 2.0 / n;
			rcoeff = 1.0;
			scoeff = 1.0 - 1.0 / n;
		} else {
			ccoeff = 0.5;
			ecoeff = 2.0;
			rcoeff = 1.0;
			scoeff = 0.5;
		}

		// storage
		p = new double[n + 1][n];
		p2star = new double[n];
		pbar = new double[n];
		pstar = new double[n];
		y = new double[n + 1];
		xmin = new double[n];
		step = new double[n];
		Arrays.fill(step, myRadius);

		// Initialization.
		icount = 0;
		jcount = myCheckEvery;
		del = 1.0;
		rq = myTol * myTol * n;
		ynewlo = 0.0;
	}

	@Override
	public void iterate() {

		// YNEWLO is, of course, the HIGHEST value???
		converged = false;
		ihi = argmax(y) + 1;
		ynewlo = y[ihi - 1];

		// Calculate PBAR, the centroid of the simplex vertices
		// excepting the vertex with Y value YNEWLO.
		for (int i = 1; i <= n; ++i) {
			double sum = 0.0;
			for (int k = 1; k <= n + 1; ++k) {
				sum += p[k - 1][i - 1];
			}
			sum -= p[ihi - 1][i - 1];
			sum /= n;
			pbar[i - 1] = sum;
		}

		// Reflection through the centroid.
		for (int k = 1; k <= n; ++k) {
			pstar[k - 1] = pbar[k - 1] + rcoeff * (pbar[k - 1] - p[ihi - 1][k - 1]);
		}
		ystar = myFunc.apply(pstar);
		++icount;

		// Successful reflection, so extension.
		if (ystar < ylo) {

			// Expansion.
			for (int k = 1; k <= n; ++k) {
				p2star[k - 1] = pbar[k - 1] + ecoeff * (pstar[k - 1] - pbar[k - 1]);
			}
			y2star = myFunc.apply(p2star);
			++icount;

			// Retain extension or contraction.
			if (ystar < y2star) {
				System.arraycopy(pstar, 0, p[ihi - 1], 0, n);
				y[ihi - 1] = ystar;
			} else {
				System.arraycopy(p2star, 0, p[ihi - 1], 0, n);
				y[ihi - 1] = y2star;
			}
		} else {

			// No extension.
			int l = 0;
			for (int i = 1; i <= n + 1; ++i) {
				if (ystar < y[i - 1]) {
					++l;
				}
			}
			if (1 < l) {

				// Copy pstar to the worst (HI) point.
				System.arraycopy(pstar, 0, p[ihi - 1], 0, n);
				y[ihi - 1] = ystar;
			} else if (l == 0) {

				// Contraction on the Y(IHI) side of the centroid.
				for (int k = 1; k <= n; ++k) {
					p2star[k - 1] = pbar[k - 1] + ccoeff * (p[ihi - 1][k - 1] - pbar[k - 1]);
				}
				y2star = myFunc.apply(p2star);
				++icount;

				// Contract the whole simplex.
				if (y[ihi - 1] < y2star) {
					for (int j = 1; j <= n + 1; ++j) {
						for (int k = 1; k <= n; ++k) {
							p[j - 1][k - 1] = scoeff * (p[j - 1][k - 1] + p[ilo - 1][k - 1]);
						}
						System.arraycopy(p[j - 1], 0, xmin, 0, n);
						y[j - 1] = myFunc.apply(xmin);
						++icount;
					}
					ilo = argmin(n + 1, y) + 1;
					ylo = y[ilo - 1];
					converged = false;
					return;
				} else {

					// Retain contraction.
					System.arraycopy(p2star, 0, p[ihi - 1], 0, n);
					y[ihi - 1] = y2star;
				}
			} else if (l == 1) {

				// Contraction on the reflection side of the centroid.
				for (int k = 1; k <= n; ++k) {
					p2star[k - 1] = pbar[k - 1] + ccoeff * (pstar[k - 1] - pbar[k - 1]);
				}
				y2star = myFunc.apply(p2star);
				++icount;

				// Retain reflection?
				if (y2star <= ystar) {
					System.arraycopy(p2star, 0, p[ihi - 1], 0, n);
					y[ihi - 1] = y2star;
				} else {
					System.arraycopy(pstar, 0, p[ihi - 1], 0, n);
					y[ihi - 1] = ystar;
				}
			}
		}

		// Check if YLO improved.
		if (y[ihi - 1] < ylo) {
			ylo = y[ihi - 1];
			ilo = ihi;
		}
		--jcount;
		if (0 < jcount) {
			converged = false;
			return;
		}

		// Check to see if minimum reached.
		if (icount <= myMaxEvals) {
			jcount = myCheckEvery;
			double sum = 0.0;
			for (int k = 1; k <= n + 1; ++k) {
				sum += y[k - 1];
			}
			sum /= (n + 1.0);
			double sumsq = 0.0;
			for (int k = 1; k <= n + 1; ++k) {
				sumsq += (y[k - 1] - sum) * (y[k - 1] - sum);
			}
			if (sumsq <= rq) {
				converged = true;
			}
		}
	}

	@Override
	public double[] optimize(final Function<? super double[], Double> func, final double[] guess) {

		// prepare variables
		initialize(func, guess);

		// call main subroutine
		final int ifault = nelmin();
		myEvals += icount;
		if (ifault == 0) {
			return xmin;
		} else {
			return null;
		}
	}

	// ==========================================================================
	// HELPER METHODS
	// ==========================================================================
	private int nelmin() {

		// Initial or restarted loop.
		while (true) {

			// Start of the restart.
			System.arraycopy(start, 0, p[n], 0, n);
			y[n + 1 - 1] = myFunc.apply(start);
			++icount;

			// Define the initial simplex.
			for (int j = 1; j <= n; ++j) {
				final double x = start[j - 1];
				start[j - 1] += step[j - 1] * del;
				System.arraycopy(start, 0, p[j - 1], 0, n);
				y[j - 1] = myFunc.apply(start);
				++icount;
				start[j - 1] = x;
			}

			// Find highest and lowest Y values. YNEWLO = Y(IHI) indicates
			// the vertex of the simplex to be replaced.
			ilo = argmin(n + 1, y) + 1;
			ylo = y[ilo - 1];

			// Inner loop.
			while (icount < myMaxEvals) {
				iterate();
				if (converged) {
					break;
				}
			}

			// Factorial tests to check that YNEWLO is a local minimum.
			System.arraycopy(p[ilo - 1], 0, xmin, 0, n);
			ynewlo = y[ilo - 1];
			if (myMaxEvals < icount) {
				return 2;
			}
			int ifault = 0;
			for (int i = 1; i <= n; ++i) {
				del = step[i - 1] * eps;
				xmin[i - 1] += del;
				double z = myFunc.apply(xmin);
				++icount;
				if (z < ynewlo) {
					ifault = 2;
					break;
				}
				xmin[i - 1] -= (del + del);
				z = myFunc.apply(xmin);
				++icount;
				if (z < ynewlo) {
					ifault = 2;
					break;
				}
				xmin[i - 1] += del;
			}
			if (ifault == 0) {
				return ifault;
			}

			// Restart the procedure.
			System.arraycopy(xmin, 0, start, 0, n);
			del = eps;
		}
	}

	private static final int argmin(final int len, final double... data) {
		int k = 0;
		int imin = -1;
		double min = 0;
		for (final double t : data) {
			if (k >= len) {
				break;
			}
			if (k == 0 || t < min) {
				min = t;
				imin = k;
			}
			++k;
		}
		return imin;
	}

	private static final int argmax(final double... data) {
		int k = 0;
		int imax = -1;
		double max = 0.0;
		for (final double t : data) {
			if (k == 0 || t > max) {
				max = t;
				imax = k;
			}
			++k;
		}
		return imax;
	}
}