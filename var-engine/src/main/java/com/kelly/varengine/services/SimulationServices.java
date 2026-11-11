package com.kelly.varengine.services;

import com.kelly.varengine.models.OHLCVBar;
import com.kelly.varengine.models.PortfolioPosition;
import com.kelly.varengine.records.ConfidenceVaRReportItem;
import com.kelly.varengine.records.MonteCarloSimulation;
import org.ejml.simple.SimpleMatrix;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

@Service
public class SimulationServices {
    private final VarServices varServices;

    public SimulationServices(VarServices varServices) { this.varServices = varServices; }

    public MonteCarloSimulation performMonteCarloSimulation(
            List<PortfolioPosition> positions, INDArray weightedPctChangeReturns,
            SimpleMatrix choleskyLMatrix, int maxTrialCount, double portfolioValue, double variance) {

        // Calculate the mean values of each asset's historical value
        HashMap<String, Double> assetMeanValues = new HashMap<>();
        HashMap<String, Double> assetStdDevValues = new HashMap<>();

        for (int index = 0; index < positions.size(); index++) {
            PortfolioPosition position = positions.get(index);
            var positionReturns = weightedPctChangeReturns.getColumn(index);
            var mean = positionReturns.meanNumber().doubleValue();
            var stdDev = positionReturns.stdNumber().doubleValue();
            assetMeanValues.put(position.getSymbol(), mean);
            assetStdDevValues.put(position.getSymbol(), stdDev);
        }

        // #Run parallel Monte Carlo trials
        double[] assetWeights = positions.stream().mapToDouble(p -> p.getCurrentValue() / portfolioValue).toArray();
        double[] assetMeans = positions.stream().mapToDouble(p -> assetMeanValues.get(p.getSymbol())).toArray();
        double[] assetStdDevs = positions.stream().mapToDouble(p -> assetStdDevValues.get(p.getSymbol())).toArray();

        double[] trialPortfolioReturns = new double[maxTrialCount];
        java.util.stream.IntStream.range(0, maxTrialCount).parallel().forEach(index -> {
            trialPortfolioReturns[index] = performSimulationTrial(choleskyLMatrix, assetWeights, assetMeans, assetStdDevs, 1);
        });

        // Sort trial outcomes for percentile extraction (ascending order of returns)
        Arrays.sort(trialPortfolioReturns);

        // Extract Key Percentiles (e.g., 10th, 5th, 1st percentiles)
        double medianOutcomeValue = portfolioValue * (1.0 + trialPortfolioReturns[maxTrialCount / 2]);
        double[] simConfidenceLevels = { 0.90, 0.95, 0.99, 0.999 };

        int[] percentileTier = new int[simConfidenceLevels.length];
        double[] percentileOutcomes = new double[simConfidenceLevels.length];
        double[] shortfallValues = new double[simConfidenceLevels.length];
        double[] shortfallProbabilities = new double[simConfidenceLevels.length];

        for (var index = 0; index < simConfidenceLevels.length; index++) {
            percentileTier[index] = (int) (maxTrialCount * simConfidenceLevels[index]);
            percentileOutcomes[index] = portfolioValue * (1.0 + trialPortfolioReturns[index]);

            // Compute Expected Shortfall (Conditional VaR) at 95% confidence (mean loss beyond 5th percentile)
            double sumTailLosses = 0.0;
            for (int trialIndex = 0; trialIndex < percentileTier[index]; trialIndex++)
                sumTailLosses += (portfolioValue * trialPortfolioReturns[trialIndex]);

            // Success Probability (% of runs where portfolio did not incur a catastrophic loss > 50%)
            long successfulRuns = Arrays.stream(trialPortfolioReturns).filter(rtn -> rtn > -0.50).count();
            double probabilitySuccess = (double) successfulRuns / maxTrialCount;

            shortfallValues[index] = Math.abs(sumTailLosses);
            shortfallProbabilities[index] = 1.0 - probabilitySuccess;
        }

        var simConfidenceVaRValues = new ConfidenceVaRReportItem[simConfidenceLevels.length];
        java.util.stream.IntStream.range(0, simConfidenceLevels.length).parallel().forEach(level -> {
            double zScoreP = varServices.calculateZScore(simConfidenceLevels[level]);
            double varValueP = varServices.calculateVaRValue(variance, zScoreP);
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

        return new MonteCarloSimulation(maxTrialCount, simConfidenceLevels, choleskyLMatrix, assetMeans,
                assetStdDevs, medianOutcomeValue, trialPortfolioReturns, simConfidenceVaRValues);
    }

    public double performSimulationTrial(SimpleMatrix choleskyL, double[] assetWeights, double[] assetMeans,
                                                  double[] assetStdDevs, int timeHorizonDays) {
        int numAssets = assetWeights.length;
        Random rand = new Random();

        // 1. Generate independent standard normal vector Z (N x 1)
        SimpleMatrix zNormals = new SimpleMatrix(numAssets, 1);
        for (int index = 0; index < numAssets; index++)
            zNormals.set(index, 0, rand.nextGaussian());

        SimpleMatrix correlatedShocks = choleskyL.mult(zNormals);

        double portfolioReturn = 0.0;
        double dt = Math.sqrt(timeHorizonDays); // scaling by horizon time steps

        for (int index = 0; index < numAssets; index++) {
            double drift = (assetMeans[index] - 0.5 * Math.pow(assetStdDevs[index], 2)) * timeHorizonDays;
            double shock = correlatedShocks.get(index, 0) * dt;
            double assetReturn = Math.exp(drift + shock) - 1.0;

            portfolioReturn += assetWeights[index] * assetReturn;
        }

        return portfolioReturn;
    }
}
