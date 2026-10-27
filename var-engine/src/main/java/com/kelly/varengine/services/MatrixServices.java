package com.kelly.varengine.services;

import com.kelly.varengine.models.OHLCVBar;
import com.kelly.varengine.models.PortfolioPosition;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
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

        INDArray logReturns = Nd4j.zeros(positionData.size(), points - 1);

        // TO DO: Use Vectorization instead of loops
        positionData.forEach((position, stockBars) -> {
            seriesIndex[0] = 0;
            stockBars.forEach((date, series) -> {
                // R<t> = ln(Price<t> / Price<t-1>)
                double rtnValue = Math.log(series.getAdjClosePrice() / prevValue[0]);
                logReturns.put(positionIndex[0], seriesIndex[0], rtnValue);
                prevValue[0] = rtnValue;
                seriesIndex[0]++;
            });

            positionIndex[0]++;
        });

        return logReturns;
    }

    // W<i> = (Quantity<i>  X Price<i>) / (Total Portfolio Value)
    public INDArray createDynamicWeights(List<PortfolioPosition> positions) {
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
    }

    public double getPortfolioTotalValue(List<PortfolioPosition> positions) {
        double totalValue = 0.0;
        for (PortfolioPosition position : positions)
            totalValue += position.getCurrentValue();

        return totalValue;
    }
}
