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

package org.jkiss.dbeaver.ext.gbase8s.edit;

import java.util.List;
import java.util.Map;

import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.ext.gbase8s.GBase8sConstants;
import org.jkiss.dbeaver.ext.generic.edit.GenericTableColumnManager;
import org.jkiss.dbeaver.ext.generic.edit.GenericTableManager;
import org.jkiss.dbeaver.ext.generic.model.GenericDataSource;
import org.jkiss.dbeaver.ext.generic.model.GenericStructContainer;
import org.jkiss.dbeaver.ext.generic.model.GenericTableBase;
import org.jkiss.dbeaver.ext.generic.model.GenericTableColumn;
import org.jkiss.dbeaver.model.DBConstants;
import org.jkiss.dbeaver.model.DBPEvaluationContext;
import org.jkiss.dbeaver.model.DBPScriptObject;
import org.jkiss.dbeaver.model.DBUtils;
import org.jkiss.dbeaver.model.edit.DBECommandContext;
import org.jkiss.dbeaver.model.edit.DBEObjectRenamer;
import org.jkiss.dbeaver.model.edit.DBEPersistAction;
import org.jkiss.dbeaver.model.exec.DBCExecutionContext;
import org.jkiss.dbeaver.model.impl.edit.SQLDatabasePersistAction;
import org.jkiss.dbeaver.model.impl.sql.edit.SQLObjectEditor;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.sql.SQLUtils;
import org.jkiss.utils.CommonUtils;

/**
 * @author Chao Tian
 */
public class GBase8sTableManager extends GenericTableManager implements DBEObjectRenamer<GenericTableBase> {

    @Override
    public boolean canEditObject(GenericTableBase object) {
        return true;
    }

    @Override
    public boolean canRenameObject(GenericTableBase object) {
        return false;
    }

    @Override
    protected boolean isIncludeDropInDDL(GenericTableBase table) {
        return false;
    }

    @Override
    public void renameObject(
            @NotNull DBECommandContext commandContext,
            @NotNull GenericTableBase object,
            @NotNull Map<String, Object> options,
            @Nullable String newName) throws DBException {
        if (object.isView()) {
            throw new DBException("View rename is not supported");
        }
        processObjectRename(commandContext, object, options, newName);
    }

    @Override
    protected void addObjectExtraActions(
            @NotNull DBRProgressMonitor monitor,
            @NotNull DBCExecutionContext executionContext,
            @NotNull List<DBEPersistAction> actions,
            @NotNull NestedObjectCommand<GenericTableBase, PropertyHandler> command,
            @NotNull Map<String, Object> options) throws DBException {
        GenericTableBase tableBase = command.getObject();
        boolean objectSave = CommonUtils.getOption(options, DBPScriptObject.OPTION_OBJECT_SAVE);
        boolean includeComments = CommonUtils.getOption(options, DBPScriptObject.OPTION_INCLUDE_COMMENTS);
        boolean hasDescription = !CommonUtils.isEmpty(tableBase.getDescription());
        // Add table comment if needed
        if ((objectSave && command.hasProperty(DBConstants.PROP_ID_DESCRIPTION)) || hasDescription) {
            addTableCommentAction(actions, tableBase);
        }
        // Add column comments if needed
        if (!tableBase.isPersisted() ? (objectSave || includeComments) : (!objectSave && includeComments)) {
            for (GenericTableColumn column : CommonUtils.safeCollection(tableBase.getAttributes(monitor))) {
                if (!CommonUtils.isEmpty(column.getDescription())) {
                    GenericTableColumnManager.addColumnCommentAction(actions, column, column.getTable());
                }
            }
        }
    }

    @Override
    protected void addObjectRenameActions(
            @NotNull DBRProgressMonitor monitor,
            @NotNull DBCExecutionContext executionContext,
            @NotNull List<DBEPersistAction> actions,
            @NotNull SQLObjectEditor<GenericTableBase, GenericStructContainer>.ObjectRenameCommand command,
            @NotNull Map<String, Object> options) {
        final GenericDataSource dataSource = command.getObject().getDataSource();
        actions.add(
                new SQLDatabasePersistAction("Rename table",
                        "ALTER TABLE "
                                + (command.getObject().getSchema() != null
                                        ? DBUtils.getQuotedIdentifier(dataSource,
                                                command.getObject().getSchema().getName()) + "."
                                        : "")
                                + DBUtils.getQuotedIdentifier(dataSource, command.getOldName()) + " RENAME TO "
                                + DBUtils.getQuotedIdentifier(dataSource, command.getNewName())));
    }

    private void addTableCommentAction(
            @NotNull List<DBEPersistAction> actionList,
            @NotNull GenericTableBase table) {
        String tableName = DBUtils.getObjectFullName(table, DBPEvaluationContext.DDL);
        String commentSQL = String.format(GBase8sConstants.SQL_TABLE_COMMENT, tableName,
                SQLUtils.quoteString(table, CommonUtils.notEmpty(table.getDescription())));
        actionList.add(new SQLDatabasePersistAction("Comment on Table", commentSQL));
    }
}
