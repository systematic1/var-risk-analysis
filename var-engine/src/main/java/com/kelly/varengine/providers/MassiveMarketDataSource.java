package com.kelly.varengine.providers;

import com.kelly.varengine.models.OHLCVSeries;
import org.springframework.beans.factory.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

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

    public List<OHLCVSeries> getDailyQuotes(String symbol, LocalDate fromDate, LocalDate toDate) {
        /*
        ---- C# version to convert ----

        if (string.IsNullOrWhiteSpace(_options.ApiKey))
            throw new InvalidOperationException("Massive: ApiKey is not configured.");

        string symbol = MapTicker(ticker);
        string fromStr = fromDate.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture);
        string toStr = toDateInclusive.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture);

        string? url = $"{_options.BaseUrl.TrimEnd('/')}/v2/aggs/ticker/{Uri.EscapeDataString(symbol)}" +
                      $"/range/1/day/{fromStr}/{toStr}?adjusted={(_options.Adjusted ? "true" : "false")}&sort=asc&limit=50000";

        var quotes = new Dictionary<DateOnly, DailyQuote>();
        int page = 0;

        while (url != null && ++page <= _options.MaxPages)
        {
            _log.LogInformation("GET {Url}", url);   // logged WITHOUT the API key

            using var response = await _http.GetAsync(WithApiKey(url), ct);

            if (RateLimitedException.IsRateLimited(response))
                throw RateLimitedException.FromResponse(response, ticker);

            if (response.StatusCode is HttpStatusCode.Unauthorized or HttpStatusCode.Forbidden)
                throw new HttpRequestException(
                    $"HTTP: Massive rejected the request for {ticker} (HTTP {(int)response.StatusCode}): " +
                    "invalid API key, or your plan does not include this data.");

            string body = await response.Content.ReadAsStringAsync(ct);
            if (!response.IsSuccessStatusCode)
            {
                throw new HttpRequestException(
                    $"HTTP: Massive returned HTTP {(int)response.StatusCode} for {ticker}: {Truncate(body, 300)}");
            }

            url = ParsePage(body, ticker, quotes);
        }

        return quotes.Values.OrderBy(q => q.TradeDate).ToList();
    }

    /// <summary>Reads one response page into <paramref name="into"/>; returns next_url (or null when finished).</summary>
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
                if (h < l) continue;

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
        return null;
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


}
