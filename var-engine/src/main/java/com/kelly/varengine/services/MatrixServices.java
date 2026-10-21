package services;

import models.PortfolioPosition;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Service
public class MatrixServices {

    //private static final Logger log = LoggerFactory.getLogger(MatrixServices.class);

    // R<t> = ln (P<t>/P<t-1>)
    public INDArray createLogarithmicReturns(INDArray prices)
    {
        double rtnValue, prevValue = 1.0;
        var logReturns = Nd4j.zeros(prices.rows(), prices.columns());

        // TO DO: Use Vectorization instead of loops
        for (int row = 0; row < prices.rows(); row++)
        {
            for (int col = 0; col < prices.columns(); col++)
            {
                rtnValue = Math.log(prices.getDouble(row, col) / prevValue);
                logReturns.put(row, col, rtnValue);
                prevValue = rtnValue;
            }
        }

        return logReturns;
    }

    // W<i> = (Quantity<i>  X Price<i>) / (Total Portfolio Value)
    public INDArray createDynamicWeights(List<PortfolioPosition> positions, double totalValue)
    {
        var dynWeights = Nd4j.zeros(positions.size(), 1);
        int qty;
        double wValue, price;

        if (totalValue != 0.0)
        {
            for (int index = 0; index < positions.size(); index++)
            {
                qty = positions.get(index).getQuantity();
                price = positions.get(index).getCurrentValue();
                wValue = (qty * price) / totalValue;

                dynWeights.put(index, 0, wValue);
            }
        }

        return dynWeights;
    }

}
