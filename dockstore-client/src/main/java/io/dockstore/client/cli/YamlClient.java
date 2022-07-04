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

import java.util.List;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import io.dockstore.client.cli.YamlVerify.ValidateYamlException;
import io.dockstore.common.FileProvisionUtil;
import org.apache.commons.configuration2.INIConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.dockstore.client.cli.ArgumentUtility.out;
import static io.dockstore.client.cli.JCommanderUtility.printJCommanderHelp;

/**
 *
 */
public final class YamlClient {

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
        JCommander jcPlugin = JCommanderUtility.addCommand(jc, "yaml", commandYaml);
        CommandYamlValidate commandYamlValidate = new CommandYamlValidate();
        JCommanderUtility.addCommand(jcPlugin, "validate", commandYamlValidate);

        // Not parsing with jc because we know the first command was plugin.  jc's purpose is to display help
        jcPlugin.parse(argv);
        try {
            if (args.isEmpty() || commandYaml.help) {
                printJCommanderHelp(jc, "dockstore", "yaml");
            } else {
                switch (jcPlugin.getParsedCommand()) {
                case "validate":
                    if (commandYamlValidate.help) {
                        printJCommanderHelp(jc, "dockstore", "validate");
                    } else {
                        try {
                            YamlVerify.dockstoreValidate(commandYaml.path);
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
            printJCommanderHelp(jc, "dockstore", "yaml");
        }
        return true;

    }



    private static boolean handleDownload(INIConfiguration configFile) {
        FileProvisionUtil.downloadPlugins(configFile);
        return true;
    }


    @Parameters(separators = "=", commandDescription = "Tools used for yaml files")
    private static class CommandYaml {
        @Parameter(names = "--help", description = "Prints help for yaml command", help = true)
        private boolean help = false;
        @Parameter(names = "--path", description = "Sets the location of config.yml", required = true)
        private String path;

    }

    @Parameters(separators = "=", commandDescription = "List currently activated file provision plugins")
    private static class CommandYamlValidate {

        @Parameter(names = "--help", description = "Verifies that .dockstore.yml has the correct fields, and that all the required files are present", help = true)
        private boolean help = false;

    }


}
