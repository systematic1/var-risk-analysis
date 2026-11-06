package com.kelly.varengine.records;

import org.nd4j.linalg.api.ndarray.INDArray;

import java.util.List;

public record ParametricMethod(double zScore, double assumedMean, INDArray covariantMatrix,
                               List<ParametricVarReportItem> riskSummaries) { }
