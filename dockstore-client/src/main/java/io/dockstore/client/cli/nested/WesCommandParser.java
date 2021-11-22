package io.dockstore.client.cli.nested;

import java.util.List;

import com.beust.jcommander.IParameterValidator;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;

public class WesCommandParser {

    public WesMain wesMain;
    public CommandLaunch commandLaunch;
    public CommandCancel commandCancel;
    public CommandStatus commandStatus;
    public CommandServiceInfo commandServiceInfo;
    public JCommander jCommander;

    public WesCommandParser() {
        this.wesMain = new WesMain();
        this.commandLaunch = new CommandLaunch();
        this.commandCancel = new CommandCancel();
        this.commandStatus = new CommandStatus();
        this.commandServiceInfo = new CommandServiceInfo();

        this.jCommander = buildWesCommandParser();
    }

    private JCommander buildWesCommandParser() {

        // Build JCommander
        return JCommander.newBuilder()
            .addObject(this.wesMain)
            .addCommand("launch", this.commandLaunch)
            .addCommand("cancel", this.commandCancel)
            .addCommand("status", this.commandStatus)
            .addCommand("service-info", this.commandServiceInfo)
            .build();
    }

    @Parameters(separators = "=", commandDescription = "Execute WES commands")
    public static class WesMain {
        @Parameter(names = "--wes-url", description = "The URL of the WES server.", required = false)
        private static String wesUrl = null;
        @Parameter(names = "--wes-auth",
            description = "The authorization type and value of this wes request. --wes-auth <type> <value>. "
                + "Type can be 'bearer' or 'aws'. "
                + "Value can be a token if the type is 'bearer', or an AWS profile if the type is 'aws'",
            variableArity = true,
            required = false)
        private static List<String> wesAuth = null;
        @Parameter(names = "--aws-config", description = "A path to an AWS configuration file containing AWS profile credentials.", required = false)
        private static String awsConfig = null;
        @Parameter(names = "--aws-region", description = "The AWS region of the WES server.", required = false)
        private static String awsRegion = null;
        @Parameter(names = "--help", description = "Prints help for launch command", help = true)
        private static boolean help = false;

        /**
         * Returns the WES authorization type (bearer or aws). If no auth type was provided, return null
         * @return
         */
        public String getWesAuthType() {
            if (wesAuth != null && !wesAuth.isEmpty()) {
                return wesAuth.get(0);
            }
            return null;
        }

        /**
         * Returns the WES authorization value (token or AWS profile). If no auth value was provided, return null.
         * @return
         */
        public String getWesAuthValue() {
            if (wesAuth != null && wesAuth.size() > 1) {
                return wesAuth.get(1);
            }
            return null;
        }

        public String getWesUrl() {
            return wesUrl;
        }

        public String getAwsConfig() {
            return awsConfig;
        }

        public String getAwsRegion() {
            return awsRegion;
        }

        public boolean isHelp() {
            return help;
        }
    }

    @Parameters(separators = "=", commandDescription = "Launch a workflow using WES")
    public static class CommandLaunch extends WesMain {
        @Parameter(names = "--entry", description = "Complete workflow path in Dockstore (ex. NCI-GDC/gdc-dnaseq-cwl/GDC_DNASeq:master)", required = true)
        private String entry;
        @Parameter(names = "--json", description = "Parameters to the entry in Dockstore, one map for one run, an array of maps for multiple runs")
        private String json;
        @Parameter(names = "--yaml", description = "Parameters to the entry in Dockstore, one map for one run, an array of maps for multiple runs")
        private String yaml;
        @Parameter(names = "--uuid", description = "Allows you to specify a uuid for 3rd party notifications")
        private String uuid;

        public String getEntry() {
            return entry;
        }

        public String getJson() {
            return json;
        }

        public String getYaml() {
            return yaml;
        }

        public String getUuid() {
            return uuid;
        }
    }

    @Parameters(separators = "=", commandDescription = "Cancel a remote WES entry")
    public static class CommandCancel extends WesMain {
        @Parameter(names = "--id", description = "The ID of the workflow to cancel", required = true)
        private String id;

        public String getId() {
            return id;
        }
    }

    @Parameters(separators = "=", commandDescription = "Retrieve the status of a workflow")
    public static class CommandStatus extends WesMain {
        @Parameter(names = "--id", description = "The ID of the workflow to cancel", required = true)
        private String id;
        @Parameter(names = "--verbose", description = "Flag indicating to print verbose logs", required = false)
        private boolean verbose = false;

        public String getId() {
            return id;
        }

        public boolean isVerbose() {
            return verbose;
        }
    }

    @Parameters(separators = "=", commandDescription = "Retrieve info about a WES server")
    public static class CommandServiceInfo extends WesMain {
    }

}
