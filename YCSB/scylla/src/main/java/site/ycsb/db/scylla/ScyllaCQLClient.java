/*
 * Copyright (c) 2020 YCSB contributors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License. See accompanying LICENSE file.
 */
package site.ycsb.db.scylla;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.ColumnDefinition;
import com.datastax.oss.driver.api.core.cql.ColumnDefinitions;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.datastax.oss.driver.api.querybuilder.QueryBuilder;


import com.datastax.oss.driver.api.querybuilder.insert.InsertInto;
import com.datastax.oss.driver.api.querybuilder.insert.RegularInsert;
import com.datastax.oss.driver.api.querybuilder.select.Select;
import com.datastax.oss.driver.api.querybuilder.select.SelectFrom;
import com.datastax.oss.driver.api.querybuilder.update.Assignment;
import com.datastax.oss.driver.api.querybuilder.update.UpdateStart;
import java.util.ArrayList;
import java.util.List;
import site.ycsb.ByteArrayByteIterator;
import site.ycsb.ByteIterator;
import site.ycsb.DB;
import site.ycsb.DBException;
import site.ycsb.Status;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.MessageFormatter;

/**
 * Scylla DB implementation.
 */
public class ScyllaCQLClient extends DB {

  private static final Logger LOGGER = LoggerFactory.getLogger(ScyllaCQLClient.class);

  private static CqlSession session = null;

  private static final ConcurrentMap<Set<String>, PreparedStatement> READ_STMTS = new ConcurrentHashMap<>();
  private static final ConcurrentMap<Set<String>, PreparedStatement> SCAN_STMTS = new ConcurrentHashMap<>();
  private static final ConcurrentMap<Set<String>, PreparedStatement> INSERT_STMTS = new ConcurrentHashMap<>();
  private static final ConcurrentMap<Set<String>, PreparedStatement> UPDATE_STMTS = new ConcurrentHashMap<>();
  private static final AtomicReference<PreparedStatement> READ_ALL_STMT = new AtomicReference<>();
  private static final AtomicReference<PreparedStatement> SCAN_ALL_STMT = new AtomicReference<>();
  private static final AtomicReference<PreparedStatement> DELETE_STMT = new AtomicReference<>();

  public static final String YCSB_KEY = "y_id";
  public static final String KEYSPACE_PROPERTY = "scylla.keyspace";
  public static final String KEYSPACE_PROPERTY_DEFAULT = "ycsb";

  public static final String TRACING_PROPERTY = "scylla.tracing";
  public static final String TRACING_PROPERTY_DEFAULT = "false";

  /**
   * Count the number of times initialized to teardown on the last
   * {@link #cleanup()}.
   */
  private static final AtomicInteger INIT_COUNT = new AtomicInteger(0);

  private static boolean debug = false;

  private static boolean trace = false;

  /**
   * Initialize any state for this DB. Called once per DB instance; there is one
   * DB instance per client thread.
   */
  @Override
  public void init() throws DBException {

    // Keep track of number of calls to init (for later cleanup)
    INIT_COUNT.incrementAndGet();

    // Synchronized so that we only have a single
    // cluster/session instance for all the threads.
    synchronized (INIT_COUNT) {

      if (session != null) {
        return;
      }

      try {
        debug = Boolean.parseBoolean(getProperties().getProperty("debug", "false"));
        trace = Boolean.parseBoolean(getProperties().getProperty(TRACING_PROPERTY, TRACING_PROPERTY_DEFAULT));

        session = CqlSession.builder()
            .withKeyspace(getProperties().getProperty(KEYSPACE_PROPERTY, KEYSPACE_PROPERTY_DEFAULT))
            .build();
      } catch (Exception e) {
        throw new DBException(e);
      }
    } // synchronized
  }

  /**
   * Cleanup any state for this DB. Called once per DB instance; there is one DB
   * instance per client thread.
   */
  @Override
  public void cleanup() throws DBException {
    synchronized (INIT_COUNT) {
      final int curInitCount = INIT_COUNT.decrementAndGet();
      if (curInitCount <= 0) {
        READ_STMTS.clear();
        SCAN_STMTS.clear();
        INSERT_STMTS.clear();
        UPDATE_STMTS.clear();
        READ_ALL_STMT.set(null);
        SCAN_ALL_STMT.set(null);
        DELETE_STMT.set(null);
        if (session != null) {
          session.close();
          session = null;
        }
      }
      if (curInitCount < 0) {
        // This should never happen.
        throw new DBException(String.format("initCount is negative: %d", curInitCount));
      }
    }
  }

  /**
   * Read a record from the database. Each field/value pair from the result will
   * be stored in a HashMap.
   *
   * @param table
   *          The name of the table
   * @param key
   *          The record key of the record to read.
   * @param fields
   *          The list of fields to read, or null for all of them
   * @param result
   *          A HashMap of field/value pairs for the result
   * @return Zero on success, a non-zero error code on error
   */
  @Override
  public Status read(String table, String key, Set<String> fields,
                     Map<String, ByteIterator> result) {
    try {
      PreparedStatement stmt = (fields == null) ? READ_ALL_STMT.get() : READ_STMTS.get(fields);

      Select query = null;
      if (stmt == null) {
        SelectFrom select = QueryBuilder.selectFrom(table);

        if (fields == null) {
          query = select.all();
        } else {
          for (String col : fields) {
            query = select.column(col);
          }
        }

        query = query.whereColumn(YCSB_KEY).isEqualTo(QueryBuilder.bindMarker()).limit(1);
        stmt = session.prepare(query.build());
      }

      PreparedStatement prevStmt = (fields == null) ?
          READ_ALL_STMT.getAndSet(stmt) :
          READ_STMTS.putIfAbsent(new HashSet<>(fields), stmt);
      if (prevStmt != null) {
        stmt = prevStmt;
      }

      LOGGER.debug(stmt.getQuery());
      LOGGER.debug("key = {}", key);

      ResultSet rs = session.execute(stmt.bind(key));

      // Should be only 1 row
      List<Row> rows = rs.all();
      if (rows.isEmpty()) {
        return Status.NOT_FOUND;
      }
      Row row = rows.get(0);
      if (row == null) {
        return Status.NOT_FOUND;
      }

      ColumnDefinitions cd = row.getColumnDefinitions();

      for (ColumnDefinition def : cd) {
        ByteBuffer val = row.getBytesUnsafe(def.getName());
        if (val != null) {
          result.put(def.getName().toString(), new ByteArrayByteIterator(val.array()));
        } else {
          result.put(def.getName().toString(), null);
        }
      }

      return Status.OK;

    } catch (Exception e) {
      e.printStackTrace();
      LOGGER.error(MessageFormatter.format("Error reading key: {}", key).getMessage(), e);
      return Status.ERROR;
    }

  }

  /**
   * Perform a range scan for a set of records in the database. Each field/value
   * pair from the result will be stored in a HashMap.
   *
   * scylla CQL uses "token" method for range scan which doesn't always yield
   * intuitive results.
   *
   * @param table
   *          The name of the table
   * @param startkey
   *          The record key of the first record to read.
   * @param recordcount
   *          The number of records to read
   * @param fields
   *          The list of fields to read, or null for all of them
   * @param result
   *          A Vector of HashMaps, where each HashMap is a set field/value
   *          pairs for one record
   * @return Zero on success, a non-zero error code on error
   */
  @Override
  public Status scan(String table, String startkey, int recordcount,
                     Set<String> fields, Vector<HashMap<String, ByteIterator>> result) {

    return Status.OK;
  }

  /**
   * Update a record in the database. Any field/value pairs in the specified
   * values HashMap will be written into the record with the specified record
   * key, overwriting any existing values with the same field name.
   *
   * @param table
   *          The name of the table
   * @param key
   *          The record key of the record to write.
   * @param values
   *          A HashMap of field/value pairs to update in the record
   * @return Zero on success, a non-zero error code on error
   */
  @Override
  public Status update(String table, String key, Map<String, ByteIterator> values) {

    try {
      Set<String> fields = values.keySet();
      PreparedStatement stmt = UPDATE_STMTS.get(fields);

      // Prepare statement on demand
      if (stmt == null) {
        UpdateStart update = QueryBuilder.update(table);

        // Add fields
        List<Assignment> assignmentList = new ArrayList<>();
        for (String field : fields) {
          assignmentList.add(
              Assignment.setColumn(field, QueryBuilder.bindMarker())
          );
        }

        SimpleStatement singleStmt = update.set(assignmentList)
            .whereColumn(YCSB_KEY).isEqualTo(QueryBuilder.bindMarker()).build();

        stmt = session.prepare(singleStmt);
        PreparedStatement prevStmt = UPDATE_STMTS.putIfAbsent(new HashSet<>(fields), stmt);
        if (prevStmt != null) {
          stmt = prevStmt;
        }
      }

      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug(stmt.getQuery().toString());
        LOGGER.debug("key = {}", key);
        for (Map.Entry<String, ByteIterator> entry : values.entrySet()) {
          LOGGER.debug("{} = {}", entry.getKey(), entry.getValue());
        }
      }

      // Add fields
      ColumnDefinitions vars = stmt.getVariableDefinitions();
      BoundStatement boundStmt = stmt.bind();
      for (int i = 0; i < vars.size() - 1; i++) {
        String filedName = vars.get(i).getName().toString();
        String value = values.get(filedName).toString();
        boundStmt = boundStmt.setString(i, value);
      }

      // Add key
      boundStmt = boundStmt.setString(vars.size() - 1, key);

      session.execute(boundStmt);

      return Status.OK;
    } catch (Exception e) {
      LOGGER.error(MessageFormatter.format("Error updating key: {}", key).getMessage(), e);
    }

    return Status.ERROR;
  }

  /**
   * Insert a record in the database. Any field/value pairs in the specified
   * values HashMap will be written into the record with the specified record
   * key.
   *
   * @param table
   *          The name of the table
   * @param key
   *          The record key of the record to insert.
   * @param values
   *          A HashMap of field/value pairs to insert in the record
   * @return Zero on success, a non-zero error code on error
   */
  @Override
  public Status insert(String table, String key, Map<String, ByteIterator> values) {

    try {
      Set<String> fields = values.keySet();
      PreparedStatement stmt = INSERT_STMTS.get(fields);

      // Prepare statement on demand
      if (stmt == null) {
        InsertInto insert = QueryBuilder.insertInto(table);
        RegularInsert regularInsert = insert.value(YCSB_KEY, QueryBuilder.bindMarker());

        // Add fields
        for (String field : fields) {
          regularInsert = regularInsert.value(field, QueryBuilder.bindMarker());
        }

        stmt = session.prepare(regularInsert.build());
        PreparedStatement prevStmt = INSERT_STMTS.putIfAbsent(new HashSet<>(fields), stmt);
        if (prevStmt != null) {
          stmt = prevStmt;
        }
      }

      // Add key
      BoundStatement boundStmt = stmt.bind().setString(0, key);

      // Add fields
      ColumnDefinitions vars = stmt.getVariableDefinitions();
      for (int i = 1; i < vars.size(); i++) {
        String filedName = vars.get(i).getName().toString();
        String value = values.get(filedName).toString();
        boundStmt = boundStmt.setString(i, value);
      }

      session.execute(boundStmt);

      return Status.OK;
    } catch (Exception e) {
      System.out.println(e);
      e.printStackTrace();
      LOGGER.error(MessageFormatter.format("Error inserting key: {}", key).getMessage(), e);
    }

    return Status.ERROR;
  }

  /**
   * Delete a record from the database.
   *
   * @param table
   *          The name of the table
   * @param key
   *          The record key of the record to delete.
   * @return Zero on success, a non-zero error code on error
   */
  @Override
  public Status delete(String table, String key) {
    return Status.OK;
  }

}
