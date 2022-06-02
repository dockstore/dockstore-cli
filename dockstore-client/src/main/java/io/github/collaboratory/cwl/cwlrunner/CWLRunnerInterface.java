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

import java.util.List;
import java.util.Optional;

import io.swagger.client.api.MetadataApi;

/**
 * Abstracts out the interaction with cwlrunners (for example, cwltool or toil)
 */
public interface CWLRunnerInterface {

    /**
     * Checks that the environment is properly setup
     */
    void checkForCWLDependencies(MetadataApi metadataApi);

    /**
     *
     * @return an array representing the command to invoke a particular cwl-runner
     */
    List<String> getExecutionCommand(String outputDir, String tmpDir, String workingDir, String cwlFile, Optional<String> jsonSettings);

}
