package com.kelly.varengine.services;

import java.util.List;
import java.util.Random;

import com.kelly.varengine.models.PortfolioPosition;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.dimensionalityreduction.PCA;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.ejml.simple.SimpleMatrix;

public class VarServices {

    public INDArray createCovariantMatrix(INDArray logReturnsMatrix) {
        INDArray[] matrices = PCA.covarianceMatrix(logReturnsMatrix);
        return matrices.length > 0 ?
                matrices[0] :
                Nd4j.empty();
    }

    public double calculateVariantSigma(INDArray covariantMatrix, List<PortfolioPosition> positions) {
        // Create a matrix from position values
        double[][] positionData = new double[positions.size()][1];  // N x 1
        for (int index = 0; index < positions.size(); index++)
            positionData[index][0] = positions.get(index).getCurrentValue();

        // Multiple covariant matrix values by position vertical vector
        INDArray valueMatrix = Nd4j.create(positionData);
        INDArray intermediateMatrix = covariantMatrix.mul(valueMatrix);

        // Transpose the resulting matrix (intermediate) and then multiply by positions matrix
        INDArray intermediateTransposed = intermediateMatrix.transpose();
        INDArray sigmaSquaredMatrix = valueMatrix.mul(intermediateTransposed);

        // Summate all the values in the resulting matrix, which is sigma^2
        double sigmaSquared = sigmaSquaredMatrix.sumNumber().doubleValue();

        // Value-at-Risk is the square root of sigma^2
        return Math.sqrt(sigmaSquared);
    }

    public double calculateValueAtRisk(double confidence, double variance, double portfolioValue) {
        //Apache.Common: NormalDistribution
        //NormalDistribution.inverseCumulativeProbability
        NormalDistribution normalDist = new NormalDistribution();
        double cumulativeArea = (1.0 + confidence) / 2.0;
        double zScore = normalDist.inverseCumulativeProbability(confidence);

        return portfolioValue * variance * zScore;
    }

    public SimpleMatrix performCholeskyDecomposition(INDArray matrix) {
        //EJML: SimpleMatrix [square matrix, N x N]
        // SimpleMatrix.cholesky()
        // SimpleMatrix.getL()
        return SimpleMatrix.ones(1, 1);
    }

    public void performMonteCarloRandomTrials(INDArray covariants, int numTrials) {
        Random rand = new Random();
        SimpleMatrix choleskyL = performCholeskyDecomposition(covariants);

        for (int i = 0; i < numTrials; i++) {
            double[][] noiseArray = new double[][]  // Must be of same dimensions as covariants
            {
                {rand.nextGaussian() /*, rand.nextGaussian(), ... */}
            };

            SimpleMatrix noisyLMatrix = choleskyL.mult(new SimpleMatrix(noiseArray));

            //System.out.printf("Trial %d -> Asset 1 Return Shift: %.4f, Asset 2 Return Shift: %.4f%n",
            //                i + 1, correlatedNoise.get(0, 0), correlatedNoise.get(1, 0))
        }
    }
}