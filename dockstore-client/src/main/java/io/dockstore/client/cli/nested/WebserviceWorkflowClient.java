package io.dockstore.client.cli.nested;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import io.dockstore.client.cli.Client;
import io.dockstore.openapi.client.model.WorkflowSubClass;
import io.swagger.client.ApiException;
import io.swagger.client.api.WorkflowsApi;
import io.swagger.client.model.Workflow;

import static io.dockstore.client.cli.ArgumentUtility.errorMessage;

public class WebserviceWorkflowClient {
    private WorkflowsApi workflowsApi;
    private String include;
    private boolean searchUnauthenticated;
    private boolean searchAppTool;
    private boolean foundAppTool = false;


    public WebserviceWorkflowClient(WorkflowsApi workflowsApi, String include, boolean searchUnauthenticated, boolean searchAppTool) {
        this.workflowsApi = workflowsApi;
        this.include = include;
        this.searchUnauthenticated = searchUnauthenticated;
        this.searchAppTool = searchAppTool;
    }

    // TODO: Catch non-404 exceptions
    /**
     * Try and get the workflow with the path (unauthenticated/authenticated bioworkflow, unauthenticated/authenticated apptool)
     *
     * @param entryPath Path of the apptool or bioworkflow
     * @return
     */
    public Workflow findAndGetDockstoreWorkflowByPath(String entryPath) {
        List<Supplier<Workflow>> workflows = new ArrayList<>();
        if (searchUnauthenticated) {
            workflows.add(getDockstoreBioworkflowByPath(entryPath));
            if (searchAppTool) {
                workflows.add(getDockstoreAppToolByPath(entryPath));
            }
        }
        workflows.add(getAuthenticatedDockstoreBioworkflowByPath(entryPath));
        if (searchAppTool) {
            workflows.add(getAuthenticatedDockstoreAppToolByPath(entryPath));
        }
        Workflow workflow = workflows.stream().map(Supplier::get).filter(Objects::nonNull).findFirst().orElse(null);
        if (workflow == null) {
            errorMessage("No workflow found with path " + entryPath, Client.ENTRY_NOT_FOUND);
            return null;
        }
        return workflow;
    }

    private Supplier<Workflow> getDockstoreBioworkflowByPath(String entryPath) {
        return () -> getDockstoreWorkflowByPath(WorkflowSubClass.BIOWORKFLOW, entryPath);
    }

    private Supplier<Workflow> getAuthenticatedDockstoreBioworkflowByPath(String entryPath) {
        return () -> getAuthenticatedDockstoreWorkflowByPath(WorkflowSubClass.BIOWORKFLOW, entryPath);
    }

    private Supplier<Workflow> getDockstoreAppToolByPath(String entryPath) {
        return () -> getDockstoreWorkflowByPath(WorkflowSubClass.APPTOOL, entryPath);
    }

    private Supplier<Workflow> getAuthenticatedDockstoreAppToolByPath(String entryPath) {
        return () -> getAuthenticatedDockstoreWorkflowByPath(WorkflowSubClass.APPTOOL, entryPath);
    }

    private Workflow getDockstoreWorkflowByPath(WorkflowSubClass workflowSubClass, String entryPath) {
        try {
            Workflow workflow = workflowsApi.getPublishedWorkflowByPath(entryPath, workflowSubClass.toString(), include, null);
            if (workflowSubClass.equals(WorkflowSubClass.APPTOOL)) {
                this.setFoundAppTool(true);
            }
            return workflow;
        } catch (ApiException e) {
            return null;
        }
    }

    private Workflow getAuthenticatedDockstoreWorkflowByPath(WorkflowSubClass workflowSubClass, String entryPath) {
        try {
            Workflow workflow = workflowsApi.getWorkflowByPath(entryPath, workflowSubClass.toString(), include);
            if (workflowSubClass.equals(WorkflowSubClass.APPTOOL)) {
                this.setFoundAppTool(true);
            }
            return workflow;
        } catch (ApiException e) {
            return null;
        }
    }

    public boolean isFoundAppTool() {
        return foundAppTool;
    }

    public void setFoundAppTool(boolean foundAppTool) {
        this.foundAppTool = foundAppTool;
    }
}
