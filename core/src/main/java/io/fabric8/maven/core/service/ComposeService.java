/*
 * Copyright 2016 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */
package io.fabric8.maven.core.service;

import io.fabric8.maven.docker.util.Logger;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.shared.utils.io.FileUtils;
import org.apache.maven.shared.utils.io.IOUtil;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

/*
* Docker compose services for converting docker compose artifacts to kubernetes artifacts
*/
public class ComposeService {

    public static final String KOMPOSE_RESOURCES_DIRECTORY = "kompose_resources";

    private Path komposeResourcesPath;
    private Path composeFilePath;
    private Logger log;
    private Process process;

    /**
     * Create instance of compose service
     *
     * @param composeFilePath
     * @param log
     */
    public ComposeService(Path composeFilePath, Logger log) {
        this.composeFilePath = composeFilePath;
        this.log = log;
    }

    /**
     * Returns array of kubernetes resource descriptor files generated from docker compose files
     * using 'kompose' (http://kompose.io) utility
     *
     * @return array of files if any resources exist. The array will be empty of no resource descriptors found.
     * @throws IOException
     * @throws MojoExecutionException
     */
    public File[] convertToKubeFragments() throws Fabric8ServiceException {
        File[] komposeResourceFiles = {};

        if(composeFilePath != null) {
            log.info("converting docker compose file %s to kubernetes resource descriptors", composeFilePath);
            try {
                initializeKompose();
                invokeKompose();
                komposeResourceFiles = handelKomposeResult();
            } catch (IOException e) {
                throw new Fabric8ServiceException(e);
            }

            log.info("conversion completed successfully : %s resource descriptors generated", komposeResourceFiles.length);
        }

        return komposeResourceFiles;
    }

    private void initializeKompose() throws IOException {
        komposeResourcesPath = Files.createTempDirectory(KOMPOSE_RESOURCES_DIRECTORY);
    }

    private void invokeKompose() throws Fabric8ServiceException {
        try {
            process = Runtime.getRuntime().exec("kompose convert -o "+ komposeResourcesPath +" -f "+ composeFilePath);
        } catch (IOException exp) {
            checkIfKomposeIsMissing(exp);
            throw new Fabric8ServiceException(exp.getMessage(), exp);
        }
        waitForConversion();
    }

    private void checkIfKomposeIsMissing(IOException exp) {
        if(exp.getMessage().contains("error=2")) {
            log.error("[[B]]kompose[[B]] utility doesn't exist, please execute [[B]]mvn fabric8:install[[B]] command to make it work");
            log.error("or");
            log.error("to install it manually, please log on to [[B]]http://kompose.io/installation/[[B]]");
        }
    }

    private File[] handelKomposeResult() throws IOException, Fabric8ServiceException {
        if(process.exitValue() != 0) {
            StringWriter stringWriter = new StringWriter();
            IOUtil.copy(process.getErrorStream(), stringWriter);
            log.error("conversion failed : " + stringWriter.toString());
            throw new Fabric8ServiceException(stringWriter.toString());
        }

        process = null;
        return komposeResourcesPath.toFile().listFiles();
    }

    public void cleanComposeResources() {
        if(komposeResourcesPath == null) {
            return;
        }

        try {
            FileUtils.deleteDirectory(komposeResourcesPath.toFile());
        } catch (IOException e) {
            log.warn("kompose clean up failed: %s", e.getMessage());
        }
    }

    private void waitForConversion() throws Fabric8ServiceException {
        try {
            process.waitFor();
        } catch (InterruptedException e) {
            log.error("kompose process interrupted: %s", e.getMessage());
            throw new Fabric8ServiceException(e);
        }
    }

    public Path getPath() {
        return komposeResourcesPath;
    }
}
