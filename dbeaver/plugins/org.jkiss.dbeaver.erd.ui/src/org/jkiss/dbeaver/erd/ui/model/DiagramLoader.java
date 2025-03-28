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
/*
 * Created on Jul 13, 2004
 */
package org.jkiss.dbeaver.erd.ui.model;

import org.eclipse.draw2d.AbsoluteBendpoint;
import org.eclipse.draw2d.Bendpoint;
import org.eclipse.draw2d.IFigure;
import org.eclipse.draw2d.RelativeBendpoint;
import org.eclipse.draw2d.geometry.Rectangle;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.resource.StringConverter;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.*;
import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.Log;
import org.jkiss.dbeaver.erd.model.*;
import org.jkiss.dbeaver.erd.ui.ERDUIConstants;
import org.jkiss.dbeaver.erd.ui.editor.ERDThemeSettings;
import org.jkiss.dbeaver.erd.ui.part.*;
import org.jkiss.dbeaver.model.*;
import org.jkiss.dbeaver.model.app.DBPProject;
import org.jkiss.dbeaver.model.navigator.DBNDataSource;
import org.jkiss.dbeaver.model.navigator.DBNNode;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.runtime.VoidProgressMonitor;
import org.jkiss.dbeaver.model.struct.*;
import org.jkiss.dbeaver.runtime.DBWorkbench;
import org.jkiss.dbeaver.ui.DBeaverIcons;
import org.jkiss.dbeaver.ui.SharedFonts;
import org.jkiss.dbeaver.ui.UIUtils;
import org.jkiss.dbeaver.ui.controls.ListContentProvider;
import org.jkiss.dbeaver.ui.controls.ViewerColumnController;
import org.jkiss.dbeaver.ui.dialogs.BaseDialog;
import org.jkiss.dbeaver.utils.GeneralUtils;
import org.jkiss.dbeaver.utils.RuntimeUtils;
import org.jkiss.utils.CommonUtils;
import org.jkiss.utils.xml.XMLBuilder;
import org.jkiss.utils.xml.XMLException;
import org.jkiss.utils.xml.XMLUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.util.List;
import java.util.*;

/**
 * Entity diagram loader/saver
 * @author Serge Rider
 */
public class DiagramLoader extends ERDPersistedState {
    private static final Log log = Log.getLog(DiagramLoader.class);

    private static class ElementSaveInfo {
        final ERDElement element;
        final NodePart nodePart;
        final int objectId;

        private ElementSaveInfo(ERDElement element, NodePart nodePart, int objectId)
        {
            this.element = element;
            this.nodePart = nodePart;
            this.objectId = objectId;
        }
    }

    private static class ElementLoadInfo {
        final String objectId;
        final DBSEntity entity;
        final ERDNote note;
        final EntityDiagram.NodeVisualInfo visualInfo;

        private ElementLoadInfo(String objectId, DBSEntity entity, ERDNote note, EntityDiagram.NodeVisualInfo visualInfo)
        {
            this.objectId = objectId;
            this.entity = entity;
            this.note = note;
            this.visualInfo = visualInfo;
        }
    }

    private static class RelationLoadInfo {
        final String name;
        final String type;
        final ElementLoadInfo pkTable;
        final ElementLoadInfo fkTable;
        final Map<String, String> columns = new LinkedHashMap<>();
        final List<int[]> bends = new ArrayList<>();

        private RelationLoadInfo(String name, String type, ElementLoadInfo pkTable, ElementLoadInfo fkTable)
        {
            this.name = name;
            this.type = type;
            this.pkTable = pkTable;
            this.fkTable = fkTable;
        }
    }

    private static class DataSourceObjects {
        List<ERDEntity> entities = new ArrayList<>();
    }

    public static void load(DBRProgressMonitor monitor, DBPProject projectMeta, DiagramPart diagramPart, Reader reader)
        throws XMLException, DBException
    {
        monitor.beginTask("Parse diagram", 1);
        final EntityDiagram diagram = diagramPart.getDiagram();

        final Document document = XMLUtils.parseDocument(reader);
        monitor.done();

        loadDiagram(monitor, document, projectMeta, diagram);
    }

    public static void loadDiagram(DBRProgressMonitor monitor, Document document, DBPProject projectMeta, EntityDiagram diagram) throws DBException {
        final Element diagramElem = document.getDocumentElement();

        // Check version
        final String diagramVersion = diagramElem.getAttribute(ATTR_VERSION);
        if (CommonUtils.isEmpty(diagramVersion)) {
            throw new DBException("Diagram version not found");
        }
        if (!diagramVersion.equals(String.valueOf(ERD_VERSION_1))) {
            throw new DBException("Unsupported diagram version: " + diagramVersion);
        }

        List<ElementLoadInfo> tableInfos = new ArrayList<>();
        List<RelationLoadInfo> relInfos = new ArrayList<>();
        Map<String, ElementLoadInfo> elementMap = new HashMap<>();

        final Element entitiesElem = XMLUtils.getChildElement(diagramElem, TAG_ENTITIES);
        if (entitiesElem != null) {
            final List<Element> dataSourceElements = XMLUtils.getChildElementList(entitiesElem, TAG_DATA_SOURCE);
            final Map<String, DBPDataSourceContainer> containers = new LinkedHashMap<>();

            // Collect data sources
            for (Element element : dataSourceElements) {
                final String id = element.getAttribute(ATTR_ID);
                if (CommonUtils.isEmpty(id)) {
                    continue;
                }
                containers.put(id, projectMeta.getDataSourceRegistry().getDataSource(id));
            }

            // Check for missing data sources in the project
            final String[] nonExistingDataSourceIds = containers.entrySet().stream()
                .filter(entry -> entry.getValue() == null)
                .map(Map.Entry::getKey)
                .toArray(String[]::new);

            if (nonExistingDataSourceIds.length > 0) {
                UIUtils.syncExec(() -> {
                    final ChooseDataSourcesDialog dialog = new ChooseDataSourcesDialog(
                        UIUtils.getActiveWorkbenchWindow().getShell(),
                        projectMeta,
                        nonExistingDataSourceIds
                    );

                    if (dialog.open() == IDialogConstants.OK_ID) {
                        containers.putAll(dialog.containers);
                        diagram.setDirty(true);
                    }
                });
            }

            // Parse data source
            for (Element dsElem : dataSourceElements) {
                String dsId = dsElem.getAttribute(ATTR_ID);
                if (CommonUtils.isEmpty(dsId)) {
                    log.warn("Missing datasource ID");
                    continue;
                }
                // Get connected datasource
                final DBPDataSourceContainer dataSourceContainer = containers.get(dsId);
                if (dataSourceContainer == null) {
                    log.warn("Datasource '" + dsId + "' not found");
                    continue;
                }
                if (!dataSourceContainer.isConnected()) {
                    monitor.subTask("Connect to '" + dataSourceContainer.getName() + "'");
                    try {
                        dataSourceContainer.connect(monitor, true, true);
                    } catch (DBException e) {
                        log.debug(e);
                        diagram.addErrorMessage("Can't connect to '" + dataSourceContainer.getName() + "': " + e.getMessage());
                        continue;
                    }
                }
                final DBPDataSource dataSource = dataSourceContainer.getDataSource();
                if (!(dataSource instanceof DBSObjectContainer)) {
                    diagram.addErrorMessage("Datasource '" + dataSourceContainer.getName() + "' entities cannot be loaded - no entity container found");
                    continue;
                }
                DBSObjectContainer rootContainer = (DBSObjectContainer)dataSource;
                // Parse entities
                Collection<Element> entityElemList = XMLUtils.getChildElementList(dsElem, TAG_ENTITY);
                monitor.beginTask("Parse entities", entityElemList.size());
                for (Element entityElem : entityElemList) {
                    String tableId = entityElem.getAttribute(ATTR_ID);
                    String tableName = entityElem.getAttribute(ATTR_NAME);
                    monitor.subTask("Load " + tableName);
                    List<String> path = new ArrayList<>();
                    for (Element pathElem : XMLUtils.getChildElementList(entityElem, TAG_PATH)) {
                        path.add(0, pathElem.getAttribute(ATTR_NAME));
                    }
                    DBSObjectContainer container = rootContainer;
                    for (String conName : path) {
                        final DBSObject child = container.getChild(monitor, conName);
                        if (child == null) {
                            diagram.addErrorMessage("Container '" + conName + "' not found within '" + container.getName() + "'. Skip table '" + tableName + "'.");
                            container = null;
                            break;
                        }
                        if (child instanceof DBSObjectContainer) {
                            container = (DBSObjectContainer) child;
                        } else {
                            diagram.addErrorMessage("Object '" + child.getName() + "' is not a container");
                            container = null;
                            break;
                        }
                    }
                    if (container == null) {
                        continue;
                    }
                    final DBSObject child = container.getChild(monitor, tableName);
                    if (!(child instanceof DBSEntity table)) {
                        log.debug("Cannot find table '" + tableName + "' in '" + container.getName() + "'");
                        continue;
                    }
                    String locX = entityElem.getAttribute(ATTR_X);
                    String locY = entityElem.getAttribute(ATTR_Y);

                    EntityDiagram.NodeVisualInfo visualInfo = new EntityDiagram.NodeVisualInfo();

                    visualInfo.initBounds = new Rectangle();
                    if (CommonUtils.isEmpty(locX) || CommonUtils.isEmpty(locY)) {
                        diagram.setNeedsAutoLayout(true);
                    } else {
                        visualInfo.initBounds.x = Integer.parseInt(locX);
                        visualInfo.initBounds.y = Integer.parseInt(locY);
                    }
                    String attrVis = entityElem.getAttribute(ATTR_ATTRIBUTE_VISIBILITY);
                    if (!CommonUtils.isEmpty(attrVis)) {
                        visualInfo.attributeVisibility = ERDAttributeVisibility.valueOf(attrVis);
                    }
                    UIUtils.syncExec(() -> loadNodeVisualInfo(entityElem, visualInfo));

                    ElementLoadInfo info = new ElementLoadInfo(tableId, table, null, visualInfo);
                    tableInfos.add(info);
                    elementMap.put(info.objectId, info);

                    diagram.addVisualInfo(table, info.visualInfo);
                    monitor.worked(1);
                }
                monitor.done();
            }
        }

        // Load notes
        final Element notesElem = XMLUtils.getChildElement(diagramElem, TAG_NOTES);
        if (notesElem != null) {
            // Parse relations
            Collection<Element> noteElemList = XMLUtils.getChildElementList(notesElem, TAG_NOTE);
            monitor.beginTask("Parse notes", noteElemList.size());
            for (Element noteElem : noteElemList) {
                final String noteText = XMLUtils.getElementBody(noteElem);
                ERDNote note = new ERDNote(noteText);
                diagram.addNote(note, false);
                String noteId = noteElem.getAttribute(ATTR_ID);
                String locX = noteElem.getAttribute(ATTR_X);
                String locY = noteElem.getAttribute(ATTR_Y);
                String locW = noteElem.getAttribute(ATTR_W);
                String locH = noteElem.getAttribute(ATTR_H);
                EntityDiagram.NodeVisualInfo visualInfo = new EntityDiagram.NodeVisualInfo();
                if (!CommonUtils.isEmpty(locX) && !CommonUtils.isEmpty(locY) && !CommonUtils.isEmpty(locW) && !CommonUtils.isEmpty(locH)) {
                    visualInfo.initBounds = new Rectangle(
                        Integer.parseInt(locX), Integer.parseInt(locY), Integer.parseInt(locW), Integer.parseInt(locH));
                }
                loadNodeVisualInfo(noteElem, visualInfo);
                diagram.addVisualInfo(note, visualInfo);

                if (!CommonUtils.isEmpty(noteId)) {
                    ElementLoadInfo info = new ElementLoadInfo(noteId, null, note, visualInfo);
                    tableInfos.add(info);
                    elementMap.put(info.objectId, info);
                }
            }
        }

        final Element relationsElem = XMLUtils.getChildElement(diagramElem, TAG_RELATIONS);
        if (relationsElem != null) {
            // Parse relations
            Collection<Element> relElemList = XMLUtils.getChildElementList(relationsElem, TAG_RELATION);
            monitor.beginTask("Parse relations", relElemList.size());
            for (Element relElem : relElemList) {
                String relName = relElem.getAttribute(ATTR_NAME);
                monitor.subTask("Load " + relName);
                String relType = relElem.getAttribute(ATTR_TYPE);
                String pkRefId = relElem.getAttribute(ATTR_PK_REF);
                String fkRefId = relElem.getAttribute(ATTR_FK_REF);
                if (CommonUtils.isEmpty(relName) || CommonUtils.isEmpty(pkRefId) || CommonUtils.isEmpty(fkRefId)) {
                    log.warn("Missing relation ID");
                    continue;
                }
                ElementLoadInfo pkTable = elementMap.get(pkRefId);
                ElementLoadInfo fkTable = elementMap.get(fkRefId);
                if (pkTable == null || fkTable == null) {
                    log.debug("PK (" + pkRefId + ") or FK (" + fkRefId +") table(s) not found for relation " + relName);
                    continue;
                }
                RelationLoadInfo relationLoadInfo = new RelationLoadInfo(relName, relType, pkTable, fkTable);
                relInfos.add(relationLoadInfo);

                // Load columns (present only in logical relations)
                for (Element columnElem : XMLUtils.getChildElementList(relElem, TAG_COLUMN)) {
                    String name = columnElem.getAttribute(ATTR_NAME);
                    String refName = columnElem.getAttribute(ATTR_REF_NAME);
                    relationLoadInfo.columns.put(name, refName);
                }

                // Load bends
                for (Element bendElem : XMLUtils.getChildElementList(relElem, TAG_BEND)) {
                    String type = bendElem.getAttribute(ATTR_TYPE);
                    if (!BEND_RELATIVE.equals(type)) {
                        String locX = bendElem.getAttribute(ATTR_X);
                        String locY = bendElem.getAttribute(ATTR_Y);
                        if (!CommonUtils.isEmpty(locX) && !CommonUtils.isEmpty(locY)) {
                            relationLoadInfo.bends.add(new int[] {
                                Integer.parseInt(locX),
                                Integer.parseInt(locY)
                            } );
                        }
                    }
                }
                monitor.worked(1);
            }
            monitor.done();
        }

        // Fill entities
        List<DBSEntity> tableList = new ArrayList<>();
        for (ElementLoadInfo info : tableInfos) {
            if (info.entity != null) {
                tableList.add(info.entity);
            }
        }
        diagram.fillEntities(monitor, tableList, null);

        // Add logical relations
        for (RelationLoadInfo info : relInfos) {
            if (info.type.equals(ERDConstants.CONSTRAINT_LOGICAL_FK.getId())) {
                final ERDElement<?> sourceEntity = info.pkTable.entity != null ? diagram.getEntity(info.pkTable.entity) : info.pkTable.note;
                final ERDElement<?> targetEntity = info.fkTable.entity != null ? diagram.getEntity(info.fkTable.entity) : info.fkTable.note;
                if (sourceEntity != null && targetEntity != null) {
                    new ERDAssociation(targetEntity, sourceEntity, false);
                }
                diagram.addInitRelationBends(sourceEntity, targetEntity, info.name, info.bends);
            }
        }
        // Set relations' bends
        for (RelationLoadInfo info : relInfos) {
            if (!CommonUtils.isEmpty(info.bends)) {
                if (info.pkTable.entity == null || info.fkTable.entity == null) {
                    // Logical connection with notes or something
                    continue;
                }
                final ERDEntity sourceEntity = diagram.getEntity(info.pkTable.entity);
                if (sourceEntity == null) {
                    log.warn("Source table " + info.pkTable.entity.getName() + " not found");
                    continue;
                }
                final ERDEntity targetEntity = diagram.getEntity(info.fkTable.entity);
                if (targetEntity == null) {
                    log.warn("Target table " + info.pkTable.entity.getName() + " not found");
                    continue;
                }
                diagram.addInitRelationBends(sourceEntity, targetEntity, info.name, info.bends);
            }
        }
    }

    public static String serializeDiagram(DBRProgressMonitor monitor, @Nullable DiagramPart diagramPart, final EntityDiagram diagram, boolean verbose, boolean compact)
        throws IOException
    {
        List<? extends IFigure> allNodeFigures = diagramPart == null ? new ArrayList<>() : diagramPart.getFigure().getChildren();
        Map<DBPDataSourceContainer, DataSourceObjects> dsMap = createDataSourceObjectMap(diagram);

        Map<ERDElement<?>, ElementSaveInfo> elementInfoMap = new IdentityHashMap<>();
        int elementCounter = ERD_VERSION_1;

        // Save as XML
        StringWriter out = new StringWriter(1000);
        XMLBuilder xml = new XMLBuilder(out, GeneralUtils.UTF8_ENCODING, !compact);
        xml.setButify(!compact);
        if (verbose) {
            xml.addContent(
                """
                    <!DOCTYPE diagram [
                    <!ATTLIST diagram version CDATA #REQUIRED
                     name CDATA #IMPLIED
                     time CDATA #REQUIRED>
                    <!ELEMENT diagram (entities, relations, notes)>
                    <!ELEMENT entities (data-source*)>
                    <!ELEMENT data-source (entity*)>
                    <!ATTLIST data-source id CDATA #REQUIRED>
                    <!ELEMENT entity (path*)>
                    <!ATTLIST entity id ID #REQUIRED
                     name CDATA #REQUIRED
                     fq-name CDATA #REQUIRED>
                    <!ELEMENT relations (relation*)>
                    <!ELEMENT relation (bend*)>
                    <!ATTLIST relation name CDATA #REQUIRED
                     fq-name CDATA #REQUIRED
                     pk-ref IDREF #REQUIRED
                     fk-ref IDREF #REQUIRED>
                    ]>
                    """
            );
        }
        xml.startElement(TAG_DIAGRAM);
        xml.addAttribute(ATTR_VERSION, ERD_VERSION_1);
        if (diagram != null) {
            xml.addAttribute(ATTR_NAME, diagram.getName());
        }
        if (compact) {
            xml.addAttribute(ATTR_TIME, RuntimeUtils.getCurrentTimeStamp());
        }

        if (diagram != null) {
            xml.startElement(TAG_ENTITIES);
            for (DBPDataSourceContainer dsContainer : dsMap.keySet()) {
                xml.startElement(TAG_DATA_SOURCE);
                xml.addAttribute(ATTR_ID, dsContainer.getId());

                final DataSourceObjects desc = dsMap.get(dsContainer);
                for (ERDEntity erdEntity : desc.entities) {
                    final DBSEntity table = erdEntity.getObject();
                    EntityPart tablePart = diagramPart == null ? null : diagramPart.getEntityPart(erdEntity);
                    ElementSaveInfo info = new ElementSaveInfo(erdEntity, tablePart, elementCounter++);
                    elementInfoMap.put(erdEntity, info);

                    xml.startElement(TAG_ENTITY);
                    xml.addAttribute(ATTR_ID, info.objectId);
                    xml.addAttribute(ATTR_NAME, table.getName());
                    if (table instanceof DBPQualifiedObject) {
                        xml.addAttribute(ATTR_FQ_NAME, ((DBPQualifiedObject)table).getFullyQualifiedName(DBPEvaluationContext.UI));
                    }
                    if (!CommonUtils.isEmpty(erdEntity.getAlias())) {
                        xml.addAttribute(ATTR_ALIAS, erdEntity.getAlias());
                    }
                    if (erdEntity.getAttributeVisibility() != null) {
                        xml.addAttribute(ATTR_ATTRIBUTE_VISIBILITY, erdEntity.getAttributeVisibility().name());
                    }
                    EntityDiagram.NodeVisualInfo visualInfo;
                    if (tablePart != null) {
                        visualInfo = new EntityDiagram.NodeVisualInfo();
                        visualInfo.initBounds = tablePart.getBounds();
                        visualInfo.bgColor = tablePart.getCustomBackgroundColor();

                        saveColorAndOrder(allNodeFigures, xml, tablePart);

                    } else {
                        visualInfo = diagram.getVisualInfo(erdEntity.getObject());
                    }
                    if (visualInfo != null && visualInfo.initBounds != null) {
                        xml.addAttribute(ATTR_X, visualInfo.initBounds.x);
                        xml.addAttribute(ATTR_Y, visualInfo.initBounds.y);
                    }

                    for (DBSObject parent = table.getParentObject(); parent != null && DBUtils.getPublicObjectContainer(parent) != dsContainer; parent = parent.getParentObject()) {
                        xml.startElement(TAG_PATH);
                        xml.addAttribute(ATTR_NAME, parent.getName());
                        xml.endElement();
                    }
                    xml.endElement();
                }
                xml.endElement();
            }
            xml.endElement();

            if (!CommonUtils.isEmpty(diagram.getNotes())) {
                // Notes
                xml.startElement(TAG_NOTES);
                for (ERDNote note : diagram.getNotes()) {
                    NotePart notePart = diagramPart == null ? null : diagramPart.getNotePart(note);

                    xml.startElement(TAG_NOTE);
                    if (notePart != null) {
                        ElementSaveInfo info = new ElementSaveInfo(note, notePart, elementCounter++);
                        elementInfoMap.put(note, info);
                        xml.addAttribute(ATTR_ID, info.objectId);

                        saveColorAndOrder(allNodeFigures, xml, notePart);

                        Rectangle noteBounds = notePart.getBounds();
                        if (noteBounds != null) {
                            xml.addAttribute(ATTR_X, noteBounds.x);
                            xml.addAttribute(ATTR_Y, noteBounds.y);
                            xml.addAttribute(ATTR_W, noteBounds.width);
                            xml.addAttribute(ATTR_H, noteBounds.height);
                        }
                    }
                    xml.addText(note.getObject());
                    xml.endElement();
                }
                xml.endElement();
            }

            // Relations
            xml.startElement(TAG_RELATIONS);

            List<ERDElement<?>> allElements = new ArrayList<>();
            allElements.addAll(diagram.getEntities());
            allElements.addAll(diagram.getNotes());
            for (ERDElement<?> element : allElements) {
                for (ERDAssociation rel : element.getReferences()) {
                    ElementSaveInfo pkInfo = elementInfoMap.get(rel.getTargetEntity());
                    if (pkInfo == null) {
                        log.error("Cannot find PK table '" + rel.getTargetEntity().getName() + "' in info map");
                        continue;
                    }
                    ElementSaveInfo fkInfo = elementInfoMap.get(rel.getSourceEntity());
                    if (fkInfo == null) {
                        log.error("Cannot find FK table '" + rel.getSourceEntity().getName() + "' in info map");
                        continue;
                    }

                    DBSEntityAssociation association = rel.getObject();

                    xml.startElement(TAG_RELATION);
                    xml.addAttribute(ATTR_NAME, association.getName());
                    if (association instanceof DBPQualifiedObject) {
                        xml.addAttribute(ATTR_FQ_NAME, ((DBPQualifiedObject) association).getFullyQualifiedName(DBPEvaluationContext.UI));
                    }
                    xml.addAttribute(ATTR_TYPE, association.getConstraintType().getId());

                    xml.addAttribute(ATTR_PK_REF, pkInfo.objectId);
                    xml.addAttribute(ATTR_FK_REF, fkInfo.objectId);

                    if (association instanceof ERDLogicalAssociation) {
                        // Save columns
                        for (DBSEntityAttributeRef column : ((ERDLogicalAssociation) association).getAttributeReferences(new VoidProgressMonitor())) {
                            xml.startElement(TAG_COLUMN);
                            xml.addAttribute(ATTR_NAME, column.getAttribute().getName());
                            try {
                                DBSEntityAttribute referenceAttribute = DBUtils.getReferenceAttribute(monitor, association, column.getAttribute(), false);
                                if (referenceAttribute != null) {
                                    xml.addAttribute(ATTR_REF_NAME, referenceAttribute.getName());
                                }
                            } catch (DBException e) {
                                log.warn("Error getting reference attribute", e);
                            }
                            xml.endElement();
                        }
                    }

                    // Save bends
                    if (pkInfo.nodePart != null) {
                        AssociationPart associationPart = pkInfo.nodePart.getConnectionPart(rel, false);
                        if (associationPart != null) {
                            final List<Bendpoint> bendpoints = associationPart.getBendpoints();
                            if (!CommonUtils.isEmpty(bendpoints)) {
                                for (Bendpoint bendpoint : bendpoints) {
                                    xml.startElement(TAG_BEND);
                                    if (bendpoint instanceof AbsoluteBendpoint) {
                                        xml.addAttribute(ATTR_TYPE, BEND_ABSOLUTE);
                                        xml.addAttribute(ATTR_X, bendpoint.getLocation().x);
                                        xml.addAttribute(ATTR_Y, bendpoint.getLocation().y);
                                    } else if (bendpoint instanceof RelativeBendpoint) {
                                        xml.addAttribute(ATTR_TYPE, BEND_RELATIVE);
                                        xml.addAttribute(ATTR_X, bendpoint.getLocation().x);
                                        xml.addAttribute(ATTR_Y, bendpoint.getLocation().y);
                                    }
                                    xml.endElement();
                                }
                            }
                        }
                    }

                    xml.endElement();
                }
            }

            xml.endElement();
        }

        xml.endElement();

        xml.flush();

        return out.toString();
    }

    private static Map<DBPDataSourceContainer, DataSourceObjects> createDataSourceObjectMap(EntityDiagram diagram) {
        // Prepare DS objects map
        Map<DBPDataSourceContainer, DataSourceObjects> dsMap = new IdentityHashMap<>();
        if (diagram != null) {
            for (ERDEntity erdEntity : diagram.getEntities()) {
                final DBPDataSourceContainer dsContainer = erdEntity.getObject().getDataSource().getContainer();
                DataSourceObjects desc = dsMap.computeIfAbsent(dsContainer, k -> new DataSourceObjects());
                desc.entities.add(erdEntity);
            }
        }
        return dsMap;
    }

    private static void saveColorAndOrder(List<?> allNodeFigures, XMLBuilder xml, NodePart nodePart) throws IOException {
        if (nodePart != null) {
            xml.addAttribute(ATTR_ORDER, allNodeFigures.indexOf(nodePart.getFigure()));
            if (nodePart.getCustomTransparency()) {
                xml.addAttribute(ATTR_TRANSPARENT, true);
            }
            Color bgColor = nodePart.getCustomBackgroundColor();
            if (bgColor != null) {
                Color defBgColor = nodePart instanceof NotePart ? ERDThemeSettings.instance.noteBackground : ERDThemeSettings.instance.entityRegularBackground;
                if (!CommonUtils.equalObjects(bgColor, defBgColor)) {
                    xml.addAttribute(ATTR_COLOR_BG, StringConverter.asString(bgColor.getRGB()));
                }
            }
            Color fgColor = nodePart.getCustomForegroundColor();
            if (fgColor != null) {
                Color defFgColor = nodePart instanceof NotePart ? ERDThemeSettings.instance.noteForeground : ERDThemeSettings.instance.entityNameForeground;
                if (!CommonUtils.equalObjects(fgColor, defFgColor)) {
                    xml.addAttribute(ATTR_COLOR_FG, StringConverter.asString(fgColor.getRGB()));
                }
            }
            int borderWidth = nodePart.getCustomBorderWidth();
            int defBorderWidth = nodePart instanceof NotePart ? ERDUIConstants.DEFAULT_NOTE_BORDER_WIDTH : ERDUIConstants.DEFAULT_ENTITY_BORDER_WIDTH;
            if (borderWidth != defBorderWidth) {
                xml.addAttribute(ATTR_BORDER_WIDTH, borderWidth);
            }
            if (!SharedFonts.equalFonts(nodePart.getCustomFont(), Display.getCurrent().getSystemFont())) {
                xml.addAttribute(ATTR_FONT, SharedFonts.toString(nodePart.getCustomFont()));
            }
        }
    }

    private static void loadNodeVisualInfo(Element entityElem, EntityDiagram.NodeVisualInfo visualInfo) {
        String isTransparent = entityElem.getAttribute(ATTR_TRANSPARENT);
        if (!CommonUtils.isEmpty(isTransparent)) {
            visualInfo.transparent = CommonUtils.toBoolean(isTransparent);
        }
        String colorBg = entityElem.getAttribute(ATTR_COLOR_BG);
        if (!CommonUtils.isEmpty(colorBg)) {
            visualInfo.bgColor = UIUtils.getSharedColor(colorBg);
        }
        String colorFg = entityElem.getAttribute(ATTR_COLOR_FG);
        if (!CommonUtils.isEmpty(colorFg)) {
            visualInfo.fgColor = UIUtils.getSharedColor(colorFg);
        }
        String borderWidth = entityElem.getAttribute(ATTR_BORDER_WIDTH);
        if (!CommonUtils.isEmpty(borderWidth)) {
            visualInfo.borderWidth = CommonUtils.toInt(borderWidth);
        }
        String fontStr = entityElem.getAttribute(ATTR_FONT);
        if (!CommonUtils.isEmpty(fontStr)) {
            visualInfo.font = UIUtils.getSharedFonts().getFont(Display.getCurrent(), fontStr);
        }
        String orderStr = entityElem.getAttribute(ATTR_ORDER);
        if (!CommonUtils.isEmpty(orderStr)) {
            visualInfo.zOrder = Integer.parseInt(orderStr);
        }
    }

    private static class ChooseDataSourcesDialog extends BaseDialog {
        private final DBPProject project;
        private final String[] ids;
        private final Map<String, DBPDataSourceContainer> containers;

        public ChooseDataSourcesDialog(@NotNull Shell shell, @NotNull DBPProject project, @NotNull String[] ids) {
            super(shell, "Missing data sources", null);

            this.project = project;
            this.ids = ids;
            this.containers = new LinkedHashMap<>(ids.length);

            for (String id : ids) {
                containers.put(id, null);
            }
        }

        @Override
        protected Composite createDialogArea(Composite parent) {
            final Composite composite = super.createDialogArea(parent);
            composite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

            UIUtils.createLabel(composite, "This diagram refers data sources that don't exist.\n\nPlease specify new data sources:");

            final TableViewer viewer = new TableViewer(composite, SWT.BORDER | SWT.SINGLE | SWT.FULL_SELECTION);

            final Table table = viewer.getTable();
            table.setLayoutData(GridDataFactory.fillDefaults().grab(true, true).hint(600, SWT.DEFAULT).create());
            table.setHeaderVisible(true);
            table.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> {
                if (table.getSelectionIndex() < 0) {
                    return;
                }

                final String id = ids[table.getSelectionIndex()];
                final DBNNode root = project.getNavigatorModel().getRoot().getProjectNode(project);
                final DBNNode leaf = DBWorkbench.getPlatformUI().selectObject(
                    getShell(),
                    "Select new data source",
                    root,
                    null,
                    new Class[]{DBPDataSourceContainer.class},
                    new Class[]{DBPDataSourceContainer.class},
                    new Class[]{DBPDataSourceContainer.class}
                );

                if (leaf != null) {
                    containers.put(id, ((DBNDataSource) leaf).getDataSourceContainer());
                    viewer.refresh();
                    updateCompletion();
                }
            }));

            final ViewerColumnController<Object, Object> controller = new ViewerColumnController<>(ChooseDataSourcesDialog.class.getName(), viewer);
            controller.addColumn("Original data source", null, SWT.LEFT, true, true, new ColumnLabelProvider() {
                @Override
                public String getText(Object element) {
                    return (String) element;
                }

                @Override
                public Image getImage(Object element) {
                    return DBeaverIcons.getImage(DBIcon.DATABASE_DEFAULT);
                }
            });
            controller.addColumn("New data source", null, SWT.LEFT, true, true, new ColumnLabelProvider() {
                @Override
                public String getText(Object element) {
                    final DBPDataSourceContainer container = containers.get((String) element);
                    if (container != null) {
                        return container.getName();
                    } else {
                        return "<unspecified>";
                    }
                }

                @Override
                public Image getImage(Object element) {
                    final DBPDataSourceContainer container = containers.get((String) element);
                    if (container != null) {
                        return DBeaverIcons.getImage(container.getDriver().getIcon());
                    } else {
                        return DBeaverIcons.getImage(DBIcon.DATABASE_DEFAULT);
                    }
                }
            });

            controller.createColumns(false);

            viewer.setContentProvider(new ListContentProvider());
            viewer.setInput(ids);

            UIUtils.asyncExec(() -> UIUtils.packColumns(viewer.getTable(), true));

            updateCompletion();

            return composite;
        }

        private void updateCompletion() {
            UIUtils.asyncExec(() -> {
                final Button button = getButton(IDialogConstants.OK_ID);
                if (button != null) {
                    button.setEnabled(!containers.containsValue(null));
                }
            });
        }
    }
}
