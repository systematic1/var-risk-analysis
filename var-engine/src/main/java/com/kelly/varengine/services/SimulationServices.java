package com.kelly.varengine.services;

import com.kelly.varengine.models.PortfolioPosition;
import com.kelly.varengine.records.ConfidenceVaRReportItem;
import com.kelly.varengine.records.MonteCarloSimulation;
import org.ejml.simple.SimpleMatrix;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.random.RandomGenerator.SplittableGenerator;

@Service
public class SimulationServices {
    private final VarServices varServices;

    public SimulationServices(VarServices varServices) { this.varServices = varServices; }

    public MonteCarloSimulation performMonteCarloSimulation(
            List<PortfolioPosition> positions, INDArray weightedPctChangeReturns,
            SimpleMatrix choleskyLMatrix, int maxTrialCount, double portfolioValue,
            double variance, int timeHorizonDays) {

        // Calculate the mean values of each asset's historical value
        double[] assetWeights = new double[positions.size()];
        double[] assetMeans = new double[positions.size()];
        double[] assetStdDevs = new double[positions.size()];

        for (int index = 0; index < positions.size(); index++) {
            var positionReturns = weightedPctChangeReturns.getColumn(index);
            assetWeights[index] = positions.get(index).getWeight();
            assetMeans[index] = positionReturns.meanNumber().doubleValue();
            assetStdDevs[index] = positionReturns.stdNumber().doubleValue();
        }

        // #Run parallel Monte Carlo trials
        double[] trialPortfolioReturns = new double[maxTrialCount];
        java.util.stream.IntStream.range(0, maxTrialCount).parallel().forEach(index -> {
            trialPortfolioReturns[index] = performSimulationTrial(choleskyLMatrix, assetWeights,
                    assetMeans, assetStdDevs, timeHorizonDays);
        });

        // Sort trial outcomes for percentile extraction (ascending order of returns)
        Arrays.sort(trialPortfolioReturns);

        // Extract Key Percentiles (e.g., 10th, 5th, 1st percentiles)
        double medianOutcomeValue = portfolioValue * (1.0 + trialPortfolioReturns[maxTrialCount / 2]);
        double[] simConfidenceLevels = { 0.90, 0.95, 0.99, 0.999 };

        int[] percentileTier = new int[simConfidenceLevels.length];
        double[] percentileOutcomes = new double[simConfidenceLevels.length];
        double[] shortfallValues = new double[simConfidenceLevels.length];
        double[] maxLoss = new double[simConfidenceLevels.length];

        for (var index = 0; index < simConfidenceLevels.length; index++) {
            percentileTier[index] = (int) (maxTrialCount * (1.0 - simConfidenceLevels[index]));
            int tierValue = percentileTier[index] != 0 ? percentileTier[index] : 1;
            percentileOutcomes[index] = portfolioValue * (1.0 + trialPortfolioReturns[tierValue]);

            // Compute Expected Shortfall (Conditional VaR) at 95% confidence (mean loss beyond 5th percentile)
            double sumTailLosses = 0.0;
            for (int trialIndex = 0; trialIndex < tierValue; trialIndex++)
                sumTailLosses += trialPortfolioReturns[trialIndex];

            sumTailLosses = sumTailLosses * portfolioValue / tierValue;

            shortfallValues[index] = Math.abs(sumTailLosses);
            maxLoss[index] = Math.abs(trialPortfolioReturns[tierValue]);
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
                    maxLoss[level],
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
        //Random rand = new Random();
        SplittableGenerator rand = SplittableGenerator.of("L64X128MixRandom");

        // Generate independent standard normal vector Z (N x 1)
        SimpleMatrix zNormals = new SimpleMatrix(numAssets, 1);
        for (int index = 0; index < numAssets; index++)
            zNormals.set(index, 0, rand.nextGaussian());

        SimpleMatrix correlatedShocks = choleskyL.mult(zNormals);

        double[] portfolioReturn = { 0.0 };
        final double dt = Math.sqrt(timeHorizonDays); // scaling by horizon time steps

        for (int index = 0; index < numAssets; index++) {
            double drift = (assetMeans[index] - 0.5 * Math.pow(assetStdDevs[index], 2)) * timeHorizonDays;
            double shock = correlatedShocks.get(index, 0) * dt;
            double assetReturn = Math.exp(drift + shock) - 1.0;
            portfolioReturn[0] += assetWeights[index] * assetReturn;
        }

        return portfolioReturn[0];
    }
}
