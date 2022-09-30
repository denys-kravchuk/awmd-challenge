package com.db.awmd.challenge;

import com.db.awmd.challenge.domain.Account;
import com.db.awmd.challenge.domain.Transaction;
import com.db.awmd.challenge.exception.DuplicateAccountIdException;
import com.db.awmd.challenge.exception.InsufficientBalanceException;
import com.db.awmd.challenge.repository.AccountsRepository;
import com.db.awmd.challenge.service.AccountsService;
import com.db.awmd.challenge.service.NotificationService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.*;

@RunWith(SpringRunner.class)
@SpringBootTest
public class AccountsServiceTest {

    @Autowired
    private AccountsService accountsService;
    @Autowired
    private AccountsRepository accountsRepository;

    private int accountNumber = 5;

    @Before
    public void prepare() {
        this.accountsService.getAccountsRepository().clearAccounts();
        Random ran = new Random();
        for (int i = 0; i < accountNumber; i++) {
            this.accountsService.createAccount(new Account("Id-" + i, new BigDecimal(1000 + ran.nextInt(1000))));
        }
    }

    @Test
    public void addAccount() throws Exception {
        Account account = new Account("Id-123");
        account.setBalance(new BigDecimal(1000));
        this.accountsService.createAccount(account);

        assertThat(this.accountsService.getAccount("Id-123")).isEqualTo(account);
    }

    @Test
    public void addAccount_failsOnDuplicateId() throws Exception {
        String uniqueId = "Id-" + System.currentTimeMillis();
        Account account = new Account(uniqueId);
        this.accountsService.createAccount(account);

        try {
            this.accountsService.createAccount(account);
            fail("Should have failed when adding duplicate account");
        } catch (DuplicateAccountIdException ex) {
            assertThat(ex.getMessage()).isEqualTo("Account id " + uniqueId + " already exists!");
        }

    }

    @Test
    public void createTransactionNotificationTest() throws Exception {
        NotificationService notificationService = mock(NotificationService.class);
        AccountsService accountsService = new AccountsService(this.accountsRepository, notificationService);
        String accountFromId = "Id-100";
        Account accountFrom = new Account(accountFromId, new BigDecimal(100));
        accountsService.createAccount(accountFrom);
        String accountToId = "Id-101";
        Account accountTo = new Account(accountToId, new BigDecimal(101));
        accountsService.createAccount(accountTo);

        Transaction transaction = new Transaction(accountFromId, accountToId, new BigDecimal(10));
        accountsService.createTransaction(transaction);

        String messageAccountFrom = "You transferred $" + transaction.getAmount() + " to account " + accountTo.getAccountId();
        verify(notificationService, times(1)).notifyAboutTransfer(accountFrom, messageAccountFrom);

        String messageAccountTo = "You received $" + transaction.getAmount() + " from account " + accountFrom.getAccountId();
        verify(notificationService, times(1)).notifyAboutTransfer(accountTo, messageAccountTo);
    }

    @Test
    public void concurrencyTest() throws Exception {
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < accountNumber; i++) {
            sum = sum.add(this.accountsService.getAccount("Id-" + i).getBalance());
        }

        int nThreads = 10;
        Set<Callable<Void>> callableSet = new HashSet<>();
        for (int t = 0; t < nThreads; t++) {
            callableSet.add(() -> {
                int fromId;
                int toId;
                Random ran = new Random();
                for (int i = 0; i < 1000; i++) {
                    do {
                        fromId = ran.nextInt(accountNumber);
                        toId = ran.nextInt(accountNumber);
                    } while (fromId == toId);
                    BigDecimal balance = this.accountsService.getAccount("Id-" + fromId).getBalance();
                    BigDecimal subtrahend = new BigDecimal(ran.nextInt(1 + balance.intValue()));
                    BigDecimal amount = balance.subtract(subtrahend);
                    try {
                        this.accountsService.createTransaction(new Transaction("Id-" + fromId, "Id-" + toId, amount));
                    } catch (InsufficientBalanceException e) {// Balance may be changed while we run createTransaction method
                        System.out.println("e = " + e);
                    }
                }
                return null;
            });
        }

        ExecutorService executor = Executors.newFixedThreadPool(nThreads);
        executor.invokeAll(callableSet);
        executor.shutdown();

        BigDecimal sumAfter = BigDecimal.ZERO;
        for (int i = 0; i < accountNumber; i++) {
            sumAfter = sumAfter.add(this.accountsService.getAccount("Id-" + i).getBalance());
        }

        assertThat(sumAfter).isEqualByComparingTo(sum);
    }
}
