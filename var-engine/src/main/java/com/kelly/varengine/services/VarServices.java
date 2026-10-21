package services;

import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.dimensionalityreduction.PCA;

import java.util.Random;
//import org.apache.commons.math3.distribution.NormalDistribution;
//import org.ejml.simple.SimpleMatrix;

public class VarServices {

    public INDArray createCovariantMatrix(INDArray logReturnsMatrix) {
        INDArray[] matrices = PCA.covarianceMatrix(logReturnsMatrix);
        return matrices.length > 0 ?
                matrices[0] :
                Nd4j.empty();
    }

    public double calculateVariantSigma(INDArray covariantMatrix) {
        return covariantMatrix.isEmpty() ?
                Double.NaN :
                covariantMatrix.varNumber().doubleValue();
    }

    public double calculateValueAtRisk(double confidence, double variance, double portfolioValue) {
        //Apache.Common: NormalDistribution
        //NormalDistribution.inverseCumulativeProbability
        double cumulativeArea = (1.0 + confidence) / 2.0;
        double zScore = 0.0; //inverseCumulativeProbability(confidence);
        double sigma = Math.sqrt(variance);

        return portfolioValue * sigma * zScore;
    }

//    public SimpleMatrix performCholeskyDecomposition(INDArray matrix) {
//        //EJML: SimpleMatrix [square matrix, N x N]
//        // SimpleMatrix.cholesky()
//        // SimpleMatrix.getL()
//    }

//    public void performMonteCarloRandomTrials(INDArray covariants, int numTrials) {
//        Random rand = new Random();
//        SimpleMatrix choleskyLMatrix = performCholeskyDecomposition(covariants);
//
//        for (int i = 0; i < numTrials; i++) {
//            double[][] noiseArray = new double[][]  // Must be of same dimensions as covariants
//            {
//                {rand.nextGaussian(), ...}
//            };
//
//            SimpleMatrix noisyLMatrix = choleskyLMatrix.mult(new SimpleMatrix(noiseArray));
//
//            //System.out.printf("Trial %d -> Asset 1 Return Shift: %.4f, Asset 2 Return Shift: %.4f%n",
//            //                i + 1, correlatedNoise.get(0, 0), correlatedNoise.get(1, 0))
//        }
//    }
}