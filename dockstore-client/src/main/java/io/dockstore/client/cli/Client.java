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

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.ws.rs.ProcessingException;

import ch.qos.logback.classic.Level;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import io.cwl.avro.CWL;
import io.dockstore.client.cli.nested.AbstractEntryClient;
import io.dockstore.client.cli.nested.DepCommand;
import io.dockstore.client.cli.nested.ToolClient;
import io.dockstore.client.cli.nested.WorkflowClient;
import io.dockstore.common.GeneratedConstants;
import io.dockstore.common.Utilities;
import io.dockstore.common.WdlBridgeShutDown;
import io.dockstore.openapi.client.api.Ga4Ghv20Api;
import io.github.collaboratory.cwl.cwlrunner.CWLRunnerFactory;
import io.github.collaboratory.cwl.cwlrunner.CWLRunnerInterface;
import io.swagger.client.ApiClient;
import io.swagger.client.ApiException;
import io.swagger.client.Configuration;
import io.swagger.client.api.ContainersApi;
import io.swagger.client.api.ContainertagsApi;
import io.swagger.client.api.ExtendedGa4GhApi;
import io.swagger.client.api.Ga4GhApi;
import io.swagger.client.api.MetadataApi;
import io.swagger.client.api.UsersApi;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.auth.ApiKeyAuth;
import io.swagger.client.model.Metadata;
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.dockstore.client.cli.ArgumentUtility.Kill;
import static io.dockstore.client.cli.ArgumentUtility.err;
import static io.dockstore.client.cli.ArgumentUtility.errorMessage;
import static io.dockstore.client.cli.ArgumentUtility.exceptionMessage;
import static io.dockstore.client.cli.ArgumentUtility.flag;
import static io.dockstore.client.cli.ArgumentUtility.invalid;
import static io.dockstore.client.cli.ArgumentUtility.isHelpRequest;
import static io.dockstore.client.cli.ArgumentUtility.optVal;
import static io.dockstore.client.cli.ArgumentUtility.out;
import static io.dockstore.client.cli.ArgumentUtility.printHelpFooter;
import static io.dockstore.client.cli.ArgumentUtility.printHelpHeader;
import static io.dockstore.client.cli.ArgumentUtility.printLineBreak;
import static io.dockstore.client.cli.YamlVerifyUtility.YAML;
import static io.dockstore.client.cli.nested.AbstractEntryClient.SEARCH;
import static io.dockstore.common.FileProvisioning.getCacheDirectory;

/**
 * Main entrypoint for the dockstore CLI.
 *
 * @author xliu
 */
public class Client {

    public static final int PADDING = 3;
    public static final int GENERIC_ERROR = 1; // General error, not yet described by an error type
    public static final int CONNECTION_ERROR = 150; // Connection exception
    public static final int IO_ERROR = 3; // IO throws an exception
    public static final int API_ERROR = 6; // API throws an exception
    public static final int CLIENT_ERROR = 4; // Client does something wrong (ex. input validation)
    public static final int COMMAND_ERROR = 10; // Command is not successful, but not due to errors
    public static final int ENTRY_NOT_FOUND = 12; // Entry could not be found locally or remotely

    public static final AtomicBoolean DEBUG = new AtomicBoolean(false);
    public static final AtomicBoolean INFO = new AtomicBoolean(false);
    public static final AtomicBoolean SCRIPT = new AtomicBoolean(false);
    public static final String DEPRECATED_PORT_MESSAGE = "Dockstore webservice has deprecated port 8443 and may remove it without warning. Please use 'https://dockstore.org/api' via the Dockstore config file (\"~/.dockstore/config\" by default) instead.";

    // Command Names
    public static final String TOOL = "tool";
    public static final String WORKFLOW = "workflow";
    public static final String CHECKER = "checker";
    public static final String PLUGIN = "plugin";
    public static final String DEPS = "deps";
    public static final String DEBUG_FLAG = "--debug";

    public static final String HELP = "--help";
    public static final String VERSION = "--version";
    public static final String SERVER_METADATA = "--server-metadata";
    public static final String UPGRADE = "--upgrade";
    public static final String UPGRADE_STABLE = "--upgrade-stable";
    public static final String UPGRADE_UNSTABLE = "--upgrade-unstable";
    public static final String CONFIG = "--config";
    public static final String SCRIPT_FLAG = "--script";
    public static final String CLEAN_CACHE = "--clean-cache";
    private static ObjectMapper objectMapper;

    private static final String DOCKSTORE_CLI_REPO_URL = "https://api.github.com/repos/dockstore/dockstore-cli/releases";
    private static final Logger LOG = LoggerFactory.getLogger(Client.class);
    private String configFile = null;
    private String serverUrl = null;
    private Ga4GhApi ga4ghApi;
    private Ga4Ghv20Api ga4ghv20Api;
    private ExtendedGa4GhApi extendedGA4GHApi;
    private MetadataApi metadataApi;

    private boolean isAdmin = false;
    private ToolClient toolClient;
    private WorkflowClient workflowClient;
    private CheckerClient checkerClient;

    private YamlClient yamlClient;


    /*
     * Dockstore Client Functions for CLI
     * ----------------------------------------------------------------------------------------------------
     * ------------------------------------
     */

    /**
     * Finds the install location of the dockstore CLI
     *
     * @return path for the dockstore
     */
    static String getInstallLocation() {
        String installLocation = null;

        String executable = "dockstore";
        String path = System.getenv("PATH");
        String[] dirs = path.split(File.pathSeparator);

        // Search for location of dockstore executable on path
        for (String dir : dirs) {
            // Check if a folder on the PATH includes dockstore
            File file = new File(dir, executable);
            if (file.isFile()) {
                installLocation = dir + File.separator + executable;
                break;
            }
        }

        return installLocation;
    }

    public static String getCurrentVersion() {
        final Properties properties = new Properties();
        ClassLoader classLoader = ClassLoader.getSystemClassLoader();
        try {
            properties.load(classLoader.getResourceAsStream("project.properties"));
        } catch (IOException e) {
            LOG.error("Could not get project.properties file");
        }
        return properties.getProperty("version");
    }

    /**
     * This method will get information based on the json file on the link to the current version
     * However, it can only be the information outside "assets" (i.e "name","id","prerelease")
     *
     * @param link
     * @param info
     * @return
     */
    private static String getFromJSON(URL link, String info) {
        ObjectMapper mapper = getObjectMapper();
        Map<String, Object> mapCur;
        try {
            mapCur = mapper.readValue(link, Map.class);
            return mapCur.get(info).toString();

        } catch (IOException e) {
            // this indicates that we cannot read versions of dockstore from github
            // and we should ignore rather than crash
            return "null";
        }
    }

    /**
     * This method will return a map consists of all the releases
     *
     * @return
     */
    private static List<Map<String, Object>> getAllReleases() {
        URL url;
        try {
            ObjectMapper mapper = getObjectMapper();
            url = new URL(DOCKSTORE_CLI_REPO_URL);
            List<Map<String, Object>> mapRel;
            try {
                TypeFactory typeFactory = mapper.getTypeFactory();
                CollectionType ct = typeFactory.constructCollectionType(List.class, Map.class);
                mapRel = mapper.readValue(url, ct);
                return mapRel;
            } catch (IOException e) {
                LOG.debug("Could not read releases of Dockstore", e);
            }

        } catch (MalformedURLException e) {
            LOG.debug("Could not read releases of Dockstore", e);
        }
        return null;
    }

    /**
     * This method will return the latest unstable version
     *
     * @return
     */
    private static String getLatestUnstableVersion() {
        List<Map<String, Object>> allReleases = getAllReleases();
        Map<String, Object> map;
        for (Map<String, Object> allRelease : allReleases) {
            map = allRelease;
            if (map.get("prerelease").toString().equals("true")) {
                return map.get("name").toString();
            }
        }
        return null;
    }

    /**
     * Check if the ID of the current is bigger or smaller than latest version
     *
     * @param current
     * @return
     */
    private static Boolean compareVersion(String current) {
        URL urlCurrent, urlLatest;
        try {
            urlCurrent = new URL(DOCKSTORE_CLI_REPO_URL + "/tags/" + current);
            urlLatest = new URL(DOCKSTORE_CLI_REPO_URL + "/latest");

            int idCurrent, idLatest;
            String prerelease;

            idLatest = Integer.parseInt(getFromJSON(urlLatest, "id"));
            idCurrent = Integer.parseInt(getFromJSON(urlCurrent, "id"));
            prerelease = getFromJSON(urlCurrent, "prerelease");

            //check if currentVersion is earlier than latestVersion or not
            //id will be bigger if newer, prerelease=true if unstable
            //newer return true, older return false
            return "true".equals(prerelease) && (idCurrent > idLatest);
        } catch (MalformedURLException e) {
            exceptionMessage(e, "Failed to open URL", CLIENT_ERROR);
        } catch (NumberFormatException e) {
            return true;
        }
        return false;
    }

    /**
     * Get the latest stable version name of dockstore available NOTE: The Github library does not include the ability to get release
     * information.
     *
     * @return
     */
    static String getLatestVersion() {
        try {
            URL url = new URL(DOCKSTORE_CLI_REPO_URL + "/latest");
            ObjectMapper mapper = getObjectMapper();
            Map<String, Object> map;
            try {
                map = mapper.readValue(url, Map.class);
                return map.get("name").toString();

            } catch (IOException e) {
                LOG.debug("Could not read latest release of Dockstore from GitHub", e);
            }
        } catch (MalformedURLException e) {
            LOG.debug("Could not read latest release of Dockstore from GitHub", e);
        }
        return null;
    }

    /**
     * Checks if the given tag exists as a release for Dockstore
     *
     * @param tag
     * @return
     */
    private static Boolean checkIfTagExists(String tag) {
        try {
            URL url = new URL(DOCKSTORE_CLI_REPO_URL);
            ObjectMapper mapper = getObjectMapper();
            try {
                ArrayList<Map<String, String>> arrayMap = mapper.readValue(url, ArrayList.class);
                for (Map<String, String> map : arrayMap) {
                    String version = map.get("name");
                    if (version.equals(tag)) {
                        return true;
                    }
                }
                return false;
            } catch (IOException | NullPointerException e) {
                LOG.debug("Could not read a release of Dockstore from GitHub", e);
            }

        } catch (MalformedURLException e) {
            LOG.debug("Could not read a release of Dockstore from GitHub", e);
        }
        return false;
    }

    /**
     * This method returns the url to upgrade to desired version
     * However, this will only work for all releases json (List&lt;Map&lt;String, Object&gt;&gt; instead of Map&lt;String,Object&gt;)
     *
     * @param version
     * @return
     */
    private static String getUnstableURL(String version, List<Map<String, Object>> allReleases) {
        Map<String, Object> map;
        for (int i = 0; i < allReleases.size(); i++) {
            map = allReleases.get(i);
            if (map.get("name").toString().equals(version)) {
                ArrayList<Map<String, String>> assetsList = (ArrayList<Map<String, String>>)allReleases.get(i).get("assets");
                return assetsList.get(0).get("browser_download_url");
            }
        }
        return null;
    }

    /**
     * for downloading content for upgrade
     */
    private static void downloadURL(String browserDownloadUrl, String installLocation, String dockstoreVersion) {
        try {
            URL dockstoreExecutable = new URL(browserDownloadUrl);
            File file = new File(installLocation);
            FileUtils.copyURLToFile(dockstoreExecutable, file);
            Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rwxrwxr-x");
            java.nio.file.Files.setPosixFilePermissions(file.toPath(), perms);

            // Run the dockstore script with the 'self-install' argument so if it needs
            // to download the appropriate CLI JAR file it will do so now
            // and not the next time the user runs the dockstore script
            // Also set the Dockstore version environment variable to make
            // sure that the dockstore script will download the new version of
            // the client jar. The Dockstore version environment variable might
            // be set to the previous version of Dockstore if the dockstore script
            // was run to upgrade the Dockstore version.
            Map<String, String> additionalEnvVarMap =
                    Collections.singletonMap("DOCKSTORE_VERSION", dockstoreVersion);
            Utilities.executeCommand(file.toPath().toString() + " self-install",
                    System.out, System.err, null, additionalEnvVarMap);
        } catch (IOException e) {
            exceptionMessage(e, "Could not connect to Github. You may have reached your rate limit.", IO_ERROR);
        }
    }

    /**
     * Checks for upgrade for Dockstore and install
     */
    private static void upgrade(String optVal) {

        // Try to get version installed
        String installLocation = getInstallLocation();
        if (installLocation == null) {
            errorMessage("Can't find location of Dockstore executable.  Is it on the PATH?", CLIENT_ERROR);
        }

        String currentVersion = getCurrentVersion();
        if (currentVersion == null) {
            errorMessage("Can't find the current version.", CLIENT_ERROR);
        }

        // Update if necessary
        URL url;

        String latestPath = DOCKSTORE_CLI_REPO_URL + "/latest";
        String latestVersion, upgradeURL;
        try {
            url = new URL(latestPath);
            ObjectMapper mapper = getObjectMapper();
            Map<String, Object> map;
            List<Map<String, Object>> mapRel;

            try {
                // Read JSON from Github
                map = mapper.readValue(url, Map.class);
                latestVersion = map.get("name").toString();
                ArrayList<Map<String, String>> map2 = (ArrayList<Map<String, String>>)map.get("assets");
                String browserDownloadUrl = map2.get(0).get("browser_download_url");

                //get the map of all releases
                mapRel = getAllReleases();
                String latestUnstable = getLatestUnstableVersion();

                out("Current Dockstore version: " + currentVersion);

                // Check if installed version is up to date
                if (latestVersion.equals(currentVersion)) {   //current is the most stable version
                    if ("unstable".equals(optVal)) {   // downgrade or upgrade to recent unstable version
                        upgradeURL = getUnstableURL(latestUnstable, mapRel);
                        out("Downloading version " + latestUnstable + " of Dockstore.");
                        downloadURL(upgradeURL, installLocation, latestUnstable);
                        out("Download complete. You are now on version " + latestUnstable + " of Dockstore.");
                    } else {
                        //user input '--upgrade' without knowing the version or the optional commands
                        out("You are running the latest stable version.");
                    }
                } else {    //current is not the most stable version
                    switch (optVal) {
                    case "stable":
                        out("Upgrading to most recent stable release (" + currentVersion + " -> " + latestVersion + ")");
                        downloadURL(browserDownloadUrl, installLocation, latestVersion);
                        out("Download complete. You are now on version " + latestVersion + " of Dockstore.");
                        break;
                    case "none":
                        if (compareVersion(currentVersion)) {
                            // current version is the latest unstable version
                            out("You are currently on the latest unstable version.");
                        } else {
                            // current version is the older unstable version
                            // upgrade to latest stable version
                            out("Upgrading to most recent stable release (" + currentVersion + " -> " + latestVersion + ")");
                            downloadURL(browserDownloadUrl, installLocation, latestVersion);
                            out("Download complete. You are now on version " + latestVersion + " of Dockstore.");
                        }
                        break;
                    case "unstable":
                        if (Objects.equals(currentVersion, latestUnstable)) {
                            // current version is the latest unstable version
                            out("You are currently on the latest unstable version. If you wish to upgrade to the latest stable version, please use the following command:");
                            out("   dockstore " + UPGRADE_STABLE);
                        } else {
                            //user wants to upgrade to newest unstable version
                            upgradeURL = getUnstableURL(latestUnstable, mapRel);
                            out("Downloading version " + latestUnstable + " of Dockstore.");
                            downloadURL(upgradeURL, installLocation, latestUnstable);
                            out("Download complete. You are now on version " + latestUnstable + " of Dockstore.");
                        }
                        break;
                    default:
                        /* do nothing */
                    }

                }
            } catch (IOException e) {
                exceptionMessage(e, "Could not connect to Github. You may have reached your rate limit.", IO_ERROR);
            }
        } catch (MalformedURLException e) {
            exceptionMessage(e, "Issue with URL : " + latestPath, IO_ERROR);
        }
    }

    /**
     * Check our dependencies and warn if they are not what we tested with
     */
    public void checkForCWLDependencies() {
        CWLRunnerFactory.setConfig(Utilities.parseConfig(getConfigFile()));
        CWLRunnerInterface cwlrunner = CWLRunnerFactory.createCWLRunner();
        cwlrunner.checkForCWLDependencies(metadataApi);
    }

    /**
     * Will check for updates if three months have gone by since the last update
     */
    private static void checkForUpdates() {
        final int monthsBeforeCheck = 3;
        String currentVersion = getCurrentVersion();
        if (currentVersion != null) {
            if (checkIfTagExists(currentVersion)) {
                URL url = null;
                try {
                    url = new URL(DOCKSTORE_CLI_REPO_URL + "/tags/" + currentVersion);
                } catch (MalformedURLException e) {
                    LOG.debug("Could not read a release of Dockstore from GitHub", e);
                }

                ObjectMapper mapper = getObjectMapper();
                try {
                    // Determine when current version was published
                    Map<String, Object> map = mapper.readValue(url, Map.class);
                    String publishedAt = map.get("published_at").toString();
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
                    try {
                        // Find out when you should check for updates again (publish date + 3 months)
                        Date date = sdf.parse(publishedAt);
                        Calendar cal = Calendar.getInstance();
                        cal.setTime(date);

                        cal.set(Calendar.MONTH, (cal.get(Calendar.MONTH) + monthsBeforeCheck));
                        Date minUpdateCheck = cal.getTime();

                        // Check for update if it has been at least 3 months since last update
                        if (minUpdateCheck.before(new Date())) {
                            String latestVersion = getLatestVersion();
                            if (currentVersion.equals(latestVersion)) {
                                out("Current version : " + currentVersion);
                                out("You have the most recent stable release.");
                                out("If you wish to upgrade to the latest unstable version, please use the following command:");
                                out("   dockstore " + UPGRADE_UNSTABLE); // takes you to the newest unstable version
                            } else {
                                err("Current version : " + currentVersion);
                                //not the latest stable version, could be on the newest unstable or older unstable/stable version
                                err("Latest version : " + latestVersion);
                                err("You do not have the most recent stable release of Dockstore.");
                                displayUpgradeMessage(currentVersion);
                            }
                        }
                    } catch (ParseException e) {
                        LOG.debug("Could not parse a release number of Dockstore from GitHub", e);
                    }

                } catch (IOException e) {
                    LOG.debug("Could not read a release of Dockstore from GitHub", e);
                }
            }
        }
    }

    private static void displayUpgradeMessage(String currentVersion) {
        if (compareVersion(currentVersion)) {
            //current version is latest than latest stable
            out("You are currently on the latest unstable version. If you wish to upgrade to the latest stable version, please use the following command:");
            out("   dockstore " + UPGRADE_STABLE); //takes you to the newest stable version no matter what
        } else {
            //current version is older than latest stable
            out("Please upgrade with the following command:");
            out("   dockstore " + UPGRADE);  // takes you to the newest stable version, unless you're already "past it"
        }
    }

    private static ObjectMapper getObjectMapper() {
        if (objectMapper == null) {
            return new ObjectMapper();
        } else {
            return objectMapper;
        }
    }

    static void setObjectMapper(ObjectMapper objectMapper) {
        Client.objectMapper = objectMapper;
    }

    /**
     * Prints out version information for the Dockstore CLI
     */
    private static void version() {
        String currentVersion = getCurrentVersion();
        if (currentVersion == null) {
            errorMessage("Can't find the current version.", CLIENT_ERROR);
        }

        out("Dockstore version " + currentVersion);
        String latestVersion = getLatestVersion();
        if (latestVersion == null) {
            err("Can't find the latest version. Something might be wrong with the connection to Github.");
            // do not crash when rate limited
            return;
        }

        // skip upgrade check for development versions
        if (currentVersion.endsWith("SNAPSHOT")) {
            return;
        }
        //check if the current version is the latest stable version or not
        if (Objects.equals(currentVersion, latestVersion)) {
            out("You are running the latest stable version."); // takes you to the newest unstable version
        } else {
            //not the latest stable version, could be on the newest unstable or older unstable/stable version
            out("The latest stable version is " + latestVersion);
            displayUpgradeMessage(currentVersion);
        }
    }

    // If you add a command, please update the list of commands given to the invalid function in the run method
    private static void printGeneralHelp() {
        printHelpHeader();
        out("Usage: dockstore [mode] [flags] [command] [command parameters]");
        out("");
        out("Modes:");
        out("   " + TOOL + "                Puts dockstore into " + TOOL + " mode.");
        out("   " + WORKFLOW + "            Puts dockstore into " + WORKFLOW + " mode.");
        out("   " + CHECKER + "             Puts dockstore into " + CHECKER + " mode.");
        out("   " + PLUGIN + "              Configure and debug plugins.");
        out("   " + DEPS + "                Print " + TOOL + "/" + WORKFLOW + " runner dependencies.");
        out("   " + YAML + "                Puts dockstore into " + YAML + " mode.");
        out("");
        printLineBreak();
        out("");
        out("Flags:");
        out("  " + HELP + "               Print help information");
        out("                       Default: false");
        out("  " + DEBUG_FLAG + "              Print debugging information");
        out("                       Default: false");
        out("  " + VERSION + "            Print dockstore's version");
        out("                       Default: false");
        out("  " + SERVER_METADATA + "    Print metadata describing the dockstore webservice");
        out("                       Default: false");
        out("  " + UPGRADE + "            Upgrades to the latest stable release of Dockstore");
        out("                       Default: false");
        out("  " + UPGRADE_STABLE + "     Force upgrade to the latest stable release of Dockstore");
        out("                       Default: false");
        out("  " + UPGRADE_UNSTABLE + "   Force upgrade to the latest unstable release of Dockstore");
        out("                       Default: false");
        out("  " + CONFIG + " <file>      Override config file");
        out("                       Default: ~/.dockstore/config");
        out("  " + SCRIPT_FLAG + "             Will not check Github for newer versions of Dockstore, or ask for user input");
        out("                       Default: false");
        out("  " + CLEAN_CACHE + "        Delete the Dockstore launcher cache to save space");
        printHelpFooter();
    }

    /**
     * Used for integration testing
     *
     * @param argv arguments provided match usage in the dockstore script (i.e. tool launch ...)
     */
    public static void main(String[] argv) {
        Client client = new Client();
        client.run(argv);
        WdlBridgeShutDown.shutdownSTTP();
    }

    /*
     * Dockstore CLI help functions
     * ----------------------------------------------------------------------------------------------------------
     * ------------------------------
     */

    /**
     * Display metadata describing the server including server version information
     */
    private void serverMetadata() {
        try {
            final Metadata metadata = ga4ghApi.metadataGet();
            final Gson gson = io.cwl.avro.CWL.getTypeSafeCWLToolDocument();
            out(gson.toJson(metadata));
            out("Dockstore server: " + serverUrl);
        } catch (ApiException ex) {
            exceptionMessage(ex, "", API_ERROR);
        } catch (CWL.GsonBuildException ex) {
            exceptionMessage(ex, "There was an error creating the CWL GSON instance.", API_ERROR);
        } catch (JsonParseException ex) {
            exceptionMessage(ex, "The JSON file provided is invalid.", API_ERROR);
        }
    }

    /*
     * Main Method
     * --------------------------------------------------------------------------------------------------------------------------
     * --------------
     */

    private void clean() throws IOException {
        final INIConfiguration configuration = Utilities.parseConfig(getConfigFile());
        final String cacheDirectory = getCacheDirectory(configuration);
        FileUtils.deleteDirectory(new File(cacheDirectory));
    }

    private void run(String[] argv) {
        List<String> args = new ArrayList<>(Arrays.asList(argv));

        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory
                .getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
        if (flag(args, DEBUG_FLAG) || flag(args, "--d")) {
            DEBUG.set(true);
            // turn on logback
            root.setLevel(Level.DEBUG);
        } else if (flag(args, "--info") || flag(args, "--i")) {
            INFO.set(true);
            // turn on logback
            root.setLevel(Level.INFO);
        } else {
            root.setLevel(Level.ERROR);
        }
        if (flag(args, SCRIPT_FLAG) || flag(args, "--s")) {
            SCRIPT.set(true);
        } else {
            SCRIPT.set(false);
        }

        try {
            setupClientEnvironment(args);

            // Check if updates are available
            if (!SCRIPT.get()) {
                checkForUpdates();
            }

            if (args.isEmpty()) {
                printGeneralHelp();
            } else {
                try {
                    String mode = args.remove(0);
                    String cmd = null;

                    // see if this is a tool command
                    boolean handled = false;
                    AbstractEntryClient targetClient = null;
                    if (TOOL.equals(mode)) {
                        targetClient = getToolClient();
                    } else if (WORKFLOW.equals(mode)) {
                        targetClient = getWorkflowClient();
                    } else if (PLUGIN.equals(mode)) {
                        handled = PluginClient.handleCommand(args, Utilities.parseConfig(configFile));
                    } else if (SEARCH.equals(mode)) {
                        handled = SearchClient.handleCommand(args, this.extendedGA4GHApi);
                    } else if (CHECKER.equals(mode)) {
                        targetClient = getCheckerClient();
                    } else if (DEPS.equals(mode)) {
                        args.add(0, DEPS);
                        String[] argsArray = new String[args.size()];
                        argsArray = args.toArray(argsArray);
                        handled = DepCommand.handleDepCommand(argsArray);
                    } else if (YAML.equals(mode)) {
                        yamlClient = new YamlClient();
                        handled = yamlClient.handleCommand(args);
                    }

                    if (targetClient != null) {
                        if (args.size() == 1 && isHelpRequest(args.get(0))) {
                            targetClient.printGeneralHelp();
                        } else if (!args.isEmpty()) {
                            cmd = args.remove(0);
                            handled = targetClient.processEntryCommands(args, cmd);
                        } else {
                            targetClient.printGeneralHelp();
                        }
                    } else {
                        // mode is cmd if it is not workflow or tool
                        if (isHelpRequest(mode)) {
                            printGeneralHelp();
                            return;
                        }
                        cmd = mode;
                    }

                    if (handled) {
                        return;
                    }

                    // see if this is a general command
                    if (null != cmd) {
                        switch (cmd) {
                        case "-v", VERSION:
                            version();
                            break;
                        case SERVER_METADATA:
                            serverMetadata();
                            break;
                        case UPGRADE:
                            upgrade("none");
                            break;
                        case UPGRADE_STABLE:
                            upgrade("stable");
                            break;
                        case UPGRADE_UNSTABLE:
                            upgrade("unstable");
                            break;
                        case CLEAN_CACHE:
                            clean();
                            break;
                        default:
                            List<String> possibleCommands = new ArrayList<String>();
                            possibleCommands.addAll(Arrays.asList(TOOL, WORKFLOW, CHECKER, PLUGIN, DEPS, YAML, VERSION,
                                    SERVER_METADATA, UPGRADE, UPGRADE_STABLE, UPGRADE_UNSTABLE, CLEAN_CACHE));
                            possibleCommands.addAll(getGeneralFlags());
                            invalid("", cmd, possibleCommands);
                            break;
                        }
                    }
                } catch (Kill k) {
                    LOG.debug("client ran into unclassified error", k.getCause());
                    System.exit(GENERIC_ERROR);
                }
            }
        } catch (ProcessingException ex) {
            exceptionMessage(ex, "Could not connect to Dockstore web service", CONNECTION_ERROR);
        } catch (Exception ex) {
            exceptionMessage(ex, "", GENERIC_ERROR);
        }
    }

    public static List<String> getGeneralFlags() {
        List<String> generalFlags = new ArrayList<String>();
        generalFlags.addAll(Arrays.asList(DEBUG_FLAG, HELP, CONFIG, SCRIPT_FLAG));
        return generalFlags;
    }

    /**
     * Setup method called by client and by consonance to setup a Dockstore client
     *
     * @param args
     */
    @SuppressWarnings("WeakerAccess")
    public void setupClientEnvironment(List<String> args) {
        INIConfiguration config = getIniConfiguration(args);
        // pull out the variables from the config
        String token = config.getString("token", "");
        serverUrl = config.getString("server-url", "https://dockstore.org/api");
        if (serverUrl.contains(":8443")) {
            err(DEPRECATED_PORT_MESSAGE);
        }
        ApiClient defaultApiClient;
        defaultApiClient = Configuration.getDefaultApiClient();
        String cliVersion = getClientVersion();
        final String userAgent = "Dockstore-CLI/" + cliVersion + "/java";
        defaultApiClient.setUserAgent(userAgent);

        ApiKeyAuth bearer = (ApiKeyAuth)defaultApiClient.getAuthentication("BEARER");
        bearer.setApiKeyPrefix("BEARER");
        bearer.setApiKey(token);
        defaultApiClient.setBasePath(serverUrl);

        ContainersApi containersApi = new ContainersApi(defaultApiClient);
        UsersApi usersApi = new UsersApi(defaultApiClient);
        this.ga4ghApi = new Ga4GhApi(defaultApiClient);
        this.extendedGA4GHApi = new ExtendedGa4GhApi(defaultApiClient);
        this.metadataApi = new MetadataApi(defaultApiClient);

        // openapi client
        io.dockstore.openapi.client.ApiClient openApiClient = new io.dockstore.openapi.client.ApiClient();
        openApiClient.setUserAgent(userAgent);
        openApiClient.addDefaultHeader("Authorization", "Bearer " + token);
        openApiClient.setBasePath(serverUrl);

        this.ga4ghv20Api = new Ga4Ghv20Api(openApiClient);

        try {
            if (usersApi.getApiClient() != null) {
                this.isAdmin = usersApi.getUser().isIsAdmin();
            }
        } catch (ApiException | ProcessingException ex) {
            this.isAdmin = false;
        }

        this.toolClient = new ToolClient(containersApi, new ContainertagsApi(defaultApiClient), usersApi, this, isAdmin);
        this.workflowClient = new WorkflowClient(new WorkflowsApi(defaultApiClient), usersApi, this, isAdmin);
        this.checkerClient = new CheckerClient(new WorkflowsApi(defaultApiClient), usersApi, this, isAdmin);

        defaultApiClient.setDebugging(DEBUG.get());
        CWLRunnerFactory.setConfig(config);
    }

    public static String getClientVersion() {
        String cliVersion = Client.class.getPackage().getImplementationVersion();
        if (cliVersion == null) {
            // then we're probably in an IDE or CI build test, it would be nice to get the current project version
            cliVersion = GeneratedConstants.PROJECT_VERSION;
        }
        return cliVersion;
    }

    private INIConfiguration getIniConfiguration(List<String> args) {
        String userHome = System.getProperty("user.home");
        String commandLineConfigFile = optVal(args, CONFIG, userHome + File.separator + ".dockstore" + File.separator + "config");
        if (this.configFile == null) {
            this.configFile = commandLineConfigFile;
        }

        return Utilities.parseConfig(configFile);
    }

    public String getConfigFile() {
        return configFile;
    }

    void setConfigFile(String configFile) {
        this.configFile = configFile;
    }
    
    public ToolClient getToolClient() {
        return toolClient;
    }

    public WorkflowClient getWorkflowClient() {
        return workflowClient;
    }

    public CheckerClient getCheckerClient() {
        return checkerClient;
    }

    public Ga4Ghv20Api getGa4Ghv20Api() {
        return ga4ghv20Api;
    }

}
