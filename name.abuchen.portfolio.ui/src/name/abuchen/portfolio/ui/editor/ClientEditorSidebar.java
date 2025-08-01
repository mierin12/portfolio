package name.abuchen.portfolio.ui.editor;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DropTargetAdapter;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.ControlListener;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;

import name.abuchen.portfolio.model.Security;
import name.abuchen.portfolio.model.Watchlist;
import name.abuchen.portfolio.ui.Images;
import name.abuchen.portfolio.ui.dnd.ImportFromFileDropAdapter;
import name.abuchen.portfolio.ui.dnd.ImportFromURLDropAdapter;
import name.abuchen.portfolio.ui.dnd.SecurityTransfer;
import name.abuchen.portfolio.ui.editor.Navigation.Item;
import name.abuchen.portfolio.ui.editor.Navigation.Tag;
import name.abuchen.portfolio.ui.views.SecurityListView;

/* package */class ClientEditorSidebar
{
    private PortfolioPart editor;

    private Sidebar<Navigation.Item> sidebar;

    public ClientEditorSidebar(PortfolioPart editor)
    {
        this.editor = editor;
    }

    public Control createSidebarControl(Composite parent)
    {
        Sidebar.Model<Navigation.Item> model = new Sidebar.Model<Navigation.Item>()
        {
            @Override
            public Stream<Navigation.Item> getElements()
            {
                return editor.getClientInput().getNavigation().getRoots().filter(i -> !i.contains(Tag.HIDE));
            }

            @Override
            public Stream<Navigation.Item> getChildren(Item item)
            {
                return item.getChildren().filter(i -> !i.contains(Tag.HIDE));
            }

            @Override
            public String getLabel(Item item)
            {
                return item.getLabel();
            }

            @Override
            public Optional<Images> getImage(Item item)
            {
                return Optional.ofNullable(item.getImage());
            }

            @Override
            public void select(Navigation.Item item)
            {
                if (item.getViewClass() != null)
                    editor.activateView(item);
            }

            @Override
            public Navigation.MenuListener getActionMenu(Navigation.Item item)
            {
                return item.getActionMenu();
            }

            @Override
            public Navigation.MenuListener getContextMenu(Navigation.Item item)
            {
                return item.getContextMenu();
            }
        };

        ScrolledComposite scrolledComposite = new ScrolledComposite(parent, SWT.V_SCROLL);

        sidebar = new Sidebar<>(scrolledComposite, editor, model);

        editor.getClientInput().getNavigation().findAll(item -> item.getViewClass() == SecurityListView.class)
                        .forEach(this::setupAllSecuritesAndWatchlistDnD);

        scrolledComposite.setContent(sidebar);
        scrolledComposite.setExpandVertical(true);
        scrolledComposite.setExpandHorizontal(true);

        parent.getParent().addControlListener(ControlListener.controlResizedAdapter(
                        e -> scrolledComposite.setMinSize(sidebar.computeSize(SWT.DEFAULT, SWT.DEFAULT))));

        Navigation.Listener listener = item -> {
            sidebar.rebuild();
            editor.getClientInput().getNavigation().findAll(i -> item.getViewClass() == SecurityListView.class)
                            .forEach(this::setupAllSecuritesAndWatchlistDnD);

            scrolledComposite.setMinSize(sidebar.computeSize(SWT.DEFAULT, SWT.DEFAULT));
            sidebar.layout(true);
            sidebar.redraw();
            sidebar.update();
        };
        editor.getClientInput().getNavigation().addListener(listener);
        sidebar.addDisposeListener(e -> editor.getClientInput().getNavigation().removeListener(listener));

        ImportFromURLDropAdapter.attach(sidebar, editor);

        ImportFromFileDropAdapter.attach(sidebar, editor);

        return scrolledComposite;
    }

    public void select(Item item)
    {
        sidebar.select(item);
    }

    private void setupAllSecuritesAndWatchlistDnD(Navigation.Item item)
    {
        DropTargetAdapter dropTargetListener = new DropTargetAdapter()
        {
            @Override
            public void drop(DropTargetEvent event)
            {
                if (!SecurityTransfer.getTransfer().isSupportedType(event.currentDataType))
                    return;

                List<Security> securities = SecurityTransfer.getTransfer().getSecurities();
                if (securities == null)
                    return;

                boolean isDirty = false;

                for (Security security : securities)
                {
                    // if the security is dragged from another file, add
                    // a deep copy to the client's securities list
                    if (!editor.getClient().getSecurities().contains(security))
                    {
                        security = security.deepCopy();
                        editor.getClient().addSecurity(security);
                        isDirty = true;
                    }

                    // if drop target is a watchlist, add
                    if (item.getParameter() instanceof Watchlist watchlist
                                    && !watchlist.getSecurities().contains(security))
                    {
                        watchlist.addSecurity(security);
                        isDirty = true;
                    }
                }

                if (isDirty)
                    editor.getClient().touch();
            }
        };

        sidebar.addDropSupport(item, DND.DROP_MOVE, new Transfer[] { SecurityTransfer.getTransfer() },
                        dropTargetListener);
    }
}
