package com.kelly.varengine.records;

public record LossRiskSummaryItem(double absoluteVar, double percentVar, int timeHorizonDays,
                                  double probOfSuccess, double probOfShortfall, double medianEndingValue,
                                  double valueAtRisk) { }
