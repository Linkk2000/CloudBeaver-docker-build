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
package org.jkiss.dbeaver.ui.editors;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.osgi.util.NLS;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.IPersistableElement;
import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.ModelPreferences;
import org.jkiss.dbeaver.model.*;
import org.jkiss.dbeaver.model.app.DBPProject;
import org.jkiss.dbeaver.model.edit.DBECommandContext;
import org.jkiss.dbeaver.model.exec.DBCExecutionContext;
import org.jkiss.dbeaver.model.exec.DBExecUtils;
import org.jkiss.dbeaver.model.navigator.DBNDataSource;
import org.jkiss.dbeaver.model.navigator.DBNDatabaseNode;
import org.jkiss.dbeaver.model.navigator.DBNModel;
import org.jkiss.dbeaver.model.navigator.DBNNode;
import org.jkiss.dbeaver.model.preferences.DBPPropertySource;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSInstance;
import org.jkiss.dbeaver.model.struct.DBSInstanceLazy;
import org.jkiss.dbeaver.model.struct.DBSObject;
import org.jkiss.dbeaver.runtime.DBWorkbench;
import org.jkiss.dbeaver.runtime.properties.PropertySourceCustom;
import org.jkiss.dbeaver.ui.DBeaverIcons;
import org.jkiss.dbeaver.ui.UITask;
import org.jkiss.dbeaver.ui.UIUtils;
import org.jkiss.dbeaver.ui.dialogs.ConnectionLostDialog;
import org.jkiss.dbeaver.ui.editors.internal.EditorsMessages;
import org.jkiss.dbeaver.utils.GeneralUtils;
import org.jkiss.dbeaver.utils.RuntimeUtils;
import org.jkiss.utils.CommonUtils;

import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

/**
 * Lazy input. Use by entity editors which are created during DBeaver startup (from memo by factory).
 */
public class DatabaseLazyEditorInput implements IDatabaseEditorInput, ILazyEditorInput, IPersistableElement, DBPDataSourceContainerProvider
{
    private static final Log log = Log.getLog(DatabaseLazyEditorInput.class);

    private final String nodePath;
    private final String nodeName;
    private final String activePageId;
    private final String activeFolderId;
    private final String dataSourceId;
    private final String inputClass;
    private final boolean canLoadImmediately;

    // Initialized on demand
    @Nullable
    private DBPProject project;
    private DBPDataSourceContainer dataSourceContainer;

    public DatabaseLazyEditorInput(IMemento memento) {
        inputClass = memento.getString(DatabaseEditorInputFactory.TAG_CLASS);
        nodePath = memento.getString(DatabaseEditorInputFactory.TAG_NODE);
        String nodeName = memento.getString(DatabaseEditorInputFactory.TAG_NODE_NAME);
        String projectName = memento.getString(DatabaseEditorInputFactory.TAG_PROJECT);
        project = CommonUtils.isEmpty(projectName) ? null : DBWorkbench.getPlatform().getWorkspace().getProject(projectName);
        dataSourceId = memento.getString(DatabaseEditorInputFactory.TAG_DATA_SOURCE);
        if (nodePath == null || inputClass == null || dataSourceId == null) {
            log.error("Corrupted memento"); //$NON-NLS-2$
        }
        activePageId = memento.getString(DatabaseEditorInputFactory.TAG_ACTIVE_PAGE);
        activeFolderId = memento.getString(DatabaseEditorInputFactory.TAG_ACTIVE_FOLDER);

        if (nodeName == null && nodePath != null) {
            int divPos = nodePath.lastIndexOf('/');
            nodeName = divPos == -1 ? nodePath : nodePath.substring(divPos + 1);
        }

        this.nodeName = nodeName;
        this.canLoadImmediately = true;
    }

    public DatabaseLazyEditorInput(
        String nodePath,
        String nodeName,
        String activePageId,
        String activeFolderId,
        String dataSourceId,
        String inputClass,
        @Nullable DBPProject project,
        @Nullable DBPDataSourceContainer dataSourceContainer,
        boolean canLoadImmediately
    ) {
        this.nodePath = nodePath;
        this.nodeName = nodeName;
        this.activePageId = activePageId;
        this.activeFolderId = activeFolderId;
        this.dataSourceId = dataSourceId;
        this.inputClass = inputClass;
        this.project = project;
        this.dataSourceContainer = dataSourceContainer;
        this.canLoadImmediately = canLoadImmediately;
    }

    @Override
    public boolean exists()
    {
        return false;
    }

    @Override
    public ImageDescriptor getImageDescriptor()
    {
        return DBeaverIcons.getImageDescriptor(DBIcon.TYPE_OBJECT);
    }

    @Override
    public String getName()
    {
        return nodeName;
    }

    public String getNodePath() {
        return nodePath;
    }

    @Override
    public IPersistableElement getPersistable() {
        if (!canLoadImmediately && DBWorkbench.getPlatform().getPreferenceStore().getBoolean(DatabaseEditorPreferences.PROP_SAVE_EDITORS_STATE)) {
            return this;
        } else {
            return null;
        }
    }

    @Override
    public String getToolTipText()
    {
        return nodeName;
    }

    @Override
    public <T> T getAdapter(Class<T> adapter)
    {
        return null;
    }

    @Override
    public DBCExecutionContext getExecutionContext() {
        return null;
    }

    @Override
    public DBNDatabaseNode getNavigatorNode()
    {
        return null;
    }

    @Override
    public DBSObject getDatabaseObject()
    {
        return dataSourceContainer;
    }

    @Override
    public String getDefaultPageId()
    {
        return activePageId;
    }

    @Override
    public String getDefaultFolderId()
    {
        return activeFolderId;
    }

    @Nullable
    @Override
    public DBECommandContext getCommandContext()
    {
        return null;
    }

    @Override
    public Collection<String> getAttributeNames() {
        return Collections.emptyList();
    }

    @Override
    public Object getAttribute(String name) {
        return null;
    }

    @Override
    public Object setAttribute(String name, Object value) {
        return null;
    }

    @Override
    public DBPPropertySource getPropertySource()
    {
        return new PropertySourceCustom();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DatabaseLazyEditorInput that = (DatabaseLazyEditorInput) o;
        return Objects.equals(nodePath, that.nodePath)
            && Objects.equals(activePageId, that.activePageId)
            && Objects.equals(activeFolderId, that.activeFolderId)
            && Objects.equals(dataSourceId, that.dataSourceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodePath, activePageId, activeFolderId, dataSourceId);
    }

    @Nullable
    @Override
    public DBPProject getProject() {
        return project;
    }

    public IDatabaseEditorInput initializeRealInput(@NotNull DBRProgressMonitor monitor) throws DBException
    {
        if (dataSourceId == null) {
            return null;
        }
        // Get the node path.
        if (project != null) {
            dataSourceContainer = project.getDataSourceRegistry().getDataSource(dataSourceId);
        }
        if (dataSourceContainer == null) {
            log.error("Can not find data source '" + dataSourceId + "'"); //$NON-NLS-2$
            return null;
        }
        if (project == null) {
            project = dataSourceContainer.getRegistry().getProject();
        }
        final DBNModel navigatorModel = DBWorkbench.getPlatform().getNavigatorModel();
        long connectionTimeout = dataSourceContainer.getPreferenceStore().getInt(ModelPreferences.CONNECTION_VALIDATION_TIMEOUT);
        long connectionStart = System.currentTimeMillis();
        while (!dataSourceContainer.isConnected()) {
            try {
                dataSourceContainer.connect(monitor, true, true);
            } catch (final DBException e) {
                // Connection error
                final Integer result = new UITask<Integer>() {
                    @Override
                    protected Integer runTask() {
                        ConnectionLostDialog clDialog = new ConnectionLostDialog(UIUtils.getActiveWorkbenchShell(), dataSourceContainer, e, "Close");
                        return clDialog.open();
                    }
                }.execute();
                if (result == IDialogConstants.STOP_ID) {
                    // Close editor
                    return null;
                } else if (result == IDialogConstants.RETRY_ID) {
                    connectionStart = System.currentTimeMillis();
                    continue;
                } else {
                    return new ErrorEditorInput(GeneralUtils.makeExceptionStatus(e), navigatorModel.getNodeByObject(dataSourceContainer));
                }
            }
            if (connectionTimeout > 0 && connectionStart + connectionTimeout <= System.currentTimeMillis()) {
                break;
            }
            // Wait a few seconds to let in-progress connection initialize
            RuntimeUtils.pause(1000);
        }
        try {
            final DBPDataSource dataSource = dataSourceContainer.getDataSource();
            if (dataSource == null) {
                throw new DBException("Connection to '" + dataSourceContainer.getName() + "' canceled");
            }

            final DBNNode[] editorNodeResult = new DBNNode[1];
            DBExecUtils.tryExecuteRecover(monitor, dataSource, param -> {
                try {
                    // FIXME: DBNModel#getNodeByObject should ensure that the project is loaded, not the caller
                    navigatorModel.ensureProjectLoaded(project);
                    DBNDataSource dsNode = (DBNDataSource) navigatorModel.getNodeByObject(monitor, this.dataSourceContainer, true);
                    if (dsNode == null) {
                        throw new DBException("Datasource '" + this.dataSourceContainer.getName() + "' navigator node not found");
                    }

                    dsNode.initializeNode(monitor, null);

                    editorNodeResult[0] = navigatorModel.getNodeByPath(
                        monitor, project, nodePath);
                } catch (Exception e) {
                    throw new InvocationTargetException(e);
                }
            });
            DBNNode node = editorNodeResult[0];
            if (node == null) {
                throw new DBException(NLS.bind(EditorsMessages.lazy_editor_input_cant_find_node, nodePath));
            }
            if (node instanceof DBNDatabaseNode) {
                DBSObject object = ((DBNDatabaseNode) node).getObject();
                DBSInstance instance = DBUtils.getObjectOwnerInstance(object);
                if (instance instanceof DBSInstanceLazy && !((DBSInstanceLazy) instance).isInstanceConnected()) {
                    ((DBSInstanceLazy) instance).checkInstanceConnection(monitor);
                }

                DatabaseNodeEditorInput realInput = new DatabaseNodeEditorInput((DBNDatabaseNode) node);
                realInput.setDefaultFolderId(activeFolderId);
                realInput.setDefaultPageId(activePageId);
                return realInput;
            } else {
                throw new DBException("Database node has bad type: " + node.getClass().getName());
            }
        } catch (DBException e) {
            return new ErrorEditorInput(GeneralUtils.makeExceptionStatus(e), navigatorModel.getNodeByObject(dataSourceContainer));
        }
    }

    @Override
    public boolean canLoadImmediately() {
        return canLoadImmediately;
    }

    @NotNull
    @Override
    public ILazyEditorInput unloadInput() {
        return new DatabaseLazyEditorInput(
            nodePath,
            nodeName,
            activePageId,
            activeFolderId,
            dataSourceId,
            inputClass,
            project,
            dataSourceContainer,
            false
        );
    }

    @Override
    public String getFactoryId() {
        return DatabaseEditorInputFactory.ID_FACTORY;
    }

    @Override
    public void saveState(IMemento memento) {
        if (!DBWorkbench.getPlatform().getPreferenceStore().getBoolean(DatabaseEditorPreferences.PROP_SAVE_EDITORS_STATE)) {
            return;
        }

        if (!CommonUtils.isEmpty(inputClass)) memento.putString(DatabaseEditorInputFactory.TAG_CLASS, inputClass);
        if (project != null) memento.putString(DatabaseEditorInputFactory.TAG_PROJECT, project.getName());
        if (!CommonUtils.isEmpty(dataSourceId)) memento.putString(DatabaseEditorInputFactory.TAG_DATA_SOURCE, dataSourceId);
        if (!CommonUtils.isEmpty(nodePath)) memento.putString(DatabaseEditorInputFactory.TAG_NODE, nodePath);
        if (!CommonUtils.isEmpty(nodeName)) memento.putString(DatabaseEditorInputFactory.TAG_NODE_NAME, nodeName);
        if (!CommonUtils.isEmpty(activePageId)) memento.putString(DatabaseEditorInputFactory.TAG_ACTIVE_PAGE, activePageId);
        if (!CommonUtils.isEmpty(activeFolderId)) memento.putString(DatabaseEditorInputFactory.TAG_ACTIVE_FOLDER, activeFolderId);
    }

    @Nullable
    @Override
    public DBPDataSourceContainer getDataSourceContainer() {
        if (dataSourceContainer != null) {
            return dataSourceContainer;
        }
        if (project != null && project.isRegistryLoaded()) {
            return project.getDataSourceRegistry().getDataSource(dataSourceId);
        }
        return null;
    }
}
