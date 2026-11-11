package com.kelly.varengine.records;

import org.nd4j.linalg.api.ndarray.INDArray;

public record PortfolioData(double portfolioValue, double portfolioVolatility, double[] weights,
                            INDArray logarithmicReturns) { }
