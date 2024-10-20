package name.abuchen.portfolio.ui.views.settings;

import java.util.Arrays;
import java.util.List;

import jakarta.inject.Inject;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.action.ToolBarManager;
import org.eclipse.jface.layout.TableColumnLayout;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;

import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.ClientSettings;
import name.abuchen.portfolio.model.CustomJson;
import name.abuchen.portfolio.model.Security;
import name.abuchen.portfolio.ui.Images;
import name.abuchen.portfolio.ui.Messages;
import name.abuchen.portfolio.ui.editor.AbstractFinanceView;
import name.abuchen.portfolio.ui.util.ContextMenu;
import name.abuchen.portfolio.ui.util.DesktopAPI;
import name.abuchen.portfolio.ui.util.DropDown;
import name.abuchen.portfolio.ui.util.LabelOnly;
import name.abuchen.portfolio.ui.util.SimpleAction;
import name.abuchen.portfolio.ui.util.viewers.Column;
import name.abuchen.portfolio.ui.util.viewers.ColumnEditingSupport;
import name.abuchen.portfolio.ui.util.viewers.ColumnEditingSupport.ModificationListener;
import name.abuchen.portfolio.ui.util.viewers.CopyPasteSupport;
import name.abuchen.portfolio.ui.util.viewers.ShowHideColumnHelper;
import name.abuchen.portfolio.ui.util.viewers.StringEditingSupport;
import name.abuchen.portfolio.ui.views.AbstractTabbedView;

public class CustomJSONListTab implements AbstractTabbedView.Tab, ModificationListener
{
    private static final String DEFAULT_URL = "http://example.net/{tickerSymbol}?isin={isin}&wkn={wkn}&name={name}"; //$NON-NLS-1$
    private static final String DEFAULT_PathToClose = "$[*].[1]"; //$NON-NLS-1$
    private static final String DEFAULT_PathToDate = "$[*].[0]"; //$NON-NLS-1$

    private TableViewer customJSON;

    @Inject
    private Client client;

    @Inject
    private IPreferenceStore preferences;

    @Inject
    private AbstractFinanceView view;

    @Override
    public String getTitle()
    {
        return "CUSTOM JSON"; //$NON-NLS-1$
    }

    @Override
    public void addButtons(ToolBarManager manager)
    {
        manager.add(new DropDown(Messages.BookmarksListView_NewBookmark, Images.PLUS, SWT.NONE, menuListener -> {

            menuListener.add(new SimpleAction(Messages.BookmarksListView_NewBookmark, a -> {
                CustomJson wl = new CustomJson(Messages.BookmarksListView_NewBookmark, DEFAULT_URL, DEFAULT_PathToDate,
                                DEFAULT_PathToClose);

                client.getSettings().getcustomJsons().add(wl);
                client.touch();

                customJSON.setInput(client.getSettings().getcustomJsons());
                customJSON.editElement(wl, 0);
            }));
        }));
    }

    @Override
    public Composite createTab(Composite parent)
    {
        Composite container = new Composite(parent, SWT.NONE);
        TableColumnLayout layout = new TableColumnLayout();
        container.setLayout(layout);

        customJSON = new TableViewer(container, SWT.FULL_SELECTION | SWT.MULTI);

        ColumnEditingSupport.prepare(customJSON);
        CopyPasteSupport.enableFor(customJSON);

        ShowHideColumnHelper support = new ShowHideColumnHelper(CustomJSONListTab.class.getSimpleName() + "@bottom", //$NON-NLS-1$
                        preferences, customJSON, layout);

        // Create Column for Bookmark
        Column column = new Column(Messages.BookmarksListView_bookmark, SWT.None, 150);
        column.setLabelProvider(new ColumnLabelProvider()
        {
            @Override
            public String getText(Object element)
            {
                return ((CustomJson) element).getLabel();
            }

            @Override
            public Image getImage(Object element)
            {
                return Images.BOOKMARK.image();
            }

        });

        new StringEditingSupport(CustomJson.class, "label").addListener(this).attachTo(column); //$NON-NLS-1$
        support.addColumn(column);

        // Create Column for URL
        column = new Column(Messages.BookmarksListView_url, SWT.None, 500);
        column.setLabelProvider(new ColumnLabelProvider()
        {
            @Override
            public String getText(Object element)
            {
                return ((CustomJson) element).getPattern();
            }
        });
        // bookmark class
        new StringEditingSupport(CustomJson.class, "pattern").addListener(this).attachTo(column); //$NON-NLS-1$
        support.addColumn(column);

        addColumns(support);

        support.createColumns();

        customJSON.getTable().setHeaderVisible(true);
        customJSON.getTable().setLinesVisible(true);

        customJSON.setContentProvider(ArrayContentProvider.getInstance());

        customJSON.setInput(client.getSettings().getcustomJsons());// to check
        customJSON.refresh();

        customJSON.addSelectionChangedListener(
                        e -> view.setInformationPaneInput(e.getStructuredSelection().getFirstElement()));

        new ContextMenu(customJSON.getTable(), this::fillContextMenu).hook();

        return container;
    }

    private void addColumns(ShowHideColumnHelper support)
    {
        Column column = new Column(Messages.LabelJSONPathToDate, SWT.None, 250);
        column.setLabelProvider(new ColumnLabelProvider()
        {
            @Override
            public String getText(Object element)
            {
                return ((CustomJson) element).getPathToDate();
            }
        });

        new StringEditingSupport(CustomJson.class, "pathToDate").addListener(this).attachTo(column); //$NON-NLS-1$
        support.addColumn(column);

        column = new Column(Messages.LabelJSONPathToClose, SWT.None, 150);
        column.setLabelProvider(new ColumnLabelProvider()
        {
            @Override
            public String getText(Object element)
            {
                return ((CustomJson) element).getpathToClose();
            }
        });
        new StringEditingSupport(CustomJson.class, "pathToClose").addListener(this).attachTo(column); //$NON-NLS-1$
        support.addColumn(column);
    }

    @Override
    public void onModified(Object element, Object newValue, Object oldValue)
    {
        client.touch();
    }

    private void fillContextMenu(IMenuManager manager)
    {
        CustomJson customJson = (CustomJson) ((IStructuredSelection) customJSON.getSelection()).getFirstElement();

        if (customJson == null)
            return;

        if (!customJson.isSeparator())
            addTestMenu(manager, customJson);

        addMoveUpAndDownActions(manager, customJson);

        manager.add(new Separator());
        manager.add(new Action(Messages.BookmarksListView_insertBefore)
        {
            @Override
            public void run()
            {
                CustomJson index = (CustomJson) ((IStructuredSelection) customJSON.getSelection()).getFirstElement();
                CustomJson wl = new CustomJson(Messages.BookmarksListView_NewBookmark, DEFAULT_URL);

                client.getSettings().insertCustomJSON(index, wl);
                client.touch();

                customJSON.setInput(client.getSettings().getcustomJsons());
                customJSON.editElement(wl, 0);
            }
        });

        manager.add(new Action(Messages.BookmarksListView_insertAfter)
        {
            @Override
            public void run()
            {
                CustomJson index = (CustomJson) ((IStructuredSelection) customJSON.getSelection()).getFirstElement();
                CustomJson wl = new CustomJson(Messages.BookmarksListView_NewBookmark, DEFAULT_URL);

                client.getSettings().insertCustomJSONAfter(index, wl);
                client.touch();

                customJSON.setInput(client.getSettings().getcustomJsons());
                customJSON.editElement(wl, 0);
            }
        });

        manager.add(new Action(Messages.BookmarksListView_addSeparator)
        {
            @Override
            public void run()
            {
                CustomJson index = (CustomJson) ((IStructuredSelection) customJSON.getSelection()).getFirstElement();
                CustomJson wl = new CustomJson("-", ""); //$NON-NLS-1$ //$NON-NLS-2$

                client.getSettings().insertCustomJSONAfter(index, wl);
                client.touch();

                customJSON.setInput(client.getSettings().getcustomJsons());
            }
        });

        manager.add(new Separator());
        addSubmenuWithPlaceholders(manager);

        manager.add(new Separator());
        manager.add(new Action(Messages.BookmarksListView_delete)
        {
            @Override
            public void run()
            {
                ClientSettings settings = client.getSettings();
                for (Object element : ((IStructuredSelection) customJSON.getSelection()).toArray())
                    settings.removeCustomJSON((CustomJson) element);

                client.touch();
                customJSON.setInput(settings.getcustomJsons());
            }
        });
    }

    private void addSubmenuWithPlaceholders(IMenuManager manager)
    {
        MenuManager submenu = new MenuManager(Messages.BookmarksListView_replacements);
        manager.add(submenu);

        @SuppressWarnings("nls")
        List<String> defaultReplacements = Arrays.asList("isin", "name", "wkn", "tickerSymbol", "tickerSymbolPrefix");

        submenu.add(new LabelOnly(Messages.BookmarksListView_LabelDefaultReplacements));
        defaultReplacements.forEach(r -> addReplacementMenu(submenu, r));

        submenu.add(new Separator());
        submenu.add(new LabelOnly(Messages.BookmarksListView_LabelAttributeReplacements));
        client.getSettings().getAttributeTypes().filter(a -> a.supports(Security.class))
                        .filter(a -> !defaultReplacements.contains(a.getColumnLabel()))
                        .forEach(a -> addReplacementMenu(submenu, a.getColumnLabel()));

        submenu.add(new Separator());
        submenu.add(new LabelOnly(Messages.BookmarksListView_LabelReplaceFirstAvailable));
        addReplacementMenu(submenu, "isin,wkn"); //$NON-NLS-1$
    }

    private void addReplacementMenu(MenuManager manager, String replacement)
    {
        manager.add(new SimpleAction('{' + replacement + '}', a -> {
            CustomJson customJson = (CustomJson) ((IStructuredSelection) customJSON.getSelection()).getFirstElement();
            customJson.setPattern(customJson.getPattern() + '{' + replacement + '}');
            customJSON.refresh(customJson);
            client.touch();
        }));
    }

    private void addTestMenu(IMenuManager manager, CustomJson customJson)
    {
        MenuManager securities = new MenuManager(Messages.MenuOpenSecurityOnSite);
        for (Security security : client.getSecurities())
        {
            securities.add(new SimpleAction(security.getName(),
                            a -> DesktopAPI.browse(customJson.constructURL(client, security))));
        }
        manager.add(securities);
        manager.add(new Separator());
    }

    private void addMoveUpAndDownActions(IMenuManager manager, CustomJson customJson)
    {
        int index = client.getSettings().getcustomJsons().indexOf(customJson);
        if (index > 0)
        {
            manager.add(new Action(Messages.MenuMoveUp)
            {
                @Override
                public void run()
                {
                    ClientSettings settings = client.getSettings();
                    settings.removeCustomJSON(customJson);
                    settings.insertCustomJSON(index - 1, customJson);
                    customJSON.setInput(client.getSettings().getcustomJsons());
                    client.touch();
                }
            });
        }

        if (index < client.getSettings().getcustomJsons().size() - 1)
        {
            manager.add(new Action(Messages.MenuMoveDown)
            {
                @Override
                public void run()
                {
                    ClientSettings settings = client.getSettings();
                    settings.removeCustomJSON(customJson);
                    settings.insertCustomJSON(index + 1, customJson);
                    customJSON.setInput(client.getSettings().getcustomJsons());
                    client.touch();
                }
            });
        }
    }
}
