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
package io.dockstore.client.cli.nested.notificationsclients;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.UUID;

import com.google.gson.Gson;
import io.dockstore.client.cli.Client;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author gluu
 * @since 12/01/18
 */
public class NotificationsClient {
    public static final String PROVISION_INPUT = "provision-in";
    public static final String RUN = "workflow-start";
    public static final String PROVISION_OUTPUT = "provision-out";
    public static final String COMPLETED = "workflow-complete";
    protected static final String USERNAME = "Dockstore CLI";
    private static final Logger LOG = LoggerFactory.getLogger(NotificationsClient.class);
    protected String hookURL;
    protected String uuid;
    protected boolean disabled = false;

    public NotificationsClient(String hookURL, String uuid) {
        boolean invalidHookURL = (hookURL == null || hookURL.isEmpty());
        boolean invalidUUID = (uuid == null || uuid.isEmpty());
        if (invalidHookURL) {
            if (!invalidUUID) {
                System.err.println(
                        "Notifications UUID is specified but no notifications webhook URL found in config file.  Aborting launch.");
                System.exit(Client.CLIENT_ERROR);
            }
            disabled = true;

        } else {
            if (invalidUUID) {
                uuid = UUID.randomUUID().toString();
                System.out.println("The UUID generated for this specific execution is " + uuid);
            }
            this.hookURL = hookURL;
            this.uuid = uuid;
        }

    }

    /**
     * Send message using the webhook URL originally provided
     *
     * @param message Base message indicating the step of the tool/workflow
     * @param success The status of the step
     */
    public void sendMessage(String message, boolean success) {
        if (disabled) {
            return;
        } else {
            LOG.info("Sending notifications message.");
            String messageToSend = createText(message, success);
            // Message to be sent
            String jsonMessage;

            if (this.hookURL.contains("://hooks.slack.com")) {
                LOG.warn("Destination is Slack. Message is not 100% compatible.");
            }
            Message messageObject = new Message();
            messageObject.setText(messageToSend);
            messageObject.setUuid(this.uuid);
            Gson gson = new Gson();
            jsonMessage = gson.toJson(messageObject);
            // If there's a message, send it.
            if (jsonMessage != null && !jsonMessage.isEmpty()) {
                generalSendMessage(jsonMessage);
            }
        }
    }

    /**
     * Construct the fail/success text to send
     *
     * @param message The base text which does not include failure/success status
     * @param success Whether the step of the workflow/tool failed/succeeded
     * @return
     */
    private String createText(String message, boolean success) {
        String textToSend = message;
        if (!success) {
            textToSend = "failed-" + message;
        }
        return textToSend;
    }

    /**
     * The general method of sending a message.
     * Currently works for Slack.  Whether it works for others depend on several assumptions:
     * 1. The message is send via HTTP POST
     * 2. The message is send with Content-type: application/json
     * 3. The message is a JSON
     *
     * @param jsonMessage
     */
    private void generalSendMessage(String jsonMessage) {
        HttpPost httpPost = new HttpPost(this.hookURL);

        StringEntity entity = null;
        try {
            entity = new StringEntity(jsonMessage);
        } catch (UnsupportedEncodingException e) {
            LOG.error("Cannot encode json");
        }
        httpPost.setEntity(entity);
        httpPost.setHeader("Accept", "application/json");
        httpPost.setHeader("Content-type", "application/json");
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            CloseableHttpResponse response = client.execute(httpPost);
            if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                LOG.warn("Did not successfully send notification");
            }
        } catch (IOException e) {
            LOG.error("Cannot send jsonMessage");
        }
    }
}
