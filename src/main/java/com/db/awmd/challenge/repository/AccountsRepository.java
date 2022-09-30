package com.db.awmd.challenge.repository;

import com.db.awmd.challenge.domain.Account;
import com.db.awmd.challenge.exception.DuplicateAccountIdException;

import java.math.BigDecimal;

public interface AccountsRepository {

    void createAccount(Account account) throws DuplicateAccountIdException;

    Account getAccount(String accountId);

    void clearAccounts();

    void transactionalTransferMoney(Account accountFrom, Account accountTo, BigDecimal amount);
}
