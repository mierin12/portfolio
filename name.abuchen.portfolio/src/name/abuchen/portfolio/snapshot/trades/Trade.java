package name.abuchen.portfolio.snapshot.trades;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import name.abuchen.portfolio.math.IRR;
import name.abuchen.portfolio.model.Adaptable;
import name.abuchen.portfolio.model.Named;
import name.abuchen.portfolio.model.Portfolio;
import name.abuchen.portfolio.model.PortfolioTransaction;
import name.abuchen.portfolio.model.Security;
import name.abuchen.portfolio.model.TransactionPair;
import name.abuchen.portfolio.money.CurrencyConverter;
import name.abuchen.portfolio.money.Money;
import name.abuchen.portfolio.money.MoneyCollectors;
import name.abuchen.portfolio.money.Values;
import name.abuchen.portfolio.util.Dates;

public class Trade implements Adaptable
{
    private final Security security;
    private final Portfolio portfolio;
    private LocalDateTime start;
    private LocalDateTime end;
    private final long shares;

    private List<TransactionPair<PortfolioTransaction>> transactions = new ArrayList<>();
    private List<TransactionPair<PortfolioTransaction>> transactionsMovingAverage = new ArrayList<>();

    private Money entryValue;
    private Money entryValueWithoutTaxesAndFees;
    private Money exitValue;
    private Money exitValueWithoutTaxesAndFees;
    private long holdingPeriod;
    private double irr;

    private Money entryValueMovingAverage;
    private Money entryValueMovingAverageWithoutTaxesAndFees;

    public Trade(Security security, Portfolio portfolio, long shares)
    {
        this.security = security;
        this.shares = shares;
        this.portfolio = portfolio;
    }

    /* package */ void calculate(CurrencyConverter converter)
    {
        // for purchases, getMonetaryAmount() returns the value including taxes
        // and fees paid
        this.entryValue = transactions.stream() //
                        .filter(t -> t.getTransaction().getType().isPurchase())
                        .map(t -> t.getTransaction().getMonetaryAmount()
                                        .with(converter.at(t.getTransaction().getDateTime())))
                        .collect(MoneyCollectors.sum(converter.getTermCurrency()));

        // for purchases, getGrossValue() returns the value without taxes and
        // fees paid
        this.entryValueWithoutTaxesAndFees = transactions.stream() //
                        .filter(t -> t.getTransaction().getType().isPurchase())
                        .map(t -> t.getTransaction().getGrossValue()
                                        .with(converter.at(t.getTransaction().getDateTime())))
                        .collect(MoneyCollectors.sum(converter.getTermCurrency()));

        if (end != null)
        {
            // for sales, getMonetaryAmount() returns the sales proceeds with
            // (after) taxes and fees deducted
            this.exitValue = transactions.stream() //
                            .filter(t -> t.getTransaction().getType().isLiquidation())
                            .map(t -> t.getTransaction().getMonetaryAmount()
                                            .with(converter.at(t.getTransaction().getDateTime())))
                            .collect(MoneyCollectors.sum(converter.getTermCurrency()));

            // for sales, getGrossValue() returns the sales proceeds without
            // (before) taxes and fees deducted
            this.exitValueWithoutTaxesAndFees = transactions.stream() //
                            .filter(t -> t.getTransaction().getType().isLiquidation())
                            .map(t -> t.getTransaction().getGrossValue()
                                            .with(converter.at(t.getTransaction().getDateTime())))
                            .collect(MoneyCollectors.sum(converter.getTermCurrency()));

            this.holdingPeriod = Math.round(transactions.stream() //
                            .filter(t -> t.getTransaction().getType().isPurchase())
                            .mapToLong(t -> t.getTransaction().getShares() * Dates.daysBetween(
                                            t.getTransaction().getDateTime().toLocalDate(), end.toLocalDate()))
                            .sum() / (double) shares);
        }
        else
        {
            LocalDate now = LocalDate.now();

            long marketValue = BigDecimal.valueOf(shares) //
                            .movePointLeft(Values.Share.precision()) //
                            .multiply(BigDecimal.valueOf(security.getSecurityPrice(now).getValue()), Values.MC)
                            .movePointLeft(Values.Quote.precisionDeltaToMoney()) //
                            .setScale(0, RoundingMode.HALF_UP).longValue();

            this.exitValue = converter.at(now).apply(Money.of(security.getCurrencyCode(), marketValue));
            this.exitValueWithoutTaxesAndFees = exitValue;

            this.holdingPeriod = Math.round(transactions.stream() //
                            .filter(t -> t.getTransaction().getType().isPurchase())
                            .mapToLong(t -> t.getTransaction().getShares()
                                            * Dates.daysBetween(t.getTransaction().getDateTime().toLocalDate(), now))
                            .sum() / (double) shares);
        }

        // let's sort again because the list might not be sorted anymore due to
        // transfers
        Collections.sort(transactions, TransactionPair.BY_DATE);

        // re-set start date from first entry after sorting
        this.setStart(transactions.get(0).getTransaction().getDateTime());

        calculateIRR(converter);

        getMovingAverageCost(converter);
    }

    private void calculateIRR(CurrencyConverter converter)
    {
        List<LocalDate> dates = new ArrayList<>();
        List<Double> values = new ArrayList<>();

        transactions.stream().forEach(t -> {
            dates.add(t.getTransaction().getDateTime().toLocalDate());

            double amount = t.getTransaction().getMonetaryAmount().with(converter.at(t.getTransaction().getDateTime()))
                            .getAmount() / Values.Amount.divider();

            if (t.getTransaction().getType().isPurchase())
                amount = -amount;

            values.add(amount);
        });

        if (end == null)
        {
            dates.add(LocalDate.now());
            values.add(exitValue.getAmount() / Values.Amount.divider());
        }

        this.irr = IRR.calculate(dates, values);
    }

    public Security getSecurity()
    {
        return security;
    }

    public Portfolio getPortfolio()
    {
        return portfolio;
    }

    public Optional<LocalDateTime> getEnd()
    {
        return Optional.ofNullable(end);
    }

    /* package */ void setEnd(LocalDateTime end)
    {
        this.end = end;
    }

    public LocalDateTime getStart()
    {
        return start;
    }

    /* package */ void setStart(LocalDateTime start)
    {
        this.start = start;
    }

    public long getShares()
    {
        return shares;
    }

    public List<TransactionPair<PortfolioTransaction>> getTransactions()
    {
        return transactions;
    }

    public List<TransactionPair<PortfolioTransaction>> getTransactionsMovingAverage()
    {
        return transactionsMovingAverage;
    }

    public TransactionPair<PortfolioTransaction> getLastTransaction()
    {
        // transactions have been sorted by calculate(), which is called once
        // after creation
        return transactions.get(transactions.size() - 1);
    }

    /**
     * Returns the transaction that closed the trade (if the trade is closed)
     */
    public Optional<TransactionPair<PortfolioTransaction>> getClosingTransaction()
    {
        return isClosed() ? Optional.of(transactions.get(transactions.size() - 1)) : Optional.empty();
    }

    public Money getEntryValue()
    {
        return entryValue;
    }

    public Money getEntryValueMovingAverage()
    {
        return entryValueMovingAverage;
    }

    public Money getExitValue()
    {
        return exitValue;
    }

    public Money getProfitLoss()
    {
        return exitValue.subtract(entryValue);
    }

    public Money getProfitLossMovingAverage()
    {
        return exitValue.subtract(entryValueMovingAverage);
    }

    public Money getProfitLossWithoutTaxesAndFees()
    {
        return exitValueWithoutTaxesAndFees.subtract(entryValueWithoutTaxesAndFees);
    }

    public Money getProfitLossMovingAverageWithoutTaxesAndFees()
    {
        return exitValueWithoutTaxesAndFees.subtract(entryValueMovingAverageWithoutTaxesAndFees);
    }

    public Money entryValueMovingAverageWithoutTaxesAndFees()
    {
        return entryValueMovingAverageWithoutTaxesAndFees;
    }

    public long getHoldingPeriod()
    {
        return holdingPeriod;
    }

    public double getIRR()
    {
        return irr;
    }

    public double getReturn()
    {
        return (exitValue.getAmount() / (double) entryValue.getAmount()) - 1;
    }

    public double getReturnMovingAverage()
    {
        return (exitValue.getAmount() / (double) entryValueMovingAverage.getAmount()) - 1;
    }

    /**
     * @brief Checks if the trade is closed
     * @return True if the trade has been closed, false otherwise
     */
    public boolean isClosed()
    {
        return this.getEnd().isPresent();
    }

    /**
     * @brief Checks if the trade made a net loss
     * @return True if the trade resulted in a net loss
     */
    public boolean isLoss()
    {
        return this.getProfitLoss().isNegative();
    }

    /**
     * @brief Check if the trade made a gross gross
     * @return True if the trade result in a gross loss
     */
    public boolean isGrossLoss()
    {
        return this.getProfitLossWithoutTaxesAndFees().isNegative();
    }

    @Override
    public <T> T adapt(Class<T> type)
    {
        if (type == Security.class || type == Named.class)
            return type.cast(security);
        else
            return null;
    }

    @Override
    public String toString()
    {
        return String.format("<Trade sh=%s %s %s -> %s %s>", //$NON-NLS-1$
                        shares, start, entryValue, end, exitValue);
    }

    private void getMovingAverageCost(CurrencyConverter converter)
    {
        double holdShares = 0;
        long amount = 0;
        long amountWithoutTaxes = 0;

        for (TransactionPair<PortfolioTransaction> t : transactionsMovingAverage)
        {
            PortfolioTransaction tx = t.getTransaction();
            var type = tx.getType();
            holdShares += type.isPurchase() ? tx.getShares() : -tx.getShares();
            if (type.isPurchase())
            {
                amount += tx.getMonetaryAmount().with(converter.at(tx.getDateTime())).getAmount();
                amountWithoutTaxes += tx.getGrossValue().with(converter.at(tx.getDateTime())).getAmount();
            }
            else
            {
                var closingTransaction = getClosingTransaction();
                boolean isClosingTransaction = closingTransaction.isPresent()
                                && closingTransaction.get().getTransaction().equals(tx);
                double ratio = calculateLiquidationRatio(isClosingTransaction, tx.getShares(), holdShares);
                amount = scaleAmount(amount * ratio);
                amountWithoutTaxes = scaleAmount(amountWithoutTaxes * ratio);
            }
        }
        this.entryValueMovingAverage = Money.of(converter.getTermCurrency(), amount);
        this.entryValueMovingAverageWithoutTaxesAndFees = Money.of(converter.getTermCurrency(), amountWithoutTaxes);
    }

    private double calculateLiquidationRatio(boolean toto, long transactionShares, double holdShares)
    {
        double sharesForRatio = toto ? transactionShares : holdShares;
        return sharesForRatio / (transactionShares + holdShares);
    }

    private long scaleAmount(double value)
    {
        return BigDecimal.valueOf(value).setScale(0, RoundingMode.HALF_DOWN).longValue();
    }
}
