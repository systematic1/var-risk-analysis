package com.kelly.varengine.services;

import com.kelly.varengine.models.*;
import com.kelly.varengine.providers.MassiveMarketDataSource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Service
public class DataLoaderService {

    private final MassiveMarketDataSource dataSource;

    public DataLoaderService(MassiveMarketDataSource _dataSource) {
        dataSource = _dataSource;
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

    // When not all positions have enough history data to satisfy the look-back time frame:
            /*
                You start with the longest available data and get the covariance matrix of that.
                For the next set of available data, you regress them against the data that is available
                longer and use the regression coefficients to expand the covariance matrix (and means).
                You then iterate through each unique group of data, steadily shrinking the amount of data
                used in each regression.
            */

    // When there are a few dates missing from one asset data history stream - interpolate missing data points

    public int interpolateMissingData(HashMap<LocalDate, OHLCVBar> quoteData, LocalDate earliestDate) {
        // To do - would add missing date data via interpolation and return the number of days that were added
        return 0;
    }

    private boolean isWeekday(LocalDate date) {
        var weekday = date.getDayOfWeek();
        return (weekday != DayOfWeek.SATURDAY && weekday != DayOfWeek.SUNDAY);
    }
}
