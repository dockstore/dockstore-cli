/*
 *    Copyright 2022 OICR and UCSC
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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.dockstore.common.yaml.DockstoreYaml12;
import io.dockstore.common.yaml.DockstoreYamlHelper;
import io.dockstore.common.yaml.Service12;
import io.dockstore.common.yaml.YamlWorkflow;
import org.yaml.snakeyaml.Yaml;

import static io.dockstore.client.cli.ArgumentUtility.out;

/*
    GENERAL STEPS:
    1. Verfiy that YAML syntax is valid
    2. Verify that all fields are named correctly
    3. Verify that all required fields are present
    4. Verify that all files exist
 */

public final class YamlVerify {

    public static final String ERROR_MESSAGE = "Your .dockstore.yml is invalid:\n";

    public static final String DOCKSTOREYML = ".dockstore.yml";


    private YamlVerify() {
    }
    // Determines if all the files referenced in a list of strings exist
    private static List<String> filesExist(List<String> paths, String base) {
        List<String> missingFiles = new ArrayList<String>();
        for (String path : paths) {
            Path pathToFile = Paths.get(base, path);
            if (!Files.exists(pathToFile)) {
                missingFiles.add(pathToFile.toString() + " does not exist");
            }
        }
        return missingFiles;
    }

    // Determines if all the files in dockstoreYaml12 exist
    private static void allFilesExist(DockstoreYaml12 dockstoreYaml12, String basePath) throws ValidateYamlException {
        List<String> missingFiles = new ArrayList<String>();

        // Check Workflows
        List<YamlWorkflow> workflows = dockstoreYaml12.getWorkflows();
        if (workflows != null) {
            for (YamlWorkflow workflow : workflows) {
                List<String> filePaths = workflow.getTestParameterFiles();
                filePaths.add(workflow.getPrimaryDescriptorPath());
                missingFiles.addAll(filesExist(filePaths, basePath));
            }
        }
        // Check Tools
        List<YamlWorkflow> tools = dockstoreYaml12.getTools();
        if (tools != null) {
            for (YamlWorkflow tool : tools) {
                List<String> filePaths = tool.getTestParameterFiles();
                filePaths.add(tool.getPrimaryDescriptorPath());
                missingFiles.addAll(filesExist(filePaths, basePath));
            }
        }

        // Check service
        Service12 service = dockstoreYaml12.getService();
        if (service != null) {
            missingFiles.addAll(filesExist(service.getFiles(), basePath));
        }
        if (!missingFiles.isEmpty()) {
            String errorMsg = "";
            for (String missingFile: missingFiles) {
                errorMsg += missingFile + '\n';
            }
            throw new ValidateYamlException("Your file structure has the following errors:\n" + errorMsg);
        }
    }

    public static void dockstoreValidate(String path) throws ValidateYamlException {
        Path workflowPath = Paths.get(path);

        // Verify that the path is a valid directory
        if (!Files.isDirectory(workflowPath)) {
            throw new ValidateYamlException(ERROR_MESSAGE + workflowPath.toString() + " does not exist");
        }

        // Verify that .dockstore.yml exists
        Path dockstoreYmlPath = Paths.get(path, DOCKSTOREYML);
        if (!Files.exists(dockstoreYmlPath)) {
            throw new ValidateYamlException(ERROR_MESSAGE + dockstoreYmlPath.toString() + " does not exist");
        }

        // Verify that .dockstore.yml is non-empty
        File dockstoreYml = new File(dockstoreYmlPath.toString());
        if (dockstoreYml.length() == 0) {
            throw new ValidateYamlException(ERROR_MESSAGE + dockstoreYmlPath.toString() + " is empty");
        }

        // Load dockstoreYml into string
        String dockstoreYmlString = "";
        try {
            dockstoreYmlString = Files.readString(dockstoreYmlPath);
        } catch (IOException ex) {
            // Unlikely to ever catch
            throw new ValidateYamlException("Error reading " + dockstoreYmlPath.toString() + " :\n" + ex.getMessage());
        }

        // Verify that the Yaml is valid, however does not verify it is valid for dockstore
        Yaml yaml = new Yaml();
        Map<String, Object> data = null;
        try {
            data = yaml.load(Files.readString(dockstoreYmlPath));
            out(dockstoreYmlPath + " is a valid yaml file");
        } catch (Exception ex) {
            throw new ValidateYamlException(ERROR_MESSAGE + dockstoreYmlPath.toString() + " is not a valid yaml file:\n" + ex.getMessage());
        }

        // Running validate first, as if readAsDockstoreYaml12 is run first and a parameter is incorrect it is difficult to understand
        // Determine if any of the parameters names are incorrect (does not verify if all the parameters are there)
        try {
            DockstoreYamlHelper.validateDockstoreYamlProperties(dockstoreYmlString);
        } catch (Exception ex) {
            throw new ValidateYamlException(ERROR_MESSAGE + dockstoreYmlPath.toString() + " has the following errors:\n" + ex.getMessage());
        }

        // Validates file dockstoreYaml in general
        DockstoreYaml12 dockstoreYaml12 = null;
        try {
            dockstoreYaml12 = DockstoreYamlHelper.readAsDockstoreYaml12(dockstoreYmlString);
        } catch (Exception ex) {
            throw new ValidateYamlException(ERROR_MESSAGE + dockstoreYmlPath.toString() + " has the following errors:\n" + ex.getMessage());
        }

        // Determines if all referenced files exist
        allFilesExist(dockstoreYaml12, workflowPath.toString());

        out(dockstoreYmlPath + " is a valid dockstore yaml file");

    }


    public static class ValidateYamlException extends Exception {
        public ValidateYamlException(final String msg) {
            super(msg);
        }
    }
}
