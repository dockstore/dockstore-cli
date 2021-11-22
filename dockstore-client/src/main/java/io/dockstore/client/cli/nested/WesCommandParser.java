package io.dockstore.client.cli.nested;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.beust.jcommander.SubParameter;
import org.apache.commons.lang3.EnumUtils;

import static io.dockstore.client.cli.ArgumentUtility.out;

public class WesCommandParser {

    public WesMain wesMain;
    public CommandLaunch commandLaunch;

    public WesCommandParser() {
        this.wesMain = new WesMain();
        this.commandLaunch = new CommandLaunch();
    }

    public JCommander buildWesCommandParser() {

        // Build JCommander
        return JCommander.newBuilder()
            .addObject(this.wesMain)
            .addCommand("launch", this.commandLaunch)
            .build();
    }

    @Parameters(separators = "=", commandDescription = "Execute WES commands")
    public static class WesMain {
        @Parameter(names = "--wes-url", description = "The URL of the WES server.", required = false)
        private String wesUrl = null;
        @Parameter(names = "--wes-auth",
            description = "The authorization type and value of this wes request. --wes-auth <type> <value>. "
                + "Type can be 'bearer' or 'aws'. "
                + "Value can be a token if the type is 'bearer', or an AWS profile if the type is 'aws'",
            variableArity = true,
            required = false)
        private List<String> wesAuth = null;
        @Parameter(names = "--aws-config", description = "A path to an AWS configuration file containing AWS profile credentials.", required = false)
        private String awsConfig = null;
        @Parameter(names = "--aws-region", description = "The AWS region of the WES server.", required = true)
        private String awsRegion = null;
        @Parameter(names = "--help", description = "Prints help for launch command", help = true)
        private boolean help = false;

        /**
         * Returns the URL for the WES request, if provided on the command line.
         * @return
         */
        public String getWesUrl() {
            return wesUrl;
        }

        /**
         * Returns the WES authorization type (bearer or aws)
         * @return
         */
        public String getWesAuthType() {
            if (wesAuth != null && !wesAuth.isEmpty()) {
                return wesAuth.get(0);
            }
                return null;
        }

        /**
         * Returns the WES authorization value (token or AWS profile).
         * @return
         */
        public String getWesAuthValue() {
            if (wesAuth != null && wesAuth.size() > 1) {
                return wesAuth.get(1);
            }
            return null;
        }
    }

    @Parameters(separators = "=", commandDescription = "Launch an entry locally or remotely.")
    public static class CommandLaunch {
        @Parameter(names = "--entry", description = "Complete workflow path in Dockstore (ex. NCI-GDC/gdc-dnaseq-cwl/GDC_DNASeq:master)")
        private String entry;
        @Parameter(names = "--json", description = "Parameters to the entry in Dockstore, one map for one run, an array of maps for multiple runs")
        private String json;
        @Parameter(names = "--yaml", description = "Parameters to the entry in Dockstore, one map for one run, an array of maps for multiple runs")
        private String yaml;
        @Parameter(names = "--help", description = "Prints help for launch command", help = true)
        private boolean help = false;
        @Parameter(names = "--uuid", description = "Allows you to specify a uuid for 3rd party notifications")
        private String uuid;
        @Parameter(names = "--aws", description = "Indicates this command is to an AWS endpoint")
        private boolean isAws = false;
    }

}
