package models;

public class OHLCVSeries {
    double openPrice;
    double highPrice;
    double lowPrice;
    double closePrice;
    double adjClosePrice;
    int volume;

    public OHLCVSeries() { }

    public OHLCVSeries(double openPrice, double highPrice, double lowPrice,
                       double closePrice, double adjClosePrice, int volume)
    {
        this.openPrice = openPrice;
        this.highPrice = highPrice;
        this.lowPrice = lowPrice;
        this.closePrice = closePrice;
        this.adjClosePrice = adjClosePrice;
        this.volume = volume;
    }

    public double getOpenPrice() { return openPrice; }
    public void setOpenPrice(double value) { openPrice = value; }

    public double getHighPrice() { return highPrice; }
    public void setHighPrice(double value) { highPrice = value; }

    public double getLowPrice() { return lowPrice; }
    public void setLowPrice(double value) { lowPrice = value; }

    public double getClosePrice() { return closePrice; }
    public void setClosePrice(double value) { closePrice = value; }

    public double getAdjClosePrice() { return adjClosePrice; }
    public void setAdjClosePrice(double value) { adjClosePrice = value; }

    public int getVolume() { return volume; }
    public void setVolume(int value) { volume = value; }
}
