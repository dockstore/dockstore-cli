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
package io.github.collaboratory.cwl.cwlrunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.ws.rs.ProcessingException;

import com.google.common.base.Joiner;
import io.dockstore.client.cli.ArgumentUtility;
import io.dockstore.client.cli.Client;
import io.swagger.client.ApiException;
import io.swagger.client.api.MetadataApi;
import org.apache.commons.lang3.tuple.ImmutablePair;

import static io.dockstore.common.PipHelper.convertPipRequirementsStringToMap;

public class CWLToolWrapper implements CWLRunnerInterface {
    @Override
    public void checkForCWLDependencies(MetadataApi metadataApi) {
        final String[] s1 = { "cwltool", "--version" };
        final ImmutablePair<String, String> pair1 = io.cwl.avro.Utilities
                .executeCommand(Joiner.on(" ").join(Arrays.asList(s1)), false, com.google.common.base.Optional.absent(),
                        com.google.common.base.Optional.absent());
        final String cwlToolVersion = pair1.getKey().split(" ")[1].trim();

        final String[] s2 = { "schema-salad-tool", "--version", "schema" };
        final ImmutablePair<String, String> pair2 = io.cwl.avro.Utilities
                .executeCommand(Joiner.on(" ").join(Arrays.asList(s2)), false, com.google.common.base.Optional.absent(),
                        com.google.common.base.Optional.absent());
        // schema salad new version changes to a different format, looks like the last value still works
        String[] split = pair2.getKey().split(" ");
        final String schemaSaladVersion = split[split.length - 1].trim();

        try {
            String clientVersion = Client.getClientVersion();
            String runnerDependencies = metadataApi
                .getRunnerDependencies(clientVersion, "3", "cwltool", "text");
            Map<String, String> stringStringMap = convertPipRequirementsStringToMap(runnerDependencies);
            final String expectedCwltoolVersion = stringStringMap.get("cwltool");
            final String expectedSchemaSaladVersion = stringStringMap.get("schema-salad");
            if (expectedCwltoolVersion != null && !cwlToolVersion.equals(expectedCwltoolVersion)) {
                ArgumentUtility.errorMessage("cwltool version is " + cwlToolVersion + " , Dockstore is tested with " + expectedCwltoolVersion
                    + "\nOverride and run with `--script`", Client.COMMAND_ERROR);
            }
            if (expectedSchemaSaladVersion != null && !schemaSaladVersion.equals(expectedSchemaSaladVersion)) {
                ArgumentUtility.errorMessage("schema-salad version is " + schemaSaladVersion + " , Dockstore is tested with " + expectedSchemaSaladVersion
                    + "\nOverride and run with `--script`", Client.COMMAND_ERROR);
            }
        } catch (ProcessingException | ApiException e) {
            ArgumentUtility.out("Could not get cwltool dependencies");
        }
    }

    @Override
    public List<String> getExecutionCommand(String outputDir, String tmpDir, String workingDir, String cwlFile, String jsonSettings) {
        return new ArrayList<>(Arrays
            .asList("cwltool", "--enable-dev", "--non-strict", "--outdir", outputDir, "--tmpdir-prefix", tmpDir, "--tmp-outdir-prefix",
                workingDir, cwlFile, jsonSettings));
    }
}
