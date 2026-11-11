package com.kelly.varengine.services;

import com.kelly.varengine.models.OHLCVBar;
import com.kelly.varengine.models.PortfolioPosition;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

@Service
public class MatrixServices {

    public INDArray calculateWeightedLogReturnsPercentageChange(HashMap<PortfolioPosition, HashMap<LocalDate, OHLCVBar>> portfolioHistoricalData) {
        var allDates = new HashSet<LocalDate>();
        double[] pctChgAmount = {0.0};
        int[] pctChgIndex = {0};
        int[] lastPriceIndex = {0};
        List<Double> lastPrices = new ArrayList<>();
        int numPositions = portfolioHistoricalData.size();

        // Get all possible dates across all asset history data into a single set
        portfolioHistoricalData.forEach((position, barData) -> {
            allDates.addAll(barData.keySet());
            lastPrices.add(0.0);
        });

        INDArray pctChangeReturnsMatrix = Nd4j.zeros(allDates.size() - 1, numPositions);

        // Loop through all dates in the list, and compute the sum percent daily change for all assets on that day
        allDates.forEach((date) -> {
            pctChgAmount[0] = 0.0;
            lastPriceIndex[0] = 0;

            portfolioHistoricalData.entrySet().parallelStream().forEach(entry -> { //position, dateBars) -> {
                // We can only compute if the asset contains a valid bar for that date
                var position = entry.getKey();
                var dateBars = entry.getValue();

                if (dateBars.containsKey(date)) {
                    double price = dateBars.get(date).getAdjClosePrice();

                    if (pctChgIndex[0] > 0) {   // Must skip the first index
                        double lastPrice = lastPrices.get(lastPriceIndex[0]);
                        // Logarithmic return times weight and then to percentage
                        double change = lastPrice > 0 ? Math.log(price / lastPrice) : 0.0;
                        double wtChange = change * position.getWeight();
                        double pctChgReturn = Math.exp(wtChange) - 1.0;
                        pctChangeReturnsMatrix.put(pctChgIndex[0] - 1, lastPriceIndex[0], pctChgReturn);
                    }

                    lastPrices.set(lastPriceIndex[0], price);
                }
                lastPriceIndex[0]++;
            });

            pctChgIndex[0]++;
        });

        return pctChangeReturnsMatrix;
    }

    public double getPortfolioTotalValue(List<PortfolioPosition> positions) {
        double totalValue = 0.0;
        for (PortfolioPosition position : positions)
            totalValue += position.getCurrentValue();

        return totalValue;
    }
}
