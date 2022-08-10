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
import io.dockstore.client.cli.YamlVerifyUtility.ValidateYamlException;

import static io.dockstore.client.cli.ArgumentUtility.out;
import static io.dockstore.client.cli.JCommanderUtility.printJCommanderHelp;
import static io.dockstore.client.cli.YamlVerifyUtility.DOCKSTOREYML;
import static io.dockstore.client.cli.YamlVerifyUtility.YAML;

public final class YamlClient {

    public static final String VALIDATE_HELP_MESSAGE = "Verifies " + DOCKSTOREYML + " is correct and ensures all required files are present";
    public static final String ERROR_NO_COMMAND = "ERROR: No command given";



    /**
     * @param args
     */
    public boolean handleCommand(List<String> args) {
        String[] argv = args.toArray(new String[args.size()]);
        JCommander jc = new JCommander();

        CommandYaml commandYaml = new CommandYaml();
        JCommander jcPlugin = JCommanderUtility.addCommand(jc, YAML, commandYaml);
        CommandYamlValidate commandYamlValidate = new CommandYamlValidate();
        JCommanderUtility.addCommand(jcPlugin, YamlVerifyUtility.COMMAND_NAME, commandYamlValidate);
        try {
            jcPlugin.parse(argv);
        } catch (ParameterException ex) {
            if (YamlVerifyUtility.COMMAND_NAME.equals(ex.getJCommander().getParsedCommand())) {
                printJCommanderHelp(jcPlugin, "dockstore " + YAML, YamlVerifyUtility.COMMAND_NAME);
                out(ex.getMessage());
            } else {
                printJCommanderHelp(jc, "dockstore", YAML);
                out(ex.getMessage());
            }
            return true;
        }
        if (commandYaml.help) {
            printJCommanderHelp(jc, "dockstore", YAML);
        } else if  (jcPlugin.getParsedCommand() == null) {
            printJCommanderHelp(jc, "dockstore", YAML);
            out(ERROR_NO_COMMAND);
        } else {
            switch (jcPlugin.getParsedCommand()) {
            case YamlVerifyUtility.COMMAND_NAME:
                if (commandYamlValidate.help) {
                    printJCommanderHelp(jcPlugin, "dockstore " + YAML, YamlVerifyUtility.COMMAND_NAME);
                } else {
                    try {
                        YamlVerifyUtility.dockstoreValidate(commandYamlValidate.path);
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
    private class CommandYaml {
        @Parameter(names = "--help", description = "Prints help for " + YAML + " command", help = true)
        private boolean help = false;
    }

    @Parameters(separators = "=", commandDescription = VALIDATE_HELP_MESSAGE)
    private class CommandYamlValidate {
        @Parameter(names = "--path", description = "Directory that contains " + DOCKSTOREYML + " (ex. /home/usr/workflow-repo, ~/Dockstore/.github, or ../biotool)", required = true)
        private String path = null;
        @Parameter(names = "--help", description = VALIDATE_HELP_MESSAGE, help = true)
        private boolean help = false;
    }


}
