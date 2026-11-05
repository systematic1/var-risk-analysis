package com.kelly.varengine.records;

public record ParametricVarReportItem(String component, double weight, double stVaR, double mVar,
                                      double cVar, double percent) {}

