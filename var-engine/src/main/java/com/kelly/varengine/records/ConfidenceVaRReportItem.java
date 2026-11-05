package com.kelly.varengine.records;

public record ConfidenceVaRReportItem(double confidenceLevel, String percentile, double outcomeValue,
                                      double varMetric, double maxLossPercent, double componentVaR ) { }
