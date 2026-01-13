package name.abuchen.portfolio.snapshot;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import name.abuchen.portfolio.money.CurrencyConverter;
import name.abuchen.portfolio.money.Money;

public final class GroupByPortfolio
{
    private final CurrencyConverter converter;
    private final LocalDate date;
    private final Money valuation;
    private final List<AssetCategory> categories = new ArrayList<>();

    private GroupByPortfolio(CurrencyConverter converter, LocalDate date, Money valuation)
    {
        this.converter = converter;
        this.date = date;
        this.valuation = valuation;
    }

    /* package */ GroupByPortfolio(ClientSnapshot snapshot)
    {
        this(snapshot.getCurrencyConverter(), snapshot.getTime(), snapshot.getMonetaryAssets());

        // portfolio and their securities
        snapshot.getPortfolios().stream().filter(p -> !p.getValue().isZero()).forEach(p -> {
            AssetCategory category = new AssetCategory(p.unwrapPortfolio(), converter, date, valuation);
            categories.add(category);
            p.getPositions().stream()
                            .forEach(pos -> category.addPosition(new AssetPosition(pos, converter, date, valuation)));
            // sort positions by name
            Collections.sort(category.getPositions(), new AssetPosition.ByDescription());
        });
        // sort portfolios by value in descending order
        Collections.sort(categories, Collections.reverseOrder(new AssetCategory.ByValue()));

        // cash
        AssetCategory category = new AssetCategory(converter, date, valuation);
        categories.add(category);
        snapshot.getAccounts().stream().filter(a -> !a.getFunds().isZero()).forEach(a -> category
                        .addPosition(new AssetPosition(new SecurityPosition(a), converter, date, valuation)));
        Collections.sort(category.getPositions(), new AssetPosition.ByDescription());
    }

    public LocalDate getDate()
    {
        return date;
    }

    public Money getValuation()
    {
        return valuation;
    }

    public CurrencyConverter getCurrencyConverter()
    {
        return converter;
    }

    public List<AssetCategory> asList()
    {
        return categories;
    }

    public Stream<AssetCategory> getCategories()
    {
        return categories.stream();
    }
}
