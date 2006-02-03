/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.test.internal.performance.eval;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.test.internal.performance.InternalPerformanceMeter;
import org.eclipse.test.internal.performance.data.DataPoint;
import org.eclipse.test.internal.performance.data.Dim;
import org.eclipse.test.internal.performance.data.Scalar;

import junit.framework.Assert;

/**
 * @since 3.1
 */
public class StatisticsSession {

	/**
	 * Percentile constants class.
	 * @since 3.2
	 */
	public static final class Percentile {
		final int fColumn;
		private final double fInside;
		private Percentile(int column, double inside) {
			fColumn= column;
			fInside= inside;
		}
		public double inside() {
			return fInside;
		}
	}
	
	public static final Percentile T90= new Percentile(0, 0.9);
	public static final Percentile T95= new Percentile(1, 0.95);
	public static final Percentile T97_5= new Percentile(2, 0.975);
	public static final Percentile T99= new Percentile(3, 0.99);

	static final class Statistics {
		public long count;
		public long sum;
		public double average;
		public double stddev;
	}
	
	private final DataPoint[] fDataPoints;
	private final Map fStatistics= new HashMap();

	public StatisticsSession(DataPoint[] datapoints) {
	    fDataPoints= datapoints;
	}
	
	public double getAverage(Dim dimension) {
		return getStats(dimension).average;
	}
	
	public long getSum(Dim dimension) {
		return getStats(dimension).sum;
	}
	
	public long getCount(Dim dimension) {
		return getStats(dimension).count;
	}
	
	public double getStddev(Dim dimension) {
		return getStats(dimension).stddev;
	}
	
	double getStderr_mean(Dim dimension) {
		return getStats(dimension).stddev / Math.sqrt(getStats(dimension).count);
	}
	
	double getStudentsT(Dim dimension, Percentile percentile) {
		int df= (int) getStats(dimension).count - 1;
		if (df > 100) {
			System.err.println("t table max degrees of freedom is 100"); //$NON-NLS-1$
			df = 100;
		}
		return T_TABLE[df][percentile.fColumn];
	}
	
	/**
	 * Returns the confidence interval for the given dimension and the percentile.
	 * 
	 * @param dimension the dimension 
	 * @param percentile the percentile
	 * @return an array of length two, with the lower and upper bounds of the confidence interval
	 */
	public double[] getConfidenceInterval(Dim dimension, Percentile percentile) {
	    double mean= getAverage(dimension);
		double stat_err= getStderr_mean(dimension);
		
		double t= getStudentsT(dimension, percentile);
		
		double[] interval= {mean - t * stat_err, mean + t * stat_err};
		
		return interval;
	}
	
	private Statistics getStats(Dim dimension) {
		Statistics stats= (Statistics) fStatistics.get(dimension);
		if (stats == null) {
			stats= computeStats(dimension);
			fStatistics.put(dimension, stats);
		}
		return stats;
	}

	private Statistics computeStats(Dim dimension) {
	    		
		Statistics stats= new Statistics();
		
		Set set= new HashSet();
		for (int j= 0; j < fDataPoints.length; j++) {
		    DataPoint dp= fDataPoints[j];
		    set.add(new Integer(dp.getStep()));
		}
		
		long mags[]= new long[fDataPoints.length];
		switch (set.size()) {
		case 1:
			// if there is only one Step, we don't calculate the delta.
			for (int i= 0; i < fDataPoints.length; i++) {
				Scalar sc= fDataPoints[i].getScalar(dimension);
				if (sc != null) {
					long magnitude= sc.getMagnitude();
					mags[i]= magnitude;
					stats.sum += magnitude;
					stats.count++;
				}
			}
			if (stats.count > 0) {
				stats.average= stats.sum / stats.count;
				for (int i= 0; i < fDataPoints.length; i++) {
					stats.stddev+= (stats.average - mags[i]) * (stats.average - mags[i]);
				}
			}
			break;
		case 2:
			for (int i= 0; i < fDataPoints.length-1; i += 2) {
				DataPoint before= fDataPoints[i];
				Assert.assertTrue("wrong order of steps", before.getStep() == InternalPerformanceMeter.BEFORE); //$NON-NLS-1$
				DataPoint after= fDataPoints[i + 1];
				Assert.assertTrue("wrong order of steps", after.getStep() == InternalPerformanceMeter.AFTER); //$NON-NLS-1$
				
				Scalar delta= getDelta(before, after, dimension);
				long magnitude= delta.getMagnitude();
				mags[i]= magnitude;
				stats.sum += magnitude;
				stats.count++;
			}
			if (stats.count > 0) {
				stats.average= stats.sum / stats.count;
				for (int i= 0; i < fDataPoints.length-1; i += 2) {
					stats.stddev+= (stats.average - mags[i]) * (stats.average - mags[i]);
				}
			}
			break;
		default:
			Assert.assertTrue("cannot handle more than two steps", false); //$NON-NLS-1$
		    break;
		}
		
		if (stats.count > 1)
			stats.stddev= Math.sqrt(stats.stddev / stats.count - 1); // unbiased sample stdev
		else
			stats.stddev= Double.NaN;

		return stats;
	}

	private Scalar getDelta(DataPoint before, DataPoint after, Dim dimension) {
		Scalar one= before.getScalar(dimension);
		Assert.assertTrue("reference has no value for dimension " + dimension, one != null); //$NON-NLS-1$

		Scalar two= after.getScalar(dimension);
		Assert.assertTrue("reference has no value for dimension " + dimension, two != null); //$NON-NLS-1$
		
		return new Scalar(one.getDimension(), two.getMagnitude() - one.getMagnitude());
	}

	public boolean contains(Dim dimension) {
		if (fDataPoints.length > 0)
			return fDataPoints[0].contains(dimension);
		return false;
	}
	
	/**
	 * The (two-tailed) T-table. [degrees_of_freedom][percentile]
	 */
	private static final double[][] T_TABLE= {
		{ Double.NaN, Double.NaN, Double.NaN, Double.NaN},
		{ Double.NaN, Double.NaN, Double.NaN, Double.NaN},
		{2.92,4.3027,6.2054,9.925},
		{2.3534,3.1824,4.1765,5.8408},
		{2.1318,2.7765,3.4954,4.6041},
		{2.015,2.5706,3.1634,4.0321},
		{1.9432,2.4469,2.9687,3.7074},
		{1.8946,2.3646,2.8412,3.4995},
		{1.8595,2.306,2.7515,3.3554},
		{1.8331,2.2622,2.685,3.2498},
		{1.8125,2.2281,2.6338,3.1693},
		{1.7959,2.201,2.5931,3.1058},
		{1.7823,2.1788,2.56,3.0545},
		{1.7709,2.1604,2.5326,3.0123},
		{1.7613,2.1448,2.5096,2.9768},
		{1.7531,2.1315,2.4899,2.9467},
		{1.7459,2.1199,2.4729,2.9208},
		{1.7396,2.1098,2.4581,2.8982},
		{1.7341,2.1009,2.445,2.8784},
		{1.7291,2.093,2.4334,2.8609},
		{1.7247,2.086,2.4231,2.8453},
		{1.7207,2.0796,2.4138,2.8314},
		{1.7171,2.0739,2.4055,2.8188},
		{1.7139,2.0687,2.3979,2.8073},
		{1.7109,2.0639,2.391,2.797},
		{1.7081,2.0595,2.3846,2.7874},
		{1.7056,2.0555,2.3788,2.7787},
		{1.7033,2.0518,2.3734,2.7707},
		{1.7011,2.0484,2.3685,2.7633},
		{1.6991,2.0452,2.3638,2.7564},
		{1.6973,2.0423,2.3596,2.75},
		{1.6955,2.0395,2.3556,2.744},
		{1.6939,2.0369,2.3518,2.7385},
		{1.6924,2.0345,2.3483,2.7333},
		{1.6909,2.0322,2.3451,2.7284},
		{1.6896,2.0301,2.342,2.7238},
		{1.6883,2.0281,2.3391,2.7195},
		{1.6871,2.0262,2.3363,2.7154},
		{1.686,2.0244,2.3337,2.7116},
		{1.6849,2.0227,2.3313,2.7079},
		{1.6839,2.0211,2.3289,2.7045},
		{1.6829,2.0195,2.3267,2.7012},
		{1.682,2.0181,2.3246,2.6981},
		{1.6811,2.0167,2.3226,2.6951},
		{1.6802,2.0154,2.3207,2.6923},
		{1.6794,2.0141,2.3189,2.6896},
		{1.6787,2.0129,2.3172,2.687},
		{1.6779,2.0117,2.3155,2.6846},
		{1.6772,2.0106,2.3139,2.6822},
		{1.6766,2.0096,2.3124,2.68},
		{1.6759,2.0086,2.3109,2.6778},
		{1.6753,2.0076,2.3095,2.6757},
		{1.6747,2.0066,2.3082,2.6737},
		{1.6741,2.0057,2.3069,2.6718},
		{1.6736,2.0049,2.3056,2.67},
		{1.673,2.004,2.3044,2.6682},
		{1.6725,2.0032,2.3033,2.6665},
		{1.672,2.0025,2.3022,2.6649},
		{1.6716,2.0017,2.3011,2.6633},
		{1.6711,2.001,2.3,2.6618},
		{1.6706,2.0003,2.299,2.6603},
		{1.6702,1.9996,2.2981,2.6589},
		{1.6698,1.999,2.2971,2.6575},
		{1.6694,1.9983,2.2962,2.6561},
		{1.669,1.9977,2.2954,2.6549},
		{1.6686,1.9971,2.2945,2.6536},
		{1.6683,1.9966,2.2937,2.6524},
		{1.6679,1.996,2.2929,2.6512},
		{1.6676,1.9955,2.2921,2.6501},
		{1.6672,1.9949,2.2914,2.649},
		{1.6669,1.9944,2.2906,2.6479},
		{1.6666,1.9939,2.2899,2.6469},
		{1.6663,1.9935,2.2892,2.6458},
		{1.666,1.993,2.2886,2.6449},
		{1.6657,1.9925,2.2879,2.6439},
		{1.6654,1.9921,2.2873,2.643},
		{1.6652,1.9917,2.2867,2.6421},
		{1.6649,1.9913,2.2861,2.6412},
		{1.6646,1.9908,2.2855,2.6403},
		{1.6644,1.9905,2.2849,2.6395},
		{1.6641,1.9901,2.2844,2.6387},
		{1.6639,1.9897,2.2838,2.6379},
		{1.6636,1.9893,2.2833,2.6371},
		{1.6634,1.989,2.2828,2.6364},
		{1.6632,1.9886,2.2823,2.6356},
		{1.663,1.9883,2.2818,2.6349},
		{1.6628,1.9879,2.2813,2.6342},
		{1.6626,1.9876,2.2809,2.6335},
		{1.6624,1.9873,2.2804,2.6329},
		{1.6622,1.987,2.28,2.6322},
		{1.662,1.9867,2.2795,2.6316},
		{1.6618,1.9864,2.2791,2.6309},
		{1.6616,1.9861,2.2787,2.6303},
		{1.6614,1.9858,2.2783,2.6297},
		{1.6612,1.9855,2.2779,2.6291},
		{1.6611,1.9852,2.2775,2.6286},
		{1.6609,1.985,2.2771,2.628},
		{1.6607,1.9847,2.2767,2.6275},
		{1.6606,1.9845,2.2764,2.6269},
		{1.6604,1.9842,2.276,2.6264},
		{1.6602,1.984,2.2757,2.6259},
	};
	
}
