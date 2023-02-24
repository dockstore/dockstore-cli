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

import com.beust.jcommander.JCommander;
import com.beust.jcommander.MissingCommandException;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import com.google.common.base.Joiner;
import io.dockstore.common.FileProvisionUtil;
import io.dockstore.common.TabExpansionUtil;
import io.dockstore.provision.PreProvisionInterface;
import io.dockstore.provision.ProvisionInterface;
import org.apache.commons.configuration2.INIConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ro.fortsoft.pf4j.PluginManager;
import ro.fortsoft.pf4j.PluginWrapper;

import static io.dockstore.client.cli.ArgumentUtility.DOWNLOAD;
import static io.dockstore.client.cli.ArgumentUtility.errorMessage;
import static io.dockstore.client.cli.ArgumentUtility.out;
import static io.dockstore.client.cli.Client.CLIENT_ERROR;
import static io.dockstore.client.cli.Client.HELP;
import static io.dockstore.client.cli.Client.PLUGIN;
import static io.dockstore.client.cli.JCommanderUtility.displayJCommanderSuggestions;
import static io.dockstore.client.cli.JCommanderUtility.getUnknownParameter;
import static io.dockstore.client.cli.JCommanderUtility.printJCommanderHelp;
import static io.dockstore.client.cli.JCommanderUtility.wasErrorDueToUnknownParameter;
import static io.dockstore.client.cli.nested.AbstractEntryClient.LIST;
import static java.lang.String.join;

/**
 *
 */
public final class PluginClient {

    private static final Logger LOG = LoggerFactory.getLogger(PluginClient.class);

    private PluginClient() {
        // disable constructor for utility class
    }


    /**
     * @param args
     * @param configFile
     */
    public static boolean handleCommand(List<String> args, INIConfiguration configFile) {
        String[] argv = args.toArray(new String[args.size()]);
        JCommander jc = new JCommander();
        CommandPlugin commandPlugin = new CommandPlugin();
        JCommander jcPlugin = JCommanderUtility.addCommand(jc, PLUGIN, commandPlugin);

        CommandPluginList commandPluginList = new CommandPluginList();
        JCommanderUtility.addCommand(jcPlugin, LIST, commandPluginList);
        CommandPluginDownload commandPluginDownload = new CommandPluginDownload();
        JCommanderUtility.addCommand(jcPlugin, DOWNLOAD, commandPluginDownload);
        // Not parsing with jc because we know the first command was plugin.  jc's purpose is to display help
        try {
            jcPlugin.parse(argv);
            if (commandPlugin.help || args.isEmpty()) {
                printJCommanderHelp(jc, "dockstore", PLUGIN);
            } else {
                switch (jcPlugin.getParsedCommand()) {
                case LIST:
                    if (commandPluginList.help) {
                        printJCommanderHelp(jc, "dockstore", PLUGIN);
                    } else {
                        return handleList(configFile);
                    }
                    break;
                case DOWNLOAD:
                    if (commandPluginDownload.help) {
                        printJCommanderHelp(jc, "dockstore", PLUGIN);
                    } else {
                        return handleDownload(configFile);
                    }
                    break;
                default:
                    // fall through
                }
            }
        } catch (MissingCommandException e) {
            displayJCommanderSuggestions(jcPlugin, e.getJCommander().getParsedCommand(), args.get(0), PLUGIN);
        } catch (ParameterException e) {
            if (wasErrorDueToUnknownParameter(e.getMessage())) {
                String incorrectCommand = getUnknownParameter(e.getMessage());
                displayJCommanderSuggestions(jcPlugin, e.getJCommander().getParsedCommand(), incorrectCommand, join(" ", PLUGIN, e.getJCommander().getParsedCommand()));
            } else {
                errorMessage(e.getMessage(), CLIENT_ERROR);
            }
        }
        return true;

    }

    private static boolean handleList(INIConfiguration configFile) {
        PluginManager pluginManager = FileProvisionUtil.getPluginManager(configFile);
        List<PluginWrapper> plugins = pluginManager.getStartedPlugins();
        StringBuilder builder = new StringBuilder();
        builder.append("PluginId\tPlugin Version\tPlugin Path\tSchemes handled\tPlugin Type\n");
        for (PluginWrapper plugin : plugins) {
            builder.append(plugin.getPluginId());
            builder.append("\t");
            builder.append(plugin.getPlugin().getWrapper().getDescriptor().getVersion());
            builder.append("\t");
            builder.append(FileProvisionUtil.getFilePluginLocation(configFile)).append(plugin.getPlugin().getWrapper().getPluginPath())
                    .append("(.zip)");
            builder.append("\t");
            List<ProvisionInterface> extensions = pluginManager.getExtensions(ProvisionInterface.class, plugin.getPluginId());
            List<PreProvisionInterface> preProvisionExtensions = pluginManager.getExtensions(PreProvisionInterface.class, plugin.getPluginId());
            extensions.forEach(extension -> Joiner.on(',').appendTo(builder, extension.schemesHandled()));
            preProvisionExtensions.forEach(extension -> Joiner.on(',').appendTo(builder, extension.schemesHandled()));
            builder.append("\t");
            extensions.forEach(extension -> Joiner.on(',').appendTo(builder, Collections.singleton("Normal")));
            preProvisionExtensions.forEach(extension -> Joiner.on(',').appendTo(builder, Collections.singleton("PreProvision")));
            builder.append("\n");
        }
        out(TabExpansionUtil.aligned(builder.toString()));
        return true;
    }

    private static boolean handleDownload(INIConfiguration configFile) {
        FileProvisionUtil.downloadPlugins(configFile);
        return true;
    }

    @Parameters(separators = "=", commandDescription = "Configure and debug plugins")
    private static class CommandPlugin {
        @Parameter(names = HELP, description = "Prints help for " + PLUGIN + " command", help = true)
        private boolean help = false;
    }

    @Parameters(separators = "=", commandDescription = "List currently activated file provision plugins")
    private static class CommandPluginList {
        @Parameter(names = HELP, description = "Prints help for list command", help = true)
        private boolean help = false;
    }

    @Parameters(separators = "=", commandDescription = "Download default file provisioning plugins")
    private static class CommandPluginDownload {
        @Parameter(names = HELP, description = "Prints help for " + DOWNLOAD + " command", help = true)
        private boolean help = false;
    }

}
