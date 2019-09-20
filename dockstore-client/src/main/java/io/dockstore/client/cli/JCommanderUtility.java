/*
 *    Copyright 2017 OICR
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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.beust.jcommander.DefaultUsageFormatter;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterDescription;
import com.beust.jcommander.Strings;
import com.beust.jcommander.WrappedParameter;

import static io.dockstore.client.cli.ArgumentUtility.out;
import static io.dockstore.client.cli.ArgumentUtility.outFormatted;
import static io.dockstore.client.cli.ArgumentUtility.printHelpHeader;

/**
 * @author gluu
 * @since 01/03/17
 */
public final class JCommanderUtility {
    private JCommanderUtility() {
        // hide the constructor for utility classes
    }

    // Have to override printJCommanderHelp because launch has --entry OR --local-entry as required parameters.
    // It's probably better to just have them as two separate commands.
    public static void printJCommanderHelpLaunch(JCommander jc, String programName, String commandName) {
        DefaultUsageFormatter formatter = new DefaultUsageFormatter(jc);
        String description = formatter.getCommandDescription(commandName);
        printHelpHeader();
        printJCommanderHelpUsage(programName, commandName, jc);
        printJCommanderHelpDescription(description);
        out("Required parameters:\n"
                + "  --entry <entry>                     Complete workflow path in the Dockstore (ex. NCI-GDC/gdc-dnaseq-cwl/GDC_DNASeq:master)\n"
                + "   OR\n"
                + "  --local-entry <local-entry>         Allows you to specify a full path to a local descriptor instead of an entry path\n");
        out("Optional parameters:\n"
                + "  --json <json file>                  Parameters to the entry in the dockstore, one map for one run, an array of maps for multiple runs\n"
                + "  --yaml <yaml file>                  Parameters to the entry in the dockstore, one map for one run, an array of maps for multiple runs\n"
                + "  --tsv <tsv file>                    One row corresponds to parameters for one run in the dockstore (Only for CWL)\n"
                + "  --wdl-output-target                 Allows you to specify a remote path to provision output files to ex: s3://oicr.temp/testing-launcher/\n"
                + "  --uuid                              Allows you to specify a uuid for 3rd party notifications\n");
        printJCommanderHelpFooter();
    }

    public static void printJCommanderHelp(JCommander jc, String programName, String commandName) {
        JCommander commander = jc.getCommands().get(commandName);
        DefaultUsageFormatter formatter = new DefaultUsageFormatter(jc);
        String description = formatter.getCommandDescription(commandName);
        List<ParameterDescription> sorted = commander.getParameters();

        printHelpHeader();
        printJCommanderHelpUsage(programName, commandName, commander);
        printJCommanderHelpDescription(description);
        printJCommanderHelpCommand(commander);
        printJCommanderHelpRequiredParameters(sorted);
        printJCommanderHelpOptionalParameters(sorted);
        printJCommanderHelpFooter();
    }

    private static void printJCommanderHelpUsage(String programName, String commandName, JCommander jc) {
        out("Usage: " + programName + " " + commandName + " --help");
        if (jc.getCommands().isEmpty()) {
            out("       " + programName + " " + commandName + " [parameters]");
        } else {
            out("       " + programName + " " + commandName + " [parameters] [command]");
        }
        out("");
    }

    private static void printJCommanderHelpCommand(JCommander jc) {
        DefaultUsageFormatter formatter = new DefaultUsageFormatter(jc);
        Map<String, JCommander> commands = jc.getCommands();
        if (!commands.isEmpty()) {
            out("Commands: ");
            for (Map.Entry<String, JCommander> commanderEntry : commands.entrySet()) {
                out("  " + commanderEntry.getKey());
                out("    " + formatter.getCommandDescription(commanderEntry.getKey()));
            }
        }
    }

    private static void printJCommanderHelpDescription(String commandDescription) {
        out("Description:");
        out("  " + commandDescription);
        out("");
    }

    private static void printJCommanderHelpRequiredParameters(List<ParameterDescription> sorted) {
        int maxLength = 0;
        for (ParameterDescription pd : sorted) {
            int length = pd.getNames().length();
            maxLength = Math.max(length, maxLength);
        }
        maxLength = ((maxLength + 2) * 2);
        boolean first = true;
        for (ParameterDescription pd : sorted) {
            WrappedParameter parameter = pd.getParameter();
            if (parameter.required() && !Objects.equals(pd.getNames(), "--help")) {
                if (first) {
                    out("Required parameters:");
                    first = false;
                }
                printJCommanderHelpParameter(pd, parameter, maxLength);
            }
        }
        out("");
    }

    private static void printJCommanderHelpOptionalParameters(List<ParameterDescription> sorted) {
        int maxLength;
        ParameterDescription maxParameter = Collections.max(sorted, (first, second) -> {
            if (first.getNames().length() > second.getNames().length()) {
                return 1;
            } else {
                return 0;
            }
        });
        maxLength = ((maxParameter.getNames().length() + 2) * 2);
        boolean first = true;
        for (ParameterDescription pd : sorted) {
            WrappedParameter parameter = pd.getParameter();
            if (!parameter.required() && !pd.getNames().equals("--help")) {
                if (first) {
                    out("Optional parameters:");
                    first = false;
                }
                printJCommanderHelpParameter(pd, parameter, maxLength);
            }
        }
        out("");
    }

    private static void printJCommanderHelpParameter(ParameterDescription pd, WrappedParameter parameter, int maxLength) {
        outFormatted("%-" + maxLength + "s %s", "  " + pd.getNames() + " <" + pd.getNames().replaceAll("--", "") + ">", pd.getDescription());
        Object def = pd.getDefault();
        if (pd.isDynamicParameter()) {
            out("Syntax: " + parameter.names()[0] + "key" + parameter.getAssignment() + "value");
        }
        if (def != null && !pd.isHelp()) {
            String displayedDef = Strings.isStringEmpty(def.toString()) ? "<empty string>" : def.toString();
            outFormatted("%-" + maxLength + "s %s", "  ", "Default: " + (parameter.password() ? "********" : displayedDef));
        }
    }

    private static void printJCommanderHelpFooter() {
        out("---------------------------------------------");
        out("");
    }

    /**
     * This function is used when nesting commands within commands (ex. list and download within plugin)
     *
     * @param parentCommand The parent command (ex. plugin)
     * @param commandName   The nested command name (ex. "list")
     * @param commandObject The nested command (ex. list)
     * @return
     */
    public static JCommander addCommand(JCommander parentCommand, String commandName, Object commandObject) {
        parentCommand.addCommand(commandName, commandObject);
        return parentCommand.getCommands().get(commandName);
    }
}
