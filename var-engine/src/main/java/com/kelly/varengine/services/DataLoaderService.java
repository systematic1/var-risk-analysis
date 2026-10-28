package com.kelly.varengine.services;

import com.kelly.varengine.models.*;
import com.kelly.varengine.providers.MassiveMarketDataSource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class DataLoaderService {

    private MassiveMarketDataSource dataSource;

    public DataLoaderService() {
        dataSource = new MassiveMarketDataSource();
    }

    public HashMap<LocalDate, OHLCVBar> getHistoricalQuotes(
            String symbol,
            LocalDate startDate,
            LocalDate endDate)
        throws IllegalArgumentException, SecurityException, ResponseStatusException,
            URISyntaxException, IOException, InterruptedException
    {
        return dataSource.getDailyQuotes(symbol, startDate, endDate);
    }

    @Async
    public CompletableFuture<Void> waitDelayTime() {
        long waitTime = (long)dataSource.getDelayBetweenCallsSeconds() * 1000;
        try {
            Thread.sleep(waitTime);
        }
        catch (InterruptedException ex) { }

        return CompletableFuture.completedFuture(null);
    }

    public int interpolateMissingData(HashMap<LocalDate, OHLCVBar> quoteData, LocalDate earliestDate) {
        LocalDate date = earliestDate;
        LocalDate now = LocalDate.now();
        OHLCVBar prevValidData, nextValidData;
        List<LocalDate> missingDates = new ArrayList<>();

        while (date.isBefore(now)) {
            if (!quoteData.containsKey(date)) {
                missingDates.add(date);
            }

            date = date.plusDays(1);
        }

        return 0;
    }

    private boolean isWeekday(LocalDate date) {
        var weekday = date.getDayOfWeek();
        return (weekday != DayOfWeek.SATURDAY && weekday != DayOfWeek.SUNDAY);
    }
}
