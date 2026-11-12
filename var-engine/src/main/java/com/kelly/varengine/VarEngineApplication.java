package com.kelly.varengine;

import com.kelly.varengine.enums.AssetClassEnum;
import com.kelly.varengine.models.OHLCVBar;
import com.kelly.varengine.models.PortfolioPosition;
import com.kelly.varengine.records.*;
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
    private final SimulationServices simulationServices;

    public static void main(String[] args) {
        var context = SpringApplication.run(VarEngineApplication.class, args);
        var appInstance = context.getBean(VarEngineApplication.class);
        appInstance.RunTestPositions();
    }

    @Value("${app.monteCarlo.trialCount}")
    public int monteCarloTrialCount;

    @Value("${app.history.lookBackDays}")
    public int historyLookBackDays;

    @Value("${app.parametric.confidenceLevel}")
    public double confidenceLevel;

    @Value("${app.history.timeHorizonDays}")
    public int timeHorizonDays;

    public VarEngineApplication(MatrixServices matrixServices, VarServices varServices,
                                DataLoaderService dataLoaderService, SimulationServices simulationServices) {
        this.matrixServices = matrixServices;
        this.varServices = varServices;
        this.dataLoaderService = dataLoaderService;
        this.simulationServices = simulationServices;
    }

    public void RunTestPositions() {
        // Creates several positions & weights
        try {
            List<PortfolioPosition> positions = new ArrayList<>();

            positions.add(new PortfolioPosition(111111, AssetClassEnum.Equity, "AAPL", 2581000d, 1000, 0.50));
            positions.add(new PortfolioPosition(222222, AssetClassEnum.Derivative, "MSFT", 3528000d, 200, 0.20));
            positions.add(new PortfolioPosition(333333, AssetClassEnum.FixedIncomeBond, "GOOG", 5285300d, 500, 0.30));

            System.out.println();
            System.out.println("Getting history data and analyzing portfolio...");
            System.out.println();

            var response = RunAnalysis(positions);

            System.out.println("                                       Value-At-Risk Analysis Results");
            System.out.println("===========================================================================================================");
            System.out.println();
            System.out.println("Portfolio Inputs:");
            System.out.println();
            System.out.println("ASSET NAME                      CLASS                 VALUE $            QTY       WEIGHT");
            System.out.println("------------------------------------------------------------------------------------------------");
            for (var position : positions) {
                System.out.printf("%-30s  %-20s  $%,14.2f    %,6d    %4.1f%%",
                                position.getSymbol(), position.getAssetClass().toString(),
                                position.getCurrentValue(), position.getQuantity(),
                                position.getWeight() * 100.0
                );
                System.out.println();
            }

            System.out.println();
            System.out.println("Portfolio Total Results:");
            System.out.println("-----------------------------------------------------------------------------------------------");
            System.out.printf("* Portfolio Total Value:    $%,.2f", response.portfolio().portfolioValue());
            System.out.println();
            System.out.printf("* Total Value-At-Risk:      $%,.2f", response.varValue());
            System.out.println();
            System.out.printf("* V-A-R %% of total:         %,.1f%%", response.varPercent() * 100.0);
            System.out.println();
            System.out.printf("* Time Horizon (days):      %d", response.timeHorizonDays());
            System.out.println();
            System.out.printf("* History Lookback (days):  %d", response.historyLookbackDays());
            System.out.println();

            System.out.println();
            System.out.println("Parametric Value-at-Risk Results:");
            System.out.println();
            System.out.println("COMPONENT                     WEIGHT   STANDALONE V-A-R   MARGINAL V-A-R     COMPONENT V-A-R");
            System.out.println("-------------------------------------------------------------------------------------------------");
            for (var summary : response.parametricMethod().riskSummaries()) {
                System.out.printf("%-26s  %6.1f%%    $%,16.2f  $%,16.2f  $%,16.2f",
                        summary.component(), summary.weight() * 100.0, summary.stVaR(), summary.mVar(), summary.cVar());
                System.out.println();
            }

            System.out.println();
            System.out.println("Monte Carlo Simulation Value-at-Risk Results:");
            System.out.println();
            System.out.printf("# Simulations:             %d", response.monteCarloSimulation().numSimulations());
            System.out.println();
            System.out.println();
            System.out.println("CONFIDENCE LEVEL   PERCENTILE  PORTFOLIO OUTCOME    V-A-R METRIC          MAX LOSS %   CONDITIONAL V-A-R");
            System.out.println("----------------------------------------------------------------------------------------------------------------");
            for (var summary : response.monteCarloSimulation().riskSummaries()) {
                System.out.printf("%16s  %11s  $%,16.2f    $%,16.2f    %6.1f      $%,14.2f",
                        summary.confidenceLevel() * 100.0, summary.percentile(), summary.outcomeValue(), summary.varMetric(),
                        summary.maxLossPercent() * 100.0, summary.shortfallAmount());
                System.out.println();
            }

            System.out.println();
            System.out.println("===========================================================================================================");
        }
        catch (Exception ex) {
            System.out.println();
            System.out.printf("!! Exception occurred:  %s%n", ex.getMessage());
            System.out.println();
            ex.printStackTrace();
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

        // Computes the asset-weighted log returns as the daily price change percentage matrix
        var logReturnsMatrix = matrixServices.calculateWeightedLogReturnsPercentageChange(portfolioHistoryData);

        // Creates covariance matrix
        var covariantMatrix = varServices.createCovariantMatrix(logReturnsMatrix);

        // Compute the variance standard deviation (sigma)
        var variance = varServices.calculateVarianceSigma(covariantMatrix, positions);
        var zScore = varServices.calculateZScore(confidenceLevel);
        var totalVaR = varServices.calculateVaRValue(variance, zScore);
        var pctTotalVaR = totalVaR / portfolioValue;

        double[] assetWeights = positions.stream().mapToDouble(p -> p.getCurrentValue() / portfolioValue).toArray();

        // ---- Value-at-Risk Summary Data
        // Creates VaR amounts and percentages based on the confidence levels [computes Z-Score normal distributions]
        var parametricVaRValues = varServices.calculateRiskDecomposition(covariantMatrix, positions, portfolioValue, zScore);

        // ---- Monte Carlo Simulation setup
        // Creates Cholesky Decomposition matrix and uses the L matrix value
        var choleskyLMatrix = varServices.performCholeskyDecomposition(covariantMatrix);

        var monteCarloSimData = simulationServices.performMonteCarloSimulation(positions, logReturnsMatrix, choleskyLMatrix,
                monteCarloTrialCount, portfolioValue, variance);

        var parametricMethodData = new ParametricMethod(zScore, 0.0, covariantMatrix, parametricVaRValues);
        var portfolioData = new PortfolioData(portfolioValue, variance, assetWeights, logReturnsMatrix);

        return new AnalysisResponse(
            totalVaR, pctTotalVaR, timeHorizonDays, confidenceLevel, historyLookBackDays, parametricMethodData,
            portfolioData, monteCarloSimData
        );

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
    }
}
