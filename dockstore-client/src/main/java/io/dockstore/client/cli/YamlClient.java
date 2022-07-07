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
import static io.dockstore.client.cli.JCommanderUtility.printJCommanderHelpYaml;
import static io.dockstore.client.cli.YamlVerify.YAML;

/**
 *
 */
public final class YamlClient {

    public static final String NO_PATH_FLAG = "ERROR: Missing --path <path>";
    private static final Logger LOG = LoggerFactory.getLogger(YamlClient.class);


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
        commandYamlValidate = new CommandYamlValidate();
        JCommanderUtility.addCommand(jcPlugin, "validate", commandYamlValidate);

        // Not parsing with jc because we know the first command was plugin.  jc's purpose is to display help
        jcPlugin.parse(argv);
        try {
            if (args.isEmpty() || commandYaml.help) {
                printJCommanderHelpYaml(jc, "dockstore", YAML);
            } else {
                switch (jcPlugin.getParsedCommand()) {
                case "validate":
                    if (commandYamlValidate.help) {
                        printJCommanderHelpYaml(jc, "dockstore", YAML);
                    } else if (commandYamlValidate.path == null) {
                        printJCommanderHelpYaml(jc, "dockstore", YAML);
                        out(NO_PATH_FLAG);
                    } else {
                        try {
                            YamlVerify.dockstoreValidate(CommandYamlValidate.path);
                        } catch (ValidateYamlException Ex) {
                            out(Ex.getMessage());
                        }
                        return true;
                    }
                    break;
                default:
                    // fall through
                }
            }
        } catch (ParameterException e) {
            printJCommanderHelp(jc, "dockstore", YAML);
        }
        return true;

    }


    @Parameters(separators = "=", commandDescription = "Tools used for " + YAML + " files")
    private static class CommandYaml {
        @Parameter(names = "--help", description = "Prints help for " + YAML + " command", help = true)
        private boolean help = false;

    }

    @Parameters(separators = "=", commandDescription = "List currently activated file provision plugins")
    private static class CommandYamlValidate {
        @Parameter(names = "--path", description = "Complete entry path on computer (ex. /home/usr/test)", required = false)
        private static String path = null;
        @Parameter(names = "--help", description = "Verifies that .dockstore.yml has the correct fields, and that all the required files are present", help = true)
        private boolean help = false;
    }


}
