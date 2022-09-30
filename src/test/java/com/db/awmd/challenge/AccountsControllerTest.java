package com.db.awmd.challenge;

import com.db.awmd.challenge.domain.Account;
import com.db.awmd.challenge.service.AccountsService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@RunWith(SpringRunner.class)
@SpringBootTest
@WebAppConfiguration
public class AccountsControllerTest {

    private MockMvc mockMvc;

    @Autowired
    private AccountsService accountsService;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Before
    public void prepareMockMvc() {
        this.mockMvc = webAppContextSetup(this.webApplicationContext).build();

        // Reset the existing accounts before each test.
        accountsService.getAccountsRepository().clearAccounts();
    }

    @Test
    public void createAccount() throws Exception {
        this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountId\":\"Id-123\",\"balance\":1000}")).andExpect(status().isCreated());

        Account account = accountsService.getAccount("Id-123");
        assertThat(account.getAccountId()).isEqualTo("Id-123");
        assertThat(account.getBalance()).isEqualByComparingTo("1000");
    }

    @Test
    public void createDuplicateAccount() throws Exception {
        this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountId\":\"Id-123\",\"balance\":1000}")).andExpect(status().isCreated());

        this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountId\":\"Id-123\",\"balance\":1000}")).andExpect(status().isBadRequest());
    }

    @Test
    public void createAccountNoAccountId() throws Exception {
        this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"balance\":1000}")).andExpect(status().isBadRequest());
    }

    @Test
    public void createAccountNoBalance() throws Exception {
        this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountId\":\"Id-123\"}")).andExpect(status().isBadRequest());
    }

    @Test
    public void createAccountNoBody() throws Exception {
        this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void createAccountNegativeBalance() throws Exception {
        this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountId\":\"Id-123\",\"balance\":-1000}")).andExpect(status().isBadRequest());
    }

    @Test
    public void createAccountEmptyAccountId() throws Exception {
        this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountId\":\"\",\"balance\":1000}")).andExpect(status().isBadRequest());
    }

    @Test
    public void getAccount() throws Exception {
        String uniqueAccountId = "Id-" + System.currentTimeMillis();
        Account account = new Account(uniqueAccountId, new BigDecimal("123.45"));
        this.accountsService.createAccount(account);
        this.mockMvc.perform(get("/v1/accounts/" + uniqueAccountId))
                .andExpect(status().isOk())
                .andExpect(
                        content().string("{\"accountId\":\"" + uniqueAccountId + "\",\"balance\":123.45}"));
    }

    @Test
    public void createTransaction() throws Exception {
        String uniqueAccountFromId = "Id-" + System.currentTimeMillis();
        this.accountsService.createAccount(new Account(uniqueAccountFromId, new BigDecimal("123.45")));
        String uniqueAccountToId = "Id-0" + System.currentTimeMillis();
        this.accountsService.createAccount(new Account(uniqueAccountToId, new BigDecimal("13.5")));

        BigDecimal amount = new BigDecimal("99.31");

        this.mockMvc.perform(
                post("/v1/accounts/" + uniqueAccountFromId + "/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountFromId\":\"" + uniqueAccountFromId + "\"," +
                                "\"accountToId\":\"" + uniqueAccountToId + "\"," +
                                "\"amount\":" + amount + "}"))
                .andExpect(status().isCreated());

        Account accountFrom = accountsService.getAccount(uniqueAccountFromId);
        assertThat(accountFrom.getBalance()).isEqualByComparingTo("24.14");
        Account accountTo = accountsService.getAccount(uniqueAccountToId);
        assertThat(accountTo.getBalance()).isEqualByComparingTo("112.81");
    }

    @Test
    public void createTransactionNegativeAmount() throws Exception {
        String uniqueAccountFromId = "Id-" + System.currentTimeMillis();
        this.accountsService.createAccount(new Account(uniqueAccountFromId, new BigDecimal("123.45")));
        String uniqueAccountToId = "Id-0" + System.currentTimeMillis();
        this.accountsService.createAccount(new Account(uniqueAccountToId, new BigDecimal("13.5")));

        BigDecimal amount = new BigDecimal("-99.31");

        this.mockMvc.perform(
                post("/v1/accounts/" + uniqueAccountFromId + "/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountFromId\":\"" + uniqueAccountFromId + "\"," +
                                "\"accountToId\":\"" + uniqueAccountToId + "\"," +
                                "\"amount\":" + amount + "}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void createTransactionNoBody() throws Exception {
        String uniqueAccountFromId = "Id-" + System.currentTimeMillis();
        this.accountsService.createAccount(new Account(uniqueAccountFromId, new BigDecimal("123.45")));

        Account accountFrom = accountsService.getAccount(uniqueAccountFromId);
        assertThat(accountFrom).isNotNull();

        this.mockMvc.perform(
                post("/v1/accounts/" + uniqueAccountFromId + "/transactions").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void createTransactionEmptyAccountFromId() throws Exception {
        String uniqueAccountFromId = "Id-" + System.currentTimeMillis();
        this.accountsService.createAccount(new Account(uniqueAccountFromId, new BigDecimal("123.45")));
        String uniqueAccountToId = "Id-0" + System.currentTimeMillis();
        this.accountsService.createAccount(new Account(uniqueAccountToId, new BigDecimal("123.45")));

        Account accountFrom = accountsService.getAccount(uniqueAccountFromId);
        assertThat(accountFrom).isNotNull();
        Account accountTo = accountsService.getAccount(uniqueAccountToId);
        assertThat(accountTo).isNotNull();

        this.mockMvc.perform(
                post("/v1/accounts/" + uniqueAccountFromId + "/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountFromId\":\"\"," +
                                "\"accountToId\":\"" + uniqueAccountToId + "\"," +
                                "\"amount\":" + "10" + "}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void createTransactionEmptyAccountToId() throws Exception {
        String uniqueAccountFromId = "Id-" + System.currentTimeMillis();
        this.accountsService.createAccount(new Account(uniqueAccountFromId, new BigDecimal("123.45")));

        Account accountFrom = accountsService.getAccount(uniqueAccountFromId);
        assertThat(accountFrom).isNotNull();

        this.mockMvc.perform(
                post("/v1/accounts/" + uniqueAccountFromId + "/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountFromId\":\"" + uniqueAccountFromId + "\"," +
                                "\"accountToId\":\"\"," +
                                "\"amount\":" + "10" + "}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void createTransactionEmptyAmount() throws Exception {
        String uniqueAccountFromId = "Id-" + System.currentTimeMillis();
        this.accountsService.createAccount(new Account(uniqueAccountFromId, new BigDecimal("123.45")));
        String uniqueAccountToId = "Id-0" + System.currentTimeMillis();
        this.accountsService.createAccount(new Account(uniqueAccountToId, new BigDecimal("123.45")));

        Account accountFrom = accountsService.getAccount(uniqueAccountFromId);
        assertThat(accountFrom).isNotNull();
        Account accountTo = accountsService.getAccount(uniqueAccountToId);
        assertThat(accountTo).isNotNull();

        this.mockMvc.perform(
                post("/v1/accounts/" + uniqueAccountFromId + "/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountFromId\":\"" + uniqueAccountFromId + "\"," +
                                "\"accountToId\":\"" + uniqueAccountToId + "\"," +
                                "\"amount\":}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void createTransactionNotMismatchPathVariableBodyValue() throws Exception {
        String pathVariableUniqueAccountFromId = "Id-" + System.currentTimeMillis();
        String uniqueAccountFromId = "Id-0" + System.currentTimeMillis();
        String uniqueAccountToId = "Id-1" + System.currentTimeMillis();

        this.mockMvc.perform(
                post("/v1/accounts/" + pathVariableUniqueAccountFromId + "/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountFromId\":\"" + uniqueAccountFromId + "\"," +
                                "\"accountToId\":\"" + uniqueAccountToId + "\"," +
                                "\"amount\":" + "10" + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(content()
                        .string("Not mismatch variable accountId in the URI and accountFromId from the body."));
    }

    @Test
    public void createTransactionAccountItself() throws Exception {
        String uniqueAccountFromId = "Id-" + System.currentTimeMillis();
        this.accountsService.createAccount(new Account(uniqueAccountFromId, new BigDecimal("123.45")));

        Account accountFrom = accountsService.getAccount(uniqueAccountFromId);
        assertThat(accountFrom).isNotNull();

        this.mockMvc.perform(
                post("/v1/accounts/" + uniqueAccountFromId + "/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountFromId\":\"" + uniqueAccountFromId + "\"," +
                                "\"accountToId\":\"" + uniqueAccountFromId + "\"," +
                                "\"amount\":" + "10" + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Cannot transfer from account to itself."));
    }

    @Test
    public void createTransactionIncorrectAccountFromId() throws Exception {
        String uniqueAccountToId = "Id-" + System.currentTimeMillis();
        this.accountsService.createAccount(new Account(uniqueAccountToId, new BigDecimal("123.45")));

        Account accountTo = accountsService.getAccount(uniqueAccountToId);
        assertThat(accountTo).isNotNull();

        String incorrectAccountFromId = "Id-incorrect-" + System.currentTimeMillis();

        this.mockMvc.perform(
                post("/v1/accounts/" + incorrectAccountFromId + "/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountFromId\":\"" + incorrectAccountFromId + "\"," +
                                "\"accountToId\":\"" + uniqueAccountToId + "\"," +
                                "\"amount\":" + "10" + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Account is not found by id " + incorrectAccountFromId));
    }

    @Test
    public void createTransactionIncorrectAccountToId() throws Exception {
        String uniqueAccountFromId = "Id-" + System.currentTimeMillis();
        this.accountsService.createAccount(new Account(uniqueAccountFromId, new BigDecimal("123.45")));

        Account accountFrom = accountsService.getAccount(uniqueAccountFromId);
        assertThat(accountFrom).isNotNull();

        String incorrectAccountToId = "Id-incorrect-" + System.currentTimeMillis();

        this.mockMvc.perform(
                post("/v1/accounts/" + uniqueAccountFromId + "/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountFromId\":\"" + uniqueAccountFromId + "\"," +
                                "\"accountToId\":\"" + incorrectAccountToId + "\"," +
                                "\"amount\":" + "10" + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Account is not found by id " + incorrectAccountToId));
    }

    @Test
    public void createTransactionInsufficientBalance() throws Exception {
        String uniqueAccountFromId = "Id-" + System.currentTimeMillis();
        this.accountsService.createAccount(new Account(uniqueAccountFromId, new BigDecimal("123.45")));
        String uniqueAccountToId = "Id-0" + System.currentTimeMillis();
        this.accountsService.createAccount(new Account(uniqueAccountToId, new BigDecimal("34.16")));

        Account accountFrom = accountsService.getAccount(uniqueAccountFromId);
        assertThat(accountFrom).isNotNull();
        Account accountTo = accountsService.getAccount(uniqueAccountToId);
        assertThat(accountTo).isNotNull();

        BigDecimal amount = new BigDecimal("145.31");

        this.mockMvc.perform(
                post("/v1/accounts/" + uniqueAccountFromId + "/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountFromId\":\"" + uniqueAccountFromId + "\"," +
                                "\"accountToId\":\"" + uniqueAccountToId + "\"," +
                                "\"amount\":" + amount + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(content()
                        .string("An account with id " + uniqueAccountFromId + " has an insufficient balance."));
    }
}
