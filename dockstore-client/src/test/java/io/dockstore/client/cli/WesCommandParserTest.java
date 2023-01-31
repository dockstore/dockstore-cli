package io.dockstore.client.cli;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import io.dockstore.client.cli.nested.WesCommandParser;
import org.junit.jupiter.api.Test;

import static io.dockstore.client.cli.ArgumentUtility.LAUNCH;
import static io.dockstore.client.cli.Client.HELP;
import static io.dockstore.client.cli.nested.AbstractEntryClient.LIST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class WesCommandParserTest {

    @Test
    void testWesMainHelp() {
        final String[] args = {
            HELP
        };

        WesCommandParser wesCommandParser = new WesCommandParser();
        JCommander parser = wesCommandParser.jCommander;
        parser.parse(args);

        assertNull(parser.getParsedCommand(), "Parsed command should be null");
        assertTrue(wesCommandParser.wesMain.isHelp(), "Should have help value set to true.");
    }

    @Test
    void testWesMainUrl() {
        final String wesUrl = "my.wes.url.com/ga4gh/v1/";
        final String[] args = {
            "--wes-url",
            wesUrl
        };

        WesCommandParser wesCommandParser = new WesCommandParser();
        JCommander parser = wesCommandParser.jCommander;
        parser.parse(args);

        assertNull(parser.getParsedCommand(), "Parsed command should be null");
        assertEquals(wesUrl, wesCommandParser.wesMain.getWesUrl(), "The parsed URL should match the URL passed in");
    }

    @Test
    void testWesMainAuth() {
        final String[] args = {};

        WesCommandParser wesCommandParser = new WesCommandParser();
        JCommander parser = wesCommandParser.jCommander;
        parser.parse(args);

        assertNull(parser.getParsedCommand(), "Parsed command should be null");
    }

    @Test
    void testCommandLaunchHelp() {
        final String[] args = {
            LAUNCH,
            HELP
        };

        WesCommandParser wesCommandParser = new WesCommandParser();
        JCommander parser = wesCommandParser.jCommander;
        parser.parse(args);

        assertEquals(LAUNCH, parser.getParsedCommand(), "Parsed command should be 'launch'");
        assertTrue(wesCommandParser.commandLaunch.isHelp(), "Should be a help command");
    }

    @Test
    void testCommandLaunch1() {
        final String[] args = {
            LAUNCH,
            "--entry",
            "my/fake/entry"
        };

        WesCommandParser wesCommandParser = new WesCommandParser();
        JCommander parser = wesCommandParser.jCommander;
        parser.parse(args);

        assertEquals(LAUNCH, parser.getParsedCommand(), "Parsed command should be 'launch'");
        assertEquals("my/fake/entry", wesCommandParser.commandLaunch.getEntry(), "The parsed entry should be 'my/fake/entry'");
    }

    @Test
    void testCommandLaunch2() {
        final String[] args = {
            LAUNCH,
            "--entry",
            "my/fake/entry"
        };

        WesCommandParser wesCommandParser = new WesCommandParser();
        JCommander parser = wesCommandParser.jCommander;
        parser.parse(args);

        assertEquals(LAUNCH, parser.getParsedCommand(), "Parsed command should be 'launch'");
        assertEquals("my/fake/entry", wesCommandParser.commandLaunch.getEntry(), "The parsed entry should be 'my/fake/entry'");
    }

    @Test
    void testCommandLaunch3() {
        final String[] args = {
            LAUNCH,
            "--wes-url",
            "banana",
            "--entry",
            "my/fake/entry"
        };

        WesCommandParser wesCommandParser = new WesCommandParser();
        JCommander parser = wesCommandParser.jCommander;
        parser.parse(args);

        assertEquals(LAUNCH, parser.getParsedCommand(), "Parsed command should be 'launch'");
        assertEquals("banana", wesCommandParser.commandLaunch.getWesUrl(), "The parsed URL should be 'banana'");
        assertEquals("my/fake/entry", wesCommandParser.commandLaunch.getEntry(), "The parsed entry should be 'my/fake/entry'");
    }

    @Test
    void testCommandLaunchNoEntry() {
        final String[] args = {
            LAUNCH,
            "--yaml",
            "path/to/yaml.yaml"
        };

        WesCommandParser wesCommandParser = new WesCommandParser();
        JCommander parser = wesCommandParser.jCommander;

        try {
            parser.parse(args);
            fail("The parser should throw an exception for missing '--entry' parameter");
        } catch (ParameterException e) {
            assertTrue(true);
        }
    }

    @Test
    void testCommandCancel() {
        final String[] args = {
            "cancel",
            "--id",
            "123456"
        };

        WesCommandParser wesCommandParser = new WesCommandParser();
        JCommander parser = wesCommandParser.jCommander;
        parser.parse(args);

        assertEquals("cancel", parser.getParsedCommand(), "Parsed command should be 'cancel'");
        assertEquals("123456", wesCommandParser.commandCancel.getId(), "The parsed entry should be '123456'");
    }

    @Test
    void testCommandCancelNoID() {
        final String[] args = {
            "cancel"
        };

        WesCommandParser wesCommandParser = new WesCommandParser();
        JCommander parser = wesCommandParser.jCommander;
        try {
            parser.parse(args);
            fail("The parser should throw an exception for missing '--id' parameter");
        } catch (ParameterException e) {
            assertTrue(true);
        }
    }

    @Test
    void testCommandCancelHelp() {
        final String[] args = {
            "cancel",
            HELP
        };

        WesCommandParser wesCommandParser = new WesCommandParser();
        JCommander parser = wesCommandParser.jCommander;
        parser.parse(args);
        assertTrue(wesCommandParser.commandCancel.isHelp(), "Help should be set");
    }

    @Test
    void testCommandStatus1() {
        final String[] args = {
            "status",
            "--id",
            "123456"
        };

        WesCommandParser wesCommandParser = new WesCommandParser();
        JCommander parser = wesCommandParser.jCommander;
        parser.parse(args);

        assertEquals("status", parser.getParsedCommand(), "Parsed command should be 'status'");
        assertEquals("123456", wesCommandParser.commandStatus.getId(), "The parsed entry should be '123456'");
    }

    @Test
    void testCommandStatus2() {
        final String[] args = {
            "status",
            "--id",
            "123456"
        };

        WesCommandParser wesCommandParser = new WesCommandParser();
        JCommander parser = wesCommandParser.jCommander;
        parser.parse(args);

        assertEquals("status", parser.getParsedCommand(), "Parsed command should be 'status'");
        assertEquals("123456", wesCommandParser.commandStatus.getId(), "The parsed entry should be '123456'");
    }

    @Test
    void testCommandStatusNoID() {
        final String[] args = {
            "status"
        };

        WesCommandParser wesCommandParser = new WesCommandParser();
        JCommander parser = wesCommandParser.jCommander;
        try {
            parser.parse(args);
            fail("The parser should throw an exception for missing '--id' parameter");
        } catch (ParameterException e) {
            assertTrue(true);
        }
    }

    @Test
    void testCommandStatusHelp() {
        final String[] args = {
            "status",
            HELP
        };

        WesCommandParser wesCommandParser = new WesCommandParser();
        JCommander parser = wesCommandParser.jCommander;
        parser.parse(args);
        assertTrue(wesCommandParser.commandStatus.isHelp(), "Help should be set");
    }

    @Test
    void testCommandServiceInfo() {
        final String[] args = {
            "service-info"
        };

        WesCommandParser wesCommandParser = new WesCommandParser();
        JCommander parser = wesCommandParser.jCommander;
        parser.parse(args);
    }

    @Test
    void testCommandServiceInfoHelp() {
        final String[] args = {
            "service-info",
            HELP
        };

        WesCommandParser wesCommandParser = new WesCommandParser();
        JCommander parser = wesCommandParser.jCommander;
        parser.parse(args);
        assertTrue(wesCommandParser.commandServiceInfo.isHelp(), "Help should be set");
    }

    @Test
    void testCommandRunListHelp() {
        final String[] args = {
            LIST,
            HELP
        };

        WesCommandParser wesCommandParser = new WesCommandParser();
        JCommander parser = wesCommandParser.jCommander;
        parser.parse(args);
        assertTrue(wesCommandParser.commandRunList.isHelp(), "Help should be set");
    }

    @Test
    void testCommandRunList() {
        final int count = 3;
        final String[] args = {
            LIST,
            "--count",
            String.valueOf(count),
            "--page-token",
            "banana"
        };

        WesCommandParser wesCommandParser = new WesCommandParser();
        JCommander parser = wesCommandParser.jCommander;
        parser.parse(args);
        assertFalse(wesCommandParser.commandRunList.isHelp(), "Help should be set");
        assertEquals(count, wesCommandParser.commandRunList.getPageSize(), "Count should be set to 3");
        assertEquals("banana", wesCommandParser.commandRunList.getPageToken(), "Page token should be set to 'banana'");

    }

    @Test
    void testCommandRunLogsHelp() {
        final String[] args = {
            "logs",
            HELP
        };

        WesCommandParser wesCommandParser = new WesCommandParser();
        JCommander parser = wesCommandParser.jCommander;
        parser.parse(args);
        assertTrue(wesCommandParser.commandRunLogs.isHelp(), "Help should be set");
    }

    @Test
    void testCommandRunlogs1() {
        final String[] args = {
            "logs",
            "--id",
            "123456"
        };

        WesCommandParser wesCommandParser = new WesCommandParser();
        JCommander parser = wesCommandParser.jCommander;
        parser.parse(args);

        assertEquals("logs", parser.getParsedCommand(), "Parsed command should be 'logs'");
        assertEquals("123456", wesCommandParser.commandRunLogs.getId(), "The parsed entry should be '123456'");
    }
}
