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
        final String wesAuthType = "aws";
        final String wesAuthValue = "my-profile";
        final String[] args = {
            "--wes-auth",
            wesAuthType,
            wesAuthValue,
            "banana"
        };

        WesCommandParser wesCommandParser = new WesCommandParser();
        JCommander parser = wesCommandParser.jCommander;
        parser.parse(args);

        assertNull("Parsed command should be null", parser.getParsedCommand());
        assertEquals("The parsed auth type should be 'aws'", wesAuthType, wesCommandParser.wesMain.getWesAuthType());
        assertEquals("The parsed auth value should be 'my-profile'", wesAuthValue, wesCommandParser.wesMain.getWesAuthValue());
    }

    @Test
    public void testWesMainAuthPartial() {
        final String wesAuthType = "aws";
        final String[] args = {
            "--wes-auth",
            wesAuthType
        };

        WesCommandParser wesCommandParser = new WesCommandParser();
        JCommander parser = wesCommandParser.jCommander;
        parser.parse(args);

        assertNull("Parsed command should be null", parser.getParsedCommand());
        assertEquals("The parsed auth type should be 'aws'", wesAuthType, wesCommandParser.wesMain.getWesAuthType());
        assertNull("The parsed auth value should be null", wesCommandParser.wesMain.getWesAuthValue());
    }

    @Test
    public void testWesMainAuthPartial2() {
        final String wesAuthType = "bearer";
        final String[] args = {
            "--wes-auth",
            wesAuthType
        };

        WesCommandParser wesCommandParser = new WesCommandParser();
        JCommander parser = wesCommandParser.jCommander;
        parser.parse(args);

        assertNull("Parsed command should be null", parser.getParsedCommand());
        assertEquals("The parsed auth type should be 'bearer'", wesAuthType, wesCommandParser.wesMain.getWesAuthType());
        assertNull("The parsed auth value should be null", wesCommandParser.wesMain.getWesAuthValue());
    }

    @Test
    public void testWesMainAuthPartial3() {
        final String[] args = {}; // make sure we don't crash on no auth provided

        WesCommandParser wesCommandParser = new WesCommandParser();
        JCommander parser = wesCommandParser.jCommander;
        parser.parse(args);

        assertNull("Parsed command should be null", parser.getParsedCommand());
        assertNull("The parsed auth type should be 'null'", wesCommandParser.wesMain.getWesAuthType());
        assertNull("The parsed auth value should be null", wesCommandParser.wesMain.getWesAuthValue());
    }

    @Test
    public void testCommandLaunchHelp() {
        final String[] args = {
            "--wes-auth",
            "bearer",
            "123456",
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
            "--wes-auth",
            "bearer",
            "123456",
            "launch",
            "--entry",
            "my/fake/entry",
            "--json",
            "path/to/json.json"
        };

        WesCommandParser wesCommandParser = new WesCommandParser();
        JCommander parser = wesCommandParser.jCommander;
        parser.parse(args);

        assertEquals("Parsed command should be 'launch'", "launch", parser.getParsedCommand());
        assertEquals("The parsed entry should be 'my/fake/entry'", "my/fake/entry", wesCommandParser.commandLaunch.getEntry());
        assertEquals("The parsed entry should be 'path/to/json.json'", "path/to/json.json", wesCommandParser.commandLaunch.getJson());
    }

    @Test
    public void testCommandLaunch2() {
        final String[] args = {
            "--wes-auth",
            "bearer",
            "123456",
            "launch",
            "--entry",
            "my/fake/entry",
            "--yaml",
            "path/to/yaml.yaml"
        };

        WesCommandParser wesCommandParser = new WesCommandParser();
        JCommander parser = wesCommandParser.jCommander;
        parser.parse(args);

        assertEquals("Parsed command should be 'launch'", "launch", parser.getParsedCommand());
        assertEquals("The parsed entry should be 'my/fake/entry'", "my/fake/entry", wesCommandParser.commandLaunch.getEntry());
        assertEquals("The parsed entry should be 'path/to/yaml.yaml'", "path/to/yaml.yaml", wesCommandParser.commandLaunch.getYaml());
    }

    @Test
    public void testCommandLaunch3() {
        final String[] args = {
            "launch",
            "--wes-url",
            "banana",
            "--wes-auth",
            "bearer",
            "123456",
            "--entry",
            "my/fake/entry",
            "--yaml",
            "path/to/yaml.yaml"
        };

        WesCommandParser wesCommandParser = new WesCommandParser();
        JCommander parser = wesCommandParser.jCommander;
        parser.parse(args);

        assertEquals("Parsed command should be 'launch'", "launch", parser.getParsedCommand());
        assertEquals("The parsed URL should be 'banana'", "banana", wesCommandParser.commandLaunch.getWesUrl());
        assertEquals("The parsed entry should be 'my/fake/entry'", "my/fake/entry", wesCommandParser.commandLaunch.getEntry());
        assertEquals("The parsed entry should be 'path/to/yaml.yaml'", "path/to/yaml.yaml", wesCommandParser.commandLaunch.getYaml());
    }

    @Test
    public void testCommandLaunchNoEntry() {
        final String[] args = {
            "--wes-auth",
            "bearer",
            "123456",
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
            "--wes-auth",
            "bearer",
            "123456",
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
            "--wes-auth",
            "bearer",
            "123456",
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
            "--wes-auth",
            "bearer",
            "123456",
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
            "--wes-auth",
            "bearer",
            "123456",
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
            "--wes-auth",
            "bearer",
            "123456",
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
