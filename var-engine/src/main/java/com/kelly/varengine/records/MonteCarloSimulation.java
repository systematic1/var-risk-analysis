package com.kelly.varengine.records;

import org.ejml.simple.SimpleMatrix;

public record MonteCarloSimulation(int numSimulations, double[] simulationConfidenceLevels,
                                   SimpleMatrix choleskyLMatrix, double[] assetMeanValues,
                                   double[] assetStdDevValues, double medianOutcomeValue,
                                   double[] trialVectorData, ConfidenceVaRReportItem[] riskSummaries) { }
