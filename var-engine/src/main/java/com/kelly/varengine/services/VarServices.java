package com.kelly.varengine.services;

import java.util.ArrayList;
import java.util.List;

import com.kelly.varengine.models.PortfolioPosition;
import com.kelly.varengine.records.ParametricVarReportItem;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.dimensionalityreduction.PCA;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.ejml.simple.SimpleMatrix;
import org.ejml.dense.row.factory.DecompositionFactory_DDRM;
import org.ejml.interfaces.decomposition.CholeskyDecomposition_F64;
import org.ejml.data.DMatrixRMaj;
import org.springframework.stereotype.Service;

@Service
public class VarServices {

    public VarServices() {
        Nd4j.setDefaultDataTypes(DataType.DOUBLE, DataType.DOUBLE);
    }

    public INDArray createCovariantMatrix(INDArray logReturnsMatrix) {
        INDArray[] matrices = PCA.covarianceMatrix(logReturnsMatrix);
        return matrices.length > 0 ? matrices[0] : Nd4j.empty();
    }

    public double calculateVarianceSigma(INDArray covariantMatrix, List<PortfolioPosition> positions) {
        // Create a matrix from position values
        double[][] positionData = new double[positions.size()][1];  // N x 1
        for (int index = 0; index < positions.size(); index++)
            positionData[index][0] = positions.get(index).getCurrentValue();

        // Multiply covariant matrix values by position vertical vector
        INDArray valueMatrix = Nd4j.create(positionData);
        INDArray intermediateMatrix = valueMatrix.mul(covariantMatrix);

        // Transpose the resulting matrix (intermediate) and then multiply by positions matrix
        INDArray intermediateTransposed = intermediateMatrix.transpose();
        INDArray sigmaSquaredMatrix = intermediateTransposed.mmul(valueMatrix);

        double sigmaSquared = sigmaSquaredMatrix.sumNumber().doubleValue();

        // Variance is the square root of sigma^2
        return Math.sqrt(sigmaSquared);
    }

    public double calculateZScore(double confidence) {
        NormalDistribution normalDist = new NormalDistribution();
        double area = 1.0 - ((1.0 - confidence) / 2.0);
        return normalDist.inverseCumulativeProbability(area);
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

    public List<ParametricVarReportItem> calculateRiskDecomposition(INDArray covariantMatrix, List<PortfolioPosition> positions,
                                                                    double portfolioValue, double zScore) {
        int n = positions.size();
        var items = new ArrayList<ParametricVarReportItem>();

        // Extract weights vector (w) and position values (v)
        double[][] weightData = new double[n][1];
        double[][] valueData = new double[n][1];
        for (int i = 0; i < n; i++) {
            valueData[i][0] = positions.get(i).getCurrentValue();
            weightData[i][0] = valueData[i][0] / portfolioValue;
        }
        INDArray w = Nd4j.create(weightData);

        // Portfolio variance (sigma_p)
        double sigmaP = calculateVarianceSigma(covariantMatrix, positions) / portfolioValue;
        if (sigmaP == 0.0)
            return items;

        // Marginal Risk Contribution (Vector): (Sigma * w) / sigma_p
        INDArray sigmaW = covariantMatrix.mmul(w);
        INDArray marginalRisk = sigmaW.div(sigmaP);

        // Total Portfolio VaR
        //double totalVaR = calculateVaRValue(sigmaP * portfolioValue, zScore);

        for (int index = 0; index < n; index++) {
            double positionVal = positions.get(index).getCurrentValue();

            double assetStdDev = Math.sqrt(covariantMatrix.getDouble(index, index));
            double standaloneVaR = positionVal * assetStdDev * zScore;

            double mVaR = positionVal * marginalRisk.getDouble(index, 0) * zScore;

            double cVaR = mVaR * weightData[index][0]; //totalVaR * (weightData[index][0] * marginalRisk.getDouble(index, 0)) / sigmaP;
            double pctContribution = cVaR / portfolioValue;

            var item = new ParametricVarReportItem(
                positions.get(index).getSymbol(), weightData[index][0], standaloneVaR, mVaR, cVaR, pctContribution
            );

            items.add(item);
        }

        return items;
    }

    public double calculateVaRValue(double sigmaP, double zScore) {
        return sigmaP * zScore;
    }

    public String getPercentileString(double confidenceLevel) {
        double percentile = 100.0 * (1.0 - confidenceLevel);
        int percentCardinal = (int)percentile % 10;
        String suffix = switch (percentCardinal) {
            case 1 -> "st";
            case 2 -> "nd";
            case 3 -> "rd";
            default -> "th";
        };
        return String.format("%3.1f%s", percentile, suffix);
    }
}