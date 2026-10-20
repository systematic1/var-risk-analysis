# Value-at-Risk Analysis Engine

The Value-At-Risk Portfolio risk analysis system will run a set of portfolio
positions and weights through a rigorous calculation and grab historical quote
data for the positions that will be used to create a comparison matrix.

This system will grab the previous 1-year of trading history. It will do the
following:

* Fill in missing data from the history feed
* Calculate the logarithmic return (R) matrix from history
* Calculate the dynamic asset weight matrix from portfolio data

### Compute the parametric Variance-Covariance matrices

* Calculate Individual Volatilities
* Calculate Inter-Asset Covariances
* For every asset pair, calculate their covariance.
* Compute the variance matrix based on covariance matrix, weight, and transpose vector

### Calculate VaR

* Determine the Z-score with a given confidence level
* Calculate VaR by multiplying variance matrix by Z-score