package com.kelly.varengine.providers;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.kelly.varengine.models.OHLCVBar;
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

    public HashMap<LocalDate, OHLCVBar> getDailyQuotes(String symbol,
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

        var quotes = new HashMap<LocalDate, OHLCVBar>();
        int page = 0;

        while (url != null && ++page <= maxPages) {
            var request = HttpRequest.newBuilder()
                    .uri(new URI(withApiKey(url)))
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(requestTimeoutSeconds))
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

    private String parseData(String json, String symbol, HashMap<LocalDate, OHLCVBar> seriesData)
        throws IllegalStateException
    {
        // Convert the json string into a MsvResult record instance
        String newUrl = null;
        ObjectMapper mapper = new ObjectMapper();
        ZoneId zoneId = ZoneId.of("America/New_York");

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
                    ZonedDateTime barDateEst = barDateUtc.atZone(ZoneOffset.UTC).withZoneSameInstant(zoneId);

                    OHLCVBar series = new OHLCVBar(bar.o(), bar.h(), bar.l(), bar.c(), bar.c(), bar.v());
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

    public int getDelayBetweenCallsSeconds() {
        return delayBetweenCallsSeconds;
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MsvBar(double c, double h, double l, int n, double o, long t, int v, double vw) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MsvResult(boolean adjusted, String next_url, int queryCount, String request_id,
                             List<MsvBar> results, int resultsCount, String status, String ticker) { }
}
