package com.kelly.varengine.services;

import com.kelly.varengine.models.*;
import com.kelly.varengine.providers.MassiveMarketDataSource;

import java.time.LocalDate;
import java.util.*;

public class DataLoaderService {

    private MassiveMarketDataSource dataSource;

    public DataLoaderService() {
        dataSource = new MassiveMarketDataSource();
    }

    public List<OHLCVSeries> getHistoricalQuotes(String symbol, LocalDate startDate, LocalDate endDate) {
        return dataSource.getDailyQuotes(symbol, startDate, endDate);
    }
}
