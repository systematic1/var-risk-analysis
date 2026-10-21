package models;

public class PortfolioPosition {
    private int assetId;
    private byte assetClass;
    private String symbol;
    private double currentValue;
    private int quantity;
    private double weight;

    public PortfolioPosition() { }

    public PortfolioPosition(int assetId, byte assetClass, String symbol,
                             double currentValue, int quantity, double weight)
    {
        this.assetId = assetId;
        this.assetClass = assetClass;
        this.symbol = symbol;
        this.currentValue = currentValue;
        this.quantity = quantity;
        this.weight = weight;
    }

    public int getAssetId() { return assetId; }
    public void setAssetId(int value) { assetId = value; }

    public byte getAssetClass() { return assetClass; }
    public void setAssetClass(byte value) { assetClass = value; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String value) { symbol = value; }

    public double getCurrentValue() { return currentValue; }
    public void setCurrentValue(double value) { currentValue = value; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int value) { quantity = value; }

    public double getWeight() { return weight; }
    public void setWeight(double value) { weight = value; }
}
