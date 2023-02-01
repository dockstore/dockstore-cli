package io.dockstore.client.cli.nested;

import java.util.List;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

import static io.dockstore.client.cli.ArgumentUtility.LAUNCH;
import static io.dockstore.client.cli.Client.HELP;
import static io.dockstore.client.cli.Client.WORKFLOW;
import static io.dockstore.client.cli.nested.AbstractEntryClient.CANCEL;
import static io.dockstore.client.cli.nested.AbstractEntryClient.LIST;
import static io.dockstore.client.cli.nested.AbstractEntryClient.LOGS;
import static io.dockstore.client.cli.nested.AbstractEntryClient.SERVICE_INFO;
import static io.dockstore.client.cli.nested.AbstractEntryClient.STATUS;

public class WesCommandParser {

    public static final String WES_URL = "--wes-url";
    public static final String VERBOSE = "--verbose";
    public static final String ENTRY = "--entry";
    public static final String INLINE_WORKFLOW = "--inline-workflow";
    public static final String JSON = "--json";
    public static final String ATTACH = "--attach";
    public WesMain wesMain;
    public CommandLaunch commandLaunch;
    public CommandCancel commandCancel;
    public CommandStatus commandStatus;
    public CommandRunLogs commandRunLogs;
    public CommandServiceInfo commandServiceInfo;
    public CommandRunList commandRunList;
    public JCommander jCommander;

    public WesCommandParser() {
        this.wesMain = new WesMain();
        this.commandLaunch = new CommandLaunch();
        this.commandCancel = new CommandCancel();
        this.commandStatus = new CommandStatus();
        this.commandRunLogs = new CommandRunLogs();
        this.commandServiceInfo = new CommandServiceInfo();
        this.commandRunList = new CommandRunList();

        this.jCommander = buildWesCommandParser();
    }

    private JCommander buildWesCommandParser() {

        // Build JCommander
        return JCommander.newBuilder()
            .addObject(this.wesMain)
            .addCommand(LAUNCH, this.commandLaunch)
            .addCommand(CANCEL, this.commandCancel)
            .addCommand(STATUS, this.commandStatus)
            .addCommand(LOGS, this.commandRunLogs)
            .addCommand(SERVICE_INFO, this.commandServiceInfo)
            .addCommand(LIST, this.commandRunList)
            .build();
    }

    @Parameters(commandDescription = "Execute WES commands")
    public static class WesMain {
        @Parameter(names = WES_URL, description = "The URL of the WES server.", required = false)
        private String wesUrl = null;
        @Parameter(names = HELP, description = "Prints help for launch command", help = true)
        private boolean help = false;
        @Parameter(names = VERBOSE, description = "Sets the CLI to verbose mode.")
        private boolean verbose = false;

        public String getWesUrl() {
            return wesUrl;
        }

        public boolean isHelp() {
            return help;
        }

        public boolean isVerbose() {
            return verbose;
        }
    }

    @Parameters(commandDescription = "Launch a " + WORKFLOW + " using WES")
    public static class CommandLaunch extends WesMain {
        @Parameter(names = ENTRY, description = "Complete " + WORKFLOW + " path in Dockstore (ex. NCI-GDC/gdc-dnaseq-cwl/GDC_DNASeq:master)", required = true)
        private String entry;
        @Parameter(names = INLINE_WORKFLOW, description = "Inlines " + WORKFLOW + " files contents directly into the WES HTTP request. This is required for some WES server implementations.")
        private boolean inlineWorkflow = false;
        @Parameter(names = JSON, description = "JSON file describing which attached file contains input parameters.")
        private String json;
        @Parameter(names = {ATTACH, "-a"}, description = "A list of paths to files that should be included in the WES request. (ex. -a <path1> <path2> OR -a <path1> -a <path2>)", variableArity = true)
        private List<String> attachments;

        public String getEntry() {
            return entry;
        }

        public boolean getInlineWorkflow() {
            return inlineWorkflow;
        }

        public String getJson() {
            return json;
        }

        public List<String> getAttachments() {
            return attachments;
        }
    }

    @Parameters(commandDescription = "Cancel a remote WES entry")
    public static class CommandCancel extends WesMain {
        @Parameter(names = "--id", description = "The ID of the " + WORKFLOW + " to " + CANCEL, required = true)
        private String id;

        public String getId() {
            return id;
        }
    }

    @Parameters(commandDescription = "Retrieve the " + STATUS + " of a " + WORKFLOW)
    public static class CommandStatus extends WesMain {
        @Parameter(names = "--id", description = "The ID of the " + WORKFLOW + " to " + CANCEL, required = true)
        private String id;

        public String getId() {
            return id;
        }

    }

    @Parameters(commandDescription = "Retrieve the " + STATUS + " of a " + WORKFLOW)
    public static class CommandRunLogs extends WesMain {
        @Parameter(names = "--id", description = "The ID of the " + WORKFLOW + " to " + CANCEL, required = true)
        private String id;

        public String getId() {
            return id;
        }
    }

    @Parameters(commandDescription = "Retrieve info about a WES server")
    public static class CommandServiceInfo extends WesMain {
    }

    @Parameters(commandDescription = "Retrieve a list of runs")
    public static class CommandRunList extends WesMain {
        private static final int DEFAULT_PAGE_SIZE = 10;

        @Parameter(names = "--count", description = "The number of entries to print")
        private int pageSize = DEFAULT_PAGE_SIZE;
        @Parameter(names = "--page-token", description = "The page token returned from a previous list of runs")
        private String pageToken = null;

        public int getPageSize() {
            return pageSize;
        }

        public String getPageToken() {
            return pageToken;
        }
    }

}
