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

import io.dockstore.common.FlushingSystemErr;
import io.dockstore.common.FlushingSystemOut;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;


public class SuggestClosestMatchTest {

    @Test
    public void onlyOneCorrectSolution() {
        List<String> acceptedCommands = new ArrayList<String>();
        LevenshteinDistance levenshteinDistance = new LevenshteinDistance();
        System.out.println(levenshteinDistance.apply("oran", "orange"));
    }

    /**
     * Tests for the case when the word entered is not at all similar to any of the possible commands
     */
    @Test
    public void noSolutionsShouldBeDisplayed() {
        // The threshold could change, but these should never return a result
        List<String> acceptedCommands = new ArrayList<String>();
        acceptedCommands.add("abcdefgh");
        assertTrue(ArgumentUtility.minDistance("b",acceptedCommands).isEmpty());
        assertTrue(ArgumentUtility.minDistance("zzzzzzzzzzzz",acceptedCommands).isEmpty());
        acceptedCommands.add("bbbbbbbb");
        assertTrue(ArgumentUtility.minDistance("cccccc",acceptedCommands).isEmpty());
        acceptedCommands.add("fffffffff");
        assertTrue(ArgumentUtility.minDistance("pppppppp",acceptedCommands).isEmpty());
    }

}
