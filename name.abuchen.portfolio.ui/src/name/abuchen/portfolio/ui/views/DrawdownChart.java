package name.abuchen.portfolio.ui.views;

import java.text.DecimalFormat;
import java.text.MessageFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.action.ToolBarManager;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swtchart.ILegend;
import org.eclipse.swtchart.ILineSeries;
import org.eclipse.swtchart.ILineSeries.PlotSymbolType;
import org.eclipse.swtchart.ISeries;
import org.eclipse.swtchart.ISeries.SeriesType;
import org.eclipse.swtchart.LineStyle;

import name.abuchen.portfolio.math.Risk;
import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.Security;
import name.abuchen.portfolio.model.SecurityPrice;
import name.abuchen.portfolio.model.Transaction;
import name.abuchen.portfolio.money.CurrencyConverter;
import name.abuchen.portfolio.money.Values;
import name.abuchen.portfolio.ui.Messages;
import name.abuchen.portfolio.ui.util.SimpleAction;
import name.abuchen.portfolio.ui.util.chart.TimelineChart;
import name.abuchen.portfolio.ui.util.chart.TimelineChartToolTip;
import name.abuchen.portfolio.ui.util.chart.TimelineSeriesModel;
import name.abuchen.portfolio.ui.util.format.AxisTickPercentNumberFormat;
import name.abuchen.portfolio.ui.views.SecuritiesChart.ChartInterval;
import name.abuchen.portfolio.ui.views.SecuritiesChart.ChartIntervalOrMessage;
import name.abuchen.portfolio.ui.views.SecuritiesChart.ChartRange;
import name.abuchen.portfolio.ui.views.SecuritiesChart.IntervalOption;
import name.abuchen.portfolio.ui.views.SecuritiesChart.MessagePainter;

/**
 * Chart of drawdown for a given security
 */
public class DrawdownChart
{
    private static final Color colorDrawdown = Display.getDefault().getSystemColor(SWT.COLOR_RED);

    private Composite container;

    private Client client;
    private CurrencyConverter converter;
    private Security security;

    private TimelineChart chart;

    /**
     * Calculates dynamically for each security the interval of security prices
     * to be shown.
     */
    private IntervalOption intervalOption = IntervalOption.Y2;

    private List<PaintListener> customPaintListeners = new ArrayList<>();
    private List<Transaction> customTooltipEvents = new ArrayList<>();

    private int swtAntialias = SWT.ON;

    private MessagePainter messagePainter = new MessagePainter();

    public DrawdownChart(Composite parent, Client client, CurrencyConverter converter)
    {
        this.client = client;
        this.converter = converter;

        container = new Composite(parent, SWT.NONE);
        container.setLayout(new FillLayout());

        chart = new TimelineChart(container);
        chart.getTitle().setText("..."); //$NON-NLS-1$
        chart.getTitle().setVisible(false);

        chart.addPlotPaintListener(event -> customPaintListeners.forEach(l -> l.paintControl(event)));
        chart.addPlotPaintListener(this.messagePainter);
        chart.getPlotArea().getControl().addDisposeListener(this.messagePainter);

        messagePainter.setMessage(Messages.SecuritiesChart_NoDataMessage_NoSecuritySelected);

        ILegend legend = chart.getLegend();
        legend.setPosition(SWT.BOTTOM);
        legend.setVisible(true);

        setupTooltip();
    }

    public IntervalOption getIntervalOption()
    {
        return intervalOption;
    }

    public void setIntervalOption(IntervalOption intervalOption)
    {
        this.intervalOption = intervalOption;
    }

    public int getAntialias()
    {
        return this.swtAntialias;
    }

    private void setupTooltip()
    {
        TimelineChartToolTip toolTip = chart.getToolTip();
        toolTip.showToolTipOnlyForDatesInDataSeries(Messages.LabelDrawdown);
        toolTip.setDefaultValueFormat(new DecimalFormat(Values.Percent2.pattern()));
    }

    private void configureSeriesPainter(ILineSeries<Integer> series, LocalDate[] dates, double[] values)
    {
        series.setDataModel(new TimelineSeriesModel(dates, values));
        series.setSymbolType(PlotSymbolType.NONE);
        series.setLineStyle(LineStyle.SOLID);
        series.setLineColor(colorDrawdown);
        series.setAntialias(swtAntialias);
        series.setVisibleInLegend(true);
        series.enableArea(true);
        series.setLineWidth(2);
    }

    public void addButtons(ToolBarManager toolBar)
    {
        chart.getChartToolsManager().addButtons(toolBar);

        toolBar.add(new Separator());

        List<Action> viewActions = new ArrayList<>();

        for (IntervalOption option : IntervalOption.values())
        {
            SimpleAction action = new SimpleAction(option.getLabel(), IAction.AS_CHECK_BOX, option.getTooltip(), a -> {
                this.intervalOption = option;
                updateChart();
                for (Action viewAction : viewActions)
                    viewAction.setChecked(a.equals(viewAction));
            });
            if (intervalOption == option)
                action.setChecked(true);
            viewActions.add(action);
            toolBar.add(action);
        }
    }

    public void updateChart(Client client, Security security)
    {
        this.client = client;
        this.security = security;
        updateChart();
    }

    public Control getControl()
    {
        return container;
    }

    private void clearChart()
    {
        // delete all line series
        ISeries<?>[] series = chart.getSeriesSet().getSeries();
        for (ISeries<?> s : series)
            chart.getSeriesSet().deleteSeries(s.getId());

        customPaintListeners.clear();
        customTooltipEvents.clear();
        chart.resetAxes();
        messagePainter.setMessage(null);
    }

    private void updateChart()
    {
        chart.setRedraw(false);
        chart.suspendUpdate(true);

        try
        {
            clearChart();
            chart.getTitle().setText(security == null ? "..." : security.getName()); //$NON-NLS-1$
            chart.getAxisSet().getYAxis(0).getTick().setFormat(new AxisTickPercentNumberFormat("+#.##%;-#.##%")); //$NON-NLS-1$

            if (security == null)
            {
                messagePainter.setMessage(Messages.SecuritiesChart_NoDataMessage_NoSecuritySelected);
                return;
            }

            List<SecurityPrice> prices = security.getPricesIncludingLatest();
            if (prices.isEmpty())
            {
                messagePainter.setMessage(Messages.SecuritiesChart_NoDataMessage_NoPrices);
                return;
            }

            ChartIntervalOrMessage chartIntervalOrMessage = intervalOption.getInterval(client, converter, security);
            if (chartIntervalOrMessage.getMessage() != null)
            {
                messagePainter.setMessage(chartIntervalOrMessage.getMessage());
                return;
            }

            // determine the interval to be shown in the chart
            ChartInterval chartInterval = chartIntervalOrMessage.getInterval();
            ChartRange range = ChartRange.createFor(prices, chartInterval);
            if (range == null)
            {
                messagePainter.setMessage(
                                MessageFormat.format(Messages.SecuritiesChart_NoDataMessage_NoPricesInSelectedPeriod,
                                                intervalOption.getTooltip()));
                return;
            }

            // Disable SWT antialias for more than 1000 records due to SWT
            // performance issue in Drawing
            swtAntialias = range.size > 1000 ? SWT.OFF : SWT.ON;

            // prepare value arrays
            LocalDate[] dates = new LocalDate[range.size];
            double[] quotes = new double[range.size];
            for (int ii = 0; ii < range.size; ii++)
            {
                SecurityPrice p = prices.get(ii + range.start);
                dates[ii] = p.getDate();
                quotes[ii] = p.getValue() / Values.Quote.divider();
            }
            double[] values = new Risk.Drawdown(quotes, dates, 0).getMaxDrawdownSerie();

            String seriesIdent = Messages.LabelDrawdown;

            @SuppressWarnings("unchecked")
            var lineSeries = (ILineSeries<Integer>) chart.getSeriesSet().createSeries(SeriesType.LINE, seriesIdent);
            configureSeriesPainter(lineSeries, dates, values);

            chart.adjustRange();
        }
        finally
        {
            chart.suspendUpdate(false);
            chart.setRedraw(true);
            chart.redraw();
        }
    }
}
