package name.abuchen.portfolio.ui.views.dashboard;

import static name.abuchen.portfolio.util.CollectorsUtil.toMutableList;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

import jakarta.inject.Inject;

import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.forms.events.HyperlinkAdapter;
import org.eclipse.ui.forms.events.HyperlinkEvent;
import org.eclipse.ui.forms.widgets.ImageHyperlink;

import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.Dashboard.Widget;
import name.abuchen.portfolio.snapshot.filter.ClientFilter;
import name.abuchen.portfolio.snapshot.trades.Trade;
import name.abuchen.portfolio.snapshot.trades.TradeCollector;
import name.abuchen.portfolio.snapshot.trades.TradeCollectorException;
import name.abuchen.portfolio.ui.Images;
import name.abuchen.portfolio.ui.UIConstants;
import name.abuchen.portfolio.ui.editor.PortfolioPart;
import name.abuchen.portfolio.ui.util.CacheKey;
import name.abuchen.portfolio.ui.util.swt.StyledLabel;
import name.abuchen.portfolio.ui.views.trades.TradeDetailsView;
import name.abuchen.portfolio.ui.views.trades.TradeDetailsView.Input;
import name.abuchen.portfolio.util.Interval;
import name.abuchen.portfolio.util.TextUtil;

/* package */ abstract class AbstractTradesWidget extends WidgetDelegate<Input>
{
    @Inject
    private PortfolioPart part;

    protected Label title;
    protected StyledLabel indicator;

    /* package */ AbstractTradesWidget(Widget widget, DashboardData data)
    {
        super(widget, data);

        addConfig(new ClientFilterConfig(this));
        addConfig(new ReportingPeriodConfig(this));
    }

    protected boolean useSecurityCurrency()
    {
        return false;
    }

    @Override
    public Composite createControl(Composite parent, DashboardResources resources)
    {
        Composite container = new Composite(parent, SWT.NONE);
        container.setBackground(parent.getBackground());
        GridLayoutFactory.fillDefaults().numColumns(2).margins(5, 5).applyTo(container);

        title = new Label(container, SWT.NONE);
        title.setText(TextUtil.tooltip(getWidget().getLabel()));
        GridDataFactory.fillDefaults().grab(true, false).span(2, 1).applyTo(title);

        indicator = new StyledLabel(container, SWT.NONE);
        indicator.setData(UIConstants.CSS.CLASS_NAME, UIConstants.CSS.KPI);
        indicator.setText(""); //$NON-NLS-1$
        GridDataFactory.fillDefaults().grab(true, false).applyTo(indicator);

        ImageHyperlink button = new ImageHyperlink(container, SWT.NONE);
        button.setImage(Images.VIEW_SHARE.image());
        button.addHyperlinkListener(new HyperlinkAdapter()
        {
            @Override
            public void linkActivated(HyperlinkEvent e)
            {
                part.activateView(TradeDetailsView.class, getUpdateTask().get());
            }
        });

        getDashboardData().getStylingEngine().style(container);

        return container;
    }

    @Override
    public Control getTitleControl()
    {
        return title;
    }

    @Override
    public Supplier<TradeDetailsView.Input> getUpdateTask()
    {
        Interval interval = get(ReportingPeriodConfig.class).getReportingPeriod().toInterval(LocalDate.now());
        ClientFilter clientFilter = get(ClientFilterConfig.class).getSelectedFilter();
        CacheKey key = new CacheKey(TradeCollector.class, useSecurityCurrency(), clientFilter, interval);

        return () -> {
            TradeDetailsView.Input input = (TradeDetailsView.Input) getDashboardData().getCache().computeIfAbsent(key,
                            k -> {

                                Client filteredClient = clientFilter.filter(getClient());

                                List<Trade> trades = new ArrayList<>();
                                List<TradeCollectorException> errors = new ArrayList<>();

                                getClient().getSecurities().forEach(s -> {
                                    try
                                    {
                                        var converter = getDashboardData().getCurrencyConverter();
                                        if (useSecurityCurrency() && s.getCurrencyCode() != null)
                                            converter = converter.with(s.getCurrencyCode());

                                        var collector = new TradeCollector(filteredClient, converter);
                                        trades.addAll(collector.collect(s));
                                    }
                                    catch (TradeCollectorException error)
                                    {
                                        errors.add(error);
                                    }
                                });

                                return new TradeDetailsView.Input(interval, trades, errors, useSecurityCurrency());
                            });

            // filter trades on the *cached* result (which includes all trades)
            // because widgets apply different filter on the result

            return new TradeDetailsView.Input(input.getInterval(),
                            input.getTrades().stream().filter(getFilter(interval)).collect(toMutableList()),
                            input.getErrors(), input.useSecurityCurrency());
        };
    }

    /**
     * Constructs the predicate the filters the Trades for the widget. The
     * default implementation includes all closed trades which have been closed
     * in the reporting interval.
     */
    protected Predicate<Trade> getFilter(Interval interval)
    {
        return t -> {
            if (!t.getEnd().isPresent())
                return false;

            return interval.contains(t.getEnd().get());
        };
    }

}
