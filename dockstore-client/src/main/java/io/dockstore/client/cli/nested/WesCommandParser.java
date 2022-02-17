package io.dockstore.client.cli.nested;

import java.util.List;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

public class WesCommandParser {

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
            .addCommand("launch", this.commandLaunch)
            .addCommand("cancel", this.commandCancel)
            .addCommand("status", this.commandStatus)
            .addCommand("logs", this.commandRunLogs)
            .addCommand("service-info", this.commandServiceInfo)
            .addCommand("list", this.commandRunList)
            .build();
    }

    @Parameters(commandDescription = "Execute WES commands")
    public static class WesMain {
        @Parameter(names = "--wes-url", description = "The URL of the WES server.", required = false)
        private String wesUrl = null;
        @Parameter(names = "--help", description = "Prints help for launch command", help = true)
        private boolean help = false;
        @Parameter(names = "--debug", description = "Sets the CLI to debug mode.", help = true)
        private boolean debug = false;

        public String getWesUrl() {
            return wesUrl;
        }

        public boolean isHelp() {
            return help;
        }

        public boolean debugMode() {
            return debug;
        }
    }

    @Parameters(commandDescription = "Launch a workflow using WES")
    public static class CommandLaunch extends WesMain {
        @Parameter(names = "--entry", description = "Complete workflow path in Dockstore (ex. NCI-GDC/gdc-dnaseq-cwl/GDC_DNASeq:master)", required = true)
        private String entry;
        @Parameter(names = "--inline-workflow", description = "Inlines workflow files contents directly into the WES HTTP request. This is required for some WES server implementations.")
        private boolean inlineWorkflow = false;
        @Parameter(names = "--json", description = "JSON file describing which attached file contains input parameters.")
        private String json;
        @Parameter(names = {"--attach", "-a"}, description = "A list of paths to files that should be included in the WES request. (ex. -a <path1> <path2> OR -a <path1> -a <path2>)", variableArity = true)
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
        @Parameter(names = "--id", description = "The ID of the workflow to cancel", required = true)
        private String id;

        public String getId() {
            return id;
        }
    }

    @Parameters(commandDescription = "Retrieve the status of a workflow")
    public static class CommandStatus extends WesMain {
        @Parameter(names = "--id", description = "The ID of the workflow to cancel", required = true)
        private String id;

        public String getId() {
            return id;
        }

    }

    @Parameters(commandDescription = "Retrieve the status of a workflow")
    public static class CommandRunLogs extends WesMain {
        @Parameter(names = "--id", description = "The ID of the workflow to cancel", required = true)
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
