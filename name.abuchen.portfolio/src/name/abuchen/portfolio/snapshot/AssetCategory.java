package name.abuchen.portfolio.snapshot;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import name.abuchen.portfolio.model.Classification;
import name.abuchen.portfolio.model.Portfolio;
import name.abuchen.portfolio.money.CurrencyConverter;
import name.abuchen.portfolio.money.Money;
import name.abuchen.portfolio.money.MutableMoney;

public class AssetCategory
{
    public static final class ByValue implements Comparator<AssetCategory>
    {
        @Override
        public int compare(AssetCategory p1, AssetCategory p2)
        {
            return p1.getValuation().compareTo(p2.getValuation());
        }
    }

    private final Classification classification;
    private final Portfolio portfolio;
    private final CurrencyConverter converter;
    private final LocalDate date;
    private final List<AssetPosition> positions = new ArrayList<>();
    private final Money totalAssets;
    private final MutableMoney valuation;

    /* package */ AssetCategory(Classification classification, CurrencyConverter converter, LocalDate date,
                    Money totalAssets)
    {
        this.classification = classification;
        this.converter = converter;
        this.date = date;
        this.totalAssets = totalAssets;
        this.valuation = MutableMoney.of(converter.getTermCurrency());
        this.portfolio = null;
    }

    /* package */ AssetCategory(Portfolio portfolio, CurrencyConverter converter, LocalDate date, Money totalAssets)
    {
        this.classification = null;
        this.converter = converter;
        this.date = date;
        this.totalAssets = totalAssets;
        this.valuation = MutableMoney.of(converter.getTermCurrency());
        this.portfolio = portfolio;
    }

    /* package */ AssetCategory(CurrencyConverter converter, LocalDate date, Money totalAssets)
    {
        this.classification = null;
        this.converter = converter;
        this.date = date;
        this.totalAssets = totalAssets;
        this.valuation = MutableMoney.of(converter.getTermCurrency());
        this.portfolio = null;
    }

    public Money getValuation()
    {
        return this.valuation.toMoney();
    }

    public double getShare()
    {
        return (double) this.valuation.getAmount() / (double) this.totalAssets.getAmount();
    }

    public Classification getClassification()
    {
        return this.classification;
    }

    public Portfolio getPortfolio()
    {
        return this.portfolio;
    }

    public List<AssetPosition> getPositions()
    {
        return positions;
    }

    public void addPosition(AssetPosition p)
    {
        this.positions.add(p);
        this.valuation.add(converter.convert(date, p.getValuation()));
    }
}
