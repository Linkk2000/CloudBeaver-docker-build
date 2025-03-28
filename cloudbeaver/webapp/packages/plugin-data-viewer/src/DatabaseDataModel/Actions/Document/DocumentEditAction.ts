/*
 * CloudBeaver - Cloud Database Manager
 * Copyright (C) 2020-2025 DBeaver Corp and others
 *
 * Licensed under the Apache License, Version 2.0.
 * you may not use this file except in compliance with the License.
 */
import { makeObservable, observable } from 'mobx';

import { ResultDataFormat, type SqlResultRow, type AsyncUpdateResultsDataBatchMutationVariables } from '@cloudbeaver/core-sdk';

import type { IDatabaseDataSource } from '../../IDatabaseDataSource.js';
import type { IDatabaseResultSet } from '../../IDatabaseResultSet.js';
import { databaseDataAction } from '../DatabaseDataActionDecorator.js';
import { DatabaseEditAction } from '../DatabaseEditAction.js';
import { DatabaseEditChangeType } from '../IDatabaseDataEditAction.js';
import { DocumentDataAction } from './DocumentDataAction.js';
import type { IDatabaseDataDocument } from './IDatabaseDataDocument.js';
import type { IDocumentElementKey } from './IDocumentElementKey.js';

@databaseDataAction()
export class DocumentEditAction extends DatabaseEditAction<IDocumentElementKey, IDatabaseDataDocument, IDatabaseResultSet> {
  static override dataFormat = [ResultDataFormat.Document];

  readonly editedElements: Map<number, IDatabaseDataDocument>;
  private readonly data: DocumentDataAction;

  constructor(source: IDatabaseDataSource<any, IDatabaseResultSet>, data: DocumentDataAction) {
    super(source);
    this.editedElements = new Map();
    this.data = data;

    makeObservable(this, {
      editedElements: observable,
    });
  }

  isEdited(): boolean {
    return this.editedElements.size > 0;
  }

  isElementEdited(key: IDocumentElementKey): boolean {
    if (!this.editedElements.has(key.index)) {
      return false;
    }

    const value = this.data.get(key.index);

    return !this.compare(value, this.get(key));
  }

  getElementState(key: IDocumentElementKey): DatabaseEditChangeType | null {
    if (this.isElementEdited(key)) {
      return DatabaseEditChangeType.update;
    }

    return null;
  }

  get(key: IDocumentElementKey): IDatabaseDataDocument | undefined {
    return this.editedElements.get(key.index);
  }

  set(key: IDocumentElementKey, value: IDatabaseDataDocument, prevValue?: IDatabaseDataDocument): void {
    if (!prevValue) {
      prevValue = this.get(key);

      if (!prevValue) {
        prevValue = this.data.get(key.index);
      }
    }

    this.editedElements.set(key.index, value);

    this.action.execute({
      type: DatabaseEditChangeType.update,
      revert: false,
      resultId: this.result.id,
      value: [
        {
          key: key,
          prevValue,
          value,
        },
      ],
    });

    this.removeUnchanged(key);
  }

  add(key: IDocumentElementKey): void {
    throw new Error('Not implemented');
  }

  duplicate(key: IDocumentElementKey): void {
    throw new Error('Not implemented');
  }

  delete(key: IDocumentElementKey): void {
    throw new Error('Not implemented');
  }

  setData(key: IDocumentElementKey, value: string): void {
    let previousValue = this.get(key);

    if (!previousValue) {
      previousValue = this.data.get(key.index);
    }

    if (!previousValue) {
      throw new Error('Source value not found');
    }

    this.set(
      key,
      {
        ...previousValue,
        data: value,
      },
      previousValue,
    );
  }

  applyPartialUpdate(result: IDatabaseResultSet): void {
    let rowIndex = 0;

    for (const [id] of this.editedElements) {
      const row = result.data?.rowsWithMetaData?.[rowIndex];
      const value = row?.data;

      if (value !== undefined) {
        this.data.set(id, value[0]);
      }
      rowIndex++;
    }
  }

  applyUpdate(result: IDatabaseResultSet): void {
    let rowIndex = 0;

    for (const [id] of this.editedElements) {
      const row = result.data?.rowsWithMetaData?.[rowIndex];
      const value = row?.data;

      if (value !== undefined) {
        this.data.set(id, value[0]);
      }
      rowIndex++;
    }
    this.clear();
  }

  revert(key: IDocumentElementKey): void {
    this.editedElements.delete(key.index);

    this.action.execute({
      revert: true,
      resultId: this.result.id,
      value: [{ key: key }],
    });
  }

  clear(): void {
    this.editedElements.clear();
    this.action.execute({
      revert: true,
      resultId: this.result.id,
    });
  }

  override dispose(): void {
    this.clear();
  }

  fillBatch(batch: AsyncUpdateResultsDataBatchMutationVariables): void {
    for (const [id, document] of this.editedElements) {
      if (batch.updatedRows === undefined) {
        batch.updatedRows = [];
      }
      const updatedRows = batch.updatedRows as SqlResultRow[];

      updatedRows.push({
        data: [this.data.get(id)],
        metaData: this.data.getMetadataForDocument(document.id),
        updateValues: {
          // TODO: remove, place new document in data field
          0: document,
        },
      });
    }
  }

  private removeUnchanged(key: IDocumentElementKey) {
    if (!this.isElementEdited(key)) {
      this.revert(key);
    }
  }

  private compare(documentA: IDatabaseDataDocument | undefined, documentB: IDatabaseDataDocument | undefined) {
    return documentA?.data === documentB?.data;
  }
}
