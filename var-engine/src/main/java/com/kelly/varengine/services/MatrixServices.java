package com.kelly.varengine.services;

import com.kelly.varengine.models.OHLCVBar;
import com.kelly.varengine.models.PortfolioPosition;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;

@Service
public class MatrixServices {

    //private static final Logger log = LoggerFactory.getLogger(MatrixServices.class);

    public INDArray createLogarithmicReturns(HashMap<PortfolioPosition, HashMap<LocalDate, OHLCVBar>> positionData, int points) {
        double[] prevValue = {1.0};  //Java hack - tracking variables used inside & outside a lambda
        int[] positionIndex = {0};
        int[] seriesIndex = {0};

        INDArray logReturns = Nd4j.zeros(points - 1, positionData.size());

        // TO DO: Use Vectorization instead of loops
        positionData.forEach((position, stockBars) -> {
            seriesIndex[0] = 0;
            stockBars.forEach((date, series) -> {
                // R<t> = ln(Price<t> / Price<t-1>)
                double rtnValue = Math.log(series.getAdjClosePrice() / prevValue[0]);
                logReturns.put(seriesIndex[0], positionIndex[0], rtnValue);
                prevValue[0] = series.getAdjClosePrice();
                seriesIndex[0]++;
            });

            positionIndex[0]++;
        });

        return logReturns;
    }

    /** From AI:
     * Computes dynamic inverse-volatility weights from a matrix of log returns using EWMA variance.
     *
     * @param logReturns 2D INDArray where rows = time steps (T), columns = assets (N)
     * @param lambda     The EWMA smoothing/decay factor (typically 0.94 for daily data)
     * @return 2D INDArray of the same shape containing the dynamic weights for each asset at each time step
     */
    public INDArray computeDynamicWeights(INDArray logReturns, double lambda) {
        long timeSteps = logReturns.size(0);
        long numAssets = logReturns.size(1);

        // Initialize matrices to hold intermediate variance states and final weights
        INDArray varianceMatrix = Nd4j.zeros(timeSteps, numAssets);
        INDArray weightMatrix = Nd4j.zeros(timeSteps, numAssets);

        // Pre-calculate squared log returns: r_t^2
        INDArray squaredReturns = Transforms.pow(logReturns, 2);

        // Initialize the first time step variance using the first squared return
        varianceMatrix.putRow(0, squaredReturns.getRow(0));

        // Equal weights for the first step as a baseline fallback
        INDArray initialWeights = Nd4j.ones(1, numAssets).divi(numAssets);
        weightMatrix.putRow(0, initialWeights);

        // Loop through time steps to compute EWMA variance and inverse weights dynamically
        for (int t = 1; t < timeSteps; t++) {
            // Formula: σ²_t = λ * σ²_{t-1} + (1 - λ) * r_{t-1}²
            INDArray pastVariance = varianceMatrix.getRow(t - 1);
            INDArray pastSquaredReturn = squaredReturns.getRow(t - 1);

            INDArray currentVariance = pastVariance.mul(lambda)
                    .add(pastSquaredReturn.mul(1 - lambda));

            varianceMatrix.putRow(t, currentVariance);

            // Calculate Volatility: σ_t = sqrt(σ²_t)
            // Add a micro epsilon (1e-8) to prevent any potential division-by-zero errors
            INDArray currentVol = Transforms.sqrt(currentVariance).add(1e-8);

            // Compute inverse volatility: 1 / σ_t
            INDArray invVol = Nd4j.ones(1, numAssets).divi(currentVol);

            // Normalize inverse vols to sum to 1.0 across assets to create the weights
            double rowSum = invVol.sumNumber().doubleValue();
            INDArray normalizedWeights = invVol.div(rowSum);

            weightMatrix.putRow(t, normalizedWeights);
        }

        return weightMatrix;
    }

    // W<i> = (Quantity<i>  X Price<i>) / (Total Portfolio Value)
    /*public INDArray createDynamicWeights(List<PortfolioPosition> positions) {
        double totalValue = getPortfolioTotalValue(positions);

        var dynWeights = Nd4j.zeros(positions.size(), 1);
        int qty;
        double wValue, price;

        if (totalValue != 0.0) {
            for (int index = 0; index < positions.size(); index++) {
                qty = positions.get(index).getQuantity();
                price = positions.get(index).getCurrentValue();
                wValue = (qty * price) / totalValue;

                dynWeights.put(index, 0, wValue);
            }
        }

        return dynWeights;
    }*/

    public double getPortfolioTotalValue(List<PortfolioPosition> positions) {
        double totalValue = 0.0;
        for (PortfolioPosition position : positions)
            totalValue += position.getCurrentValue();

        return totalValue;
    }
}
