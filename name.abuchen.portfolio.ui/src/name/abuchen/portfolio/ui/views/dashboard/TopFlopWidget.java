package name.abuchen.portfolio.ui.views.dashboard;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

import name.abuchen.portfolio.model.Dashboard;
import name.abuchen.portfolio.model.Dashboard.Widget;
import name.abuchen.portfolio.model.Security;
import name.abuchen.portfolio.money.Money;
import name.abuchen.portfolio.money.Values;
import name.abuchen.portfolio.snapshot.security.LazySecurityPerformanceRecord;
import name.abuchen.portfolio.snapshot.security.LazySecurityPerformanceSnapshot;
import name.abuchen.portfolio.ui.Messages;
import name.abuchen.portfolio.ui.UIConstants;
import name.abuchen.portfolio.ui.util.FormDataFactory;
import name.abuchen.portfolio.ui.util.LogoManager;
import name.abuchen.portfolio.ui.util.ValueColorScheme;
import name.abuchen.portfolio.ui.util.swt.StyledLabel;
import name.abuchen.portfolio.ui.views.dashboard.lists.AbstractSecurityListWidget;
import name.abuchen.portfolio.util.TextUtil;

public class TopFlopWidget extends AbstractSecurityListWidget<TopFlopWidget.PerformanceItem>
{
    public static class PerformanceItem extends AbstractSecurityListWidget.Item
    {
        private double perf;
        private Money money;
        private PerformanceType performanceType;

        public PerformanceItem(Security security, PerformanceType performanceList, Object item)
        {
            super(security);
            switch (performanceList)
            {
                case PerformanceType.DELTA -> {
                    this.perf = 0;
                    this.money = (Money) item;
                    this.performanceType = performanceList;
                }
                case PerformanceType.IRR, PerformanceType.TWR_ANNUALIZED, PerformanceType.TWR_CUMULATED -> {
                    this.perf = (double) item;
                    this.money = null;
                    this.performanceType = performanceList;
                }
                default -> {
                    this.perf = 0;
                    this.money = null;
                }
            }
        }

        public double getAmount()
        {
            return performanceType == PerformanceType.DELTA ? money.getAmount() : perf;
        }

        public Money getMoney()
        {
            return money;
        }
    }

    public enum PerformanceType
    {
        TWR_CUMULATED(Messages.LabelTTWROR), TWR_ANNUALIZED(Messages.LabelTTWROR_Annualized), IRR(
                        Messages.LabelIRR), DELTA(Messages.ColumnAbsolutePerformance);

        private String label;

        private PerformanceType(String label)
        {
            this.label = label;
        }

        @Override
        public String toString()
        {
            return label;
        }
    }

    static class PerformanceListConfig extends EnumBasedConfig<PerformanceType>
    {
        public PerformanceListConfig(WidgetDelegate<?> delegate)
        {
            super(delegate, Messages.LabelPerformanceMetric, PerformanceType.class,
                            Dashboard.Config.PERFORMANCE,
                            Policy.EXACTLY_ONE);
        }
    }

    public enum TopFlopList
    {
        TOP(Messages.LabelWinner), FLOP(Messages.LabelLoser);

        private String label;

        private TopFlopList(String label)
        {
            this.label = label;
        }

        @Override
        public String toString()
        {
            return label;
        }
    }

    static class TopFlopConfig extends EnumBasedConfig<TopFlopList>
    {
        public TopFlopConfig(WidgetDelegate<?> delegate)
        {
            super(delegate, Messages.LabelWinnerLoserList, TopFlopList.class,
                            Dashboard.Config.TOPFLOP,
                            Policy.EXACTLY_ONE);
        }
    }

    protected StyledLabel subtitle;

    public TopFlopWidget(Widget widget, DashboardData data)
    {
        super(widget, data);

        addConfig(new ReportingPeriodConfig(this));
        addConfig(new ClientFilterConfig(this));
        addConfig(new PerformanceListConfig(this));
        addConfig(new TopFlopConfig(this));
    }

    @Override
    public Supplier<List<PerformanceItem>> getUpdateTask()
    {
        var perfType = get(PerformanceListConfig.class).getValue();
        var interval = get(ReportingPeriodConfig.class).getReportingPeriod().toInterval(LocalDate.now());
        var filteredClient = get(ClientFilterConfig.class).getSelectedFilter().filter(getClient());
        var topFlop = get(TopFlopConfig.class).getValue();

        var snapshot = LazySecurityPerformanceSnapshot.create(filteredClient,
                        getDashboardData().getCurrencyConverter().with(getClient().getBaseCurrency()), interval);
        var securities = snapshot.getRecords().stream().map(r -> r.getSecurity()).toList();

        Map<Security, LazySecurityPerformanceRecord> map = snapshot.getRecords().stream()
                        .collect(Collectors.toMap(LazySecurityPerformanceRecord::getSecurity, r -> r));

        Function<LazySecurityPerformanceRecord, Object> extractor = switch (perfType)
        {
            case IRR -> r -> (Double) r.getIrr().get();
            case TWR_ANNUALIZED -> r -> (Double) r.getTrueTimeWeightedRateOfReturnAnnualized().get();
            case TWR_CUMULATED -> r -> (Double) r.getTrueTimeWeightedRateOfReturn().get();
            case DELTA -> r -> r.getDelta().get();
         };

        List<PerformanceItem> items = securities.stream()
                        .map(security -> new PerformanceItem(security, perfType, extractor.apply(map.get(security))))
                        .collect(Collectors.toList());

        return () -> sortAndLimitListEntries(items, topFlop);
    }

    private List<PerformanceItem> sortAndLimitListEntries(List<PerformanceItem> items, TopFlopList topFlop)
    {
        Comparator<PerformanceItem> comparator = switch (topFlop)
        {
            case TOP -> Comparator.comparingDouble(PerformanceItem::getAmount).reversed();
            case FLOP -> Comparator.comparingDouble(PerformanceItem::getAmount);
            default -> throw new IllegalArgumentException();
        };

        return items.stream().sorted(comparator).limit(5).toList();
    }

    @Override
    public Composite createControl(Composite parent, DashboardResources resources)
    {
        var composite = super.createControl(parent, resources);
        GridLayoutFactory.fillDefaults().numColumns(1).margins(5, 5).applyTo(composite);
        GridLayoutFactory.fillDefaults().numColumns(1).spacing(0, 2).applyTo(list);
        GridDataFactory.fillDefaults().grab(true, true).applyTo(list);

        return composite;
    }

    @Override
    protected void createTitleArea(Composite container)
    {
        super.createTitleArea(container);

        subtitle = new StyledLabel(container, SWT.NONE);
        subtitle.setBackground(container.getBackground());
        subtitle.setText(TextUtil.tooltip(get(TopFlopConfig.class).getValue().toString() + " | " //$NON-NLS-1$
                        + get(PerformanceListConfig.class).getValue().toString()));
        subtitle.setData(UIConstants.CSS.CLASS_NAME, UIConstants.CSS.HEADING2);
        GridDataFactory.fillDefaults().grab(true, true).applyTo(subtitle);
    }

    @Override
    public void update(List<PerformanceItem> items)
    {
        subtitle.setText(TextUtil.tooltip(get(TopFlopConfig.class).getValue().toString()) + " | " //$NON-NLS-1$
                        + get(PerformanceListConfig.class).getValue().toString());
        super.update(items);
    }

    @Override
    protected Composite createItemControl(Composite parent, PerformanceItem item, PerformanceItem previous)
    {
        var security = item.getSecurity();

        var composite = new Composite(parent, SWT.NONE);
        composite.setLayout(new FormLayout());

        var image = LogoManager.instance().getDefaultColumnImage(security, getClient().getSettings());
        var logo = createLabel(composite, image);
        var name = createLabel(composite, security.getName());
        var amount = createColoredLabel(composite, item, SWT.RIGHT);

        addListener(mouseUpAdapter, composite, name, amount);

        FormDataFactory.startingWith(logo).thenRight(name).right(new FormAttachment(amount, -5, SWT.LEFT));
        FormDataFactory.startingWith(amount).right(new FormAttachment(100));
        GridDataFactory.fillDefaults().grab(true, false).applyTo(composite);
        return composite;
    }

    private String buildAmountLabel(PerformanceItem item)
    {
        var perfType = get(PerformanceListConfig.class).getValue();
        var label = new StringBuilder();
        var amount = switch (perfType)
        {
            case PerformanceType.IRR, PerformanceType.TWR_ANNUALIZED -> Values.AnnualizedPercent2
                            .format(item.getAmount());
            case PerformanceType.TWR_CUMULATED -> Values.Percent2.format(item.getAmount());
            case PerformanceType.DELTA -> Values.Money.format(item.getMoney(), getClient().getBaseCurrency());
        };
        label.append(" "); //$NON-NLS-1$
        label.append(amount);
        return label.toString();
    }

    @Override
    protected void createEmptyControl(Composite parent)
    {
        // nothing to do
    }

    protected Label createColoredLabel(Composite composite, PerformanceItem item, int style)
    {
        var amount = item.getAmount();
        Color color;
        if (amount == 0)
            color = null;
        else if (amount > 0)
            color = ValueColorScheme.current().positiveForeground();
        else
            color = ValueColorScheme.current().negativeForeground();

        var label = new Label(composite, style);
        label.setText(TextUtil.tooltip(Objects.toString(buildAmountLabel(item), ""))); //$NON-NLS-1$
        label.setForeground(color);
        return label;
    }
}
