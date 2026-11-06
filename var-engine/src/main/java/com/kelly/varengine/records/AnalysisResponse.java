package com.kelly.varengine.records;

public record AnalysisResponse(double varValue, double varPercent, int timeHorizonDays,
                               double confidenceLevel, int historyLookbackDays,
                               ParametricMethod parametricMethod, PortfolioData portfolio,
                               MonteCarloSimulation monteCarloSimulation) { }

