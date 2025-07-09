package name.abuchen.portfolio.snapshot.filter;

import java.util.Collections;
import java.util.List;

import name.abuchen.portfolio.model.Account;
import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.Portfolio;
import name.abuchen.portfolio.model.PortfolioTransaction;
import name.abuchen.portfolio.model.Security;
import name.abuchen.portfolio.model.TransactionPair;
import name.abuchen.portfolio.snapshot.trades.TradeCollector;

/**
 * Remove all transactions starting with the given transaction. Transactions are
 * sorted by exactly how the TradeCollector is sorting the transactions. It is
 * used to create a client that allows to calculate the moving average costs for
 * a given trade, i.e., the costs before the sale is applied. <br/>
 * <br/>
 * Attention: This filter processes only portfolio transactions.
 */
public class ClientTransactionFilter implements ClientFilter
{
    private final List<TransactionPair<PortfolioTransaction>> transactions;
    private final Security security;

    public ClientTransactionFilter(Security security, List<TransactionPair<PortfolioTransaction>> transactions)
    {
        this.security = security;
        this.transactions = transactions;
    }

    @Override
    public Client filter(Client client)
    {
        Collections.sort(transactions, new TradeCollector.ByDateAndType());

        ReadOnlyClient pseudoClient = new ReadOnlyClient(client);
        pseudoClient.internalAddSecurity(security);

        Account account = new Account();
        Portfolio portfolio = new Portfolio();
        portfolio.setReferenceAccount(account);

        ReadOnlyAccount pa = new ReadOnlyAccount(account);
        pseudoClient.internalAddAccount(pa);

        ReadOnlyPortfolio pp = new ReadOnlyPortfolio(portfolio);
        pp.setReferenceAccount(pa);
        pseudoClient.internalAddPortfolio(pp);

        for (var tx : transactions)
        {
            var t = tx.getTransaction();
            pp.internalAddTransaction(t);
        }
        return pseudoClient;
    }
}
