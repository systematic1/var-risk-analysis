package com.kelly.varengine;

import com.kelly.varengine.enums.AssetClassEnum;
import com.kelly.varengine.models.OHLCVBar;
import com.kelly.varengine.models.PortfolioPosition;
import com.kelly.varengine.records.*;
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

    @Value("${app.monteCarlo.trialCount}")
    public int monteCarloTrialCount;

    @Value("${app.history.lookBackDays}")
    public int historyLookBackDays;

    @Value("${app.parametric.confidenceLevel}")
    public double confidenceLevel;

    @Value("${app.smoothing.lambda}")
    public double lambda;

    @Value("${app.history.timeHorizonDays}")
    public int timeHorizonDays;

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

            var response = RunAnalysis(positions);

            System.out.println("                            Value-At-Risk Analysis Results");
            System.out.println("-----------------------------------------------------------------------------------------");
            System.out.println();
            System.out.println("Portfolio Inputs:");
            System.out.println();
            System.out.printf("ASSET NAME                                CLASS            VALUE $      QTY     WEIGHT");
            for (var position : positions) {
                System.out.printf("%-40s  %-15s  $%10.2f  %6d  %4.1f%%",
                                position.getSymbol(), position.getAssetClass().toString(),
                                position.getCurrentValue(), position.getQuantity(),
                                position.getWeight() * 100.0
                );
                System.out.println();
            }

            System.out.println();
            System.out.println("Portfolio Total Results:");
            System.out.println();
            System.out.printf("* Total Value-At-Risk:      $%.2f", response.varValue());
            System.out.println();
            System.out.printf("* V-A-R %% of total:        %4.1f%%", response.varPercent() * 100.0);
            System.out.println();
            System.out.printf("* Time Horizon (days):      %d", response.timeHorizonDays());
            System.out.println();
            System.out.printf("* History Lookback (days):  %d", response.historyLookbackDays());
            System.out.println();

            System.out.println();
            System.out.println("Parametric Value-at-Risk Results:");
            System.out.println();
            System.out.println("COMPONENT                   WEIGHT     STANDALONE V-A-R   MARGINAL V-A-R     COMPONENT V-A-R");
            for (var summary : response.parametricMethod().riskSummaries()) {
                System.out.printf("%-26s  %8.1f%%  $%16.2f  $%16.2f  $16.2f",
                        summary.component(), summary.weight(), summary.stVaR(), summary.mVar(), summary.cVar());
                System.out.println();
            }

            System.out.println();
            System.out.println("Monte Carlo Simulation Value-at-Risk Results:");
            System.out.println();
            System.out.printf("# Simulations:             %d", response.monteCarloSimulation().numSimulations());
            System.out.println();
            System.out.println("CONFIDENCE LEVEL   PERCENTILE   PORTFOLIO OUTCOME    V-A-R METRIC     MAX LOSS %     CONDITIONAL V-A-R");
            for (var summary : response.monteCarloSimulation().riskSummaries()) {
                System.out.printf("%17s  %11s  $%15.2f    $%14.2f  %13.1f  $%14.2f",
                        summary.confidenceLevel() * 100.0, summary.percentile(), summary.outcomeValue(), summary.varMetric(),
                        summary.maxLossPercent() * 100.0, summary.shortfallAmount());
                System.out.println();
            }

            System.out.println();
            System.out.println("-----------------------------------------------------------------------------------------");
        }
        catch (Exception ex) {
            System.out.println();
            System.out.printf("!! Exception occurred:  %s%n", ex.getMessage());
        }
    }

    public AnalysisResponse RunAnalysis(List<PortfolioPosition> positions)
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
        var zScore = varServices.calculateZScore(confidenceLevel, variance, portfolioValue);
        var totalVaR = varServices.calculateVaRValue(variance, zScore, portfolioValue);
        var pctTotalVaR = totalVaR / portfolioValue;

        // ---- Value-at-Risk Summary Data
        // Creates VaR amounts and percentages based on the confidence levels [computes Z-Score normal distributions]
        var parametricVaRValues = varServices.calculateRiskDecomposition(covariantMatrix, positions, portfolioValue, zScore);

        // ---- Monte Carlo Simulation setup
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
        java.util.stream.IntStream.range(0, monteCarloTrialCount).parallel().forEach(index -> {
            trialPortfolioReturns[index] = varServices.performMonteCarloPortfolioTrial(choleskyLMatrix, assetWeights, assetMeans, assetStdDevs, 1);
        });

        // Sort trial outcomes for percentile extraction (ascending order of returns)
        Arrays.sort(trialPortfolioReturns);

        // Extract Key Percentiles (e.g., 10th, 5th, 1st percentiles)
        double medianOutcomeValue = portfolioValue * (1.0 + trialPortfolioReturns[monteCarloTrialCount / 2]);
        double[] simConfidenceLevels = { 0.90, 0.95, 0.99, 0.999 };

        int[] percentileTier = new int[simConfidenceLevels.length];
        double[] percentileOutcomes = new double[simConfidenceLevels.length];
        double[] shortfallValues = new double[simConfidenceLevels.length];
        double[] shortfallProbabilities = new double[simConfidenceLevels.length];

        for (var index = 0; index < simConfidenceLevels.length; index++) {
            percentileTier[index] = (int) (monteCarloTrialCount * simConfidenceLevels[index]);
            percentileOutcomes[index] = portfolioValue * (1.0 + trialPortfolioReturns[index]);

            // Compute Expected Shortfall (Conditional VaR) at 95% confidence (mean loss beyond 5th percentile)
            double sumTailLosses = 0.0;
            for (int trialIndex = 0; trialIndex < percentileTier[index]; trialIndex++)
                sumTailLosses += (portfolioValue * trialPortfolioReturns[trialIndex]);

            // Success Probability (% of runs where portfolio did not incur a catastrophic loss > 50%)
            long successfulRuns = Arrays.stream(trialPortfolioReturns).filter(rtn -> rtn > -0.50).count();
            double probabilitySuccess = (double) successfulRuns / monteCarloTrialCount;

            shortfallValues[index] = sumTailLosses;
            shortfallProbabilities[index] = 1.0 - probabilitySuccess;
        }

         /*
        int idx10 = (int) (monteCarloTrialCount * 0.10);
        int idx5  = (int) (monteCarloTrialCount * 0.05);
        int idx1  = (int) (monteCarloTrialCount * 0.01);
        int idx01 = (int) (monteCarloTrialCount * 0.001);

        double p90OutcomeValue = portfolioValue * (1.0 + trialPortfolioReturns[(int) (monteCarloTrialCount * 0.90)]);
        double p10OutcomeValue = portfolioValue * (1.0 + trialPortfolioReturns[idx10]);
        double p5OutcomeValue  = portfolioValue * (1.0 + trialPortfolioReturns[idx5]);
        double p1OutcomeValue  = portfolioValue * (1.0 + trialPortfolioReturns[idx1]);

        // Compute Expected Shortfall (Conditional VaR) at 95% confidence (mean loss beyond 5th percentile)
        double sumTailLosses = 0.0;
        for (int index = 0; index < idx5; index++)
            sumTailLosses += (portfolioValue * trialPortfolioReturns[index]);

        // Success Probability (% of runs where portfolio did not incur a catastrophic loss > 50%)
        long successfulRuns = Arrays.stream(trialPortfolioReturns).filter(rtn -> rtn > -0.50).count();
        double probabilitySuccess = (double) successfulRuns / monteCarloTrialCount;
        double shortfallRisk = 1.0 - probabilitySuccess;
        */

        var simConfidenceVaRValues = new ConfidenceVaRReportItem[simConfidenceLevels.length];
        java.util.stream.IntStream.range(0, simConfidenceLevels.length).parallel().forEach(level -> {
            double zScoreP = varServices.calculateZScore(simConfidenceLevels[level], variance, portfolioValue);
            double varValueP = varServices.calculateVaRValue(variance, zScoreP, portfolioValue);
            String percentileString = varServices.getPercentileString(simConfidenceLevels[level]);

            var simVaRValues = new ConfidenceVaRReportItem(
                simConfidenceLevels[level],
                percentileString,
                percentileOutcomes[level],
                varValueP,
                shortfallProbabilities[level],
                shortfallValues[level]
            );
            simConfidenceVaRValues[level] = simVaRValues;
        });

        var parametricMethodData = new ParametricMethod(zScore, 0.0, covariantMatrix, parametricVaRValues);
        var portfolioData = new PortfolioData(portfolioValue, variance, assetWeights, logReturnsMatrix, dynWeightReturnsMatrix);
        var monteCarloSimData = new MonteCarloSimulation(monteCarloTrialCount, simConfidenceLevels, choleskyLMatrix, assetMeans,
                assetStdDevs, medianOutcomeValue, trialPortfolioReturns, simConfidenceVaRValues);
        var response = new AnalysisResponse(
            totalVaR, pctTotalVaR, timeHorizonDays, confidenceLevel, historyLookBackDays, parametricMethodData,
            portfolioData, monteCarloSimData
        );

        return response;

        // Produces the following output:
        //  Downside loss limit
        //      - Absolute VaR (maximum loss $)
        //      - Percentage VaR (% of portfolio value)       { var * 100 / portfolioValue }
        // --> confidenceVaRValues

        //  Key Parametric Outcome tables  <example>
        //      ===> Headers:  Asset/Risk Component      Position Weight    Standalone VaR   Marginal VaR   Component VaR
        //                     US Equities (S&P 500)     50%                $165,000         $125,000       $62,500
        //                     Global Bonds (Agg)        50%                $45,000          $15,000        $6,000
        //                     Undiversified Total       100%               $210,000         $140,000       $68,500
        //                     Diversification Benefit   --                 -$130,000        --             --
        //                     Portfolio Parametric VaR  100%               $80,000          --             $80,000

        //  Key Simulation Tables at Multiple Confidence Level  <example>
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
        //      Component VaR (CVaR): Aboslute amount of total VaR attributed directly to a specific asset or sector = asset_weight * mVAR

        // -------- Overall data structure
        // double varValue   - var at confidence level
        // double varPercent  - var as % of total portfolio
        // int timeHorizonDays
        // double confidenceLevel
        // int historyLookbackDays
        // parametricMethod: {
        //      parameters: {
        //          double zScore
        //          double assumedMean   - the expected return (mu) often set to 0 for short-term daily horizons
        //          INDArray covariantMatrix
        //      }
        //      results: {
        //          ParametricVarReportItem[] riskSummaries
        //      }
        // }
        // portfolio: {
        //      double portfolioValue   - total market value
        //      double portfolioVolatility   - annualized or scaled std dev of portfolio (sigma = SQRT(w<transposed> * SUM(w)))
        //      double[] weights  - allocation weight values of each asset
        //      INDArray logarithmicReturns
        //      INDArray dynamicallyWeightedReturns
        // }
        // monteCarloSimulation: {
        //      parameters: {
        //          int numSimulations
        //          double[] simulationConfidenceLevels
        //      }
        //      results: {
        //          SimpleMatrix choleskyLMatrix
        //          double[] assetMeanValues
        //          double[] assetStdDevValues
        //          double medianOutcomeValue
        //          double trialVectorData
        //          ConfidenceVaRReportItem[] riskSummaries
        //      }
        // }
    }
}
