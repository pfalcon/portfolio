package name.abuchen.portfolio.ui.views;

import static name.abuchen.portfolio.util.CollectorsUtil.toMutableList;

import java.io.IOException;
import java.text.MessageFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.layout.TableColumnLayout;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ColumnViewerToolTipSupport;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.jface.window.ToolTip;
import org.eclipse.jface.window.Window;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Shell;

import com.google.common.collect.Streams;

import name.abuchen.portfolio.model.AccountTransaction;
import name.abuchen.portfolio.model.Classification;
import name.abuchen.portfolio.model.Classification.Assignment;
import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.InvestmentPlan;
import name.abuchen.portfolio.model.PortfolioTransaction;
import name.abuchen.portfolio.model.Security;
import name.abuchen.portfolio.model.SecurityPrice;
import name.abuchen.portfolio.model.Taxonomy;
import name.abuchen.portfolio.model.Watchlist;
import name.abuchen.portfolio.money.Values;
import name.abuchen.portfolio.online.Factory;
import name.abuchen.portfolio.online.QuoteFeed;
import name.abuchen.portfolio.online.SecuritySearchProvider.ResultItem;
import name.abuchen.portfolio.online.impl.PortfolioReportNet;
import name.abuchen.portfolio.snapshot.QuoteQualityMetrics;
import name.abuchen.portfolio.snapshot.ReportingPeriod;
import name.abuchen.portfolio.ui.Images;
import name.abuchen.portfolio.ui.Messages;
import name.abuchen.portfolio.ui.dialogs.transactions.AccountTransactionDialog;
import name.abuchen.portfolio.ui.dialogs.transactions.InvestmentPlanDialog;
import name.abuchen.portfolio.ui.dialogs.transactions.OpenDialogAction;
import name.abuchen.portfolio.ui.dialogs.transactions.SecurityTransactionDialog;
import name.abuchen.portfolio.ui.dialogs.transactions.SecurityTransferDialog;
import name.abuchen.portfolio.ui.dnd.ImportFromFileDropAdapter;
import name.abuchen.portfolio.ui.dnd.ImportFromURLDropAdapter;
import name.abuchen.portfolio.ui.dnd.SecurityDragListener;
import name.abuchen.portfolio.ui.dnd.SecurityTransfer;
import name.abuchen.portfolio.ui.editor.AbstractFinanceView;
import name.abuchen.portfolio.ui.jobs.UpdateQuotesJob;
import name.abuchen.portfolio.ui.util.BookmarkMenu;
import name.abuchen.portfolio.ui.util.Colors;
import name.abuchen.portfolio.ui.util.ConfirmActionWithSelection;
import name.abuchen.portfolio.ui.util.ContextMenu;
import name.abuchen.portfolio.ui.util.LogoManager;
import name.abuchen.portfolio.ui.util.SimpleAction;
import name.abuchen.portfolio.ui.util.viewers.BooleanEditingSupport;
import name.abuchen.portfolio.ui.util.viewers.Column;
import name.abuchen.portfolio.ui.util.viewers.ColumnEditingSupport;
import name.abuchen.portfolio.ui.util.viewers.ColumnEditingSupport.ModificationListener;
import name.abuchen.portfolio.ui.util.viewers.ColumnViewerSorter;
import name.abuchen.portfolio.ui.util.viewers.CopyPasteSupport;
import name.abuchen.portfolio.ui.util.viewers.DateLabelProvider;
import name.abuchen.portfolio.ui.util.viewers.NumberColorLabelProvider;
import name.abuchen.portfolio.ui.util.viewers.ParameterizedColumnLabelProvider;
import name.abuchen.portfolio.ui.util.viewers.ReportingPeriodColumnOptions;
import name.abuchen.portfolio.ui.util.viewers.ShowHideColumnHelper;
import name.abuchen.portfolio.ui.util.viewers.StringEditingSupport;
import name.abuchen.portfolio.ui.views.columns.AttributeColumn;
import name.abuchen.portfolio.ui.views.columns.DistanceFromAllTimeHighColumn;
import name.abuchen.portfolio.ui.views.columns.DistanceFromMovingAverageColumn;
import name.abuchen.portfolio.ui.views.columns.DividendPaymentColumns;
import name.abuchen.portfolio.ui.views.columns.IsinColumn;
import name.abuchen.portfolio.ui.views.columns.NoteColumn;
import name.abuchen.portfolio.ui.views.columns.QuoteRangeColumn;
import name.abuchen.portfolio.ui.views.columns.SymbolColumn;
import name.abuchen.portfolio.ui.views.columns.TaxonomyColumn;
import name.abuchen.portfolio.ui.views.columns.WknColumn;
import name.abuchen.portfolio.ui.wizards.events.CustomEventWizard;
import name.abuchen.portfolio.ui.wizards.security.EditSecurityDialog;
import name.abuchen.portfolio.ui.wizards.security.FindQuoteProviderDialog;
import name.abuchen.portfolio.ui.wizards.splits.StockSplitWizard;
import name.abuchen.portfolio.util.Interval;
import name.abuchen.portfolio.util.Pair;

public final class SecuritiesTable implements ModificationListener
{
    private class LinkToPortfolioReport extends Action
    {
        private Security security;

        public LinkToPortfolioReport(Security security)
        {
            super(Messages.LabelLinkToPortfolioReportNet);
            this.security = security;
        }

        @Override
        public void run()
        {
            InputDialog dlg = new InputDialog(Display.getCurrent().getActiveShell(), Messages.LabelInfo,
                            "Portfolio Report URL", "https://www.portfolio-report.net/", //$NON-NLS-1$//$NON-NLS-2$
                            newText -> ImportFromURLDropAdapter.URL_PATTERN.matcher(newText).matches() ? null
                                            : MessageFormat.format(Messages.DesktopAPIIllegalURL, newText));
            if (dlg.open() != Window.OK)
                return;

            String url = dlg.getValue();

            Matcher matcher = ImportFromURLDropAdapter.URL_PATTERN.matcher(url);
            if (!matcher.matches())
                return;

            String onlineId = matcher.group(1);

            try
            {
                Optional<ResultItem> result = new PortfolioReportNet().getUpdatedValues(onlineId);
                if (!result.isPresent())
                {
                    MessageDialog.openError(Display.getDefault().getActiveShell(), Messages.LabelError,
                                    MessageFormat.format(Messages.MsgErrorNoInvestmentVehicleFoundAtURL, url));
                }
                else
                {
                    security.setOnlineId(onlineId);
                    security.getEphemeralData().touchFeedConfigurationChanged();
                    PortfolioReportNet.updateWith(security, result.get());
                }
            }
            catch (IOException e)
            {
                MessageDialog.openError(Display.getDefault().getActiveShell(), Messages.LabelError, e.getMessage());
            }
        }
    }

    private AbstractFinanceView view;

    private Watchlist watchlist;

    private TableViewer securities;

    private Map<Security, QuoteQualityMetrics> metricsCache = new HashMap<Security, QuoteQualityMetrics>()
    {
        private static final long serialVersionUID = 1L;

        @Override
        public QuoteQualityMetrics get(Object key)
        {
            return super.computeIfAbsent((Security) key, QuoteQualityMetrics::new);
        }
    };

    private ShowHideColumnHelper support;

    public SecuritiesTable(Composite parent, AbstractFinanceView view)
    {
        this.view = view;

        Composite container = new Composite(parent, SWT.NONE);
        TableColumnLayout layout = new TableColumnLayout();
        container.setLayout(layout);

        this.securities = new TableViewer(container, SWT.FULL_SELECTION | SWT.MULTI);

        ImportFromURLDropAdapter.attach(this.securities.getControl(), view.getPart());
        ImportFromFileDropAdapter.attach(this.securities.getControl(), view.getPart());

        ColumnEditingSupport.prepare(view.getEditorActivationState(), securities);
        ColumnViewerToolTipSupport.enableFor(securities, ToolTip.NO_RECREATE);
        CopyPasteSupport.enableFor(securities);

        support = new ShowHideColumnHelper(SecuritiesTable.class.getName(), getClient(), view.getPreferenceStore(),
                        securities, layout);

        addMasterDataColumns();
        addColumnLatestPrice();
        addDeltaColumn();
        addDeltaAmountColumn();
        addColumnDateOfLatestPrice();
        addColumnDateOfLatestHistoricalPrice();
        addQuoteDeltaColumn();
        support.addColumn(new DistanceFromMovingAverageColumn(LocalDate::now));
        support.addColumn(new DistanceFromAllTimeHighColumn(LocalDate::now,
                        view.getPart().getReportingPeriods().stream().collect(toMutableList())));
        support.addColumn(new QuoteRangeColumn(LocalDate::now,
                        view.getPart().getReportingPeriods().stream().collect(toMutableList())));

        for (Taxonomy taxonomy : getClient().getTaxonomies())
        {
            Column column = new TaxonomyColumn(taxonomy);
            column.setVisible(false);
            support.addColumn(column);
        }

        addAttributeColumns();
        addDividendColumns();
        addQuoteFeedColumns();
        addDataQualityColumns();

        support.createColumns(true);

        securities.getTable().setHeaderVisible(true);
        securities.getTable().setLinesVisible(true);

        securities.setContentProvider(ArrayContentProvider.getInstance());

        securities.addDragSupport(DND.DROP_MOVE, //
                        new Transfer[] { SecurityTransfer.getTransfer() }, //
                        new SecurityDragListener(securities));

        hookKeyListener();

        securities.refresh();

        hookContextMenu();
    }

    private void addDividendColumns()
    {
        DividendPaymentColumns.createFor(getClient()).forEach(support::addColumn);
    }

    private void addMasterDataColumns()
    {
        Column column = new Column("0", Messages.ColumnName, SWT.LEFT, 400); //$NON-NLS-1$
        column.setLabelProvider(new ColumnLabelProvider()
        {
            @Override
            public String getText(Object e)
            {
                return ((Security) e).getName();
            }

            @Override
            public Image getImage(Object e)
            {
                return LogoManager.instance().getDefaultColumnImage(e, getClient().getSettings());
            }
        });
        ColumnViewerSorter.create(Security.class, "name").attachTo(column, SWT.UP); //$NON-NLS-1$
        new StringEditingSupport(Security.class, "name").setMandatory(true).addListener(this).attachTo(column); //$NON-NLS-1$
        support.addColumn(column);

        column = new NoteColumn();
        column.getEditingSupport().addListener(this);
        support.addColumn(column);

        column = new IsinColumn("1"); //$NON-NLS-1$
        column.getEditingSupport().addListener(this);
        support.addColumn(column);

        column = new SymbolColumn("2"); //$NON-NLS-1$
        column.getEditingSupport().addListener(this);
        support.addColumn(column);

        column = new WknColumn("7"); //$NON-NLS-1$
        column.getEditingSupport().addListener(this);
        support.addColumn(column);

        column = new Column("currency", Messages.ColumnCurrency, SWT.LEFT, 60); //$NON-NLS-1$
        column.setLabelProvider(new ColumnLabelProvider()
        {
            @Override
            public String getText(Object element)
            {
                return ((Security) element).getCurrencyCode();
            }
        });
        column.setSorter(ColumnViewerSorter.createIgnoreCase(element -> ((Security) element).getCurrencyCode()));
        column.setVisible(false);
        support.addColumn(column);

        column = new Column("targetCurrency", Messages.ColumnTargetCurrency, SWT.LEFT, 60); //$NON-NLS-1$
        column.setDescription(Messages.ColumnTargetCurrencyToolTip);
        column.setLabelProvider(new ColumnLabelProvider()
        {
            @Override
            public String getText(Object element)
            {
                return ((Security) element).getTargetCurrencyCode();
            }
        });
        column.setSorter(ColumnViewerSorter.createIgnoreCase(element -> ((Security) element).getTargetCurrencyCode()));
        column.setVisible(false);
        support.addColumn(column);

        column = new Column("8", Messages.ColumnRetired, SWT.LEFT, 40); //$NON-NLS-1$
        column.setLabelProvider(new ColumnLabelProvider()
        {
            @Override
            public String getText(Object e)
            {
                return ""; //$NON-NLS-1$
            }

            @Override
            public Image getImage(Object e)
            {
                return ((Security) e).isRetired() ? Images.CHECK.image() : null;
            }
        });
        column.setSorter(ColumnViewerSorter.create(Security.class, "retired")); //$NON-NLS-1$
        new BooleanEditingSupport(Security.class, "retired").addListener(this).attachTo(column); //$NON-NLS-1$
        column.setVisible(false);
        support.addColumn(column);
    }

    private void addColumnLatestPrice() // NOSONAR
    {
        Column column = new Column("4", Messages.ColumnLatest, SWT.RIGHT, 60); //$NON-NLS-1$
        column.setMenuLabel(Messages.ColumnLatest_MenuLabel);
        column.setLabelProvider(new ColumnLabelProvider()
        {
            @Override
            public String getText(Object e)
            {
                Security security = (Security) e;
                SecurityPrice latest = security.getSecurityPrice(LocalDate.now());
                if (latest == null)
                    return null;

                if (security.getCurrencyCode() == null)
                    return Values.Quote.format(latest.getValue());
                else
                    return Values.Quote.format(security.getCurrencyCode(), latest.getValue(),
                                    getClient().getBaseCurrency());
            }
        });
        column.setSorter(ColumnViewerSorter.create((o1, o2) -> {
            SecurityPrice p1 = ((Security) o1).getSecurityPrice(LocalDate.now());
            SecurityPrice p2 = ((Security) o2).getSecurityPrice(LocalDate.now());

            if (p1 == null)
                return p2 == null ? 0 : -1;
            if (p2 == null)
                return 1;

            return Long.compare(p1.getValue(), p2.getValue());
        }));
        support.addColumn(column);
    }

    private void addDeltaColumn() // NOSONAR
    {
        Column column;
        column = new Column("5", Messages.ColumnChangeOnPrevious, SWT.RIGHT, 80); //$NON-NLS-1$
        column.setMenuLabel(Messages.ColumnChangeOnPrevious_MenuLabel);
        column.setLabelProvider(new NumberColorLabelProvider<>(Values.Percent2, element -> {
            Optional<Pair<SecurityPrice, SecurityPrice>> previous = ((Security) element).getLatestTwoSecurityPrices();
            if (previous.isPresent())
            {
                double latestQuote = previous.get().getLeft().getValue();
                double previousQuote = previous.get().getRight().getValue();
                return (latestQuote - previousQuote) / previousQuote;
            }
            else
            {
                return null;
            }
        }, element -> {
            Optional<Pair<SecurityPrice, SecurityPrice>> previous = ((Security) element).getLatestTwoSecurityPrices();
            if (previous.isPresent())
            {
                return Messages.ColumnLatestPrice + ": " //$NON-NLS-1$
                                + MessageFormat.format(Messages.TooltipQuoteAtDate,
                                                Values.Quote.format(previous.get().getLeft().getValue()),
                                                Values.Date.format(previous.get().getLeft().getDate()))
                                + "\n" // //$NON-NLS-1$
                                + Messages.ColumnPreviousPrice + ": " //$NON-NLS-1$
                                + MessageFormat.format(Messages.TooltipQuoteAtDate,
                                                Values.Quote.format(previous.get().getRight().getValue()),
                                                Values.Date.format(previous.get().getRight().getDate()));
            }
            else
            {
                return null;
            }
        }));
        column.setSorter(ColumnViewerSorter.create((o1, o2) -> { // NOSONAR

            Optional<Pair<SecurityPrice, SecurityPrice>> previous1 = ((Security) o1).getLatestTwoSecurityPrices();
            Optional<Pair<SecurityPrice, SecurityPrice>> previous2 = ((Security) o2).getLatestTwoSecurityPrices();

            if (!previous1.isPresent() && !previous2.isPresent())
                return 0;
            if (!previous1.isPresent() && previous2.isPresent())
                return -1;
            if (previous1.isPresent() && !previous2.isPresent())
                return 1;

            double latestQuote1 = previous1.get().getLeft().getValue();
            double previousQuote1 = previous1.get().getRight().getValue();
            double v1 = (latestQuote1 - previousQuote1) / previousQuote1;

            double latestQuote2 = previous2.get().getLeft().getValue();
            double previousQuote2 = previous2.get().getRight().getValue();
            double v2 = (latestQuote2 - previousQuote2) / previousQuote2;

            return Double.compare(v1, v2);
        }));
        support.addColumn(column);
    }

    private void addDeltaAmountColumn() // NOSONAR
    {
        Column column;
        column = new Column("changeonpreviousamount", Messages.ColumnChangeOnPreviousAmount, SWT.RIGHT, 80); //$NON-NLS-1$
        column.setMenuLabel(Messages.ColumnChangeOnPrevious_MenuLabelAmount);
        column.setLabelProvider(new NumberColorLabelProvider<>(Values.CalculatedQuote, element -> {
            Optional<Pair<SecurityPrice, SecurityPrice>> previous = ((Security) element).getLatestTwoSecurityPrices();
            if (previous.isPresent())
            {
                double latestQuote = previous.get().getLeft().getValue();
                double previousQuote = previous.get().getRight().getValue();
                return (long) (latestQuote - previousQuote);
            }
            else
            {
                return null;
            }
        }, element -> {
            Optional<Pair<SecurityPrice, SecurityPrice>> previous = ((Security) element).getLatestTwoSecurityPrices();
            if (previous.isPresent())
            {
                return Messages.ColumnLatestPrice + ": " //$NON-NLS-1$
                                + MessageFormat.format(Messages.TooltipQuoteAtDate,
                                                Values.Quote.format(previous.get().getLeft().getValue()),
                                                Values.Date.format(previous.get().getLeft().getDate()))
                                + "\n" // //$NON-NLS-1$
                                + Messages.ColumnPreviousPrice + ": " //$NON-NLS-1$
                                + MessageFormat.format(Messages.TooltipQuoteAtDate,
                                                Values.Quote.format(previous.get().getRight().getValue()),
                                                Values.Date.format(previous.get().getRight().getDate()));
            }
            else
            {
                return null;
            }
        }));
        column.setSorter(ColumnViewerSorter.create((o1, o2) -> { // NOSONAR

            Optional<Pair<SecurityPrice, SecurityPrice>> previous1 = ((Security) o1).getLatestTwoSecurityPrices();
            Optional<Pair<SecurityPrice, SecurityPrice>> previous2 = ((Security) o2).getLatestTwoSecurityPrices();

            if (!previous1.isPresent() && !previous2.isPresent())
                return 0;
            if (!previous1.isPresent() && previous2.isPresent())
                return -1;
            if (previous1.isPresent() && !previous2.isPresent())
                return 1;

            double latestQuote1 = previous1.get().getLeft().getValue();
            double previousQuote1 = previous1.get().getRight().getValue();
            double v1 = latestQuote1 - previousQuote1;

            double latestQuote2 = previous2.get().getLeft().getValue();
            double previousQuote2 = previous2.get().getRight().getValue();
            double v2 = latestQuote2 - previousQuote2;

            return Double.compare(v1, v2);
        }));
        support.addColumn(column);
    }

    private void addColumnDateOfLatestPrice() // NOSONAR
    {
        Column column;
        column = new Column("9", Messages.ColumnLatestDate, SWT.LEFT, 80); //$NON-NLS-1$
        column.setMenuLabel(Messages.ColumnLatestDate_MenuLabel);

        Function<Object, LocalDate> dataProvider = element -> {
            SecurityPrice latest = ((Security) element).getSecurityPrice(LocalDate.now());
            return latest != null ? latest.getDate() : null;
        };

        column.setLabelProvider(new DateLabelProvider(dataProvider)
        {
            @Override
            public Color getBackground(Object element)
            {
                Security security = (Security) element;
                SecurityPrice latest = security.getSecurityPrice(LocalDate.now());
                if (latest == null)
                    return null;

                String feed = security.getLatestFeed() != null ? security.getLatestFeed() : security.getFeed();
                if (QuoteFeed.MANUAL.equals(feed))
                    return null;

                LocalDate sevenDaysAgo = LocalDate.now().minusDays(7);
                return latest.getDate().isBefore(sevenDaysAgo) ? Colors.theme().warningBackground() : null;
            }
        });
        column.setSorter(ColumnViewerSorter.create(dataProvider::apply));
        support.addColumn(column);
    }

    private void addColumnDateOfLatestHistoricalPrice() // NOSONAR
    {
        Column column = new Column("10", Messages.ColumnLatestHistoricalDate, SWT.LEFT, 80); //$NON-NLS-1$
        column.setMenuLabel(Messages.ColumnLatestHistoricalDate_MenuLabel);
        column.setGroupLabel(Messages.GroupLabelDataQuality);

        Function<Object, LocalDate> dataProvider = element -> {
            List<SecurityPrice> prices = ((Security) element).getPrices();
            if (prices.isEmpty())
                return null;

            SecurityPrice latest = prices.get(prices.size() - 1);
            return latest != null ? latest.getDate() : null;
        };

        column.setLabelProvider(new DateLabelProvider(dataProvider)
        {
            @Override
            public Color getBackground(Object element)
            {
                Security security = (Security) element;
                List<SecurityPrice> prices = security.getPrices();
                if (prices.isEmpty())
                    return null;

                if (QuoteFeed.MANUAL.equals(security.getFeed()))
                    return null;

                SecurityPrice latest = prices.get(prices.size() - 1);
                if (!((Security) element).isRetired() && latest.getDate().isBefore(LocalDate.now().minusDays(7)))
                    return Colors.theme().warningBackground();
                else
                    return null;
            }
        });
        column.setSorter(ColumnViewerSorter.create(dataProvider::apply));
        support.addColumn(column);
    }

    private void addQuoteDeltaColumn() // NOSONAR
    {
        // create a modifiable copy as all menus share the same list of
        // reporting periods
        List<ReportingPeriod> options = view.getPart().getReportingPeriods().stream().collect(toMutableList());

        Column column = new Column("delta-w-period", Messages.ColumnQuoteChange, SWT.RIGHT, 80); //$NON-NLS-1$
        column.setOptions(new ReportingPeriodColumnOptions(Messages.ColumnQuoteChange_Option, options));
        column.setDescription(Messages.ColumnQuoteChange_Description);
        column.setLabelProvider(() -> new QuoteReportingPeriodLabelProvider());
        column.setVisible(false);
        column.setSorter(ColumnViewerSorter.create((o1, o2) -> {
            ReportingPeriod option = (ReportingPeriod) ColumnViewerSorter.SortingContext.getColumnOption();

            Double v1 = QuoteReportingPeriodLabelProvider.getQuoteChange((Security) o1, option);
            Double v2 = QuoteReportingPeriodLabelProvider.getQuoteChange((Security) o2, option);

            if (v1 == null && v2 == null)
                return 0;
            else if (v1 == null)
                return -1;
            else if (v2 == null)
                return 1;

            return Double.compare(v1.doubleValue(), v2.doubleValue());
        }));
        support.addColumn(column);
    }

    private void addAttributeColumns()
    {
        AttributeColumn.createFor(getClient(), Security.class) //
                        .forEach(column -> {
                            column.getEditingSupport().addListener(this);
                            support.addColumn(column);
                        });
    }

    private void addQuoteFeedColumns()
    {
        Function<Object, String> quoteFeed = e -> {
            String feedId = ((Security) e).getFeed();
            if (feedId == null || feedId.isEmpty())
                return null;

            QuoteFeed feed = Factory.getQuoteFeedProvider(feedId);
            return feed != null ? feed.getName() : null;
        };

        Column column = new Column("qf-historic", Messages.ColumnQuoteFeedHistoric, SWT.LEFT, 200); //$NON-NLS-1$
        column.setGroupLabel(Messages.GroupLabelQuoteFeed);
        column.setVisible(false);
        column.setLabelProvider(new ColumnLabelProvider()
        {
            @Override
            public String getText(Object e)
            {
                return quoteFeed.apply(e);
            }
        });
        column.setSorter(ColumnViewerSorter.createIgnoreCase(quoteFeed::apply));
        support.addColumn(column);

        Function<Object, String> latestQuoteFeed = e -> {
            Security security = (Security) e;
            String feedId = security.getLatestFeed();
            if (feedId == null || feedId.isEmpty())
                return security.getFeed() != null ? Messages.EditWizardOptionSameAsHistoricalQuoteFeed : null;

            QuoteFeed feed = Factory.getQuoteFeedProvider(feedId);
            return feed != null ? feed.getName() : null;
        };

        column = new Column("qf-latest", Messages.ColumnQuoteFeedLatest, SWT.LEFT, 200); //$NON-NLS-1$
        column.setGroupLabel(Messages.GroupLabelQuoteFeed);
        column.setVisible(false);
        column.setLabelProvider(new ColumnLabelProvider()
        {
            @Override
            public String getText(Object e)
            {
                return latestQuoteFeed.apply(e);
            }
        });
        column.setSorter(ColumnViewerSorter.createIgnoreCase(latestQuoteFeed::apply));
        support.addColumn(column);

        column = new Column("url-history", Messages.ColumnFeedURLHistoric, SWT.LEFT, 200); //$NON-NLS-1$
        column.setGroupLabel(Messages.GroupLabelQuoteFeed);
        column.setVisible(false);
        column.setLabelProvider(new ColumnLabelProvider()
        {
            @Override
            public String getText(Object e)
            {
                Security security = (Security) e;
                return security.getFeedURL();
            }
        });
        column.setSorter(ColumnViewerSorter.createIgnoreCase(s -> ((Security) s).getFeedURL()));
        support.addColumn(column);

        column = new Column("url-latest", Messages.ColumnFeedURLLatest, SWT.LEFT, 200); //$NON-NLS-1$
        column.setGroupLabel(Messages.GroupLabelQuoteFeed);
        column.setVisible(false);
        column.setLabelProvider(new ColumnLabelProvider()
        {
            @Override
            public String getText(Object e)
            {
                Security security = (Security) e;
                return security.getLatestFeedURL();
            }
        });
        column.setSorter(ColumnViewerSorter.createIgnoreCase(s -> ((Security) s).getLatestFeedURL()));
        support.addColumn(column);
    }

    private void addDataQualityColumns()
    {
        // ColumnLatestHistoricalDate
        Column column = new Column("q-date-first-historic", Messages.ColumnDateFirstHistoricalQuote, SWT.LEFT, 80); //$NON-NLS-1$
        column.setMenuLabel(Messages.ColumnDateFirstHistoricalQuote_MenuLabel);
        column.setGroupLabel(Messages.GroupLabelDataQuality);

        Function<Object, LocalDate> dataProvider = element -> {
            List<SecurityPrice> prices = ((Security) element).getPrices();
            return prices.isEmpty() ? null : prices.get(0).getDate();
        };

        column.setLabelProvider(new DateLabelProvider(dataProvider));
        column.setSorter(ColumnViewerSorter.create(dataProvider::apply));
        support.addColumn(column);

        column = new Column("qqm-completeness", Messages.ColumnMetricCompleteness, SWT.RIGHT, 80); //$NON-NLS-1$
        column.setGroupLabel(Messages.GroupLabelDataQuality);
        column.setDescription(Messages.ColumnMetricCompleteness_Description);
        column.setVisible(false);
        column.setLabelProvider(new ColumnLabelProvider()
        {
            @Override
            public String getText(Object e)
            {
                return Values.Percent2.format(metricsCache.get((Security) e).getCompleteness());
            }
        });
        column.setSorter(ColumnViewerSorter.create(o -> metricsCache.get((Security) o).getCompleteness()));
        support.addColumn(column);

        column = new Column("qqm-expected", Messages.ColumnMetricExpectedNumberOfQuotes, SWT.RIGHT, 80); //$NON-NLS-1$
        column.setGroupLabel(Messages.GroupLabelDataQuality);
        column.setVisible(false);
        column.setLabelProvider(new ColumnLabelProvider()
        {
            @Override
            public String getText(Object e)
            {
                return Integer.toString(metricsCache.get((Security) e).getExpectedNumberOfQuotes());
            }
        });
        column.setSorter(ColumnViewerSorter.create(o -> metricsCache.get((Security) o).getExpectedNumberOfQuotes()));
        support.addColumn(column);

        column = new Column("qqm-actual", Messages.ColumnMetricActualNumberOfQuotes, SWT.RIGHT, 80); //$NON-NLS-1$
        column.setGroupLabel(Messages.GroupLabelDataQuality);
        column.setVisible(false);
        column.setLabelProvider(new ColumnLabelProvider()
        {
            @Override
            public String getText(Object e)
            {
                return Integer.toString(metricsCache.get((Security) e).getActualNumberOfQuotes());
            }
        });
        column.setSorter(ColumnViewerSorter.create(o -> metricsCache.get((Security) o).getActualNumberOfQuotes()));
        support.addColumn(column);

        column = new Column("qqm-missing", Messages.ColumnMetricNumberOfMissingQuotes, SWT.RIGHT, 80); //$NON-NLS-1$
        column.setGroupLabel(Messages.GroupLabelDataQuality);
        column.setVisible(false);
        column.setLabelProvider(new ColumnLabelProvider()
        {
            @Override
            public String getText(Object e)
            {
                QuoteQualityMetrics metrics = metricsCache.get((Security) e);
                return Integer.toString(metrics.getExpectedNumberOfQuotes() - metrics.getActualNumberOfQuotes());
            }
        });
        column.setSorter(ColumnViewerSorter.create(e -> {
            QuoteQualityMetrics metrics = metricsCache.get((Security) e);
            return metrics.getExpectedNumberOfQuotes() - metrics.getActualNumberOfQuotes();
        }));
        support.addColumn(column);
    }

    public void addSelectionChangedListener(ISelectionChangedListener listener)
    {
        this.securities.addSelectionChangedListener(listener);
    }

    public void addFilter(ViewerFilter filter)
    {
        this.securities.addFilter(filter);
    }

    public void setInput(List<Security> securities)
    {
        this.securities.setInput(securities);
        this.watchlist = null;
    }

    public void setInput(Watchlist watchlist)
    {
        this.securities.setInput(watchlist.getSecurities());
        this.watchlist = watchlist;
    }

    public void refresh(Security security)
    {
        this.metricsCache.remove(security);
        this.securities.refresh(security, true);
    }

    public void refresh(boolean updateLabels)
    {
        try
        {
            securities.getControl().setRedraw(false);

            if (updateLabels)
                metricsCache.clear();
            securities.refresh(updateLabels);
            securities.setSelection(securities.getSelection());

        }
        finally
        {
            securities.getControl().setRedraw(true);
        }
    }

    @Override
    public void onModified(Object element, Object newValue, Object oldValue)
    {
        markDirty();
    }

    public void updateQuotes(Security security)
    {
        new UpdateQuotesJob(getClient(), security).schedule();
    }

    public TableViewer getTableViewer()
    {
        return securities;
    }

    public ShowHideColumnHelper getColumnHelper()
    {
        return support;
    }

    //
    // private
    //

    private Client getClient()
    {
        return view.getClient();
    }

    private Shell getShell()
    {
        return securities.getTable().getShell();
    }

    private void markDirty()
    {
        view.markDirty();
    }

    private void hookKeyListener()
    {
        securities.getControl().addKeyListener(new KeyAdapter()
        {
            @Override
            public void keyPressed(KeyEvent e)
            {
                if (e.keyCode == 'e' && e.stateMask == SWT.MOD1)
                    new EditSecurityAction().run();
            }
        });
    }

    private void hookContextMenu()
    {
        MenuManager menuMgr = new MenuManager("#PopupMenu"); //$NON-NLS-1$
        menuMgr.setRemoveAllWhenShown(true);
        menuMgr.addMenuListener(this::fillContextMenu);

        Menu contextMenu = menuMgr.createContextMenu(securities.getTable());
        securities.getTable().setMenu(contextMenu);
        securities.getTable().setData(ContextMenu.DEFAULT_MENU, contextMenu);

        securities.getTable().addDisposeListener(e -> {
            if (contextMenu != null)
                contextMenu.dispose();
        });
    }

    @SuppressWarnings("unchecked")
    private void fillContextMenu(IMenuManager manager)
    {
        IStructuredSelection selection = (IStructuredSelection) securities.getSelection();

        if (selection.isEmpty())
            return;

        if (selection.size() == 1)
        {
            Security security = (Security) selection.getFirstElement();

            // only if the security has a currency code, it can be bought
            if (security.getCurrencyCode() != null)
                fillTransactionContextMenu(manager, security);

            manager.add(new EditSecurityAction());

            manager.add(new Separator());
            new QuotesContextMenu(this.view).menuAboutToShow(manager, security);
        }

        manager.add(new Separator());

        manager.add(new BookmarkMenu(view.getPart(), selection.toList()));

        if (selection.size() == 1)
        {
            Security security = (Security) selection.getFirstElement();
            if (security.getOnlineId() == null)
            {
                manager.add(new Separator());
                manager.add(new LinkToPortfolioReport(security));
            }

            manager.add(new Separator());

            if (LogoManager.instance().hasCustomLogo(security, getClient().getSettings()))
            {
                manager.add(new SimpleAction(Messages.LabelRemoveLogo, a -> {
                    LogoManager.instance().clearCustomLogo(security, getClient().getSettings());
                    markDirty();
                }));
            }
        }

        manager.add(new Separator());
        // update quotes for multiple securities
        if (selection.size() > 1)
        {
            manager.add(new SimpleAction(
                            MessageFormat.format(Messages.SecurityMenuUpdateQuotesMultipleSecurities, selection.size()),
                            a -> new UpdateQuotesJob(getClient(),
                                            selection.toList().stream().map(Security.class::cast).toList())
                                                            .schedule()));

            manager.add(new SimpleAction(Messages.LabelSearchForQuoteFeeds + "...", //$NON-NLS-1$
                            a -> Display.getDefault().asyncExec(() -> {
                                FindQuoteProviderDialog dialog = new FindQuoteProviderDialog(getShell(), getClient(),
                                                selection.toList().stream().map(Security.class::cast).toList());
                                dialog.open();
                            })));

        }

        // if any retired security in selection, add "unretire/activate all"
        // option
        if (Streams.stream((Iterable<?>) selection).anyMatch(s -> ((Security) s).isRetired()))
        {
            manager.add(new ConfirmActionWithSelection(Messages.SecurityMenuSetSingleSecurityActive,
                            Messages.SecurityMenuSetMultipleSecurityActive,
                            MessageFormat.format(Messages.SecurityMenuSetSingleSecurityActiveConfirm,
                                            selection.getFirstElement()),
                            Messages.SecurityMenuSetMultipleSecurityActiveConfirm, selection,
                            (s, a) -> setRetireStatus(selection, false)));
        }

        // if any active (non-retired) security in selection, add "retire all"
        // option
        if (Streams.stream((Iterable<?>) selection).anyMatch(s -> !((Security) s).isRetired()))
        {
            manager.add(new ConfirmActionWithSelection(Messages.SecurityMenuSetSingleSecurityInactive,
                            Messages.SecurityMenuSetMultipleSecurityInactive,
                            MessageFormat.format(Messages.SecurityMenuSetSingleSecurityInactiveConfirm,
                                            selection.getFirstElement()),
                            Messages.SecurityMenuSetMultipleSecurityInactiveConfirm, selection,
                            (s, a) -> setRetireStatus(selection, true)));
        }

        if (watchlist == null)
        {
            if (selection.size() == 1)
                manager.add(new SimpleAction(Messages.LabelDuplicateSecurity, a -> {
                    Security source = (Security) selection.getFirstElement();
                    Security target = source.deepCopy();

                    // add security
                    getClient().addSecurity(target);

                    // copy attributes
                    target.setAttributes(source.getAttributes().copy());

                    // copy taxonomy assignments
                    getClient().getTaxonomies().stream().forEach(taxonomy -> taxonomy.foreach(new Taxonomy.Visitor()
                    {
                        @Override
                        public void visit(Classification classification, Assignment assignment)
                        {
                            if (assignment.getInvestmentVehicle() == source)
                            {
                                Assignment newAssignment = assignment.copyWith(target);
                                List<Assignment> children = classification.getAssignments();
                                newAssignment.setRank(children.get(children.size() - 1).getRank() + 1);
                                classification.addAssignment(newAssignment);
                            }
                        }
                    }));

                    markDirty();
                }));

            manager.add(new ConfirmActionWithSelection(Messages.SecurityMenuDeleteSingleSecurity,
                            Messages.SecurityMenuDeleteMultipleSecurity,
                            MessageFormat.format(Messages.SecurityMenuDeleteSingleSecurityConfirm,
                                            selection.getFirstElement()),
                            Messages.SecurityMenuDeleteMultipleSecurityConfirm, selection,
                            (s, a) -> deleteSecurity(selection)));
        }
        else
        {
            manager.add(new Action(MessageFormat.format(Messages.SecurityMenuRemoveFromWatchlist, watchlist.getName()))
            {
                @Override
                public void run()
                {
                    for (Object security : selection.toArray())
                        watchlist.getSecurities().remove(security);

                    markDirty();
                    securities.setInput(watchlist.getSecurities());
                }
            });
        }
    }

    private void fillTransactionContextMenu(IMenuManager manager, Security security)
    {
        new OpenDialogAction(view, Messages.SecurityMenuBuy + "...") //$NON-NLS-1$
                        .type(SecurityTransactionDialog.class) //
                        .parameters(PortfolioTransaction.Type.BUY) //
                        .with(security) //
                        .addTo(manager);

        new OpenDialogAction(view, Messages.SecurityMenuSell + "...") //$NON-NLS-1$
                        .type(SecurityTransactionDialog.class) //
                        .parameters(PortfolioTransaction.Type.SELL) //
                        .with(security) //
                        .addTo(manager);

        new OpenDialogAction(view, Messages.SecurityMenuDividends + "...") //$NON-NLS-1$
                        .type(AccountTransactionDialog.class) //
                        .parameters(AccountTransaction.Type.DIVIDENDS) //
                        .with(security) //
                        .addTo(manager);

        new OpenDialogAction(view, AccountTransaction.Type.TAX_REFUND + "...") //$NON-NLS-1$
                        .type(AccountTransactionDialog.class) //
                        .parameters(AccountTransaction.Type.TAX_REFUND) //
                        .with(security) //
                        .addTo(manager);

        manager.add(new AbstractDialogAction(Messages.SecurityMenuStockSplit)
        {
            @Override
            Dialog createDialog(Security security)
            {
                StockSplitWizard wizard = new StockSplitWizard(view.getStylingEngine(), getClient(), security);
                return new WizardDialog(getShell(), wizard);
            }
        });

        manager.add(new AbstractDialogAction(Messages.SecurityMenuAddEvent)
        {
            @Override
            Dialog createDialog(Security security)
            {
                CustomEventWizard wizard = new CustomEventWizard(view.getStylingEngine(), getClient(), security);
                return new WizardDialog(getShell(), wizard);
            }
        });

        if (view.getClient().getActivePortfolios().size() > 1)
        {
            manager.add(new Separator());
            new OpenDialogAction(view, Messages.SecurityMenuTransfer) //
                            .type(SecurityTransferDialog.class) //
                            .with(security) //
                            .addTo(manager);
        }

        manager.add(new Separator());

        new OpenDialogAction(view, PortfolioTransaction.Type.DELIVERY_INBOUND.toString() + "...") //$NON-NLS-1$
                        .type(SecurityTransactionDialog.class) //
                        .parameters(PortfolioTransaction.Type.DELIVERY_INBOUND) //
                        .with(security) //
                        .addTo(manager);

        new OpenDialogAction(view, PortfolioTransaction.Type.DELIVERY_OUTBOUND.toString() + "...") //$NON-NLS-1$
                        .type(SecurityTransactionDialog.class) //
                        .parameters(PortfolioTransaction.Type.DELIVERY_OUTBOUND) //
                        .with(security) //
                        .addTo(manager);

        new OpenDialogAction(view, Messages.InvestmentPlanMenuCreate) //
                        .type(InvestmentPlanDialog.class) //
                        .parameters(InvestmentPlan.Type.PURCHASE_OR_DELIVERY) //
                        .with(security) //
                        .addTo(manager);

        manager.add(new Separator());
    }

    private void setRetireStatus(IStructuredSelection selection, boolean isRetired)
    {
        for (Object obj : selection)
        {
            Security security = (Security) obj;
            security.setRetired(isRetired);
        }
        securities.refresh();
    }

    private void deleteSecurity(IStructuredSelection selection)
    {
        boolean isDirty = false;
        List<Security> withTransactions = new ArrayList<>();

        for (Object obj : selection.toArray())
        {
            Security security = (Security) obj;

            if (!security.getTransactions(getClient()).isEmpty())
            {
                withTransactions.add(security);
            }
            else
            {
                getClient().removeSecurity(security);
                isDirty = true;
            }
        }

        if (!withTransactions.isEmpty())
        {
            String label = String.join(", ", //$NON-NLS-1$
                            withTransactions.stream().map(Security::getName).collect(Collectors.toList()));

            MessageDialog.openError(getShell(), Messages.MsgDeletionNotPossible,
                            MessageFormat.format(Messages.MsgDeletionNotPossibleDetail, label));
        }

        if (isDirty)
        {
            markDirty();
            securities.setInput(getClient().getSecurities());
        }
    }

    private abstract class AbstractDialogAction extends Action
    {

        public AbstractDialogAction(String text)
        {
            super(text);
        }

        @Override
        public final void run()
        {
            Security security = (Security) ((IStructuredSelection) securities.getSelection()).getFirstElement();

            if (security == null)
                return;

            Dialog dialog = createDialog(security);
            if (dialog.open() == Window.OK)
                performFinish(security);
        }

        protected void performFinish(Security security)
        {
            markDirty();
            if (!securities.getControl().isDisposed())
            {
                // the check if the security gets filtered after the change.
                // Since it is unknown which property of the security has
                // been changed, filter.isFilterProperty(...) can't be used.
                boolean areFiltersAffected = false;
                for (ViewerFilter filter : securities.getFilters())
                {
                    if (!filter.select(securities, security, security))
                        areFiltersAffected = true;
                }

                if (areFiltersAffected)
                    securities.refresh();
                else
                    securities.refresh(security, true);

                securities.setSelection(securities.getSelection());
            }
        }

        abstract Dialog createDialog(Security security);
    }

    private final class EditSecurityAction extends AbstractDialogAction
    {
        private EditSecurityAction()
        {
            super(Messages.SecurityMenuEditSecurity);
            setAccelerator(SWT.MOD1 | 'E');
        }

        @Override
        Dialog createDialog(Security security)
        {
            return view.make(EditSecurityDialog.class, security);
        }

        @Override
        protected void performFinish(Security security)
        {
            super.performFinish(security);
            updateQuotes(security);
        }
    }

    private static final class QuoteReportingPeriodLabelProvider
                    extends ParameterizedColumnLabelProvider<ReportingPeriod>
    {

        @Override
        public String getText(Object e)
        {
            Optional<Pair<SecurityPrice, SecurityPrice>> prices = getPrices((Security) e, getOption());
            if (prices.isEmpty())
                return null;

            return getTextInternal(prices.get());
        }

        private static String getTextInternal(Pair<SecurityPrice, SecurityPrice> prices)
        {
            Double value = getQuoteChange(prices);
            return String.format("%,.2f %%", value * 100); //$NON-NLS-1$
        }

        @Override
        public Color getForeground(Object e)
        {
            Double value = getQuoteChange((Security) e, getOption());
            if (value == null)
                return null;

            if (value.doubleValue() < 0)
                return Colors.theme().redForeground();
            else if (value.doubleValue() > 0)
                return Colors.theme().greenForeground();
            else
                return null;
        }

        @Override
        public String getToolTipText(Object e)
        {
            Security security = (Security) e;

            Optional<Pair<SecurityPrice, SecurityPrice>> prices = getPrices(security, getOption());
            if (prices.isEmpty())
                return null;

            String value = getTextInternal(prices.get());
            if (value == null)
                return null;

            Double valuePA = getAnnualizedQuoteChange(prices.get());

            String firstPrice = Values.Quote.format(security.getCurrencyCode(), prices.get().getLeft().getValue());
            String secondPrice = Values.Quote.format(security.getCurrencyCode(), prices.get().getRight().getValue());

            String firstDate = Values.Date.format(prices.get().getLeft().getDate());
            String secondDate = Values.Date.format(prices.get().getRight().getDate());

            String stringValuePA = String.format("%,.2f %%", valuePA * 100); //$NON-NLS-1$

            return firstDate + " \u27A4 " + secondDate + "\r\n" // dates //$NON-NLS-1$ //$NON-NLS-2$
                            + firstPrice + " \u27A4 " + secondPrice + "\r\n" // prices //$NON-NLS-1$ //$NON-NLS-2$
                            + value + " \u2259 " + stringValuePA + " p.a."; // annualized //$NON-NLS-1$ //$NON-NLS-2$
        }

        /* package */ static Double getQuoteChange(Security security, ReportingPeriod period)
        {
            Optional<Pair<SecurityPrice, SecurityPrice>> prices = getPrices(security, period);

            if (prices.isEmpty())
                return null;

            return getQuoteChange(prices.get());
        }

        private static Double getQuoteChange(Pair<SecurityPrice, SecurityPrice> prices)
        {
            return Double.valueOf((prices.getRight().getValue() / (double) prices.getLeft().getValue()) - 1);
        }

        private static Double getAnnualizedQuoteChange(Pair<SecurityPrice, SecurityPrice> prices)
        {
            SecurityPrice previous = prices.getLeft();
            SecurityPrice latest = prices.getRight();

            double totalDays = java.time.temporal.ChronoUnit.DAYS.between(previous.getDate(), latest.getDate());
            double totalGain = latest.getValue() / (double) previous.getValue();
            return Double.valueOf(Math.pow(totalGain, 365 / totalDays)) - 1;
        }

        private static Optional<Pair<SecurityPrice, SecurityPrice>> getPrices(Security security, ReportingPeriod period)
        {
            Interval interval = period.toInterval(LocalDate.now());

            SecurityPrice latest = security.getSecurityPrice(interval.getEnd());
            SecurityPrice previous = security.getSecurityPrice(interval.getStart());

            if (latest == null || previous == null)
                return Optional.empty();

            if (previous.getValue() == 0)
                return Optional.empty();

            if (previous.getDate().isAfter(interval.getStart()))
                return Optional.empty();

            return Optional.of(new Pair<>(previous, latest));
        }

        @Override
        public Image getImage(Object e)
        {
            Double value = getQuoteChange((Security) e, getOption());
            if (value == null)
                return null;

            if (value.doubleValue() > 0)
                return Images.GREEN_ARROW.image();
            if (value.doubleValue() < 0)
                return Images.RED_ARROW.image();
            return null;
        }
    }
}
