package io.dockstore.client.cli;

import static io.dockstore.client.cli.ArgumentUtility.LAUNCH;
import static io.dockstore.client.cli.Client.CLIENT_ERROR;
import static io.dockstore.client.cli.Client.CONFIG;
import static io.dockstore.client.cli.Client.SCRIPT_FLAG;
import static io.dockstore.client.cli.Client.TOOL;
import static io.dockstore.client.cli.Client.WORKFLOW;
import static io.dockstore.client.cli.nested.WesCommandParser.ENTRY;
import static io.dockstore.client.cli.nested.WesCommandParser.JSON;
import static org.junit.Assert.assertTrue;

import io.dockstore.client.cli.nested.AbstractEntryClient;
import io.dockstore.common.FlushingSystemErrRule;
import io.dockstore.common.FlushingSystemOutRule;
import io.dropwizard.testing.ResourceHelpers;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.ExpectedSystemExit;
import org.junit.contrib.java.lang.system.SystemErrRule;
import org.junit.contrib.java.lang.system.SystemOutRule;
import org.junit.rules.ExpectedException;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

public class LaunchTestYamlIT {

    @Rule
    public final SystemOutRule systemOutRule = new FlushingSystemOutRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final SystemErrRule systemErrRule = new FlushingSystemErrRule().enableLog().muteForSuccessfulTests();

    @Rule
    public final ExpectedSystemExit exit = ExpectedSystemExit.none();

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Rule
    public TestRule watcher = new TestWatcher() {
        protected void starting(Description description) {
            System.out.println("Starting test: " + description.getMethodName());
        }
    };

    @Test
    public void yamlAndJsonWorkflowCorrect() {
        yamlAndJsonEntryCorrect(WORKFLOW);
    }

    @Test
    public void yamlAndJsonToolCorrect() {
        yamlAndJsonEntryCorrect(TOOL);
    }

    private void yamlAndJsonEntryCorrect(String entryType) {
        File yamlTestParameterFile = new File(ResourceHelpers.resourceFilePath("hello.yaml"));
        File jsonTestParameterFile = new File(ResourceHelpers.resourceFilePath("hello.json"));

        List<String> args = getLaunchStringList(entryType);
        args.add("--yaml");
        args.add(yamlTestParameterFile.getAbsolutePath());
        args.add(JSON);
        args.add(jsonTestParameterFile.getAbsolutePath());
        exit.expectSystemExitWithStatus(CLIENT_ERROR);
        exit.checkAssertionAfterwards(() -> Assert.assertTrue(systemErrRule.getLog().contains(AbstractEntryClient.MULTIPLE_TEST_FILE_ERROR_MESSAGE)));
        Client.main(args.toArray(new String[0]));
    }

    @Test
    public void testMaliciousParameterYaml() {
        File yamlTestParameterFile = new File(ResourceHelpers.resourceFilePath("malicious.input.yaml"));

        List<String> args = getLaunchStringList(WORKFLOW);
        args.add("--yaml");
        args.add(yamlTestParameterFile.getAbsolutePath());
        exit.expectSystemExit();
        exit.checkAssertionAfterwards(() -> Assert.assertTrue(systemErrRule.getLog().contains("Global tag is not allowed: tag:yaml.org,2002:javax.script.ScriptEngineManager")));
        Client.main(args.toArray(new String[0]));
    }


    @Test
    public void duplicateTestParameterFile() {
        // Return client failure if both --json and --yaml are passed
        File file = new File(ResourceHelpers.resourceFilePath("wrongExtcwl.wdl"));
        File helloJSON = new File(ResourceHelpers.resourceFilePath("helloSpaces.json"));
        File helloYAML = new File(ResourceHelpers.resourceFilePath("hello.yaml"));

        ArrayList<String> args = new ArrayList<>();
        args.add(WORKFLOW);
        args.add(LAUNCH);
        args.add(ENTRY);
        args.add(file.getAbsolutePath());
        args.add(JSON);
        args.add(helloJSON.getPath());
        args.add("--yaml");
        args.add(helloYAML.getPath());

        File config = new File(ResourceHelpers.resourceFilePath("clientConfig"));
        exit.expectSystemExitWithStatus(CLIENT_ERROR);
        exit.checkAssertionAfterwards(() -> assertTrue("Client error should be returned",
            systemErrRule.getLog().contains(AbstractEntryClient.MULTIPLE_TEST_FILE_ERROR_MESSAGE)));
        args.add(0, config.getPath());
        args.add(0, CONFIG);
        args.add(0, SCRIPT_FLAG);
        Client.main(args.toArray(new String[0]));
    }

    @Test
    public void wdlWorkflowCorrectFlags() {
        wdlEntryCorrectFlags(WORKFLOW);
    }

    @Test
    public void wdlToolCorrectFlags() {
        wdlEntryCorrectFlags(TOOL);
    }

    private void wdlEntryCorrectFlags(String entryType) {
        File yamlTestParameterFile = new File(ResourceHelpers.resourceFilePath("hello.yaml"));
        File jsonTestParameterFile = new File(ResourceHelpers.resourceFilePath("hello.json"));

        List<String> yamlFileWithJSONFlag = getLaunchStringList(entryType);
        yamlFileWithJSONFlag.add(JSON);
        yamlFileWithJSONFlag.add(yamlTestParameterFile.getAbsolutePath());

        List<String> yamlFileWithYAMLFlag = getLaunchStringList(entryType);
        yamlFileWithYAMLFlag.add("--yaml");
        yamlFileWithYAMLFlag.add(yamlTestParameterFile.getAbsolutePath());

        List<String> jsonFileWithJSONFlag = getLaunchStringList(entryType);
        jsonFileWithJSONFlag.add(JSON);
        jsonFileWithJSONFlag.add(jsonTestParameterFile.getAbsolutePath());

        List<String> jsonFileWithYAMLFlag = getLaunchStringList(entryType);
        jsonFileWithYAMLFlag.add("--yaml");
        jsonFileWithYAMLFlag.add(jsonTestParameterFile.getAbsolutePath());

        Client.main(yamlFileWithJSONFlag.toArray(new String[0]));
        Client.main(yamlFileWithYAMLFlag.toArray(new String[0]));
        Client.main(jsonFileWithJSONFlag.toArray(new String[0]));
        Client.main(jsonFileWithYAMLFlag.toArray(new String[0]));
    }

    private List<String> getLaunchStringList(String entryType) {
        File descriptorFile = new File(ResourceHelpers.resourceFilePath("hello.wdl"));
        final List<String> strings = new ArrayList<>();
        strings.add(SCRIPT_FLAG);
        strings.add(CONFIG);
        strings.add(ResourceHelpers.resourceFilePath("config"));
        strings.add(entryType);
        strings.add(LAUNCH);
        strings.add("--local-entry");
        strings.add(descriptorFile.getAbsolutePath());

        return strings;
    }



}
