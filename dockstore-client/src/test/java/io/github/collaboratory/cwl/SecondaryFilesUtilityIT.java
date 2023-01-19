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
package io.github.collaboratory.cwl;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import com.google.gson.Gson;
import io.cwl.avro.CWL;
import io.cwl.avro.Workflow;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author gluu
 * @since 18/09/17
 */
public class SecondaryFilesUtilityIT {

    private static final String IMAGE_DESCRIPTOR_PATH = FileUtils
        .getFile("src", "test", "resources", "gdc/cwl/workflows/dnaseq/transform.cwl").getAbsolutePath();
    private static final CWL CWL_UTIL = new CWL();
    private static final String IMAGE_DESCRIPTOR_CONTENT = CWL_UTIL.parseCWL(IMAGE_DESCRIPTOR_PATH).getLeft();
    private static final Gson GSON = CWL.getTypeSafeCWLToolDocument();

    @Test
    void modifyWorkflowToIncludeToolSecondaryFiles() {
        IntStream.range(0, 5).forEach(i -> modifyWorkflow());
    }

    private void modifyWorkflow() {
        Workflow workflow = GSON.fromJson(IMAGE_DESCRIPTOR_CONTENT, Workflow.class);
        SecondaryFilesUtility secondaryFilesUtility = new SecondaryFilesUtility(CWL_UTIL, GSON);
        secondaryFilesUtility.modifyWorkflowToIncludeToolSecondaryFiles(workflow);
        List<Object> inputParameters = new ArrayList<>();
        workflow.getInputs().forEach(input -> {
            Object secondaryFiles = input.getSecondaryFiles();
            if (secondaryFiles != null) {
                inputParameters.add(secondaryFiles);
            }
        });
        assertEquals(1, inputParameters.size());
        ArrayList inputParameterArray = (ArrayList)inputParameters.get(0);
        assertEquals(5, inputParameterArray.size());
    }
}
