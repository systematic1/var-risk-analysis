package services;

import models.*;
import java.time.LocalDate;
import java.util.*;

public class DataLoaderService {
    public List<OHLCVSeries> getHistoricalQuotes(String symbol, LocalDate startDate, LocalDate endDate) {
        List<OHLCVSeries> series = new ArrayList<OHLCVSeries>();
        // ...

        return series;
    }
}
