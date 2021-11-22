package io.dockstore.client.cli;

import com.beust.jcommander.JCommander;
import io.dockstore.client.cli.nested.WesCommandParser;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class WesCommandParserTest {

    @Test
    public void testWesMainHelp() {
        final String[] args = {
            "--help"
        };

        WesCommandParser wesCommandParser = new WesCommandParser();
        JCommander parser = wesCommandParser.buildWesCommandParser();
        parser.parse(args);
        //assertTrue("Should have help value set to true.", wesCommandParser.wesMain.help);
    }

    @Test
    public void testWesMainUrl() {
        final String wesUrl = "my.wes.url.com/ga4gh/v1/";
        final String[] args = {
            "--wes-url",
            wesUrl,
            "--aws-region",
            "space-mars-2"
        };

        WesCommandParser wesCommandParser = new WesCommandParser();
        JCommander parser = wesCommandParser.buildWesCommandParser();
        parser.parse(args);
        //assertEquals("The parsed URL should match the URL passed in", wesUrl, wesCommandParser.wesMain.wesUrl);
    }

    @Test
    public void testWesMainAuth() {
        final String wesAuthType = "aws";
        final String wesAuthValue = "my-profile";
        final String[] args = {
            "--aws-region",
            "space-mars-2",
            "--wes-auth",
            wesAuthType,
            wesAuthValue,
            "banana"
        };

        WesCommandParser wesCommandParser = new WesCommandParser();
        JCommander parser = wesCommandParser.buildWesCommandParser();
        parser.parse(args);
        assertEquals("The parsed auth type should be 'aws'", wesAuthType, wesCommandParser.wesMain.getWesAuthType());
        assertEquals("The parsed auth value should be 'my-profile'", wesAuthValue, wesCommandParser.wesMain.getWesAuthValue());
    }

    @Test
    public void testWesMainAuthPartial() {
        final String wesAuthType = "aws";
        final String[] args = {
            "--aws-region",
            "space-mars-2",
            "--wes-auth",
            wesAuthType
        };

        WesCommandParser wesCommandParser = new WesCommandParser();
        JCommander parser = wesCommandParser.buildWesCommandParser();
        parser.parse(args);
        assertEquals("The parsed auth type should be 'aws'", wesAuthType, wesCommandParser.wesMain.getWesAuthType());
        assertEquals("The parsed auth value should be null", null, wesCommandParser.wesMain.getWesAuthValue());
    }
}
