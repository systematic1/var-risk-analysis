package com.kelly.varengine;

import com.kelly.varengine.enums.AssetClassEnum;
import com.kelly.varengine.models.OHLCVBar;
import com.kelly.varengine.models.PortfolioPosition;
import org.springframework.boot.CommandLineRunner;
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

    @Value("${app.smoothing.lambda}")
    public double lambda;

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

            RunAnalysis(positions);
        }
        catch (Exception ex) {
            System.out.println();
            System.out.printf("!! Exception occurred:  %s%n", ex.getMessage());
        }
    }

    public void RunAnalysis(List<PortfolioPosition> positions)
        throws URISyntaxException, IOException, InterruptedException
    {
        int points = 0;
        HashMap<PortfolioPosition, HashMap<LocalDate, OHLCVBar>> portfolioHistoryData = new HashMap<>();

        // Loads historical backtest data via Massive
        for (var positionItem : positions) {
            LocalDate startDate = LocalDate.now().minusDays(historyLookBackDays);
            HashMap<LocalDate, OHLCVBar> historicalQuotes =
                dataLoaderService.getHistoricalQuotes(positionItem.getSymbol(), startDate, LocalDate.now());

            portfolioHistoryData.put(positionItem, historicalQuotes);
            points = Math.max(points, historicalQuotes.size());

            // Fills in gaps
            int correctedCount = dataLoaderService.interpolateMissingData(historicalQuotes, startDate);

            dataLoaderService.waitDelayTime()
                    .thenAccept(result -> {})
                    .exceptionally(exception -> null);
        }

        // Calculate the sum of the portfolio positions
        var portfolioValue = positions.stream()
                .mapToDouble(PortfolioPosition::getCurrentValue)
                .sum();

        // Computes the logarithmic returns matrix
        var logReturnsMatrix = matrixServices.createLogarithmicReturns(portfolioHistoryData, points);

        // Computes the dynamic weight of the log returns given a smoothing lambda
        var dynWeightReturnsMatrix = matrixServices.computeDynamicWeights(logReturnsMatrix, lambda); //positions

        // Creates covariance matrix
        var covariantMatrix = varServices.createCovariantMatrix(dynWeightReturnsMatrix);

        // Compute the variance standard deviation (sigma)
        var variance = varServices.calculateVarianceSigma(covariantMatrix, positions);

        // Creates VaR amount based on 2 confidence levels and computed Z-Score normal distributions
        var varConfidence1 = varServices.calculateValueAtRisk(confidenceLevel1, variance, portfolioValue);
        var varConfidence2 = varServices.calculateValueAtRisk(confidenceLevel2, variance, portfolioValue);

        // Monte Carlo Simulation setup
        // Creates Cholesky Decomposition matrix and uses the L matrix value
        var choleskyLMatrix = varServices.performCholeskyDecomposition(covariantMatrix);

        // Calculate the mean values of each asset's historical value
        HashMap<String, Double> assetMeanValues = new HashMap<>();
        HashMap<String, Double> assetStdDevValues = new HashMap<>();

        portfolioHistoryData.forEach((position, histData) -> {
            double[] total = { 0.0, 0.0 };
            histData.forEach((localDate, bar) -> total[0] += bar.getAdjClosePrice());
            if (!histData.isEmpty()) {
                double mean = total[0] / histData.size();
                assetMeanValues.put(position.getSymbol(), mean);

                histData.forEach((localDate, bar) -> total[1] += Math.pow(bar.getAdjClosePrice() - mean, 2.0));
                double stdDev = Math.sqrt(total[1] / histData.size());
                assetStdDevValues.put(position.getSymbol(), stdDev);
            }
        });

        // #Run parallel Monte Carlo trials
        double[] assetWeights = positions.stream().mapToDouble(p -> p.getCurrentValue() / portfolioValue).toArray();
        double[] assetMeans = positions.stream().mapToDouble(p -> assetMeanValues.get(p.getSymbol())).toArray();
        double[] assetStdDevs = positions.stream().mapToDouble(p -> assetStdDevValues.get(p.getSymbol())).toArray();

        double[] trialPortfolioReturns = new double[monteCarloTrialCount];
        java.util.stream.IntStream.range(0, monteCarloTrialCount).parallel().forEach(i -> {
            trialPortfolioReturns[i] = varServices.performMonteCarloPortfolioTrial(choleskyLMatrix, assetWeights, assetMeans, assetStdDevs, 1);
        });

        // Sort trial outcomes for percentile extraction (ascending order of returns)
        Arrays.sort(trialPortfolioReturns);

        // Extract Key Percentiles (e.g., 10th, 5th, 1st percentiles)
        int idx10 = (int) (monteCarloTrialCount * 0.10);
        int idx5  = (int) (monteCarloTrialCount * 0.05);
        int idx1  = (int) (monteCarloTrialCount * 0.01);
        int idx01 = (int) (monteCarloTrialCount * 0.001);

        double p90OutcomeValue = portfolioValue * (1.0 + trialPortfolioReturns[(int) (monteCarloTrialCount * 0.90)]);
        double medianOutcomeValue = portfolioValue * (1.0 + trialPortfolioReturns[monteCarloTrialCount / 2]);
        double p10OutcomeValue = portfolioValue * (1.0 + trialPortfolioReturns[idx10]);
        double p5OutcomeValue  = portfolioValue * (1.0 + trialPortfolioReturns[idx5]);
        double p1OutcomeValue  = portfolioValue * (1.0 + trialPortfolioReturns[idx1]);

        // Compute Expected Shortfall (Conditional VaR) at 95% confidence (mean loss beyond 5th percentile)
        double sumTailLosses = 0.0;
        for (int i = 0; i < idx5; i++)
            sumTailLosses += (portfolioValue * trialPortfolioReturns[i]);

        double conditionalVaR95 = idx5 > 0 ? - (sumTailLosses / idx5) : 0.0;

        // Success Probability (% of runs where portfolio did not incur a catastrophic loss > 50%)
        long successfulRuns = Arrays.stream(trialPortfolioReturns).filter(r -> r > -0.50).count();
        double probabilitySuccess = (double) successfulRuns / monteCarloTrialCount;
        double shortfallRisk = 1.0 - probabilitySuccess;

        // Produces the following output:
        //  Downside loss limit
        //      - Absolute VaR (maximum loss $)               { varConfidence1, varConfidence2 }
        //      - Percentage VaR (% of portfolio value)       { var * 100 / portfolioValue }

        double pctVarConfidence1 = Math.round(varConfidence1 * 10000.0 / portfolioValue) * 100.0;
        double pctVarConfidence2 = Math.round(varConfidence2 * 10000.0 / portfolioValue) * 100.0;

        //  Max loss projections at given confidence levels
        //      - VaR Distribution chart (bell curve) per confidence level
        //          - Safe zone: represents area of bell curve within expected normal losses (at 95% or 99% confidence)
        //          - Tail area: segment area (1% or 5%) representing chosen significance level

        //  Key Parametric Outcome tables  <example>
        //      ===> Headers:  Asset/Risk Component      Position Weight    Standalone VaR   Marginal VaR   Component VaR
        //                     US Equities (S&P 500)     50%                $165,000         $125,000       $62,500
        //                     Global Bonds (Agg)        50%                $45,000          $15,000        $6,000
        //                     Undiversified Total       100%               $210,000         $140,000       $68,500
        //                     Diversification Benefit   --                 -$130,000        --             --
        //                     Portfolio Parametric VaR  100%               $80,000          --             $80,000

        record AssetPositionRiskOutcomeVaR(String positionName, double weight, double var, double mVar, double cVar) { }
        record MCSimulationByConfidence(double confidenceLevel, String percentile, double portfolioValueOutcome,
                                        double varMetric, double varMaxLossPct, double conditionalVarShortfall) { }
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
