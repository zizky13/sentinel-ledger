-- 1. Accounts Table (The core user identity in the ledger)
CREATE TABLE accounts (
                          id UUID PRIMARY KEY, -- signifies the account
                          user_id UUID NOT NULL, -- signifies who owns the account. person may hold multiple accounts
                          currency VARCHAR(3) NOT NULL DEFAULT 'USD', -- currency of the account, might vary
                          status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- status of the account
                          created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 2. CQRS Read Table (The Materialized Balance for ultra-fast locks and reads)
CREATE TABLE account_balances (
                                  account_id UUID PRIMARY KEY REFERENCES accounts(id), -- connect account balance to the accounts
                                  current_balance NUMERIC(15, 4) NOT NULL DEFAULT 0.0000, -- separate concern of the account
                                  updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 3. Master Transaction Group (Groups the atomic multi-leg transfers)
CREATE TABLE transactions (
                              id UUID PRIMARY KEY,
                              idempotency_key UUID UNIQUE NOT NULL,
                              description TEXT,
                              status VARCHAR(20) NOT NULL DEFAULT 'COMMITTED',
                              created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 4. Immutable Journal Entries (The Write Table)
CREATE TABLE ledger_entries (
                                id UUID PRIMARY KEY,
                                transaction_id UUID NOT NULL REFERENCES transactions(id),
                                account_id UUID NOT NULL REFERENCES accounts(id),
                                amount NUMERIC(15, 4) NOT NULL,
                                created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for performance and idempotency
CREATE INDEX idx_ledger_entries_account_id ON ledger_entries(account_id);
CREATE INDEX idx_transactions_idempotency ON transactions(idempotency_key);