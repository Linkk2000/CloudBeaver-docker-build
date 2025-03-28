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
package com.dbeaver.jdbc.driver.libsql;

import java.sql.SQLException;

public class LibSqlException extends SQLException {
    public LibSqlException(String reason, String SQLState, int vendorCode) {
        super(reason, SQLState, vendorCode);
    }

    public LibSqlException(String reason, String SQLState) {
        super(reason, SQLState);
    }

    public LibSqlException(String reason) {
        super(reason);
    }

    public LibSqlException() {
    }

    public LibSqlException(Throwable cause) {
        super(cause);
    }

    public LibSqlException(String reason, Throwable cause) {
        super(reason, cause);
    }

    public LibSqlException(String reason, String sqlState, Throwable cause) {
        super(reason, sqlState, cause);
    }

    public LibSqlException(String reason, String sqlState, int vendorCode, Throwable cause) {
        super(reason, sqlState, vendorCode, cause);
    }
}
