/*
 *    Copyright 2023 OICR
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package io.dockstore.client.cli;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;

import static io.dockstore.client.cli.Client.CHECKER;
import static io.dockstore.client.cli.Client.DEPS;
import static io.dockstore.client.cli.Client.PLUGIN;
import static io.dockstore.client.cli.Client.TOOL;
import static io.dockstore.client.cli.Client.WORKFLOW;
import static io.dockstore.client.cli.YamlVerifyUtility.YAML;

@ExtendWith(SystemStubsExtension.class)
public class InvalidCommandIT extends BaseIT {

    @SystemStub
    public final SystemOut systemOutRule = new SystemOut();

    @SystemStub
    public final SystemErr systemErrRule = new SystemErr();


    @Test
    public void noSuggestions() {
        List<String> acceptedCommands = new ArrayList<String>();
        acceptedCommands.add(TOOL);
        acceptedCommands.add(WORKFLOW);
        acceptedCommands.add(CHECKER);
        acceptedCommands.add(PLUGIN);
        acceptedCommands.add(DEPS);
        acceptedCommands.add(YAML);
        ArgumentUtility.invalid("", "z", acceptedCommands);
        System.out.println("HELLO");
        String output = systemOutRule.getOutput().getText();
        Assertions.assertEquals("test", output);
    }
}
