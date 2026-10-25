package com.kelly.varengine.services;

import com.kelly.varengine.models.*;
import com.kelly.varengine.providers.MassiveMarketDataSource;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URISyntaxException;
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
        throws IllegalArgumentException, SecurityException, ResponseStatusException,
            URISyntaxException, IOException, InterruptedException
    {
        return dataSource.getDailyQuotes(symbol, startDate, endDate);
    }
}
