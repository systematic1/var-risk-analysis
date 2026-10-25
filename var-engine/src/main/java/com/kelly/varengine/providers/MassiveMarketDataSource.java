package com.kelly.varengine.providers;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.*;
import com.kelly.varengine.models.OHLCVSeries;
import org.springframework.beans.factory.annotation.*;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class MassiveMarketDataSource {

    @Value("${app.massive.apiKey}")
    private String apiKey;

    @Value("${app.massive.baseUrl}")
    private String baseUrl;

    @Value("${app.massive.requestTimeoutSeconds}")
    private int requestTimeoutSeconds = 60;

    @Value("${app.massive.maxPages}")
    private int maxPages = 5;

    @Value("${app.massive.delayBetweenCallsSeconds}")
    private int delayBetweenCallsSeconds = 15;

    @Value("${app.massive.convertDashToDot}")
    private boolean convertDashToDot = true;

    public MassiveMarketDataSource() { }

    public HashMap<LocalDate, OHLCVSeries> getDailyQuotes(String symbol,
                                                          LocalDate fromDate,
                                                          LocalDate toDateInclusive)
            throws IllegalArgumentException, SecurityException, ResponseStatusException,
                URISyntaxException, IOException, InterruptedException
    {

        HttpClient http = HttpClient.newHttpClient();
        if (apiKey.isBlank())
            throw new IllegalArgumentException("Massive-ApiKey is not configured.");

        symbol = mapTicker(symbol);
        String fromStr = fromDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String toStr = toDateInclusive.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        String url = String.format("%s/v2/aggs/ticker/%s/range/1/day/%s/%s?adjusted=true&sort=asc&limit=50000",
                baseUrl, symbol, fromStr, toStr);

        var quotes = new HashMap<LocalDate, OHLCVSeries>();
        int page = 0;

        while (url != null && ++page <= maxPages) {
            var request = HttpRequest.newBuilder()
                    .uri(new URI(withApiKey(url)))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 401 || response.statusCode() == 403)
                throw new SecurityException("Massive rejected the request - no API key was provided, or your plan does not include this data.");

            String body = response.body();
            if (response.statusCode() != 200)
                throw new ResponseStatusException(HttpStatusCode.valueOf(response.statusCode()));

            url = parseData(body, symbol, quotes);
        }

        return quotes;
    }

    private String parseData(String json, String symbol, HashMap<LocalDate, OHLCVSeries> seriesData)
        throws IllegalStateException
    {
        // Convert the json string into a MsvResult record instance
        String newUrl = null;
        ObjectMapper mapper = new ObjectMapper();

        try {
            MsvResult parsedResult = mapper.readValue(json, MsvResult.class);

            if (parsedResult == null)
                throw new IllegalStateException("Response was empty or null");
            else if (!parsedResult.status().equalsIgnoreCase("OK"))
                throw new IllegalStateException(String.format("Response status returned '%s'", parsedResult.status()));
            else {
                for (MsvBar bar : parsedResult.results()) {
                    if (bar.h < bar.l)
                        continue;

                    LocalDateTime barDateUtc = LocalDateTime.ofEpochSecond(bar.t(), 0, ZoneOffset.UTC);
                    ZoneId zoneId = ZoneId.of("America/New_York");
                    ZonedDateTime barDateEst = barDateUtc.atZone(ZoneOffset.UTC).withZoneSameInstant(zoneId);

                    OHLCVSeries series = new OHLCVSeries(bar.o(), bar.h(), bar.l(), bar.c(), bar.c(), bar.v());
                    seriesData.put(barDateEst.toLocalDate(), series);
                }

                newUrl = parsedResult.next_url();
            }
        }
        catch (tools.jackson.core.JacksonException jex) {
            throw new IllegalStateException("Json response could not be parsed into expected form.");
        }

        return newUrl;
    }

    private String withApiKey(String url) {
        return url + (url.contains("?") ? "&" : "?") + "apiKey=" +
                java.net.URLEncoder.encode(apiKey);
    }

    private String mapTicker(String ticker)
    {
        String t = ticker.trim().toUpperCase(Locale.ROOT);
        return convertDashToDot ? t.replace('-', '.') : t;
    }

    private Optional<Double> getNum(String str) {
        if (str == null || str.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(Double.parseDouble(str.trim()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MsvBar(double c, double h, double l, int n, double o, long t, int v, double vw) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MsvResult(boolean adjusted, String next_url, int queryCount, String request_id,
                             List<MsvBar> results, int resultsCount, String status, String ticker) { }

/*
{
  "adjusted": true,
  "next_url": "https://api.massive.com/v2/aggs/ticker/AAPL/range/1/day/1578114000000/2020-01-10?cursor=bGltaXQ9MiZzb3J0PWFzYw",
  "queryCount": 2,
  "request_id": "6a7e466379af0a71039d60cc78e72282",
  "results": [
    {
      "c": 75.0875,
      "h": 75.15,
      "l": 73.7975,
      "n": 1,
      "o": 74.06,
      "t": 1577941200000,
      "v": 135647456,
      "vw": 74.6099
    },
    {
      "c": 74.3575,
      "h": 75.145,
      "l": 74.125,
      "n": 1,
      "o": 74.2875,
      "t": 1578027600000,
      "v": 146535512,
      "vw": 74.7026
    }
  ],
  "resultsCount": 2,
  "status": "OK",
  "ticker": "AAPL"
}
*/
}
