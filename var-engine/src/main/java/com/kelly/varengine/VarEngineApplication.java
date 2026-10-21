import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.beans.factory.annotation.*;


@SpringBootApplication
public class VarEngineApplication {

    private MatrixServices matrixServices;
    private VarServices varServices;

    public static void main(String[] args) {
        SpringApplication.run(VarEngineApplication.class, args);
    }

    @Value("${app.confidenceLevel.1}")
    public double confidenceLevel1;

    @Value("${app.confidenceLevel.2}")
    public double confidenceLevel2;

    @Value("${app.monteCarlo.trialCount}")
    public int monteCarloTrialCount;

    public VarEngineApplication(MatrixServices matrixServices, VarServices varServices) {
        this.matrixServices = matrixServices;
        this.varServices = varServices;
    }

    public void Run() {
        // Creates several positions & weights
        // Loads historical backtest data via Massive or Alpaca
        // Transforms historical data into matrix
        // Computes the logarthmic returns matrix
        // Computes the dynamic weight of the log returns
        // Fills in gaps
        // Creates covariance matrix
        // Creates a Z-Score and VaR amount based on 2 confidence levels
        // Creates Colesky Decomposition matrix and uses the L matrix value
        // Runs the configured number of random trials

        // Produces the following output:
        //  Downside loss limit
        //      - Absolute VaR (maximum loss $)
        //      - Percentage VaR (% of portfolio value)
        //      - Time horizon (holding period in days)
        //      - Confidence ("#-Day VaR at ##% Confidence")
        //  Max loss projections at given confidence levels
        //      - VaR Distribution chart (bell curve) per confidence level
        //          - Safe zone: represents area of bell curve within expected normal losses (at 95% or 99% confidence)
        //          - Tail area: segment area (1% or 5%) representing chosen significance level
        //  KPIs / Summary table result
        //      Probablility of Success = %     (percentage of simulated runs where portfolio never hit zero)
        //      Median Ending Value = $         (P50 outcome - half the runs ended higher, half ended lower)
        //      Shortfall Risk = %              (inverse of success: 100% - Prob of Success)
        //      Value at Risk = $               (Maximum expected loss at specific confidence interval over a given timeframe)
        //  Key Parametric Outcome tables  <example>
        //      ===> Headers:  Asset/Risk Component      Position Weight    Standalone VaR   Marginal VaR   Component VaR
        //                     US Equities (S&P 500)     50%                $165,000         $125,000       $62,500
        //                     Global Nonds (Agg)        50%                $45,000          $15,000        $6,000
        //                     Undiversified Total       100%               $210,000         $140,000       $68,500
        //                     Diversification Benefit   --                 -$130,000        --             --
        //                     Portfolio Parametric VaR  100%               $80,000          --             $80,000
        //      ===> Headers:  Confidence Level      Simulated %       Portfolio Value       VaR Metric             Conditional VaR
        //                                           Percentile        Outcome               (Max Loss)             Expected Shortfall
        //                     90.0%                 10th percentile   $9,150,000            $850,000 (8.5%)        $1,420,000
        //                     95.0%                 5th percentile    $8,600,000            $1,400,000 (14.0%)     $1,950,000
        //                     99.0%                 1st percentile    $7,200,000            $2,800,000 (28.0%)     $3,410,000
        //                     99.9%                 0.1 percentile    $5,100,000            $4,900,000 (49.0%)     $5,250,000
        //
        //  Orders MC outcomes from best-case (P90) to median (P50) to worst (P10) tail outcomes  <example>
        //      Diversification Benefit: $ or % reduction in risk by mixing uncorrelated assets
        //      Marginal VaR (MVaR): Change in total portfolio VaR resulting from incremental 1% increase in asset's position
        //      Component VaR (CVaR): Aboslute amount of total VaR attributed directly to a specific asset or sector
        //  [Opt] Extreme shock modeling (market crashes volatility spikes, broken correlations)

    }
}
