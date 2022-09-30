package com.db.awmd.challenge.service;

import com.db.awmd.challenge.domain.Account;
import com.db.awmd.challenge.domain.Transaction;
import com.db.awmd.challenge.exception.IncorrectAccountIdException;
import com.db.awmd.challenge.exception.InsufficientBalanceException;
import com.db.awmd.challenge.repository.AccountsRepository;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Service
public class AccountsService {

    private Map<String, ReentrantReadWriteLock> readWriteAccountLock = new ConcurrentHashMap<>();

    @Getter
    private final AccountsRepository accountsRepository;
    private final NotificationService notificationService;

    @Autowired
    public AccountsService(AccountsRepository accountsRepository, NotificationService notificationService) {
        this.accountsRepository = accountsRepository;
        this.notificationService = notificationService;
    }

    private ReentrantReadWriteLock getLock(String accountId) {
        ReentrantReadWriteLock reentrantReadWriteLock = readWriteAccountLock.putIfAbsent(accountId, new ReentrantReadWriteLock());
        if (reentrantReadWriteLock == null) {
            reentrantReadWriteLock = readWriteAccountLock.get(accountId);
        }
        return reentrantReadWriteLock;
    }

    public void createAccount(Account account) {
        this.accountsRepository.createAccount(account);
    }

    public Account getAccount(String accountId) {
        ReentrantReadWriteLock.ReadLock readLock = this.getLock(accountId).readLock();
        readLock.lock();
        try {
            return this.accountsRepository.getAccount(accountId);
        } finally {
            readLock.unlock();
        }
    }

    public void createTransaction(Transaction transaction) {
        String accountFromId = transaction.getAccountFromId();
        String accountToId = transaction.getAccountToId();
        if (accountFromId.equals(accountToId)) {
            throw new IncorrectAccountIdException("Cannot transfer from account to itself.");
        }

        ReentrantReadWriteLock.WriteLock[] writeLocks = new ReentrantReadWriteLock.WriteLock[2];
        if (accountFromId.compareTo(accountToId) < 0) {
            writeLocks[0] = this.getLock(accountFromId).writeLock();
            writeLocks[1] = this.getLock(accountToId).writeLock();
        } else {
            writeLocks[0] = this.getLock(accountToId).writeLock();
            writeLocks[1] = this.getLock(accountFromId).writeLock();
        }
        writeLocks[0].lock();
        writeLocks[1].lock();

        Account accountFrom;
        Account accountTo;
        try {
            accountFrom = this.accountsRepository.getAccount(accountFromId);
            if (accountFrom == null) {
                throw new IncorrectAccountIdException(
                        "Account is not found by id " + accountFromId);
            } else if (accountFrom.getBalance().compareTo(transaction.getAmount()) < 0) {
                throw new InsufficientBalanceException(
                        "An account with id " + accountFrom.getAccountId() + " has an insufficient balance.");
            }
            accountTo = this.accountsRepository.getAccount(accountToId);
            if (accountTo == null) {
                throw new IncorrectAccountIdException(
                        "Account is not found by id " + accountToId);
            }
            this.accountsRepository.transactionalTransferMoney(accountFrom, accountTo, transaction.getAmount());
        } finally {
            writeLocks[0].unlock();
            writeLocks[1].unlock();
        }

        // Sending notifications is out of lock block to increase transaction speed.
        String accountFromMessage = "You transferred $" + transaction.getAmount()
                + " to account " + accountTo.getAccountId();
        notificationService.notifyAboutTransfer(accountFrom, accountFromMessage);
        String accountToMessage = "You received $" + transaction.getAmount()
                + " from account " + accountFrom.getAccountId();
        notificationService.notifyAboutTransfer(accountTo, accountToMessage);
    }
}
