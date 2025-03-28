/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2025 DBeaver Corp and others
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
package org.jkiss.dbeaver.registry.driver;

import com.google.gson.stream.JsonWriter;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.model.connection.DBPDriverLibrary;
import org.jkiss.dbeaver.model.connection.DBPDriverLoader;
import org.jkiss.dbeaver.model.connection.DBPNativeClientLocation;
import org.jkiss.dbeaver.model.data.json.JSONUtils;
import org.jkiss.dbeaver.registry.DataSourceProviderDescriptor;
import org.jkiss.dbeaver.registry.RegistryConstants;
import org.jkiss.utils.CommonUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

/**
 * DriverDescriptorSerializerModern
 */
public class DriverDescriptorSerializerModern extends DriverDescriptorSerializer {

    private static final Log log = Log.getLog(DriverDescriptorSerializerModern.class);

    public static final String DRIVERS_FILE_NAME = "drivers-configuration.json"; //$NON-NLS-1$

    @Override
    void serializeDrivers(OutputStream os, List<DataSourceProviderDescriptor> providers) throws IOException {

    }

    public void serializeDriver(JsonWriter json, DriverDescriptor driver, boolean export) throws IOException {
        Map<String, String> pathSubstitutions = getPathSubstitutions();

        json.name(driver.getId());
        json.beginObject();

        {
            if (export) {
                JSONUtils.fieldNE(json, RegistryConstants.ATTR_PROVIDER, driver.getProviderDescriptor().getId());
            }
            JSONUtils.field(json, RegistryConstants.ATTR_ID, driver.getId());
            JSONUtils.field(json, RegistryConstants.ATTR_NAME, driver.getName());
            JSONUtils.field(json, RegistryConstants.ATTR_CLASS, driver.getDriverClassName());
            JSONUtils.fieldNE(json, RegistryConstants.ATTR_URL, driver.getSampleURL());
            JSONUtils.fieldNE(json, RegistryConstants.ATTR_PORT, driver.getDefaultPort());
            JSONUtils.fieldNE(json, RegistryConstants.ATTR_DESCRIPTION, driver.getDescription());

            JSONUtils.fieldNE(json, RegistryConstants.ATTR_CATEGORIES, String.join(",", driver.getCategories()));
            JSONUtils.field(json, RegistryConstants.ATTR_CUSTOM, driver.isCustom());
            JSONUtils.field(json, RegistryConstants.ATTR_EMBEDDED, driver.isEmbedded());
            JSONUtils.field(json, RegistryConstants.ATTR_PROPAGATE_DRIVER_PROPERTIES, driver.isPropagateDriverProperties());
            JSONUtils.field(json, RegistryConstants.ATTR_ANONYMOUS, driver.isAnonymousAccess());
            JSONUtils.field(json, "allowsEmptyPassword", driver.isAnonymousAccess());
            JSONUtils.field(json, RegistryConstants.ATTR_INSTANTIABLE, driver.isInstantiable());
            if (driver.isCustomDriverLoader()) {
                JSONUtils.field(json, RegistryConstants.ATTR_CUSTOM_DRIVER_LOADER, driver.isCustomDriverLoader());
            }
            if (driver.isDisabled()) {
                JSONUtils.field(json, RegistryConstants.ATTR_DISABLED, true);
            }
            if (!CommonUtils.isEmpty(driver.getCategory())) {
                JSONUtils.fieldNE(json, RegistryConstants.ATTR_CATEGORY, driver.getCategory());
            }

            if (!CommonUtils.isEmpty(driver.getDriverLibraries())) {
                json.name("libraries");
                json.beginObject();
                // Libraries
                for (DBPDriverLibrary lib : driver.getDriverLibraries()) {
                    if (export && !lib.isDisabled()) {
                        continue;
                    }
                    {
                        json.name(substitutePathVariables(pathSubstitutions, lib.getPath()));
                        json.beginObject();
                        JSONUtils.fieldNE(json, RegistryConstants.ATTR_TYPE, lib.getType().name());
                        JSONUtils.field(json, RegistryConstants.ATTR_CUSTOM, lib.isCustom());
                        if (lib.isEmbedded()) {
                            JSONUtils.field(json, RegistryConstants.ATTR_EMBEDDED, true);
                        }
                        if (lib.isDisabled()) {
                            JSONUtils.field(json, RegistryConstants.ATTR_DISABLED, true);
                        }
                        if (!CommonUtils.isEmpty(lib.getPreferredVersion())) {
                            JSONUtils.field(json, RegistryConstants.ATTR_VERSION, lib.getPreferredVersion());
                        }

                        if (!export) {
                            for (DBPDriverLoader driverLoader : driver.getAllDriverLoaders()) {
                                if (!(driverLoader instanceof DriverLoaderDescriptor dld)) {
                                    continue;
                                }
                                List<DriverFileInfo> files = dld.getResolvedFiles().get(lib);
                                if (!CommonUtils.isEmpty(files)) {
                                    json.name("files");
                                    json.beginObject();

                                    for (DriverFileInfo file : files) {
                                        {
                                            if (file.getFile() == null) {
                                                log.warn("File missing in " + file.getId());
                                                continue;
                                            }
                                            json.name(file.getId());
                                            json.beginObject();
                                            if (!CommonUtils.isEmpty(file.getVersion())) {
                                                JSONUtils.field(json, RegistryConstants.ATTR_VERSION, file.getVersion());
                                            }
                                            JSONUtils.field(
                                                json,
                                                RegistryConstants.ATTR_PATH,
                                                substitutePathVariables(pathSubstitutions, file.getFile().toAbsolutePath().toString())
                                            );
                                            json.endObject();
                                        }
                                    }
                                    json.endObject();
                                }
                            }
                        }
                        json.endObject();
                    }
                }
                json.endObject();
            }

            // Client homes
            if (!CommonUtils.isEmpty(driver.getNativeClientHomes())) {
                json.name("native-clients");
                json.beginObject();
                for (DBPNativeClientLocation location : driver.getNativeClientHomes()) {
                    json.name(location.getName());
                    json.beginObject();
                    if (location.getPath() != null) {
                        JSONUtils.fieldNE(json, RegistryConstants.ATTR_PATH, location.getPath().getAbsolutePath());
                    }
                    json.endObject();
                }
                json.endObject();
            }

            // Parameters
            if (!CommonUtils.isEmpty(driver.getCustomParameters())) {
                json.name("driver-parameters");
                json.beginObject();
                for (Map.Entry<String, Object> paramEntry : driver.getCustomParameters().entrySet()) {
                    if (driver.isCustom() || !CommonUtils.equalObjects(paramEntry.getValue(), driver.getDefaultParameters().get(paramEntry.getKey()))) {
                        json.name(paramEntry.getKey());
                        json.value(CommonUtils.toString(paramEntry.getValue()));
                    }
                }
                json.endObject();
            }

            // Properties
            if (!CommonUtils.isEmpty(driver.getConnectionProperties())) {
                json.name("connection-properties");
                json.beginObject();
                for (Map.Entry<String, Object> propEntry : driver.getConnectionProperties().entrySet()) {
                    if (!CommonUtils.equalObjects(propEntry.getValue(), driver.getDefaultConnectionProperties().get(propEntry.getKey()))) {
                        json.name(CommonUtils.toString(propEntry.getKey()));
                        json.value(CommonUtils.toString(propEntry.getValue()));
                    }
                }
                json.endObject();
            }
        }
        json.endObject();
    }

}
