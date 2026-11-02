package com.kelly.varengine.services;

import java.util.List;
import java.util.Random;

import com.kelly.varengine.models.PortfolioPosition;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.dimensionalityreduction.PCA;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.ejml.simple.SimpleMatrix;
import org.ejml.dense.row.factory.DecompositionFactory_DDRM;
import org.ejml.interfaces.decomposition.CholeskyDecomposition_F64;
import org.ejml.data.DMatrixRMaj;

public class VarServices {

    public INDArray createCovariantMatrix(INDArray logReturnsMatrix) {
        INDArray[] matrices = PCA.covarianceMatrix(logReturnsMatrix);
        return matrices.length > 0 ?
                matrices[0] :
                Nd4j.empty();
    }

    public double calculateVarianceSigma(INDArray covariantMatrix, List<PortfolioPosition> positions) {
        // Create a matrix from position values
        double[][] positionData = new double[positions.size()][1];  // N x 1
        for (int index = 0; index < positions.size(); index++)
            positionData[index][0] = positions.get(index).getCurrentValue();

        // Multiple covariant matrix values by position vertical vector
        INDArray valueMatrix = Nd4j.create(positionData);
        INDArray intermediateMatrix = covariantMatrix.mul(valueMatrix);

        // Transpose the resulting matrix (intermediate) and then multiply by positions matrix
        INDArray intermediateTransposed = intermediateMatrix.transpose();
        INDArray sigmaSquaredMatrix = valueMatrix.mmul(intermediateTransposed);

        // Summate all the values in the resulting matrix, which is sigma^2
        double sigmaSquared = sigmaSquaredMatrix.sumNumber().doubleValue();

        // Variance is the square root of sigma^2
        return Math.sqrt(sigmaSquared);
    }

    public double calculateValueAtRisk(double confidence, double variance, double portfolioValue) {
        NormalDistribution normalDist = new NormalDistribution();
        //double cumulativeArea = (1.0 + confidence) / 2.0;
        double zScore = normalDist.inverseCumulativeProbability(confidence);

        return portfolioValue * variance * zScore;
    }

    public SimpleMatrix performCholeskyDecomposition(INDArray covariantMatrix) {
        // Convert ND4J INDArray to EJML SimpleMatrix [square matrix, N x N]
        double[][] array2d = covariantMatrix.toDoubleMatrix();
        SimpleMatrix covarianceSimpleMatrix = new SimpleMatrix(array2d);

        // Calculate the Cholesky Decomposition Lower Triangular Matrix (L)
        CholeskyDecomposition_F64<DMatrixRMaj> cholesky =
                DecompositionFactory_DDRM.chol(covarianceSimpleMatrix.getNumRows(), true);

        if (!cholesky.decompose(covarianceSimpleMatrix.getMatrix()))
            throw new RuntimeException("Cholesky Decomposition failed. Matrix must be symmetric positive-definite.");

        // Return the "L" (lower triangular) matrix
        return SimpleMatrix.wrap(cholesky.getT(null));
    }

    public double[][] calculateRiskDecomposition(INDArray covariantMatrix, List<PortfolioPosition> positions, double portfolioValue, double zScore) {
        int n = positions.size();
        double[][] decomposition = new double[n][3]; // [Standalone VaR, Marginal VaR, Component VaR]

        // Extract weights vector (w) and position values (v)
        double[][] weightData = new double[n][1];
        double[][] valueData = new double[n][1];
        for (int i = 0; i < n; i++) {
            valueData[i][0] = positions.get(i).getCurrentValue();
            weightData[i][0] = valueData[i][0] / portfolioValue;
        }
        INDArray w = Nd4j.create(weightData);
        INDArray Sigma = covariantMatrix;

        // Portfolio variance and volatility (sigma_p)
        double sigmaP = calculateVarianceSigma(covariantMatrix, positions);
        if (sigmaP == 0.0) return decomposition;

        // Marginal Risk Contribution (Vector): (Sigma * w) / sigma_p
        INDArray sigmaW = Sigma.mmul(w);
        INDArray marginalRisk = sigmaW.div(sigmaP);

        // Total Portfolio VaR
        double totalVaR = portfolioValue * sigmaP * zScore;

        for (int i = 0; i < n; i++) {
            double positionVal = positions.get(i).getCurrentValue();

            // 1. Standalone VaR (VaR if asset was 100% of portfolio, using its diagonal variance)
            double assetStdDev = Math.sqrt(Sigma.getDouble(i, i));
            double standaloneVaR = positionVal * assetStdDev * zScore;
            decomposition[i][0] = standaloneVaR;

            // 2. Marginal VaR (MVaR): Change in total VaR per unit change in weight
            double mVar = positionVal * marginalRisk.getDouble(i, 0) * zScore;
            decomposition[i][1] = mVar;

            // 3. Component VaR (CVaR): Absolute $ risk contribution summing up to total portfolio VaR
            double cVar = totalVaR * (weightData[i][0] * marginalRisk.getDouble(i, 0)) / sigmaP;
            decomposition[i][2] = cVar;
        }

        return decomposition;
    }

    public double performMonteCarloPortfolioTrial(SimpleMatrix choleskyL, double[] assetWeights, double[] assetMeans,
                                                  double[] assetStdDevs, int timeHorizonDays) {
        int numAssets = assetWeights.length;
        Random rand = new Random();

        // 1. Generate independent standard normal vector Z (N x 1)
        SimpleMatrix zNormals = new SimpleMatrix(numAssets, 1);
        for (int i = 0; i < numAssets; i++)
            zNormals.set(i, 0, rand.nextGaussian());

        // 2. Apply Cholesky decomposition to introduce correlation: X = L * Z + Drift
        SimpleMatrix correlatedShocks = choleskyL.mult(zNormals);

        // 3. Calculate simulated portfolio return over the horizon
        double portfolioReturn = 0.0;
        double dt = Math.sqrt(timeHorizonDays); // scaling by horizon time steps

        for (int i = 0; i < numAssets; i++) {
            double drift = (assetMeans[i] - 0.5 * Math.pow(assetStdDevs[i], 2)) * timeHorizonDays;
            double shock = correlatedShocks.get(i, 0) * dt;
            double assetReturn = Math.exp(drift + shock) - 1.0;

            portfolioReturn += assetWeights[i] * assetReturn;
        }

        return portfolioReturn;
    }
}