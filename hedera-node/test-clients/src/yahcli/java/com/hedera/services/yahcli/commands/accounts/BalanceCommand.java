/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.services.yahcli.commands.accounts;

import com.hedera.services.yahcli.config.ConfigUtils;
import com.hedera.services.yahcli.suites.BalanceSuite;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

@Command(
        name = "balance",
        subcommands = {HelpCommand.class},
        description = "Checks account balances")
public class BalanceCommand implements Callable<Integer> {
    @ParentCommand
    AccountsCommand accountsCommand;

    @Parameters(arity = "1..*", paramLabel = "<accounts>", description = "account names or numbers")
    String[] accounts;

    @Override
    public Integer call() throws Exception {
        var config = ConfigUtils.configFrom(accountsCommand.getYahcli());

        StringBuilder balanceRegister = new StringBuilder();
        String serviceBorder = "---------------------|----------------------|";
        balanceRegister.append(serviceBorder).append("\n");
        balanceRegister.append(String.format("%20s | %20s |%n", "Account Id", "Balance"));
        balanceRegister.append(serviceBorder);

        printTable(balanceRegister);

        var delegate = new BalanceSuite(config.asSpecConfig(), accounts);
        delegate.runSuiteSync();

        return 0;
    }

    private void printTable(final StringBuilder balanceRegister) {
        System.out.println(balanceRegister.toString());
    }
}
