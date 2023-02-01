package io.dockstore.client.cli;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import io.dockstore.client.cli.nested.WesCommandParser;
import org.junit.jupiter.api.Test;

import static io.dockstore.client.cli.ArgumentUtility.LAUNCH;
import static io.dockstore.client.cli.Client.HELP;
import static io.dockstore.client.cli.nested.AbstractEntryClient.CANCEL;
import static io.dockstore.client.cli.nested.AbstractEntryClient.LIST;
import static io.dockstore.client.cli.nested.AbstractEntryClient.LOGS;
import static io.dockstore.client.cli.nested.AbstractEntryClient.SERVICE_INFO;
import static io.dockstore.client.cli.nested.AbstractEntryClient.STATUS;
import static io.dockstore.client.cli.nested.WesCommandParser.ENTRY;
import static io.dockstore.client.cli.nested.WesCommandParser.ID;
import static io.dockstore.client.cli.nested.WesCommandParser.WES_URL;
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
            WES_URL,
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
            ENTRY,
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
            ENTRY,
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
            WES_URL,
            "banana",
            ENTRY,
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
            CANCEL,
            ID,
            "123456"
        };

        WesCommandParser wesCommandParser = new WesCommandParser();
        JCommander parser = wesCommandParser.jCommander;
        parser.parse(args);

        assertEquals(CANCEL, parser.getParsedCommand(), "Parsed command should be '" + CANCEL + "'");
        assertEquals("123456", wesCommandParser.commandCancel.getId(), "The parsed entry should be '123456'");
    }

    @Test
    void testCommandCancelNoID() {
        final String[] args = {
            CANCEL
        };

        WesCommandParser wesCommandParser = new WesCommandParser();
        JCommander parser = wesCommandParser.jCommander;
        try {
            parser.parse(args);
            fail("The parser should throw an exception for missing '" + ID + "' parameter");
        } catch (ParameterException e) {
            assertTrue(true);
        }
    }

    @Test
    void testCommandCancelHelp() {
        final String[] args = {
            CANCEL,
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
            STATUS,
            ID,
            "123456"
        };

        WesCommandParser wesCommandParser = new WesCommandParser();
        JCommander parser = wesCommandParser.jCommander;
        parser.parse(args);

        assertEquals(STATUS, parser.getParsedCommand(), "Parsed command should be '" + STATUS + "'");
        assertEquals("123456", wesCommandParser.commandStatus.getId(), "The parsed entry should be '123456'");
    }

    @Test
    void testCommandStatus2() {
        final String[] args = {
            STATUS,
            ID,
            "123456"
        };

        WesCommandParser wesCommandParser = new WesCommandParser();
        JCommander parser = wesCommandParser.jCommander;
        parser.parse(args);

        assertEquals(STATUS, parser.getParsedCommand(), "Parsed command should be '" + STATUS + "'");
        assertEquals("123456", wesCommandParser.commandStatus.getId(), "The parsed entry should be '123456'");
    }

    @Test
    void testCommandStatusNoID() {
        final String[] args = {
            STATUS
        };

        WesCommandParser wesCommandParser = new WesCommandParser();
        JCommander parser = wesCommandParser.jCommander;
        try {
            parser.parse(args);
            fail("The parser should throw an exception for missing '" + ID + "' parameter");
        } catch (ParameterException e) {
            assertTrue(true);
        }
    }

    @Test
    void testCommandStatusHelp() {
        final String[] args = {
            STATUS,
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
            SERVICE_INFO
        };

        WesCommandParser wesCommandParser = new WesCommandParser();
        JCommander parser = wesCommandParser.jCommander;
        parser.parse(args);
    }

    @Test
    void testCommandServiceInfoHelp() {
        final String[] args = {
            SERVICE_INFO,
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
            LOGS,
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
            LOGS,
            ID,
            "123456"
        };

        WesCommandParser wesCommandParser = new WesCommandParser();
        JCommander parser = wesCommandParser.jCommander;
        parser.parse(args);

        assertEquals(LOGS, parser.getParsedCommand(), "Parsed command should be '" + LOGS + "'");
        assertEquals("123456", wesCommandParser.commandRunLogs.getId(), "The parsed entry should be '123456'");
    }
}
