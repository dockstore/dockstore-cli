/*
 *    Copyright 2017 OICR
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package io.dockstore.client.cli;

import io.dockstore.common.Utilities;
import io.dropwizard.testing.ResourceHelpers;
import io.github.collaboratory.cwl.cwlrunner.CWLRunnerFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.stream.SystemErr;
import uk.org.webcompere.systemstubs.stream.SystemOut;

import static io.dockstore.client.cli.Client.DEPRECATED_PORT_MESSAGE;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Created by dyuen on 2/23/17.
 */
@ExtendWith(SystemStubsExtension.class)
public class ClientTestIT {

    @SystemStub
    public final SystemOut systemOutRule = new SystemOut();

    @SystemStub
    public final SystemErr systemErrRule = new SystemErr();

    @Test
    public void testDependencies() {
        String config = ResourceHelpers.resourceFilePath("config");
        CWLRunnerFactory.setConfig(Utilities.parseConfig(config));
        assertFalse(systemErrRule.getText().contains("Override and run with"));
        assertFalse(systemOutRule.getText().contains("Override and run with"));
    }

    /**
     * When javax.activation is missing (because using Java 11), there will be error logs
     * Check that there are no error logs
     * Careful, test scope may interfere with validity of this test
     */
    @Test
    public void noErrorLogs() {
        String clientConfig = ResourceHelpers.resourceFilePath("clientConfig");
        String[] command = { "--help", "--config", clientConfig };
        Client.main(command);
        assertTrue(systemErrRule.getText().isBlank(), "There are unexpected error logs");
        assertFalse(systemErrRule.getText().contains(DEPRECATED_PORT_MESSAGE), "Should not have warned about port 8443");
    }

    /**
     * When the old 8443 port is used, the user should be warned
     */
    @Test
    public void testPort8443() {
        String clientConfig = ResourceHelpers.resourceFilePath("oldClientConfig");
        String[] command = { "--help", "--config", clientConfig };
        Client.main(command);
        assertTrue(systemErrRule.getText().contains(DEPRECATED_PORT_MESSAGE), "Should have warned about port 8443");
    }
}
