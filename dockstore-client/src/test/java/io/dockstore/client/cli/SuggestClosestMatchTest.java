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
import static org.junit.Assert.assertTrue;

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
        List<String> result = ArgumentUtility.minDistance("oran", acceptedCommands);
        Assertions.assertEquals(1, result.size());
        assertTrue(result.contains("orange"));

        acceptedCommands.add("test1");
        acceptedCommands.add("test2");
        acceptedCommands.add("test3");
        result = ArgumentUtility.minDistance("test7", acceptedCommands);
        Assertions.assertEquals(3, result.size());
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
        assertTrue(ArgumentUtility.minDistance("zzzzzzzzzzzz", acceptedCommands).isEmpty());
        acceptedCommands.add("bbbbbbbb");
        assertTrue(ArgumentUtility.minDistance("qqqqqqq", acceptedCommands).isEmpty());
        acceptedCommands.add("fffffffff");
        assertTrue(ArgumentUtility.minDistance("pppppppp", acceptedCommands).isEmpty());
    }

    @Test
    public void noSuggestions() {
        systemOutRule.clear();
        List<String> acceptedCommands = new ArrayList<String>();
        acceptedCommands.add("tool");
        acceptedCommands.add("workflow");
        acceptedCommands.add(CHECKER);
        acceptedCommands.add(PLUGIN);
        acceptedCommands.add(DEPS);
        acceptedCommands.add("yaml");
        ArgumentUtility.invalid("", "z", acceptedCommands);
        Assertions.assertEquals("dockstore: 'z' is not a dockstore command. See 'dockstore --help'.\n",
                systemOutRule.getText());
        systemOutRule.clear();

        ArgumentUtility.invalid("", "xxzz", acceptedCommands);
        Assertions.assertEquals("dockstore: 'xxzz' is not a dockstore command. See 'dockstore --help'.\n",
                systemOutRule.getText());
        systemOutRule.clear();

        ArgumentUtility.invalid("random_command_1", "xxzz", acceptedCommands);
        Assertions.assertEquals("dockstore random_command_1: 'xxzz' is not a dockstore command. "
                        + "See 'dockstore random_command_1 --help'.\n",
                systemOutRule.getText());
        systemOutRule.clear();

        ArgumentUtility.invalid("random_command_1 random_command_2", "xxzz", acceptedCommands);
        Assertions.assertEquals("dockstore random_command_1 random_command_2: 'xxzz' is not a dockstore command. "
                        + "See 'dockstore random_command_1 random_command_2 --help'.\n",
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
        acceptedCommands.add("tool");
        acceptedCommands.add("workflow");
        acceptedCommands.add(CHECKER);
        acceptedCommands.add(PLUGIN);
        acceptedCommands.add(DEPS);
        acceptedCommands.add("yaml");

        ArgumentUtility.invalid("", "CHECKER", acceptedCommands);
        Assertions.assertEquals("""
                        dockstore: 'CHECKER' is not a dockstore command. See 'dockstore --help'.
                                                
                        The most similar command is:
                            checker
                        """,
                systemOutRule.getText());
        systemOutRule.clear();

        ArgumentUtility.invalid("", "Checker", acceptedCommands);
        Assertions.assertEquals("""
                        dockstore: 'Checker' is not a dockstore command. See 'dockstore --help'.
                                                
                        The most similar command is:
                            checker
                        """,
                systemOutRule.getText());
        systemOutRule.clear();

        ArgumentUtility.invalid("", "cheCKer", acceptedCommands);
        Assertions.assertEquals("""
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
        acceptedCommands.add("tool");

        ArgumentUtility.invalid("", "too", acceptedCommands);
        Assertions.assertEquals("""
                        dockstore: 'too' is not a dockstore command. See 'dockstore --help'.
                        
                        The most similar command is:
                            tool
                        """,
                systemOutRule.getText());
        systemOutRule.clear();

        acceptedCommands.add("workflow");
        acceptedCommands.add(CHECKER);
        acceptedCommands.add(PLUGIN);
        acceptedCommands.add(DEPS);
        acceptedCommands.add("yaml");

        ArgumentUtility.invalid("", "pluggn", acceptedCommands);
        Assertions.assertEquals("""
                        dockstore: 'pluggn' is not a dockstore command. See 'dockstore --help'.
                        
                        The most similar command is:
                            plugin
                        """,
                systemOutRule.getText());
        systemOutRule.clear();

        ArgumentUtility.invalid("random_1 random_2", "y", acceptedCommands);
        Assertions.assertEquals("""
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
        acceptedCommands.add("workflow");
        acceptedCommands.add(CHECKER);
        acceptedCommands.add("test1");
        acceptedCommands.add(PLUGIN);
        acceptedCommands.add(DEPS);
        acceptedCommands.add("test2");
        acceptedCommands.add("yaml");

        ArgumentUtility.invalid("", "test0", acceptedCommands);
        Assertions.assertEquals("""
                        dockstore: 'test0' is not a dockstore command. See 'dockstore --help'.
                        
                        The most similar commands are:
                            test1
                            test2
                        """,
                systemOutRule.getText());
        systemOutRule.clear();

        acceptedCommands.add("test8");
        ArgumentUtility.invalid("random_1 random_2", "test0", acceptedCommands);
        Assertions.assertEquals("""
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
