package io.dockstore.client.cli;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import io.dockstore.client.cli.nested.WesCommandParser;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class WesCommandParserTest {

    @Test
    public void testWesMainHelp() {
        final String[] args = {
            "--help"
        };

        WesCommandParser wesCommandParser = new WesCommandParser();
        JCommander parser = wesCommandParser.jCommander;
        parser.parse(args);

        assertNull("Parsed command should be null", parser.getParsedCommand());
        assertTrue("Should have help value set to true.", wesCommandParser.wesMain.isHelp());
    }

    @Test
    public void testWesMainUrl() {
        final String wesUrl = "my.wes.url.com/ga4gh/v1/";
        final String[] args = {
            "--wes-url",
            wesUrl
        };

        WesCommandParser wesCommandParser = new WesCommandParser();
        JCommander parser = wesCommandParser.jCommander;
        parser.parse(args);

        assertNull("Parsed command should be null", parser.getParsedCommand());
        assertEquals("The parsed URL should match the URL passed in", wesUrl, wesCommandParser.wesMain.getWesUrl());
    }

    @Test
    public void testWesMainAuth() {
        final String[] args = {};

        WesCommandParser wesCommandParser = new WesCommandParser();
        JCommander parser = wesCommandParser.jCommander;
        parser.parse(args);

        assertNull("Parsed command should be null", parser.getParsedCommand());
    }

    @Test
    public void testCommandLaunchHelp() {
        final String[] args = {
            "launch",
            "--help"
        };

        WesCommandParser wesCommandParser = new WesCommandParser();
        JCommander parser = wesCommandParser.jCommander;
        parser.parse(args);

        assertEquals("Parsed command should be 'launch'", "launch", parser.getParsedCommand());
        assertTrue("Should be a help command", wesCommandParser.commandLaunch.isHelp());
    }

    @Test
    public void testCommandLaunch1() {
        final String[] args = {
            "launch",
            "--entry",
            "my/fake/entry"
        };

        WesCommandParser wesCommandParser = new WesCommandParser();
        JCommander parser = wesCommandParser.jCommander;
        parser.parse(args);

        assertEquals("Parsed command should be 'launch'", "launch", parser.getParsedCommand());
        assertEquals("The parsed entry should be 'my/fake/entry'", "my/fake/entry", wesCommandParser.commandLaunch.getEntry());
    }

    @Test
    public void testCommandLaunch2() {
        final String[] args = {
            "launch",
            "--entry",
            "my/fake/entry"
        };

        WesCommandParser wesCommandParser = new WesCommandParser();
        JCommander parser = wesCommandParser.jCommander;
        parser.parse(args);

        assertEquals("Parsed command should be 'launch'", "launch", parser.getParsedCommand());
        assertEquals("The parsed entry should be 'my/fake/entry'", "my/fake/entry", wesCommandParser.commandLaunch.getEntry());
    }

    @Test
    public void testCommandLaunch3() {
        final String[] args = {
            "launch",
            "--wes-url",
            "banana",
            "--entry",
            "my/fake/entry"
        };

        WesCommandParser wesCommandParser = new WesCommandParser();
        JCommander parser = wesCommandParser.jCommander;
        parser.parse(args);

        assertEquals("Parsed command should be 'launch'", "launch", parser.getParsedCommand());
        assertEquals("The parsed URL should be 'banana'", "banana", wesCommandParser.commandLaunch.getWesUrl());
        assertEquals("The parsed entry should be 'my/fake/entry'", "my/fake/entry", wesCommandParser.commandLaunch.getEntry());
    }

    @Test
    public void testCommandLaunchNoEntry() {
        final String[] args = {
            "launch",
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
    public void testCommandCancel() {
        final String[] args = {
            "cancel",
            "--id",
            "123456"
        };

        WesCommandParser wesCommandParser = new WesCommandParser();
        JCommander parser = wesCommandParser.jCommander;
        parser.parse(args);

        assertEquals("Parsed command should be 'cancel'", "cancel", parser.getParsedCommand());
        assertEquals("The parsed entry should be '123456'", "123456", wesCommandParser.commandCancel.getId());
    }

    @Test
    public void testCommandCancelNoID() {
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
    public void testCommandCancelHelp() {
        final String[] args = {
            "cancel",
            "--help"
        };

        WesCommandParser wesCommandParser = new WesCommandParser();
        JCommander parser = wesCommandParser.jCommander;
        parser.parse(args);
        assertTrue("Help should be set", wesCommandParser.commandCancel.isHelp());
    }

    @Test
    public void testCommandStatus1() {
        final String[] args = {
            "status",
            "--id",
            "123456"
        };

        WesCommandParser wesCommandParser = new WesCommandParser();
        JCommander parser = wesCommandParser.jCommander;
        parser.parse(args);

        assertEquals("Parsed command should be 'status'", "status", parser.getParsedCommand());
        assertEquals("The parsed entry should be '123456'", "123456", wesCommandParser.commandStatus.getId());
    }

    @Test
    public void testCommandStatus2() {
        final String[] args = {
            "status",
            "--id",
            "123456",
            "--verbose"
        };

        WesCommandParser wesCommandParser = new WesCommandParser();
        JCommander parser = wesCommandParser.jCommander;
        parser.parse(args);

        assertEquals("Parsed command should be 'status'", "status", parser.getParsedCommand());
        assertEquals("The parsed entry should be '123456'", "123456", wesCommandParser.commandStatus.getId());
        assertTrue("verbose flag should be set", wesCommandParser.commandStatus.isVerbose());
    }

    @Test
    public void testCommandStatusNoID() {
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
    public void testCommandStatusHelp() {
        final String[] args = {
            "status",
            "--help"
        };

        WesCommandParser wesCommandParser = new WesCommandParser();
        JCommander parser = wesCommandParser.jCommander;
        parser.parse(args);
        assertTrue("Help should be set", wesCommandParser.commandStatus.isHelp());
    }

    @Test
    public void testCommandServiceInfo() {
        final String[] args = {
            "service-info"
        };

        WesCommandParser wesCommandParser = new WesCommandParser();
        JCommander parser = wesCommandParser.jCommander;
        parser.parse(args);
    }

    @Test
    public void testCommandServiceInfoHelp() {
        final String[] args = {
            "service-info",
            "--help"
        };

        WesCommandParser wesCommandParser = new WesCommandParser();
        JCommander parser = wesCommandParser.jCommander;
        parser.parse(args);
        assertTrue("Help should be set", wesCommandParser.commandServiceInfo.isHelp());
    }
}
