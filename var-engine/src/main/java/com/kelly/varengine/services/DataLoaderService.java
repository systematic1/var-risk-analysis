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
import java.util.stream.Collectors;

@Service
public class DataLoaderService {

    private final MassiveMarketDataSource dataSource;

    public DataLoaderService(MassiveMarketDataSource _dataSource) {
        dataSource = _dataSource;
    }

    public Map<LocalDate, OHLCVBar> getHistoricalQuotes(
            String symbol,
            LocalDate startDate,
            LocalDate endDate)
        throws IllegalArgumentException, SecurityException, ResponseStatusException,
            URISyntaxException, IOException, InterruptedException
    {
        // Ensure the map is sorted by key (datetime) (LinkedHashMap)
        return dataSource
                .getDailyQuotes(symbol, startDate, endDate)
                .entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (existing, replacement) -> existing,
                        LinkedHashMap::new
                ));
    }

    @Async
    public void waitDelayTime() {
        long waitTime = (long)dataSource.getDelayBetweenCallsSeconds() * 1000;
        try {
            Thread.sleep(waitTime);
        }
        catch (InterruptedException ex) { }
    }

    public int interpolateMissingData(Map<LocalDate, OHLCVBar> quoteData, LocalDate earliestDate) {
        double[] lastPrice = {0.0};
        LocalDate[] lastDate = {LocalDate.MIN};
        Map<LocalDate, OHLCVBar> addedDates = new LinkedHashMap<>();

        quoteData.forEach((date, bars) -> {
            // if a weekday was skipped in the data
            if (lastDate[0] != date.minusDays(-1L) && lastDate[0] != LocalDate.MIN) {
                if (isWeekday(date)) {
                    var paddedBar = new OHLCVBar(lastPrice[0], lastPrice[0], lastPrice[0], lastPrice[0], lastPrice[0], 0);
                    addedDates.put(date, paddedBar);
                }
            }
            else
                lastPrice[0] = bars.getAdjClosePrice();

            lastDate[0] = date;
        });

        if (!addedDates.isEmpty()) {
            // add the new date bars to the quote data and then force it to be sorted
            quoteData.putAll(addedDates);
            quoteData = quoteData
                    .entrySet()
                    .stream()
                    .sorted(Map.Entry.comparingByKey())
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue,
                            (existing, replacement) -> existing,
                            LinkedHashMap::new)
                    );
        }

        return addedDates.size();
    }

    private boolean isWeekday(LocalDate date) {
        var weekday = date.getDayOfWeek();
        return (weekday != DayOfWeek.SATURDAY && weekday != DayOfWeek.SUNDAY);
    }
}
