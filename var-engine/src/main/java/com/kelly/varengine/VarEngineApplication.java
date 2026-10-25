package com.kelly.varengine;

import com.kelly.varengine.enums.AssetClassEnum;
import com.kelly.varengine.models.OHLCVSeries;
import com.kelly.varengine.models.PortfolioPosition;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.beans.factory.annotation.*;
import com.kelly.varengine.services.*;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.util.*;

@SpringBootApplication
public class VarEngineApplication {

    private final MatrixServices matrixServices;
    private final VarServices varServices;
    private final DataLoaderService dataLoaderService;

    public static void main(String[] args) {
        SpringApplication.run(VarEngineApplication.class, args);
    }

    @Value("${app.confidenceLevel.1}")
    public double confidenceLevel1;

    @Value("${app.confidenceLevel.2}")
    public double confidenceLevel2;

    @Value("${app.monteCarlo.trialCount}")
    public int monteCarloTrialCount;

    @Value("${app.history.lookBackDays}")
    public int historyLookBackDays;

    public VarEngineApplication(MatrixServices matrixServices, VarServices varServices,
                                DataLoaderService dataLoaderService) {
        this.matrixServices = matrixServices;
        this.varServices = varServices;
        this.dataLoaderService = dataLoaderService;
    }

    public void RunTestPositions() {
        // Creates several positions & weights
        try {
            List<PortfolioPosition> positions = new ArrayList<>();

            positions.add(new PortfolioPosition(111111, AssetClassEnum.Equity, "AAPL", 2581000d, 1000, 0.25));
            positions.add(new PortfolioPosition(222222, AssetClassEnum.Derivative, "MSFT", 352800d, 20, 0.50));
            positions.add(new PortfolioPosition(333333, AssetClassEnum.FixedIncomeBond, "USB21", 5285300d, 500, 0.25));

            Run(positions);
        }
        catch (Exception ex) {
            System.out.println();
            System.out.printf("!! Exception occurred:  %s%n", ex.getMessage());
        }
    }

    public void Run(List<PortfolioPosition> positions)
        throws URISyntaxException, IOException, InterruptedException
    {
        int points = 0;
        HashMap<PortfolioPosition, HashMap<LocalDate, OHLCVSeries>> portfolioHistoryData = new HashMap<>();

        // Loads historical backtest data via Massive
        for (var positionItem : positions) {
            LocalDate startDate = LocalDate.now().minusDays(historyLookBackDays);
            HashMap<LocalDate, OHLCVSeries> historicalQuotes =
                dataLoaderService.getHistoricalQuotes(positionItem.getSymbol(), startDate, LocalDate.now());

            portfolioHistoryData.put(positionItem, historicalQuotes);
            points = Math.max(points, historicalQuotes.size());
        }

        // Fills in gaps
            // When not all positions have enough history data to satisfy the look-back time frame:
            /*
                You start with the longest available data and get the covariance matrix of that.
                For the next set of available data, you regress them against the data that is available
                longer and use the regression coefficients to expand the covariance matrix (and means).
                You then iterate through each unique group of data, steadily shrinking the amount of data
                used in each regression.
            */

            // When there are a few dates missing from one asset data history stream - interpolate missing data points

        // Computes the logarithmic returns matrix
        var logReturnsMatrix = matrixServices.createLogarithmicReturns(portfolioHistoryData, points);

        // Computes the dynamic weight of the log returns
        var dynWeightReturnsMatrix = matrixServices.createDynamicWeights(positions);

        // Creates covariance matrix
        var covariantMatrix = varServices.createCovariantMatrix(logReturnsMatrix);

        // Creates a Z-Score and VaR amount based on 2 confidence levels
        var zScoreConfidence1 = 0;
        var zScoreConfidence2 = 0;

        var varConfidence1 = 0.0;
        var varConfidence2 = 0.0;

        // Creates Cholesky Decomposition matrix and uses the L matrix value
        // Runs the configured number of random trials

        // Produces the following output:
        //  Downside loss limit
        //      - Absolute VaR (maximum loss $)
        //      - Percentage VaR (% of portfolio value)
        //      - Time horizon (holding period in days)
        //      - Confidence ("#-Day VaR at ##% Confidence")
        //  Max loss projections at given confidence levels
        //      - VaR Distribution chart (bell curve) per confidence level
        //          - Safe zone: represents area of bell curve within expected normal losses (at 95% or 99% confidence)
        //          - Tail area: segment area (1% or 5%) representing chosen significance level
        //  KPIs / Summary table result
        //      Probablility of Success = %     (percentage of simulated runs where portfolio never hit zero)
        //      Median Ending Value = $         (P50 outcome - half the runs ended higher, half ended lower)
        //      Shortfall Risk = %              (inverse of success: 100% - Prob of Success)
        //      Value at Risk = $               (Maximum expected loss at specific confidence interval over a given timeframe)
        //  Key Parametric Outcome tables  <example>
        //      ===> Headers:  Asset/Risk Component      Position Weight    Standalone VaR   Marginal VaR   Component VaR
        //                     US Equities (S&P 500)     50%                $165,000         $125,000       $62,500
        //                     Global Bonds (Agg)        50%                $45,000          $15,000        $6,000
        //                     Undiversified Total       100%               $210,000         $140,000       $68,500
        //                     Diversification Benefit   --                 -$130,000        --             --
        //                     Portfolio Parametric VaR  100%               $80,000          --             $80,000
        //      ===> Headers:  Confidence Level      Simulated %       Portfolio Value       VaR Metric             Conditional VaR
        //                                           Percentile        Outcome               (Max Loss)             Expected Shortfall
        //                     90.0%                 10th percentile   $9,150,000            $850,000 (8.5%)        $1,420,000
        //                     95.0%                 5th percentile    $8,600,000            $1,400,000 (14.0%)     $1,950,000
        //                     99.0%                 1st percentile    $7,200,000            $2,800,000 (28.0%)     $3,410,000
        //                     99.9%                 0.1 percentile    $5,100,000            $4,900,000 (49.0%)     $5,250,000
        //
        //  Orders MC outcomes from best-case (P90) to median (P50) to worst (P10) tail outcomes  <example>
        //      Diversification Benefit: $ or % reduction in risk by mixing uncorrelated assets
        //      Marginal VaR (MVaR): Change in total portfolio VaR resulting from incremental 1% increase in asset's position
        //      Component VaR (CVaR): Aboslute amount of total VaR attributed directly to a specific asset or sector
        //  [Opt] Extreme shock modeling (market crashes volatility spikes, broken correlations)

    }
}
