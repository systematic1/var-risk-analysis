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
        INDArray sigmaSquaredMatrix = valueMatrix.mul(intermediateTransposed);

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

        // Return the "L" matrix
        return SimpleMatrix.wrap(cholesky.getT(null));
    }

    public double performMonteCarloRandomTrial(SimpleMatrix choleskyL) {
        // Create a random noise matrix the same size as the cholesky L matrix
        Random rand = new Random();
        SimpleMatrix randomMatrix = new SimpleMatrix(choleskyL.getNumRows(), choleskyL.getNumCols());

        for (int row = 0; row < randomMatrix.getNumRows(); row++) {
            for (int col = 0; col < randomMatrix.getNumCols(); col++) {
                randomMatrix.set(row, col, rand.nextGaussian());
            }
        }

        // Multiply the L matrix and the random noise matrix
        SimpleMatrix multMatrix = choleskyL.mult(new SimpleMatrix(randomMatrix));

        // Return the sum of all the cross matrix values
        return multMatrix.elementSum();
    }
}