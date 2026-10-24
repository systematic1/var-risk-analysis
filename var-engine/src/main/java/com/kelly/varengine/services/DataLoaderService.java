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

    public HashMap<LocalDate, OHLCVSeries> getHistoricalQuotes(
            String symbol,
            LocalDate startDate,
            LocalDate endDate)
        throws IllegalArgumentException, SecurityException, Exception
    {
        return dataSource.getDailyQuotes(symbol, startDate, endDate);
    }
}
