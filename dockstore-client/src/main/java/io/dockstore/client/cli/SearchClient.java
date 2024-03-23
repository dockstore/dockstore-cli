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

import static io.dockstore.client.cli.ArgumentUtility.exceptionMessage;
import static io.dockstore.client.cli.ArgumentUtility.out;
import static io.dockstore.client.cli.Client.HELP;
import static io.dockstore.client.cli.Client.PLUGIN;
import static io.dockstore.client.cli.Client.TOOL;
import static io.dockstore.client.cli.Client.WORKFLOW;
import static io.dockstore.client.cli.JCommanderUtility.printJCommanderHelp;
import static io.dockstore.client.cli.nested.AbstractEntryClient.SEARCH;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import io.dockstore.common.ToolWorkflowDeserializer;
import io.dockstore.openapi.client.ApiException;
import io.dockstore.openapi.client.api.ExtendedGa4GhApi;
import io.dockstore.openapi.client.model.ToolV1;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author dyuen
 *
 * Command-line search prototype
 * This is not currently hooked up but is functional and could be expanded if needed.
 */
public final class SearchClient {

    private static final Logger LOG = LoggerFactory.getLogger(SearchClient.class);

    private SearchClient() {
        // disable constructor for utility class
    }

    /**
     * This function is used when nesting commands within commands (ex. list and download within plugin)
     *
     * @param parentCommand The parent command (ex. plugin)
     * @param commandName   The nested command name (ex. "list")
     * @param commandObject The nested command (ex. list)
     * @return
     */
    private static JCommander addCommand(JCommander parentCommand, String commandName, Object commandObject) {
        parentCommand.addCommand(commandName, commandObject);
        return parentCommand.getCommands().get(commandName);
    }

    /**
     * @param args
     */
    static boolean handleCommand(List<String> args, ExtendedGa4GhApi api) {
        String[] argv = args.toArray(new String[0]);
        JCommander jc = new JCommander();

        SearchPlugin searchPlugin = new SearchPlugin();
        JCommander jcPlugin = addCommand(jc, SEARCH, searchPlugin);

        IndexPlugin indexPlugin = new IndexPlugin();
        addCommand(jcPlugin, "index", indexPlugin);

        SearchPluginList searchPluginList = new SearchPluginList();
        addCommand(jcPlugin, SEARCH, searchPluginList);
        // Not parsing with jc because we know the first command was search.  jc's purpose is to display help
        jcPlugin.parse(argv);
        try {
            if (args.isEmpty() || searchPlugin.help) {
                printJCommanderHelp(jc, "dockstore", PLUGIN);
            } else {
                switch (jcPlugin.getParsedCommand()) {
                case "index":
                    if (indexPlugin.help) {
                        printJCommanderHelp(jc, "dockstore", "index");
                    } else {
                        return index(api);
                    }
                    break;
                case SEARCH:
                    if (searchPluginList.help) {
                        printJCommanderHelp(jc, "dockstore", SEARCH);
                    } else {
                        return search(api);
                    }
                    break;
                default:
                    // fall through
                }
            }
        } catch (ParameterException e) {
            printJCommanderHelp(jc, "dockstore", SEARCH);
        }
        return true;

    }

    private static boolean search(ExtendedGa4GhApi api) {
        // this needs to be improved, obviously a hardcoded search query is not what we'll want in the future
        String body = "{\"aggs\":{\"registry_0\":{\"terms\":{\"field\":\"registry\",\"size\":5}},\"author_1\":{\"terms\":{\"field\":\"author\",\"size\":10}}},\"query\":{\"match_all\":{}}}";
        try {
            String s = api.toolsIndexSearch(body);
            Gson gson = new GsonBuilder().registerTypeAdapter(ElasticSearchObject.HitsInternal.class, new ToolWorkflowDeserializer()).create();
            ElasticSearchObject elasticSearchObject = gson.fromJson(s, ElasticSearchObject.class);
            for (ElasticSearchObject.HitsInternal hit : elasticSearchObject.hits.hits) {
                if (hit.source instanceof ToolV1) {
                    out("Found deserialized " + TOOL);
                } else {
                    out("Found deserialized " + WORKFLOW);
                }
            }
            System.out.println(s);
        } catch (ApiException e) {
            exceptionMessage(e, "", Client.API_ERROR);
        }
        return true;
    }

    private static boolean index(ExtendedGa4GhApi api) {
        try {
            api.updateTheWorkflowsAndToolsIndices();
        } catch (ApiException e) {
            exceptionMessage(e, "", Client.API_ERROR);
        }
        return true;
    }

    @Parameters(separators = "=", commandDescription = "Search operations")
    private static class SearchPlugin {
        @Parameter(names = HELP, description = "Prints help for search in general command", help = true)
        private boolean help = false;
    }

    @Parameters(separators = "=", commandDescription = "Trigger an explicit index update, should be admin-only eventually")
    private static class IndexPlugin {
        @Parameter(names = HELP, description = "Prints help for index command", help = true)
        private boolean help = false;
    }

    @Parameters(separators = "=", commandDescription = "Search currently published tools and workflows")
    private static class SearchPluginList {
        @Parameter(names = HELP, description = "Prints help for search command ", help = true)
        private boolean help = false;
    }

    public static class ElasticSearchObject {

        @SerializedName("hits")
        public HitsExternal hits;

        public class HitsExternal {
            @SerializedName("hits")
            public HitsInternal[] hits;
        }

        public class HitsInternal {
            @SerializedName("_source")
            public Object source;
        }
    }
}
