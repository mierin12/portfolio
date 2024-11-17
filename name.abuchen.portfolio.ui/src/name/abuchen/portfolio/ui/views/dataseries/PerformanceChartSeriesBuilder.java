package name.abuchen.portfolio.ui.views.dataseries;

import org.eclipse.swt.graphics.Color;

import name.abuchen.portfolio.snapshot.Aggregation;
import name.abuchen.portfolio.snapshot.PerformanceIndex;
import name.abuchen.portfolio.ui.Messages;
import name.abuchen.portfolio.ui.util.Colors;
import name.abuchen.portfolio.ui.util.chart.TimelineChart;
import name.abuchen.portfolio.ui.views.dataseries.DataSeries.ClientDataSeries;
import name.abuchen.portfolio.util.Interval;

public class PerformanceChartSeriesBuilder extends AbstractChartSeriesBuilder
{
    private Color colorDeltaPositive = Colors.getColor(55, 117, 0); // #377500
    private Color colorDeltaNegative = Colors.getColor(127, 0, 0); // #7f0000

    public PerformanceChartSeriesBuilder(TimelineChart chart, DataSeriesCache cache)
    {
        super(chart, cache);
    }

    public void build(DataSeries series, Interval reportingPeriod, Aggregation.Period aggregationPeriod)
    {
        if (!series.isVisible())
            return;

        PerformanceIndex index = getCache().lookup(series, reportingPeriod);

        if (series.getType() == DataSeries.Type.CLIENT)
        {
            addClient(series, index, aggregationPeriod);
        }
        else
        {
            if (aggregationPeriod != null)
                index = Aggregation.aggregate(index, aggregationPeriod);

            var lineSeries = getChart().addDateSeries(series.getUUID(), index.getDates(),
                            index.getAccumulatedPercentage(), series.getLabel());
            configure(series, lineSeries);
        }

    }

    private void addClient(DataSeries series, PerformanceIndex clientIndex, Aggregation.Period aggregationPeriod)
    {
        PerformanceIndex index = aggregationPeriod != null ? Aggregation.aggregate(clientIndex, aggregationPeriod)
                        : clientIndex;

        switch ((ClientDataSeries) series.getInstance())
        {
            case TOTALS:
                var lineSeries = getChart().addDateSeries(series.getUUID(), index.getDates(),
                                index.getAccumulatedPercentage(), series.getLabel());
                configure(series, lineSeries);
                break;
            case DELTA_PERCENTAGE:
                String aggreagtionPeriodLabel = aggregationPeriod != null ? aggregationPeriod.toString()
                                : Messages.LabelAggregationDaily;

                double[] values = index.getDeltaPercentage();

                double[] valuesRelativePositive = new double[values.length];
                double[] valuesRelativeNegative = new double[values.length];
                for (int ii = 0; ii < values.length; ii++)
                {
                    if (values[ii] >= 0)
                    {
                        valuesRelativePositive[ii] = values[ii];
                        valuesRelativeNegative[ii] = 0;
                    }
                    else
                    {
                        valuesRelativePositive[ii] = 0;
                        valuesRelativeNegative[ii] = values[ii];
                    }
                }

                String barIDPos = aggreagtionPeriodLabel + "Positive"; //$NON-NLS-1$
                String barIDNeg = aggreagtionPeriodLabel + "Negative"; //$NON-NLS-1$

                var barSeriesPOS = getChart().addDateBarSeries(series.getUUID() + "Positive", index.getDates(), //$NON-NLS-1$
                                valuesRelativePositive, colorDeltaPositive, barIDPos);
                barSeriesPOS.setBarPadding(50);
                barSeriesPOS.setBarOverlay(true);

                var barSeriesNEG = getChart().addDateBarSeries(series.getUUID() + "Negative", index.getDates(), //$NON-NLS-1$
                                valuesRelativeNegative, colorDeltaNegative, barIDNeg);
                barSeriesNEG.setBarPadding(50);
                barSeriesNEG.setBarOverlay(true);

                var barSeries = getChart().addDateBarSeries(series.getUUID(), index.getDates(),
                                index.getDeltaPercentage(), aggreagtionPeriodLabel);
                barSeries.setVisible(false);
                barSeries.setBarOverlay(true);

                // update label, e.g. 'daily' to 'weekly'
                series.setLabel(aggreagtionPeriodLabel);
                configure(series, barSeries);

                var toolTip = getChart().getToolTip();
                toolTip.addSeriesExclude(series.getUUID() + "Positive"); //$NON-NLS-1$
                toolTip.addSeriesExclude(series.getUUID() + "Negative"); //$NON-NLS-1$
                break;
            default:
                break;
        }
    }
}
