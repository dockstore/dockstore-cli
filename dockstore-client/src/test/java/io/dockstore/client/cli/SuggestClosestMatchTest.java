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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;

import static io.dockstore.client.cli.ArgumentUtility.invalid;
import static io.dockstore.client.cli.ArgumentUtility.minDistance;
import static io.dockstore.client.cli.Client.CHECKER;
import static io.dockstore.client.cli.Client.DEPS;
import static io.dockstore.client.cli.Client.HELP;
import static io.dockstore.client.cli.Client.PLUGIN;
import static io.dockstore.client.cli.Client.TOOL;
import static io.dockstore.client.cli.Client.WORKFLOW;
import static io.dockstore.client.cli.YamlVerifyUtility.YAML;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(SystemStubsExtension.class)
public class SuggestClosestMatchTest {

    @SystemStub
    public final SystemOut systemOutRule = new SystemOut();

    @SystemStub
    public final SystemErr systemErrRule = new SystemErr();

    @Test
    public void onlyOneCorrectSolution() {
        List<String> acceptedCommands = new ArrayList<String>();

        acceptedCommands.add("orange");
        acceptedCommands.add("juice");
        List<String> result = minDistance("oran", acceptedCommands);
        assertEquals(1, result.size());
        assertTrue(result.contains("orange"));

        acceptedCommands.add("test1");
        acceptedCommands.add("test2");
        acceptedCommands.add("test3");
        result = minDistance("test7", acceptedCommands);
        assertEquals(3, result.size());
        assertTrue(result.contains("test1"));
        assertTrue(result.contains("test2"));
        assertTrue(result.contains("test3"));
    }

    /**
     * Tests for the case when the word entered is not at all similar to any of the possible commands
     */
    @Test
    public void noSolutionsShouldBeDisplayed() {
        // The threshold could change, but these should never return a result
        List<String> acceptedCommands = new ArrayList<String>();
        acceptedCommands.add("abcdefgh");
        assertTrue(minDistance("zzzzzzzzzzzz", acceptedCommands).isEmpty());
        acceptedCommands.add("bbbbbbbb");
        assertTrue(minDistance("qqqqqqq", acceptedCommands).isEmpty());
        acceptedCommands.add("fffffffff");
        assertTrue(minDistance("pppppppp", acceptedCommands).isEmpty());
    }

    @Test
    public void noSuggestions() {
        systemOutRule.clear();
        List<String> acceptedCommands = new ArrayList<String>();
        acceptedCommands.add(TOOL);
        acceptedCommands.add(WORKFLOW);
        acceptedCommands.add(CHECKER);
        acceptedCommands.add(PLUGIN);
        acceptedCommands.add(DEPS);
        acceptedCommands.add(YAML);
        invalid("", "z", acceptedCommands);
        assertEquals("dockstore: 'z' is not a dockstore command. See 'dockstore " + HELP + "'.\n",
                systemOutRule.getText());
        systemOutRule.clear();

        invalid("", "xxzz", acceptedCommands);
        assertEquals("dockstore: 'xxzz' is not a dockstore command. See 'dockstore " + HELP + "'.\n",
                systemOutRule.getText());
        systemOutRule.clear();

        invalid("random_command_1", "xxzz", acceptedCommands);
        assertEquals("dockstore random_command_1: 'xxzz' is not a dockstore command. "
                        + "See 'dockstore random_command_1 " + HELP + "'.\n",
                systemOutRule.getText());
        systemOutRule.clear();

        invalid("random_command_1 random_command_2", "xxzz", acceptedCommands);
        assertEquals("dockstore random_command_1 random_command_2: 'xxzz' is not a dockstore command. "
                        + "See 'dockstore random_command_1 random_command_2 " + HELP + "'.\n",
                systemOutRule.getText());
        systemOutRule.clear();
    }

    /**
     * Ensures that if a user accidentally uses upper-case, they are shown the correct lower-case command
     */
    @Test
    public void usedUpperCase() {
        systemOutRule.clear();
        List<String> acceptedCommands = new ArrayList<String>();
        acceptedCommands.add(TOOL);
        acceptedCommands.add(WORKFLOW);
        acceptedCommands.add(CHECKER);
        acceptedCommands.add(PLUGIN);
        acceptedCommands.add(DEPS);
        acceptedCommands.add(YAML);

        invalid("", "CHECKER", acceptedCommands);
        assertEquals("""
                        dockstore: 'CHECKER' is not a dockstore command. See 'dockstore --help'.
                                                
                        The most similar command is:
                            checker
                        """,
                systemOutRule.getText());
        systemOutRule.clear();

        invalid("", "Checker", acceptedCommands);
        assertEquals("""
                        dockstore: 'Checker' is not a dockstore command. See 'dockstore --help'.
                                                
                        The most similar command is:
                            checker
                        """,
                systemOutRule.getText());
        systemOutRule.clear();

        invalid("", "cheCKer", acceptedCommands);
        assertEquals("""
                        dockstore: 'cheCKer' is not a dockstore command. See 'dockstore --help'.
                                                
                        The most similar command is:
                            checker
                        """,
                systemOutRule.getText());
        systemOutRule.clear();

    }

    @Test
    public void onlyOneCloseMatch() {
        systemOutRule.clear();
        List<String> acceptedCommands = new ArrayList<String>();
        acceptedCommands.add(TOOL);

        invalid("", "too", acceptedCommands);
        assertEquals("""
                        dockstore: 'too' is not a dockstore command. See 'dockstore --help'.
                        
                        The most similar command is:
                            tool
                        """,
                systemOutRule.getText());
        systemOutRule.clear();

        acceptedCommands.add(WORKFLOW);
        acceptedCommands.add(CHECKER);
        acceptedCommands.add(PLUGIN);
        acceptedCommands.add(DEPS);
        acceptedCommands.add(YAML);

        invalid("", "pluggn", acceptedCommands);
        assertEquals("""
                        dockstore: 'pluggn' is not a dockstore command. See 'dockstore --help'.
                        
                        The most similar command is:
                            plugin
                        """,
                systemOutRule.getText());
        systemOutRule.clear();

        invalid("random_1 random_2", "y", acceptedCommands);
        assertEquals("""
                        dockstore random_1 random_2: 'y' is not a dockstore command. See 'dockstore random_1 random_2 --help'.
                        
                        The most similar command is:
                            yaml
                        """,
                systemOutRule.getText());
        systemOutRule.clear();

    }

    @Test
    public void severalCloseMatches() {
        systemOutRule.clear();
        List<String> acceptedCommands = new ArrayList<String>();
        acceptedCommands.add(WORKFLOW);
        acceptedCommands.add(CHECKER);
        acceptedCommands.add("test1");
        acceptedCommands.add(PLUGIN);
        acceptedCommands.add(DEPS);
        acceptedCommands.add("test2");
        acceptedCommands.add(YAML);

        invalid("", "test0", acceptedCommands);
        assertEquals("""
                        dockstore: 'test0' is not a dockstore command. See 'dockstore --help'.
                        
                        The most similar commands are:
                            test1
                            test2
                        """,
                systemOutRule.getText());
        systemOutRule.clear();

        acceptedCommands.add("test8");
        invalid("random_1 random_2", "test0", acceptedCommands);
        assertEquals("""
                        dockstore random_1 random_2: 'test0' is not a dockstore command. See 'dockstore random_1 random_2 --help'.
                        
                        The most similar commands are:
                            test1
                            test2
                            test8
                        """,
                systemOutRule.getText());
        systemOutRule.clear();
    }
}
