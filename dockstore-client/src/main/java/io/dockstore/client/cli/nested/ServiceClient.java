package io.dockstore.client.cli.nested;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameters;
import com.google.gson.Gson;
import io.dockstore.client.cli.ArgumentUtility;
import io.dockstore.client.cli.Client;
import io.dockstore.client.cli.JCommanderUtility;
import io.dockstore.common.FileProvisioning;
import io.dockstore.common.Utilities;
import io.swagger.client.api.UsersApi;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.model.ToolDescriptor;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.introspector.Property;
import org.yaml.snakeyaml.introspector.PropertyUtils;

import static io.dockstore.client.cli.ArgumentUtility.errorMessage;
import static io.dockstore.client.cli.ArgumentUtility.out;

public class ServiceClient extends WorkflowClient {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceClient.class);

    /**
     * Before any provisioning has executed.
     */
    private static final String PRE_PROVISION = "preprovision";

    /**
     * After files have been provisioned, before starting up the service
     */
    private static final String PRE_START = "prestart";

    /**
     * Starts up the service; required
     */
    private static final String START = "start";

    /**
     * After the service has been started
     */
    private static final String POST_START = "poststart";

    private static final String STOP = "stop";

    /**
     * After files have been provisioned and service has been started
     */
    private static final String POST_PROVISION = "postprovision";

    private  static final String[] PRE_PROVISION_SCRIPTS = {
        PRE_PROVISION
    };

    private static final String[] POST_PROVISION_SCRIPTS = {
        PRE_START,
        START,
        POST_START,
        POST_PROVISION
    };

    private final JCommander jCommander;
    private final CommandLaunch commandLaunch;
    private final FileProvisioning fileProvisioning;


    public ServiceClient(WorkflowsApi workflowsApi, UsersApi usersApi, Client client, boolean isAdmin) {
        super(workflowsApi, usersApi, client, isAdmin);
        this.jCommander = new JCommander();
        this.commandLaunch = new CommandLaunch();
        this.jCommander.addCommand("launch", commandLaunch);
        this.fileProvisioning = new FileProvisioning(getConfigFile());
    }

    @Override
    public boolean processEntrySpecificCommands(List<String> args, String activeCommand) {
        if (activeCommand != null) {
            switch (activeCommand) {
            case "stop":
                stop(args);
                return true;
            default:
            }
        }
        return false;
    }

    @Override
    public void launch(List<String> args) {
        String commandName = "launch";
        preValidateLaunchArguments(args);
        String[] argv = args.toArray(new String[0]);
        String[] argv1 = { commandName };
        String[] both = ArrayUtils.addAll(argv1, argv);
        this.jCommander.parse(both);
        String jsonRun = commandLaunch.json;
        final String entry = commandLaunch.entry;
        final String localEntry = commandLaunch.localEntry;
        if (entry == null && localEntry == null) {
            ArgumentUtility.out("Missing --entry or --local-entry.");
            JCommanderUtility.printJCommanderHelpServiceLaunch(jCommander, "dockstore service", commandName);
        } else {
            try {
                if (entry != null) {
                    // TODO: Verify directory is empty?
                    downloadTargetEntry(entry, ToolDescriptor.TypeEnum.SERVICE, true, new File("."));
                }
                final DockstoreServiceYaml dockstoreYaml = getAndValidateDockstoreYml();
                final Optional<Map<String, Object>> inputParameterJson = getInputParameterJson(dockstoreYaml, jsonRun);

                runScripts(dockstoreYaml, inputParameterJson, PRE_PROVISION_SCRIPTS);

                fileProvisioning.provisionInputFiles("", calculateInputs(Optional.ofNullable(dockstoreYaml.service.data), inputParameterJson));

                runScripts(dockstoreYaml, inputParameterJson, POST_PROVISION_SCRIPTS);

            } catch (IOException e) {
                LOG.error("Error launching service", e);
                out("Error launching service: " + e.getMessage());
            }
        }
    }

    public void stop(List<String> args) {
        final DockstoreServiceYaml dockstoreYml = getAndValidateDockstoreYml();
        runScripts(dockstoreYml, Optional.empty(), new String[] {STOP});
    }

    private DockstoreServiceYaml getAndValidateDockstoreYml() {
        final Constructor constructor = new Constructor(DockstoreServiceYaml.class);
        final PropertyUtils propertyUtils = new PropertyUtils() {
            @Override
            public Property getProperty(Class<?> type, String name) {
                return super.getProperty(type, "default".equals(name) ? "defaultValue" : name);
            }
        };
        propertyUtils.setSkipMissingProperties(true);
        constructor.setPropertyUtils(propertyUtils);
        final Yaml yaml = new Yaml(constructor);
        try {
            final DockstoreServiceYaml dockstoreYaml = yaml.load(new FileInputStream(".dockstore.yml"));
            validateDockstoreYaml(dockstoreYaml);
            return dockstoreYaml;
        } catch (FileNotFoundException e) {
            final String message = "Error loading .dockstore.yml";
            LOG.error(message, e);
            errorMessage(message, Client.CLIENT_ERROR);
            // Previous line exits VM, but need return to satisfy compiler
            return null;
        }
    }

    private Optional<Map<String, Object>> getInputParameterJson(DockstoreServiceYaml dockstoreYaml, String jsonFile) throws IOException {
        if (jsonFile == null) {
            return Optional.empty();
        }
        final Map<String, Object> inputParameterJson = new Gson().fromJson(fileToJSON(jsonFile), HashMap.class);
        if (dockstoreYaml.service.data != null) {
            validateDatasets(dockstoreYaml.service.data, inputParameterJson);
        }
        return Optional.of(inputParameterJson);
    }

    private void validateDockstoreYaml(DockstoreServiceYaml dockstoreYaml) {
        if (dockstoreYaml.service == null) {
            errorMessage("The .dockstore.yml contains no service section.", Client.CLIENT_ERROR);
        }
        if (dockstoreYaml.service.scripts == null || StringUtils.isBlank(dockstoreYaml.service.scripts.get(START))) {
            errorMessage("The .dockstore.yml is missing a 'start' script.", Client.CLIENT_ERROR);
        }
    }

    private void runScripts(DockstoreServiceYaml dockstoreYaml, Optional<Map<String, Object>> inputParameterJson, String[] scripts) {
        final File workingDir = Paths.get("").toAbsolutePath().toFile();
        final Map<String, String> environment = environment(dockstoreYaml, inputParameterJson);
        Arrays.stream(scripts)
                .map(scriptName -> dockstoreYaml.service.scripts.get(scriptName))
                .filter(StringUtils::isNotBlank)
                .forEach(script -> {
                    try {
                        // Since the entry is downloaded as a zip file, there will be no execute permissions
                        final ImmutablePair<String, String> output = Utilities
                                .executeCommand("bash " + script, System.out, System.err, workingDir, environment);
                        LOG.debug(output.toString());
                    } catch (Exception e) {
                        LOG.error("Error executing " + script, e);
                        throw e;
                    }
                });
    }

    private Map<String, String> environment(DockstoreServiceYaml dockstoreYaml, Optional<Map<String, Object>> map) {
        if (map.isPresent() && dockstoreYaml.service.environment != null) {
            final Map<String, String> env = new HashMap();
            final Map<String, Object> theMap = map.get();
            env.putAll(dockstoreYaml.service.environment.entrySet().stream()
                    .collect(Collectors.toMap(e -> e.getKey(), e -> envValue(e, theMap))));
            return env;
        } else {
            return Collections.emptyMap();
        }
    }

    private String envValue(Map.Entry<String, EnvironmentVariable> entry, Map<String, Object> jsonParameter) {
        return envValue(jsonParameter, entry.getKey()).orElseGet(() -> {
            Object defaultValue = entry.getValue().defaultValue;
            return defaultValue != null ? defaultValue.toString() : "";
        });
    }

    private Optional<String> envValue(Map<String, Object> jsonParameter, String envVarName) {
        Object environment = jsonParameter.get("environment");
        if (environment instanceof Map) {
            Object value = ((Map)environment).get(envVarName);
            return value == null ? Optional.empty() : Optional.of(value.toString());
        }
        return Optional.empty();
    }

    private List<Pair<String, Path>> calculateInputs(Optional<Map<String, Dataset>> maybeSchema, Optional<Map<String, Object>> maybeParams) {
        if (maybeParams.isPresent() && maybeSchema.isPresent()) {
            final Map<String, Dataset> schema = maybeSchema.get();
            final Map<String, Object> jsonParameters = maybeParams.get();
            final List<String> datasetParameterNames = schema.entrySet().stream().map(e -> e.getKey()).collect(Collectors.toList());
            final Map<String, Object> data = (Map<String, Object>)jsonParameters.get("data");
            return datasetParameterNames.stream().flatMap(datasetParamName -> {
                final Dataset datasetParameter = schema.get(datasetParamName);
                final String defaultTargetDirectory = datasetParameter.targetDirectory;
                final List<Object> dataset = (List<Object>)data.get(datasetParamName);
                return dataset.stream().flatMap(d -> datasetParameter.files.entrySet().stream().map(e -> {
                    final String targetDirectory = e.getValue().targetDirectory;
                    final String name = e.getKey();
                    Map<String, Object> m = (Map<String, Object>)d;
                    final String fileToDownload = (String)m.get(name);
                    final int i = fileToDownload.lastIndexOf('/');
                    return ImmutablePair.of(fileToDownload,
                            Paths.get(targetDirectory != null ? targetDirectory : defaultTargetDirectory, fileToDownload.substring(i + 1)));
                }));
            }).collect(Collectors.toList());
        } else {
            return Collections.emptyList();
        }
    }

    private void validateDatasets(Map<String, Dataset> datasetsMap, Map<String, Object> jsonParameterMap) {
        datasetsMap.entrySet().stream()
                .forEach(e -> {
                    final String datasetName = e.getKey();
                    final Object datasetParameter = jsonParameterMap.get(datasetName);
                    if (datasetParameter instanceof List) {
                        List<Object> list = (List<Object>)datasetParameter;
                        list.stream().forEach(o -> {
                            if (o instanceof Map) {
                                final Map m = (Map)o;
                                // TODO: Validate multiple
                                e.getValue().files.entrySet().stream()
                                        .forEach(e2 -> {
                                            final Object file = m.get(e2.getKey());
                                            if (file == null) {
                                                errorMessage("Missing something in param file", Client.CLIENT_ERROR);
                                            }
                                        });
                            } else {
                                errorMessage("Missing something in param file", Client.CLIENT_ERROR);
                            }
                        });
                    }
                });
    }

    private static class DockstoreServiceYaml {
        public String version;
        public Service service;
    }
    private static class Service {
        public String type;
        public String name;
        public String author;
        public String description;
        public List<String> files;
        public Map<String, String> scripts;
        public Map<String, EnvironmentVariable> environment;
        public Map<String, Dataset> data;
    }

    private static class EnvironmentVariable {
        public String description;
        public Object defaultValue;
    }

    private static class Dataset {
        public Boolean multiple;
        public String targetDirectory;
        public Map<String, FileDesc> files;
    }

    private static class FileDesc {
        public String description;
        public String targetDirectory;
    }

    @Parameters(separators = "=", commandDescription = "Launch an entry locally or remotely.")
    private static class CommandLaunch {
        @com.beust.jcommander.Parameter(names = "--entry", description = "Complete service path in the Dockstore (ex. NCI-GDC/gdc-dnaseq-cwl/GDC_DNASeq:master)")
        private String entry;
        @com.beust.jcommander.Parameter(names = "--local-entry", description = "Allows you to specify a full path to a local descriptor instead of an entry path")
        private String localEntry;
        @com.beust.jcommander.Parameter(names = "--json", description = "Parameters to the entry in the dockstore, one map for one run, an array of maps for multiple runs")
        private String json;
        @com.beust.jcommander.Parameter(names = "--help", description = "Prints help for launch command", help = true)
        private boolean help = false;
    }


}

