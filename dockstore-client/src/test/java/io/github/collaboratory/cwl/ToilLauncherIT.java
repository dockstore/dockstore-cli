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

import io.dockstore.common.ToilOnlyTest;
import org.apache.commons.io.FileUtils;
import org.junit.experimental.categories.Category;
import org.junit.jupiter.api.Tag;

/**
 * @author dyuen
 */
@Tag(ToilOnlyTest.NAME)
@Category(ToilOnlyTest.class)
public class ToilLauncherIT extends LauncherIT {

    public String getConfigFile() {
        return FileUtils.getFile("src", "test", "resources", "launcher.toil.ini").getAbsolutePath();
    }

    @Override
    public String getConfigFileWithExtraParameters() {
        return FileUtils.getFile("src", "test", "resources", "launcher.toil.extra.ini").getAbsolutePath();
    }
}
