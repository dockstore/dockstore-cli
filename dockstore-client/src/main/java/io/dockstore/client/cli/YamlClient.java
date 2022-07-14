/*
 *    Copyright 2022 OICR and UCSC
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

import java.util.List;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import io.dockstore.client.cli.YamlVerify.ValidateYamlException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.dockstore.client.cli.ArgumentUtility.out;
import static io.dockstore.client.cli.JCommanderUtility.printJCommanderHelp;
import static io.dockstore.client.cli.YamlVerify.DOCKSTOREYML;
import static io.dockstore.client.cli.YamlVerify.YAML;

public final class YamlClient {

    public static final String VALIDATE_HELP_MESSAGE = "Verifies " + DOCKSTOREYML + " is correct and ensures all required files are present";

    private YamlClient() {
        // disable constructor for utility class
    }


    /**
     * @param args
     */
    public static boolean handleCommand(List<String> args) {
        String[] argv = args.toArray(new String[args.size()]);
        JCommander jc = new JCommander();

        CommandYaml commandYaml = new CommandYaml();
        JCommander jcPlugin = JCommanderUtility.addCommand(jc, YAML, commandYaml);
        CommandYamlValidate commandYamlValidate = new CommandYamlValidate();
        JCommanderUtility.addCommand(jcPlugin, "validate", commandYamlValidate);
        try {
            jcPlugin.parse(argv);
        } catch (ParameterException ex) {
            printJCommanderHelp(jc, "dockstore", YAML);
            out(ex.getMessage());
            return true;
        }
        if (commandYaml.help) {
            printJCommanderHelp(jc, "dockstore", YAML);
        } else {
            switch (jcPlugin.getParsedCommand()) {
            case "validate":
                if (commandYamlValidate.help) {
                    printJCommanderHelp(jc, "dockstore", YAML);
                } else {
                    try {
                        YamlVerify.dockstoreValidate(CommandYaml.path);
                    } catch (ValidateYamlException ex) {
                        out(ex.getMessage());
                    }
                }
                break;
            default:
                // fall through
            }
        }
        return true;
    }


    @Parameters(separators = "=", commandDescription = "Tools used for " + YAML + " files")
    private static class CommandYaml {
        @Parameter(names = "--path", description = "Path to " + DOCKSTOREYML + " (ex. /home/usr/Dockstore/test, ~/Dockstore/test, or ../test)", required = true)
        private static String path = null;
        @Parameter(names = "--help", description = "Prints help for " + YAML + " command", help = true)
        private boolean help = false;
    }

    @Parameters(separators = "=", commandDescription = VALIDATE_HELP_MESSAGE)
    private static class CommandYamlValidate {
        @Parameter(names = "--help", description = VALIDATE_HELP_MESSAGE, help = true)
        private boolean help = false;
    }


}
