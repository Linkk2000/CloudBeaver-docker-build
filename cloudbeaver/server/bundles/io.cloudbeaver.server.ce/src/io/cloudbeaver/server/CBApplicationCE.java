/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2024 DBeaver Corp and others
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.cloudbeaver.server;

import io.cloudbeaver.auth.NoAuthCredentialsProvider;
import io.cloudbeaver.model.CBWebServerConfig;
import io.cloudbeaver.model.WebServerConfig;
import io.cloudbeaver.model.config.CBServerConfig;
import io.cloudbeaver.model.rm.local.LocalResourceController;
import io.cloudbeaver.service.security.CBEmbeddedSecurityController;
import io.cloudbeaver.service.security.EmbeddedSecurityControllerFactory;
import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.model.DBFileController;
import org.jkiss.dbeaver.model.app.DBPWorkspace;
import org.jkiss.dbeaver.model.auth.AuthInfo;
import org.jkiss.dbeaver.model.auth.SMCredentialsProvider;
import org.jkiss.dbeaver.model.rm.RMController;
import org.jkiss.dbeaver.model.security.SMAdminController;
import org.jkiss.dbeaver.model.security.SMController;
import org.jkiss.dbeaver.registry.LocalFileController;
import org.jkiss.dbeaver.runtime.DBWorkbench;

import java.util.List;

public class CBApplicationCE extends CBApplication<CBServerConfig> {
    private static final Log log = Log.getLog(CBApplicationCE.class);

    private final CBServerConfigurationControllerEmbedded<CBServerConfig> serverConfigController;

    public CBApplicationCE() {
        super();
        this.serverConfigController = new CBServerConfigurationControllerEmbedded<>(new CBServerConfig(), getHomeDirectory());
    }

    @Override
    public SMController createSecurityController(@NotNull SMCredentialsProvider credentialsProvider) throws DBException {
        return new EmbeddedSecurityControllerFactory<>().createSecurityService(
            this,
            getServerConfiguration().getDatabaseConfiguration(),
            credentialsProvider,
            getServerConfiguration().getSecurityManagerConfiguration()
        );
    }
    @Override
    public SMAdminController getAdminSecurityController(@NotNull SMCredentialsProvider credentialsProvider) throws DBException {
        return new EmbeddedSecurityControllerFactory<>().createSecurityService(
            this,
            getServerConfiguration().getDatabaseConfiguration(),
            credentialsProvider,
            getServerConfiguration().getSecurityManagerConfiguration()
        );
    }

    protected SMAdminController createGlobalSecurityController() throws DBException {
        return new EmbeddedSecurityControllerFactory<>().createSecurityService(
            this,
            getServerConfiguration().getDatabaseConfiguration(),
            new NoAuthCredentialsProvider(),
            getServerConfiguration().getSecurityManagerConfiguration()
        );
    }



    @Override
    public RMController createResourceController(@NotNull SMCredentialsProvider credentialsProvider,
                                                 @NotNull DBPWorkspace workspace) throws DBException {
        return LocalResourceController.builder(credentialsProvider, workspace, this::getSecurityController).build();
    }

    @NotNull
    @Override
    public DBFileController createFileController(@NotNull SMCredentialsProvider credentialsProvider) {
        return new LocalFileController(DBWorkbench.getPlatform().getWorkspace().getAbsolutePath().resolve(DBFileController.DATA_FOLDER));
    }

    @Override
    public CBServerConfigurationControllerEmbedded<CBServerConfig> getServerConfigurationController() {
        return serverConfigController;
    }

    protected void shutdown() {
        try {
            if (securityController instanceof CBEmbeddedSecurityController<?> embeddedSecurityController) {
                embeddedSecurityController.shutdown();
            }
        } catch (Exception e) {
            log.error(e);
        }
        super.shutdown();
    }

    protected void finishSecurityServiceConfiguration(
        @NotNull String adminName,
        @Nullable String adminPassword,
        @NotNull List<AuthInfo> authInfoList
    ) throws DBException {
        if (securityController instanceof CBEmbeddedSecurityController<?> embeddedSecurityController) {
            embeddedSecurityController.finishConfiguration(adminName, adminPassword, authInfoList);
        }
    }
}
