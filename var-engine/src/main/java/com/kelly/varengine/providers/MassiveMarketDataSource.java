package com.kelly.varengine.providers;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.kelly.varengine.models.OHLCVSeries;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.autoconfigure.web.format.DateTimeFormatters;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;

import java.net.*;
import java.net.http.*;
import java.time.LocalDate;
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
            throws IllegalArgumentException, SecurityException, Exception {

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
                throw new Exception(String.format("Massive API call failed with code", response.statusCode()));

            url = parsePage(body, symbol, quotes);
        }

        return quotes;
    }

    private String parsePage(String json, String symbol, HashMap<LocalDate, OHLCVSeries> into) {
        // Convert the json string into a MsvResult record instance
        // Iterate over the MsvResult.results list and add each item to the "into" HashMap
        // Return MsvResult.next_url, or null if next_url is empty
        return null;
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
        ---- C# version to convert ----

    internal static string? ParsePage(string json, string ticker, Dictionary<DateOnly, DailyQuote> into)
    {
        using var doc = JsonDocument.Parse(json);
        var root = doc.RootElement;

        if (root.TryGetProperty("status", out var status) && status.ValueKind == JsonValueKind.String &&
            string.Equals(status.GetString(), "ERROR", StringComparison.OrdinalIgnoreCase))
        {
            string msg = root.TryGetProperty("error", out var e) ? e.ToString()
                       : (root.TryGetProperty("message", out var m) ? m.ToString() : "unknown error");
            throw new InvalidOperationException($"Massive error for {ticker}: {msg}");
        }

        if (root.TryGetProperty("results", out var results) && results.ValueKind == JsonValueKind.Array)
        {
            foreach (var r in results.EnumerateArray())
            {
                if (!Num(r, "o", out var o) || !Num(r, "h", out var h) || !Num(r, "l", out var l) ||
                    !Num(r, "c", out var c) || !Num(r, "t", out var t))
                    continue;

                Num(r, "v", out var v);
                if (h < l)
                    continue;

                // Daily bars are stamped at the start of the trading day in US Eastern time.
                var utc = DateTimeOffset.FromUnixTimeMilliseconds((long)t).UtcDateTime;
                var date = DateOnly.FromDateTime(TimeZoneInfo.ConvertTimeFromUtc(utc, EasternTime));

                into[date] = new DailyQuote
                {
                    TradeDate = date,
                    OpenPrice = (decimal)o,
                    HighPrice = (decimal)h,
                    LowPrice = (decimal)l,
                    ClosePrice = (decimal)c,
                    AdjClosePrice = null,                 // Massive bars carry no dividend-adjusted close
                    Volume = (long)Math.Round(v)
                };
            }
        }

        return root.TryGetProperty("next_url", out var next) && next.ValueKind == JsonValueKind.String
            ? next.GetString()
            : null;
    */

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


}
