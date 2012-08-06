/*
 * $Id: MetadataManager.java,v 1.51 2012-08-06 02:20:59 pgust Exp $
 */

/*

 Copyright (c) 2000-2010 Board of Trustees of Leland Stanford Jr. University,
 all rights reserved.

 Permission is hereby granted, free of charge, to any person obtaining a copy
 of this software and associated documentation files (the "Software"), to deal
 in the Software without restriction, including without limitation the rights
 to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 copies of the Software, and to permit persons to whom the Software is
 furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in
 all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL
 STANFORD UNIVERSITY BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

 Except as contained in this notice, the name of Stanford University shall not
 be used in advertising or otherwise to promote the sale, use or other dealings
 in this Software without prior written authorization from Stanford University.

 */
package org.lockss.daemon;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.sql.BatchUpdateException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.mutable.MutableInt;
import org.lockss.app.BaseLockssDaemonManager;
import org.lockss.app.ConfigurableManager;
import org.lockss.app.LockssDaemon;
import org.lockss.config.ConfigManager;
import org.lockss.config.Configuration;
import org.lockss.config.Configuration.Differences;
import org.lockss.config.TdbAu;
import org.lockss.daemon.ArticleMetadataBuffer.ArticleMetadataInfo;
import org.lockss.daemon.status.StatusService;
import org.lockss.db.DbManager;
import org.lockss.extractor.ArticleMetadata;
import org.lockss.extractor.ArticleMetadataExtractor;
import org.lockss.extractor.ArticleMetadataExtractor.Emitter;
import org.lockss.extractor.BaseArticleMetadataExtractor;
import org.lockss.extractor.MetadataException.ValidationException;
import org.lockss.extractor.MetadataField;
import org.lockss.extractor.MetadataTarget;
import org.lockss.plugin.ArchivalUnit;
import org.lockss.plugin.ArticleFiles;
import org.lockss.plugin.AuEventHandler;
import org.lockss.plugin.AuUtil;
import org.lockss.plugin.Plugin;
import org.lockss.plugin.Plugin.Feature;
import org.lockss.plugin.PluginManager;
import org.lockss.scheduler.SchedulableTask;
import org.lockss.scheduler.Schedule;
import org.lockss.scheduler.StepTask;
import org.lockss.scheduler.TaskCallback;
import org.lockss.util.Constants;
import org.lockss.util.Logger;
import org.lockss.util.MetadataUtil;
import org.lockss.util.TimeInterval;
import org.lockss.util.StringUtil;

/**
 * This class implements a metadata manager that is responsible for managing an
 * index of metadata from AUs.
 * 
 * @author Philip Gust
 * @version 1.0
 */
public class MetadataManager extends BaseLockssDaemonManager implements
    ConfigurableManager {

  private static Logger log = Logger.getLogger("MetadataManager");

  /** prefix for config properties */
  static public final String PREFIX = "org.lockss.metadataManager.";

  /**
   * Determines whether MedataExtractor specified by plugin should be used if it
   * is available. If <code>false</code>, a MetaDataExtractor is created that
   * returns data from the TDB rather than from the content metadata. This is
   * faster than extracting metadata form content, but less complete. Use only
   * when minimal article info is required. This parameter can only be set at
   * startup.
   */
  static public final String PARAM_USE_METADATA_EXTRACTOR = PREFIX
      + "use_metadata_extractor";

  /**
   * Default value of MetadataManager use_metadata_extractor configuration
   * parameter; <code>true</code> to use specified MetadataExtractor.
   */
  static public final boolean DEFAULT_USE_METADATA_EXTRACTOR = true;

  /**
   * Determines whether indexing should be enabled. If indexing is not enabled,
   * AUs are queued for indexing, but the AUs are not reindexed until the
   * process is re-enabled. This parameter can be changed at runtime.
   */
  static public final String PARAM_INDEXING_ENABLED = PREFIX
      + "indexing_enabled";

  /**
   * Default value of MetadataManager indexing enabled configuration parameter;
   * <code>false</code> to disable, <code>true</code> to enable.
   */
  static public final boolean DEFAULT_INDEXING_ENABLED = false;

  /**
   * The maximum number of concurrent reindexing tasks. This property can be
   * changed at runtime
   */
  static public final String PARAM_MAX_REINDEXING_TASKS = PREFIX
      + "maxReindexingTasks";

  /** Default maximum concurrent reindexing tasks */
  static public final int DEFAULT_MAX_REINDEXING_TASKS = 1;

  /** Disable allowing crawl to interrupt reindexing tasks */
  static public final String PARAM_DISABLE_CRAWL_RESCHEDULE_TASK = PREFIX
      + "disableCrawlRescheduleTask";

  /** Default disable allowing crawl to interrupt reindexing tasks */
  static public final boolean DEFAULT_DISABLE_CRAWL_RESCHEDULE_TASK = false;

  /**
   * The maximum number reindexing task history. This property can be changed at
   * runtime
   */
  static public final String PARAM_HISTORY_MAX = PREFIX + "historySize";

  /** Default maximum reindexing tasks history */
  static public final int DEFAULT_HISTORY_MAX = 200;

  /** Name of metadata status table */
  public static final String METADATA_STATUS_TABLE_NAME = "metadata_status_table";

  /**
   * Map of running reindexing tasks keyed and sorted by their AuIds
   */
  final Map<String, ReindexingTask> activeReindexingTasks = new HashMap<String, ReindexingTask>();

  /**
   * List of reindexing tasks in order from most recent (0) to least recent.
   */
  final List<ReindexingTask> reindexingTaskHistory = new ArrayList<ReindexingTask>();

  /**
   * Metadata manager indexing enabled flag
   */
  boolean reindexingEnabled = DEFAULT_INDEXING_ENABLED;

  /**
   * Metadata manager use metadata extractor flag. Note: set this to false only
   * where specific metadata from the metadata extractor are not needed.
   */
  boolean useMetadataExtractor = DEFAULT_USE_METADATA_EXTRACTOR;

  /** Determines whether new database was created */
  boolean dbIsNew = false;

  /** Maximum number of reindexing tasks */
  int maxReindexingTasks = DEFAULT_MAX_REINDEXING_TASKS;

  /** Disable crawl completion rescheduling a running task for same AU */
  boolean disableCrawlRescheduleTask = DEFAULT_DISABLE_CRAWL_RESCHEDULE_TASK;

  /**
   * Maximum size of the reindexing task history list int
   * maxReindexingTaskHistory = DEFAULT_MAX_REINDEXING_TASKS_HISTORY;
   * 
   * /** The number of articles currently in the metadatabase
   */
  private long metadataArticleCount = 0;

  /** The number of AUs pending for reindexing */
  private long pendingAusCount = 0;

  /** The number successful reindexing operations */
  private long successfulReindexingCount = 0;

  /** The number successful reindexing operations */
  private long failedReindexingCount = 0;

  private int maxReindexingTaskHistory = DEFAULT_HISTORY_MAX;

  /** The plugin manager */
  private PluginManager pluginMgr = null;

  /** The database manager */
  private DbManager dbManager = null;

  /** Name of DOI table */
  static final String DOI_TABLE = "DOI";

  /** Name of ISBN table */
  static final String ISBN_TABLE = "ISBN";

  /** Name of ISSN table */
  static final String ISSN_TABLE = "ISSN";

  /** Name of ISSN to title table */
  static final String TITLE_TABLE = "Title";

  /** Name of Feature table */
  static final String FEATURE_TABLE = "Feature";

  /** Name of Metadata table */
  static final String METADATA_TABLE = "Metadata";

  /** Name of Pending AUs table */
  static final String PENDINGAUS_TABLE = "PendingAus";

  /** Name of access_url field */
  static public final String ACCESS_URL_FIELD = "access_url";

  /** Name of article_title field */
  static public final String ARTICLE_TITLE_FIELD = "article_title";

  /** Name of au_key field */
  static public final String AU_KEY_FIELD = "au_key";

  /** Name of author field */
  static public final String AUTHOR_FIELD = "author";

  /** Name of date field */
  static public final String DATE_FIELD = "date";

  /** Name of doi field */
  static public final String DOI_FIELD = "doi";

  /** Name of isbn field */
  static public final String ISBN_FIELD = "isbn";

  /** Name of issn field */
  static public final String ISSN_FIELD = "issn";

  /** Name of jornal or book title field */
  static public final String TITLE_FIELD = "title";

  /** Name of journal issue field */
  static public final String ISSUE_FIELD = "issue";

  /** Name of book edition field (same as journal EDITION_FILED */
  static public final String EDITION_FIELD = ISSUE_FIELD;

  /** Name of feature field (e.g. "fulltext", "abstract", "toc") */
  static public final String FEATURE_FIELD = "feature";

  /** Name of md_id foreign key field */
  static public final String MD_ID_FIELD = "md_id";

  /** Name of plugin_id field */
  static public final String PLUGIN_ID_FIELD = "plugin_id";

  /** Name of start_page field */
  static public final String START_PAGE_FIELD = "start_page";

  /** Name of volume field */
  static public final String VOLUME_FIELD = "volume";

  /** Length of access URL field */
  static public final int MAX_ACCESS_URL_FIELD = 4096;

  /** Length of database article title field */
  static public final int MAX_ATITLE_FIELD = 512;

  /** Length of database book or journal title field */
  static public final int MAX_TITLE_FIELD = 512;

  /** public of au key field */
  static private final int MAX_AU_KEY_FIELD = 512;

  /** Length of database author field -- enough for maybe first three authors */
  static public final int MAX_AUTHOR_FIELD = 512;

  /** Length of date field */
  static public final int MAX_DATE_FIELD = 16;

  /** Length of doi field */
  static public final int MAX_DOI_FIELD = 256;

  /** Length of isbn field */
  static public final int MAX_ISBN_FIELD = 13;

  /** Length of issn field */
  static public final int MAX_ISSN_FIELD = 8;

  /** Length of journal issue field */
  static public final int MAX_ISSUE_FIELD = 16;

  /** Length of book edition field (same as MAX_EDITION_FIELD) */
  static public final int MAX_EDITION_FIELD = 16;

  /** Length of feature field */
  static public final int MAX_FEATURE_FIELD = 16;

  /**
   * Length of plugin ID field. This field will be used as horizontal
   * partitioning field in the future, so it's length must be compatible for
   * that purpose for the database product used.
   */
  static public final int MAX_PLUGIN_ID_FIELD = 128;

  /** Length of start_page field */
  static public final int MAX_STARTPAGE_FIELD = 16;

  /** Length of volume field */
  static public final int MAX_VOLUME_FIELD = 16;

  private static ThreadMXBean tmxb = ManagementFactory.getThreadMXBean();
  static {
    log.debug3("current thread CPU time supported? "
        + tmxb.isCurrentThreadCpuTimeSupported());
    if (tmxb.isCurrentThreadCpuTimeSupported()) {
      tmxb.setThreadCpuTimeEnabled(true);
    }
  }

  /**
   * Start MetadataManager service
   */
  @Override
  public void startService() {
    log.debug("Starting MetadataManager");

    if (!initializeService(ConfigManager.getCurrentConfig())) {
      log.error("Error initializing manager");
      return;
    }

    Connection conn;
    try {
      conn = dbManager.getConnection();
    } catch (SQLException ex) {
      log.error("Cannot connect to database -- service not started");
      return;
    }

    // create schema and initialize tables if schema does not exist
    String[] schemas = null;

    try {
      dbIsNew = !dbManager.tableExists(conn, PENDINGAUS_TABLE);
      if (dbIsNew) {
        schemas = createSchema;
      } else if (!dbManager.tableExists(conn, TITLE_TABLE)) {
        // add new title table if needed
        schemas = new String[] { create_TITLE_TABLE };
      }
    } catch (SQLException sqle) {
      log.error("Error initializing manager");
      return;
    }

    if (schemas != null) {
      try {
        dbManager.executeBatch(conn, schemas);
      } catch (BatchUpdateException ex) {
        // handle batch update exception
        int[] counts = ex.getUpdateCounts();
        for (int i = 0; i < counts.length; i++) {
          log.error("Error in schema statement " + i + "(" + counts[i] + "): "
              + schemas[i]);
        }
        log.error("Cannot initialize schema -- service not started", ex);
        dbManager.safeRollbackAndClose(conn);
        return;
      } catch (SQLException ex) {
        log.error("Cannot initialize schema -- service not started", ex);
        dbManager.safeRollbackAndClose(conn);
        return;
      }
    }

    if (!dbIsNew) {
      // delete all old functions
      try {
        dbManager.executeBatch(conn, deleteFunctions);
      } catch (BatchUpdateException ex) {
        // handle batch update exception
        int[] counts = ex.getUpdateCounts();
        for (int i = 0; i < counts.length; i++) {
          log.error("Error in schema statement " + i + "(" + counts[i] + "): "
              + deleteFunctions[i]);
        }
      } catch (SQLException ex) {
        log.error("Cannot delete functions", ex);
      }
    }

    // add new functions
    try {
      dbManager.executeBatch(conn, createFunctions);
      conn.commit();
    } catch (BatchUpdateException ex) {
      // handle batch update exception
      int[] counts = ex.getUpdateCounts();
      for (int i = 0; i < counts.length; i++) {
        log.error("Error in schema statement " + i + "(" + counts[i] + "): "
            + createFunctions[i]);
      }
    } catch (SQLException ex) {
      log.error("Cannot create functions", ex);
    }

    // initialize pending AUs and article counts from database
    try {
      pendingAusCount = getPendingAusCount(conn);
      metadataArticleCount = getArticleCount(conn);
    } catch (SQLException ex) {
      log.error("Cannot get pending AUs and article counts");
    }

    dbManager.safeRollbackAndClose(conn);

    StatusService statusServ = dbManager.getDaemon().getStatusService();
    statusServ.registerStatusAccessor(METADATA_STATUS_TABLE_NAME,
        new MetadataManagerStatusAccessor(this));
    statusServ.registerOverviewAccessor(METADATA_STATUS_TABLE_NAME,
        new MetadataManagerStatusAccessor.IndexingOverview(this));

    // register to receive content change notifications to
    // reindex the database content associated with the au
    pluginMgr = getDaemon().getPluginManager();

    // start metadata extraction
    MetadataStarter starter = new MetadataStarter();
    new Thread(starter).start();
  }

  /**
   * Get the number of pending AUs.
   * 
   * @param conn
   *          the connection
   * @return the number of pending AUs
   * @throws SQLException
   *           if error executing the query
   */
  public long getPendingAusCount(Connection conn) throws SQLException {
    Statement stmt = conn.createStatement();
    ResultSet resultSet = null;
    try {
      resultSet = stmt.executeQuery("SELECT COUNT(*) FROM " + PENDINGAUS_TABLE);
      resultSet.next();
      long rowCount = resultSet.getLong(1);
      return rowCount;
    } finally {
      dbManager.safeCloseResultSet(resultSet);
      stmt.close();
    }
  }

  /**
   * Get the number articles in the metadatabase.
   * 
   * @param conn
   *          the connection
   * @return the number of articles in the metadatabase
   * @throws SQLException
   *           if error executing the query
   */
  public long getArticleCount(Connection conn) throws SQLException {
    Statement stmt = conn.createStatement();
    ResultSet resultSet = null;
    try {
      resultSet = stmt.executeQuery("SELECT COUNT(*) FROM "
          + METADATA_TABLE);
      resultSet.next();
      long rowCount = resultSet.getLong(1);
      return rowCount;
    } finally {
      dbManager.safeCloseResultSet(resultSet);
      stmt.close();
    }
  }

  /**
   * Get the number of articles in the specified AU.
   * 
   * @param conn
   *          the connection
   * @param au
   *          the AU
   * @return the number of articles in the metadatabase for the AU
   * @throws SQLException
   *           if error executing the query
   */
  public long getArticleCount(Connection conn, String auid) throws SQLException {
    Statement stmt = conn.createStatement();
    ResultSet resultSet = null;
    try {
      String plugin_id = PluginManager.pluginIdFromAuId(auid);
      String au_key = PluginManager.auKeyFromAuId(auid);
      String query = "SELECT COUNT(*) FROM " + METADATA_TABLE + " WHERE "
          + PLUGIN_ID_FIELD + "='" + plugin_id + "'" + " AND " + AU_KEY_FIELD
          + "='" + au_key + "'";
      resultSet = stmt.executeQuery(query);
      resultSet.next();
      long rowCount = resultSet.getLong(1);
      log.debug("Article count for " + query + " = " + rowCount);
      return rowCount;
    } finally {
      dbManager.safeCloseResultSet(resultSet);
      stmt.close();
    }
  }

  /**
   * Handle new configuration.
   * 
   * @param config
   *          the new configuration
   * @param prevConfig
   *          the previous configuration
   * @changedKeys the configuration keys that changed
   */
  @Override
  public void setConfig(Configuration config, Configuration prevConfig,
      Differences changedKeys) {
    useMetadataExtractor = config.getBoolean(PARAM_USE_METADATA_EXTRACTOR,
        DEFAULT_USE_METADATA_EXTRACTOR);
    maxReindexingTasks = config.getInt(PARAM_MAX_REINDEXING_TASKS,
        DEFAULT_MAX_REINDEXING_TASKS);
    maxReindexingTasks = Math.max(0, maxReindexingTasks);
    disableCrawlRescheduleTask = 
        config.getBoolean(PARAM_DISABLE_CRAWL_RESCHEDULE_TASK, 
                          DEFAULT_DISABLE_CRAWL_RESCHEDULE_TASK);
    boolean doEnable = config.getBoolean(PARAM_INDEXING_ENABLED,
        DEFAULT_INDEXING_ENABLED);
    setIndexingEnabled(doEnable);

    if (changedKeys.contains(PARAM_HISTORY_MAX)) {
      int histSize = config.getInt(PARAM_HISTORY_MAX, DEFAULT_HISTORY_MAX);
      setMaxHistory(histSize);
    }
  }

  /**
   * Restart the Metadata Managaer service by terminating any running reindexing
   * tasks and then resetting its database before calling {@link #startServie()}
   * .
   * <p>
   * This method is only used for testing.
   */
  protected void restartService() {
    if (!initializeService(ConfigManager.getCurrentConfig())) {
      log.error("Error initializing manager -- service not started");
      return;
    }

    stopReindexing();

    Connection conn;
    try {
      conn = dbManager.getConnection();
    } catch (SQLException ex) {
      log.error("Cannot get database connection -- service not started");
      return;
    }

    // reset database tables
    try {
      // drop schema tables already exist
      if (dbManager.tableExists(conn, PENDINGAUS_TABLE)) {
        dbManager.executeBatch(conn, dropSchema);
        pendingAusCount = 0;
      }
      conn.commit();
      conn.close();
    } catch (BatchUpdateException ex) {
      // handle batch update exception
      int[] counts = ex.getUpdateCounts();
      for (int i = 0; i < counts.length; i++) {
        log.error("Error in statement " + i + "(" + counts[i] + "): "
            + createSchema[i]);
      }
      log.error("Cannot drop existing schema -- service not started", ex);
      dbManager.safeRollbackAndClose(conn);
      return;
    } catch (SQLException ex) {
      log.error("Cannot drop existing schema -- service not started", ex);
      dbManager.safeRollbackAndClose(conn);
      return;
    }

    // start the service
    startService();
  }

  /**
   * Initialize the service from the current configuration.
   * 
   * @param config
   *          the configuration
   * @return <code>true</code> if initialized
   */
  protected boolean initializeService(Configuration config) {
    // determine maximum number of concurrent reindexing tasks
    // (0 disables reindexing)
    maxReindexingTasks = config.getInt(PARAM_MAX_REINDEXING_TASKS,
        DEFAULT_MAX_REINDEXING_TASKS);
    maxReindexingTasks = Math.max(0, maxReindexingTasks);

    dbManager = getDaemon().getDbManager();
    return true;
  }

  /**
   * SQL statements that create the database schema
   */

  /** Table for recording pending AUs to index */
  private static final String create_PENDINGAUS_TABLE = "create table "
      + PENDINGAUS_TABLE + " (" + PLUGIN_ID_FIELD + " VARCHAR("
      + MAX_PLUGIN_ID_FIELD + ") NOT NULL," + AU_KEY_FIELD + " VARCHAR("
      + MAX_AU_KEY_FIELD + ") NOT NULL" + ")";

  /** table for recording bibliobraphic metadata for an article */
  private static final String create_METADATA_TABLE = "create table "
      + METADATA_TABLE + " (" + MD_ID_FIELD
      + " BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY," + DATE_FIELD
      + " VARCHAR(" + MAX_DATE_FIELD + ")," + VOLUME_FIELD + " VARCHAR("
      + MAX_VOLUME_FIELD + ")," + ISSUE_FIELD + " VARCHAR(" + MAX_ISSUE_FIELD
      + ")," + START_PAGE_FIELD + " VARCHAR(" + MAX_STARTPAGE_FIELD + "),"
      + ARTICLE_TITLE_FIELD + " VARCHAR("
      + MAX_ATITLE_FIELD
      + "),"
      // author field is a semicolon-separated list
      + AUTHOR_FIELD + " VARCHAR(" + MAX_AUTHOR_FIELD + ")," + PLUGIN_ID_FIELD
      + " VARCHAR(" + MAX_PLUGIN_ID_FIELD
      + ") NOT NULL,"
      // partition by
      + AU_KEY_FIELD + " VARCHAR(" + MAX_AU_KEY_FIELD + ") NOT NULL,"
      + ACCESS_URL_FIELD + " VARCHAR(" + MAX_ACCESS_URL_FIELD + ") NOT NULL"
      + ")";

  /** table for recording a feature URL for an article */
//  private static final String create_FEATURE_TABLE = "create table "
//      + FEATURE_TABLE + " (" + FEATURE_FIELD + " VARCHAR(" + MAX_FEATURE_FIELD
//      + ") NOT NULL," + ACCESS_URL_FIELD + " VARCHAR(" + MAX_ACCESS_URL_FIELD
//      + ") NOT NULL" + MD_ID_FIELD + " BIGINT NOT NULL REFERENCES "
//      + METADATA_TABLE + "(md_id) on delete cascade" + ")";

  /** table for recording a DOI for an article */
  private static final String create_DOI_TABLE = "create table " + DOI_TABLE
      + " (" + DOI_FIELD + " VARCHAR(" + MAX_DOI_FIELD + ") NOT NULL,"
      + MD_ID_FIELD + " BIGINT NOT NULL REFERENCES " + METADATA_TABLE
      + "(md_id) on delete cascade" + ")";

  /** table for recording an ISBN for an article */
  private static final String create_ISBN_TABLE = "create table " + ISBN_TABLE
      + " (" + ISBN_FIELD + " VARCHAR(" + MAX_ISBN_FIELD + ") NOT NULL,"
      + MD_ID_FIELD + " BIGINT NOT NULL REFERENCES " + METADATA_TABLE
      + "(md_id) on delete cascade" + ")";

  /** table for recording an ISSN for an article */
  private static final String create_ISSN_TABLE = "create table " + ISSN_TABLE
      + " (" + ISSN_FIELD + " VARCHAR(" + MAX_ISSN_FIELD + ") NOT NULL,"
      + MD_ID_FIELD + " BIGINT NOT NULL REFERENCES " + METADATA_TABLE
      + "(md_id) on delete cascade" + ")";

  /** table for recording title journal/book title of an article */
  private static final String create_TITLE_TABLE = "create table "
      + TITLE_TABLE + " (" + TITLE_FIELD + " VARCHAR(" + MAX_TITLE_FIELD
      + ") NOT NULL," + MD_ID_FIELD + " BIGINT NOT NULL REFERENCES "
      + METADATA_TABLE + "(md_id) on delete cascade" + ")";

  /** array of tables to create */
  private static final String createSchema[] = { create_PENDINGAUS_TABLE,
      create_METADATA_TABLE,
      // create_FEATURE_TABLE,
      create_DOI_TABLE, create_ISBN_TABLE, create_ISSN_TABLE,
      create_TITLE_TABLE, };

  /**
   * SQL statements that drop the database schema
   */
  private static final String[] dropSchema = new String[] {
      "drop table " + PENDINGAUS_TABLE, "drop table " + DOI_TABLE,
      "drop table " + ISSN_TABLE, "drop table " + ISBN_TABLE,
      "drop table " + TITLE_TABLE,
      // "drop table " + FEATURE_TABLE,
      "drop table " + METADATA_TABLE, };

  /**
   * SQL statements that create stored functions
   */
  private static final String[] createFunctions = new String[] {

      "create function contentSizeFromUrl(url varchar(4096)) "
          + "returns bigint language java external name "
          + "'org.lockss.util.SqlStoredProcedures.getContentSizeFromArticleUrl' "
          + "parameter style java no sql",

      "create function contentTypeFromUrl(url varchar(4096)) "
          + "returns varchar(512) language java external name "
          + "'org.lockss.util.SqlStoredProcedures.getContentTypeFromArticleUrl' "
          + "parameter style java no sql",

      "create function eisbnFromAuId(pluginId varchar(128), "
          + "auKey varchar(512)) returns varchar(8) language java external name "
          + "'org.lockss.util.SqlStoredProcedures.getEisbnFromAuId' "
          + "parameter style java no sql",

      "create function eisbnFromUrl(url varchar(4096)) "
          + "returns varchar(13) language java external name "
          + "'org.lockss.util.SqlStoredProcedures.getEisbnFromArticleUrl' "
          + "parameter style java no sql",

      "create function eissnFromAuId(pluginId varchar(128), "
          + "auKey varchar(512)) returns varchar(8) language java external name "
          + "'org.lockss.util.SqlStoredProcedures.getEissnFromAuId' "
          + "parameter style java no sql",

      "create function eissnFromUrl(url varchar(4096)) "
          + "returns varchar(8) language java external name "
          + "'org.lockss.util.SqlStoredProcedures.getEissnFromArticleUrl' "
          + "parameter style java no sql",

      "create function endVolumeFromAuId(pluginId varchar(128), "
          + "auKey varchar(512)) returns varchar(16) language java external name "
          + "'org.lockss.util.SqlStoredProcedures.getEndVolumeFromAuId' "
          + "parameter style java no sql",

      "create function endVolumeFromUrl(url varchar(4096)) "
          + "returns varchar(16) language java external name "
          + "'org.lockss.util.SqlStoredProcedures.getEndVolumeFromArticleUrl' "
          + "parameter style java no sql",

      "create function endYearFromAuId(pluginId varchar(128), "
          + "auKey varchar(512)) returns varchar(16) language java external name "
          + "'org.lockss.util.SqlStoredProcedures.getEndYearFromAuId' "
          + "parameter style java no sql",

      "create function endYearFromUrl(url varchar(4096)) "
          + "returns varchar(16) language java external name "
          + "'org.lockss.util.SqlStoredProcedures.getEndYearFromArticleUrl' "
          + "parameter style java no sql",

      "create function generateAuId(pluginId varchar(128), "
          + "auKey varchar(512)) returns varchar(640) language java external name "
          + "'org.lockss.plugin.PluginManager.generateAuId' "
          + "parameter style java no sql",

      "create function ingestDateFromAuId(pluginId varchar(128), "
          + "auKey varchar(512)) returns varchar(16) language java external name "
          + "'org.lockss.util.SqlStoredProcedures.getingestDateFromAuId' "
          + "parameter style java no sql",

      "create function ingestDateFromUrl(url varchar(4096)) "
          + "returns varchar(16) language java external name "
          + "'org.lockss.util.SqlStoredProcedures.getIngestDateFromArticleUrl' "
          + "parameter style java no sql",

      "create function ingestYearFromAuId(pluginId varchar(128), "
          + "auKey varchar(512)) returns varchar(4) language java external name "
          + "'org.lockss.util.SqlStoredProcedures.getIngestYearFromAuId' "
          + "parameter style java no sql",

      "create function ingestYearFromUrl(url varchar(4096)) "
          + "returns varchar(4) language java external name "
          + "'org.lockss.util.SqlStoredProcedures.getIngestYearFromArticleUrl' "
          + "parameter style java no sql",

      "create function isbnFromAuId(pluginId varchar(128), "
          + "auKey varchar(512)) returns varchar(8) language java external name "
          + "'org.lockss.util.SqlStoredProcedures.getPrintIsbnFromAuId' "
          + "parameter style java no sql",

      "create function isbnFromUrl(url varchar(4096)) "
          + "returns varchar(13) language java external name "
          + "'org.lockss.util.SqlStoredProcedures.getIsbnFromArticleUrl' "
          + "parameter style java no sql",

      "create function issnFromAuId(pluginId varchar(128), "
          + "auKey varchar(512)) returns varchar(8) language java external name "
          + "'org.lockss.util.SqlStoredProcedures.getPrintIssnFromAuId' "
          + "parameter style java no sql",

      "create function issnFromUrl(url varchar(4096)) "
          + "returns varchar(8) language java external name "
          + "'org.lockss.util.SqlStoredProcedures.getIssnFromArticleUrl' "
          + "parameter style java no sql",

      "create function issnlFromAuId(pluginId varchar(128), "
          + "auKey varchar(512)) returns varchar(8) language java external name "
          + "'org.lockss.util.SqlStoredProcedures.getIssnLFromAuId' "
          + "parameter style java no sql",

      "create function issnlFromUrl(url varchar(4096)) "
          + "returns varchar(8) language java external name "
          + "'org.lockss.util.SqlStoredProcedures.getIssnLFromArticleUrl' "
          + "parameter style java no sql",

      "create function printIsbnFromAuId(pluginId varchar(128), "
          + "auKey varchar(512)) returns varchar(8) language java external name "
          + "'org.lockss.util.SqlStoredProcedures.getPrintIsbnFromAuId' "
          + "parameter style java no sql",

      "create function printIsbnFromUrl(url varchar(4096)) "
          + "returns varchar(13) language java external name "
          + "'org.lockss.util.SqlStoredProcedures.getPrintIsbnFromArticleUrl' "
          + "parameter style java no sql",

      "create function printIssnFromAuId(pluginId varchar(128), "
          + "auKey varchar(512)) returns varchar(8) language java external name "
          + "'org.lockss.util.SqlStoredProcedures.getPrintIssnFromAuId' "
          + "parameter style java no sql",

      "create function printIssnFromUrl(url varchar(4096)) "
          + "returns varchar(8) language java external name "
          + "'org.lockss.util.SqlStoredProcedures.getPrintIssnFromArticleUrl' "
          + "parameter style java no sql",

      "create function publisherFromUrl(url varchar(4096)) "
          + "returns varchar(256) language java external name "
          + "'org.lockss.util.SqlStoredProcedures.getPublisherFromArticleUrl' "
          + "parameter style java no sql",

      "create function publisherFromAuId(pluginId varchar(128), "
          + "auKey varchar(512)) returns varchar(256)  language java external name "
          + "'org.lockss.util.SqlStoredProcedures.getPublisherFromAuId' "
          + "parameter style java no sql",

      "create function startVolumeFromAuId(pluginId varchar(128), "
          + "auKey varchar(512)) returns varchar(16) language java external name "
          + "'org.lockss.util.SqlStoredProcedures.getStartVolumeFromAuId' "
          + "parameter style java no sql",

      "create function startVolumeFromUrl(url varchar(4096)) "
          + "returns varchar(16) language java external name "
          + "'org.lockss.util.SqlStoredProcedures.getStartVolumeFromArticleUrl' "
          + "parameter style java no sql",

      "create function startYearFromAuId(pluginId varchar(128), "
          + "auKey varchar(512)) returns varchar(16) language java external name "
          + "'org.lockss.util.SqlStoredProcedures.getStartYearFromAuId' "
          + "parameter style java no sql",

      "create function startYearFromUrl(url varchar(4096)) "
          + "returns varchar(16) language java external name "
          + "'org.lockss.util.SqlStoredProcedures.getStartYearFromArticleUrl' "
          + "parameter style java no sql",

      "create function titleFromAuId(pluginId varchar(128), "
          + "auKey varchar(512)) returns varchar(256) language java external name "
          + "'org.lockss.util.SqlStoredProcedures.getTitleFromAuId' "
          + "parameter style java no sql",

      "create function titleFromIssn(issn varchar(9)) "
          + "returns varchar(512) language java external name "
          + "'org.lockss.util.SqlStoredProcedures.getTitleFromIssn' "
          + "parameter style java no sql",

      "create function titleFromUrl(url varchar(4096)) "
          + "returns varchar(512) language java external name "
          + "'org.lockss.util.SqlStoredProcedures.getTitleFromArticleUrl' "
          + "parameter style java no sql",

      "create function volumeTitleFromAuId(pluginId varchar(128), "
          + "auKey varchar(512)) returns varchar(256) language java external name "
          + "'org.lockss.util.SqlStoredProcedures.getVolumeTitleFromAuId' "
          + "parameter style java no sql",

      "create function volumeTitleFromIsbn(issn varchar(18)) "
          + "returns varchar(512) language java external name "
          + "'org.lockss.util.SqlStoredProcedures.getVolumeTitleFromIsbn' "
          + "parameter style java no sql",

      "create function volumeTitleFromUrl(url varchar(4096)) "
          + "returns varchar(512) language java external name "
          + "'org.lockss.util.SqlStoredProcedures.getVolumeTitleFromArticleUrl' "
          + "parameter style java no sql",

      "create function yearFromDate(date varchar(16)) returns varchar(4) "
          + "language java external name "
          + "'org.lockss.util.SqlStoredProcedures.getYearFromDate' "
          + "parameter style java no sql", };

  /**
   * SQL statements that drop the functions
   */
  private static final String[] deleteFunctions = new String[] {
      "drop function contentSizeFromUrl", "drop function contentTypeFromUrl",
      "drop function eisbnFromAuId", "drop function eisbnFromUrl",
      "drop function eissnFromAuId", "drop function eissnFromUrl",
      "drop function endVolumeFromAuId", "drop function endVolumeFromUrl",
      "drop function endYearFromAuId", "drop function endYearFromUrl",
      "drop function generateAuId", "drop function ingestDateFromAuId",
      "drop function ingestDateFromUrl", "drop function ingestYearFromAuId",
      "drop function ingestYearFromUrl", "drop function isbnFromAuId",
      "drop function isbnFromUrl", "drop function issnFromAuId",
      "drop function issnFromUrl", "drop function issnlFromAuId",
      "drop function issnlFromUrl", "drop function printIsbnFromAuId",
      "drop function printIsbnFromUrl", "drop function printIssnFromAuId",
      "drop function printIssnFromUrl", "drop function publisherFromAuId",
      "drop function publisherFromUrl", "drop function startVolumeFromAuId",
      "drop function startVolumeFromUrl", "drop function startYearFromAuId",
      "drop function startYearFromUrl", "drop function titleFromAuId",
      "drop function titleFromIssn", "drop function titleFromUrl",
      "drop function volumeTitleFromAuId", "drop function volumeTitleFromIsbn",
      "drop function volumeTitleFromUrl", "drop function yearFromDate", };

  /**
   * Ensure that as many reindexing tasks as possible are running if manager is
   * enabled.
   * 
   * @param conn
   *          the database connection
   * @return the number of reindexing tasks started
   */
  private int startReindexing(Connection conn) {
    if (!getDaemon().isDaemonInited()) {
      log.debug("daemon not initialized: no reindexing tasks.");
      return 0;
    }

    // don't reindexing tasks run if reindexing is disabled
    if (!reindexingEnabled) {
      log.debug("metadata manager is disabled: no reindexing tasks.");
      return 0;
    }

    int reindexedTaskCount = 0;
    synchronized (activeReindexingTasks) {
      while (activeReindexingTasks.size() < maxReindexingTasks) {
        // get list of pending aus to reindex
        List<String> auIds = getAuIdsToReindex(conn, maxReindexingTasks
            - activeReindexingTasks.size());
        if (auIds.isEmpty()) {
          break;
        }

        // schedule pending aus
        for (String auId : auIds) {
          ArchivalUnit au = pluginMgr.getAuFromId(auId);
          if (au == null) {
            try {
              int count = deleteAu(conn, auId);
              notifyDeletedAu(auId, count);
            } catch (SQLException e) {
              log.error("Error removing au " + auId + "from pending", e);
            }
          } else {
            ArticleMetadataExtractor ae = getArticleMetadataExtractor(au);
            if (ae == null) {
              // shouldn't happen because it was checked before adding to
              // pending
              log.debug("not running reindexing task for au: " + au.getName()
                  + "  because it nas no metadata extractor");
              try {
                removeFromPendingAus(conn, au.getAuId());
              } catch (SQLException e) {
                log.error("Error removing au " + au.getName() + "from pending",
                    e);
                break;
              }
            } else {
              log.debug("running reindexing task for au: " + au.getName());
              ReindexingTask task = new ReindexingTask(au, ae);
              activeReindexingTasks.put(au.getAuId(), task);

              // add reindexing task to the history; limit history list size
              addToHistory(task);

              runReindexingTask(task);
              reindexedTaskCount++;
            }
          }
        }
      }
    }
    log.debug("Started " + reindexedTaskCount + " au reindexing tasks");
    return reindexedTaskCount;
  }

  /**
   * Get get the number of active reindexing tasks.
   * 
   * @return the number of active reindexing tasks
   */
  public long getActiveReindexingCount() {
    return activeReindexingTasks.size();
  }

  /**
   * Return the number of succesful reindexing operations.
   * 
   * @return the number of successful reindexing operations
   */
  public long getSuccessfulReindexingCount() {
    return this.successfulReindexingCount;
  }

  /**
   * Return the number of unsuccesful reindexing operations.
   * 
   * @return the number of unsuccessful reindexing operations
   */
  public long getFailedReindexingCount() {
    return this.failedReindexingCount;
  }

  /**
   * Get the list of reindexing tasks.
   * 
   * @return the
   */
  public List<ReindexingTask> getReindexingTasks() {
    return new ArrayList<ReindexingTask>(reindexingTaskHistory);
  }

  /**
   * Get the number of distinct articles in the metadatabase.
   * 
   * @return the number of distinct articles in the metadatabase
   */
  public long getArticleCount() {
    return metadataArticleCount;
  }

  public long getPendingAusCount() {
    return pendingAusCount;
  }

  /**
   * Ensure as many reindexing tasks as possible are running if manager is
   * enabled.
   * 
   * @return the number of reindexing tasks started
   */
  private int startReindexing() {

    Connection conn = null;
    int count = 0;
    try {
      conn = dbManager.getConnection();
      count = startReindexing(conn);
    } catch (SQLException ex) {
      log.debug("Cannot connect to database -- indexing not started", ex);
    } finally {
      dbManager.safeRollbackAndClose(conn);
    }
    return count;
  }

  /**
   * Stop any pending reindexing operations.
   */
  private void stopReindexing() {
    log.debug("number of reindexing tasks being stopped: "
        + activeReindexingTasks.size());

    // quit any running reindexing tasks
    synchronized (activeReindexingTasks) {
      for (MetadataManager.ReindexingTask task : activeReindexingTasks.values()) {
        task.cancel();
      }
      activeReindexingTasks.clear();
    }
  }

  /**
   * Gets indexing enabled state of this manager.
   * 
   * @return the indexing enabled state of this manager
   */
  public boolean isIndexingEnabled() {
    return reindexingEnabled;
  }

  /**
   * Set indexing enabled state of this manager.
   * 
   * @param enable
   *          new enabled state of manager
   */
  public void setIndexingEnabled(boolean enable) {
    boolean wasEnabled = reindexingEnabled;
    reindexingEnabled = enable;
    log.debug("enabled: " + reindexingEnabled);

    // start or stop reindexing if initialized
    if (dbManager != null) {
      if (!wasEnabled && reindexingEnabled) {
        // start reindexing
        startReindexing();
      } else if (wasEnabled && !reindexingEnabled) {
        // stop any pending reindexing operations
        stopReindexing();
      }
    }
  }

  /**
   * This class handles deferred initialization until AUs are available.
   * 
   * @author phil
   * 
   */
  private class MetadataStarter extends LockssRunnable {
    public MetadataStarter() {
      super("MetadataStarter");
    }

    // start metadata extraction process
    public void lockssRun() {
      LockssDaemon daemon = getDaemon();

      // add all aus to list of pending aus
      if (!daemon.areAusStarted()) {
        log.debug("Waiting for aus to start");
        while (!getDaemon().areAusStarted()) {
          try {
            getDaemon().waitUntilAusStarted();
          } catch (InterruptedException ex) {
          }
        }
      }
      log.debug("Starting metadata extraction");

      Connection conn;
      try {
        conn = dbManager.getConnection();
      } catch (SQLException ex) {
        log.error("Cannot connect to database -- extraction not started");
        return;
      }

      Collection<ArchivalUnit> allAus = pluginMgr.getRandomizedAus();
      if (!dbIsNew) {
        // add only AUs whose metadata feature version has changed
        for (Iterator<ArchivalUnit> itr = allAus.iterator(); itr.hasNext();) {
          ArchivalUnit au = itr.next();
          if (!AuUtil.hasCrawled(au)
              || AuUtil.isCurrentFeatureVersion(au, Feature.Metadata)) {
            itr.remove();
          } else {
            log.debug("'" + au.getName() + "': Au feature vsn: "
                + AuUtil.getAuState(au).getFeatureVersion(Feature.Metadata)
                + " plugin feature vsn: "
                + au.getPlugin().getFeatureVersion(Feature.Metadata));
          }
        }
      }

      try {
        addToPendingAus(conn, allAus);
        conn.commit();
        dbIsNew = false;
      } catch (SQLException ex) {
        log.error("Cannot add pending AUs table \"" + PENDINGAUS_TABLE + "\"",
            ex);
        dbManager.safeRollbackAndClose(conn);
        return;
      }

      startReindexing(conn);
      dbManager.safeRollbackAndClose(conn);

      // now that all aus started, it's safe to add the AuEventHandler
      pluginMgr.registerAuEventHandler(new AuEventHandler.Base() {

        /** Called after the AU is created */
        @Override
        public void auCreated(PluginManager.AuEvent event, ArchivalUnit au) {
          switch (event) {
            case StartupCreate:
              log.debug2("StartupCreate for au: " + au);

              // Since this handler is installed after daemon startup, this
              // case only occurs rarely, as when an AU is added to au.txt,
              // which is then rescanned by the daemon. If this restores an
              // existing AU that has already crawled, we schedule it to be
              // added the metadata database now. Otherwise it will be added
              // through auContentChanged() once the crawl has completed
              if (AuUtil.hasCrawled(au)) {
                addAuToReindex(au);
              }
              break;
            case Create:
              log.debug2("Create for au: " + au);

              // This case occurs when the user has added an AU through the GUI.
              // If this restores an existing AU that has already crawled,
              // we schedule it to be added the metadata database now.
              // Otherwise it will be added through auContentChanged() once the
              // crawl has completed
              if (AuUtil.hasCrawled(au)) {
                addAuToReindex(au);
              }

              break;
            case RestartCreate:
              log.debug2("RestartCreate for au: " + au);

              // A new version of the plugin has been loaded. Refresh the
              // metadata
              // only if the feature version of the metadata extractor changed,
              if (!AuUtil.isCurrentFeatureVersion(au, Feature.Metadata)) {
                addAuToReindex(au);
              }
              break;
          }
        }

        /** Called for AU deleted events */
        @Override
        public void auDeleted(PluginManager.AuEvent event, ArchivalUnit au) {
          switch (event) {
            case Delete:
              log.debug2("Delete for au: " + au);

              // This case occurs when the AU is being deleted, so delete
              // its metadata
              removeAuForReindex(au);
              break;
            case RestartDelete:
              // This case occurs when the plugin is about to restart. There is
              // nothing to do in this case but wait for plugin to reactivate
              // to see if anything needs to be done if metadatamanager changed
              break;
          }
        }

        /** Called for AU changed events */
        @Override
        public void auContentChanged(PluginManager.AuEvent event,
            ArchivalUnit au, ChangeInfo info) {
          switch (event) {
            case ContentChanged:
              // This case occurs after a change to the AU's content after
              // a crawl. This code assumes that a new crawl will simply add
              // new metadata and not change existing metadata. Otherwise,
              // deleteOrRestartAu(au, true) should be called.
              if (info.isComplete()) {
                addAuToReindex(au);
              }
          }
        }

      });
    }
  }

  /**
   * Determines whether the AU has article metadata.
   * 
   * @param au
   *          the au
   * @return <code>true</code> if the AU has article metadata
   */
  private boolean hasArticleMetadata(ArchivalUnit au) {
    if (au.getArticleIterator(MetadataTarget.OpenURL) == null) {
      return false;
    }
    // has article metadata if there is a metadata extractor
    if (useMetadataExtractor) {
      Plugin p = au.getPlugin();
      if (p.getArticleMetadataExtractor(MetadataTarget.OpenURL, au) != null) {
        return true;
      }
    }
    // otherwise has metadata if can create it from TitleConfig's TdbAu
    TitleConfig tc = au.getTitleConfig();
    return (tc != null) && (tc.getTdbAu() != null);
  }

  /**
   * Get the ArticleMetadataExtractor for the specified au.
   * 
   * @param au
   *          the au
   * @return the article metadata extractor
   */
  private ArticleMetadataExtractor getArticleMetadataExtractor(ArchivalUnit au) {

    ArticleMetadataExtractor ae = null;
    if (useMetadataExtractor) {
      Plugin plugin = au.getPlugin();
      ae = plugin.getArticleMetadataExtractor(MetadataTarget.OpenURL, au);
    }
    if (ae == null) {
      ae = new BaseArticleMetadataExtractor(null);
    }
    return ae;
  }

  /** enumeration status for reindexing tasks */
  enum ReindexingStatus {
    success, // if the reindexing task was successful
    failed, // if the reindexing task failed
    rescheduled // if the reindexing task was rescheduled
  };

  /**
   * This class implements a reindexing task that extracts metadata from all the
   * articles in the specified AU.
   */
  class ReindexingTask extends StepTask {

    /** The archival unit for this task */
    private final ArchivalUnit au;

    /** The article metadata extractor for this task */
    private final ArticleMetadataExtractor ae;

    /** The article iterator for this task */
    private Iterator<ArticleFiles> articleIterator = null;

    /** List of log messages already emitted for this task's au */
    private final HashSet<Integer> auLogTable = new HashSet<Integer>();

    /** The database connection for this task */
    private Connection conn;

    /** The default number of steps for this task */
    private static final int default_steps = 10;

    /** The status of the task: successful if true */
    private ReindexingStatus status = ReindexingStatus.success;

    /** Whether the AU being indexed is new to the index */
    private boolean isNewAu = true;

    // properties of AU and the TdbAu for this task
    private final String auid;
    private final String pluginId;
    private final String auKey;
    private final TdbAu tdbau;
    private final String tdbauStartYear;
    private final String tdbauIsbn;
    private final String tdbauIssn;
    private final String tdbauEissn;
    private final String tdbauYear;
    private final String tdbauJournalTitle;
    private final String tdbauName;

    /** The number of articles indexed by this task */
    private long indexedArticleCount = 0;
    /** The number of articles updated by this task */
    private long updatedArticleCount = 0;

    // ThreadMXBean times
    long startCpuTime = 0;
    long startUserTime = 0;
    long startClockTime = 0;

    long startUpdateCpuTime = 0;
    long startUpdateUserTime = 0;
    long startUpdateClockTime = 0;

    private long endCpuTime = 0;
    private long endUserTime = 0;
    private long endClockTime = 0;
    
    final String auName;
    final String auId;
    final boolean auNoSubstance;


    private final boolean isTitleInTdb;

    ArticleMetadataBuffer articleMetadataInfoBuffer = null;

    /**
     * Create a reindexing task for the AU
     * 
     * @param theAu
     *          the archival unit
     * @param theAe
     *          the article metadata extractor to use
     */
    public ReindexingTask(ArchivalUnit theAu, ArticleMetadataExtractor theAe) {
      // NOTE: estimated window time interval duration not currently used.
      super(new TimeInterval(System.currentTimeMillis(),
          System.currentTimeMillis() + Constants.HOUR), 0, /*
                                                            * long
                                                            * estimatedDuration
                                                            */
      null, /* TaskCallback */
      null); /* Object cookie */
      this.au = theAu;
      this.ae = theAe;
      this.auName = au.getName();
      this.auId = au.getAuId();
      this.auNoSubstance = AuUtil.getAuState(au).hasNoSubstance();
      
      // initialize values used for processing every article in the AU
      auid = au.getAuId();
      pluginId = PluginManager.pluginIdFromAuId(auid);

      // TEMPORARY KLUDGE FOR DEVELOPMENT ONLY -- HONEST!
      isTitleInTdb = !pluginId.endsWith("SourcePlugin");

      auKey = PluginManager.auKeyFromAuId(auid);
      TitleConfig tc = au.getTitleConfig();
      tdbau = (tc == null) ? null : tc.getTdbAu();
      tdbauName = (tdbau == null) ? null : tdbau.getName();
      tdbauStartYear = (tdbau == null) ? au.getName() : tdbau.getStartYear();
      tdbauYear = (tdbau == null) ? null : tdbau.getYear();

      if (isTitleInTdb && (tdbau != null)) {
        // get title information from TDB
        tdbauIsbn = tdbau.getIsbn();
        tdbauIssn = tdbau.getPrintIssn();
        tdbauEissn = tdbau.getEissn();
        tdbauJournalTitle = tdbau.getJournalTitle();
      } else {
        // TDB does not have title information
        tdbauIsbn = null;
        tdbauIssn = null;
        tdbauEissn = null;
        tdbauJournalTitle = null;
      }

      // set task callback after construction to ensure instance is initialized
      callback = new TaskCallback() {
        public void taskEvent(SchedulableTask task, Schedule.EventType type) {
          long threadCpuTime = 0;
          long threadUserTime = 0;
          long currentClockTime = System.currentTimeMillis();
          if (tmxb.isCurrentThreadCpuTimeSupported()) {
            threadCpuTime = tmxb.getCurrentThreadCpuTime();
            threadUserTime = tmxb.getCurrentThreadUserTime();
          }
          // TODO: handle task success vs. failure?
          if (type == Schedule.EventType.START) {
            // article iterator won't be null because only aus
            // with article iterators are queued for processing
            articleIterator = au.getArticleIterator(MetadataTarget.OpenURL);
            log.debug2("Starting reindexing task for au: " + au.getName()
                + " has articles? " + articleIterator.hasNext());
            try {
              articleMetadataInfoBuffer = new ArticleMetadataBuffer();

              // determine whether this is a new or reindexed AU
              conn = dbManager.getConnection();
              long articleCount = getArticleCount(conn, au.getAuId());
              isNewAu = (articleCount == 0);

              startCpuTime = threadCpuTime;
              startUserTime = threadUserTime;
              startClockTime = currentClockTime;

              notifyStartReindexingAu(au);
            } catch (IOException ex) {
              log.error(
                  "Failed to set up to reindex pending au: " + au.getName(), ex);
              setFinished();
              if (status == ReindexingStatus.success) {
                status = ReindexingStatus.rescheduled;
              }
            } catch (SQLException ex) {
              log.error(
                  "Failed to set up to reindex pending au: " + au.getName(), ex);
              setFinished();
              if (status == ReindexingStatus.success) {
                status = ReindexingStatus.rescheduled;
              }
            }

            // close connection now, and re-establish it when finished
            // to avoid database connection timeouts that seem to be
            // endemic to Derby
            dbManager.safeRollbackAndClose(conn);
            conn = null;
 
          } else if (type == Schedule.EventType.FINISH) {
            log.debug3(  "Finishing reindexing (" + status + ")" 
                       + " for au: " + auName);
            
            startUpdateCpuTime = threadCpuTime;
            startUpdateUserTime = threadUserTime;
            startUpdateClockTime = currentClockTime;
            
            switch (status) {
              case success:
                try {
                  // re-establish connection to database
                  conn = dbManager.getConnection();

                  // remove old metadata before adding new for AU
                  long removedArticleCount = removeMetadata(conn, auid);

                  Iterator<ArticleMetadataInfo> mditr = 
                      articleMetadataInfoBuffer.iterator();
                  while (mditr.hasNext()) {
                    try {
                      addMetadata(mditr.next());
                      updatedArticleCount++;
                    } catch (ValidationException ex) {
                      log.warning(ex.getMessage());
                    }
                  }
                  
                  // remove the AU just reindexed from the pending list
                  removeFromPendingAus(conn, auid);
                  conn.commit();

                  successfulReindexingCount++;
                  
                  // update total article count
                  metadataArticleCount += 
                      updatedArticleCount - removedArticleCount;
                  
                  // update AU's feature version to that of the plugin
                  // so we can test whether it's up-to-date in case of a
                  // later reload of the plugin
                  AuUtil.getAuState(au).setFeatureVersion(Feature.Metadata,
                      au.getPlugin().getFeatureVersion(Feature.Metadata));

                  break;

                } catch (SQLException ex) {
                  log.warning(  "Error updating metadata at FINISH"
                      + " for " + status + " -- rescheduling", ex);
                  status = ReindexingStatus.rescheduled;
                  try {
                    conn.rollback();
                  } catch (SQLException ex2) {
                    log.error("Failed to rollback database transaction", ex2);
                    
                    // force new database connection
                    dbManager.safeRollbackAndClose(conn);
                    conn = null;
                  }
                }
                
                // fall through if SQL exception occurred during update
              case failed:
              case rescheduled:
                
                failedReindexingCount++;

                // reindexing not successful so, again try later
                // if status indicates the operation should be rescheduled
                log.debug2(  "Reindexing task (" + status 
                           + ") did not finish for au " + au.getName());
                try {
                  // re-establish connection to database if necessary
                  if (conn == null) {
                    conn = dbManager.getConnection();
                  }
                  
                  // attempt to move failed AU to end of pending list
                  removeFromPendingAus(conn, au.getAuId());
                  if (status == ReindexingStatus.rescheduled) {
                    log.debug2("Rescheduling reindexing task au "
                        + au.getName());
                    addToPendingAus(conn, Collections.singleton(au));
                  }
                  conn.commit();
                } catch (SQLException ex) {
                  log.warning("Error updating pending queue at FINISH" 
                            + " for " + status, ex);
                  try {
                    if (conn != null) {
                      conn.rollback();
                    }
                  } catch (SQLException ex2) {
                    log.error("Failed to rollback database transaction", ex2);
                  }
                }
            }

            articleIterator = null;

            endClockTime = System.currentTimeMillis();
            if (tmxb.isCurrentThreadCpuTimeSupported()) {
              endCpuTime = tmxb.getCurrentThreadCpuTime();
              endUserTime = tmxb.getCurrentThreadUserTime();
            }

            long elapsedCpuTime = threadCpuTime = startCpuTime;
            long elapsedUserTime = threadUserTime - startUserTime;
            long elapsedClockTime = currentClockTime - startClockTime;
            if (log.isDebug2()) {
              log.debug2("Reindexing task finished (" + status + ")"
                  + " for au: " + au.getName()
                  + " CPU time: " + elapsedCpuTime
                  / 1.0e9 + " (" + endCpuTime / 1.0e9
                  + "), UserTime: " + elapsedUserTime / 1.0e9 + " ("
                  + endUserTime / 1.0e9 + ") Clock time: "
                  + elapsedClockTime / 1.0e3 + " (" + endClockTime
                  / 1.0e3 + ")");
            }
            // release collected metadata info once finished
            articleMetadataInfoBuffer.close();
            articleMetadataInfoBuffer = null;

            synchronized (activeReindexingTasks) {
              activeReindexingTasks.remove(au.getAuId());
              notifyFinishReindexingAu(au, status);
              try {
                if (conn == null) {
                  conn = dbManager.getConnection();
                }

                // schedule another task if available
                startReindexing(conn);
                
              } catch (SQLException ex) {
                  log.error("Cannot restart indexing", ex);
              }
            }
            
            dbManager.safeRollbackAndClose(conn);
            conn = null;

          }
        }

      };
    }

    /** Cancel current task without rescheduling */
    public void cancel() {
      if (!isFinished() && (status == ReindexingStatus.success)) {
        status = ReindexingStatus.failed;
        super.cancel();
        setFinished();
      }
    }

    /** Cancel and mark current task for rescheduling */
    public void reschedule() {
      if (!isFinished() && (status == ReindexingStatus.success)) {
        status = ReindexingStatus.rescheduled;
        super.cancel();
        setFinished();
      }
    }

    /**
     * Returns the task AU.
     * 
     * @return the AU being reindexed
     */
    public ArchivalUnit getAu() {
      return au;
    }

    /**
     * Returns the name of the task AU.
     * @return the name of the task AU
     */
    public String getAuName() {
      return auName;
    }
    
    /**
     * Returns auid of task AU.
     * @return the auid of the task AU
     */
    public String getAuId() {
      return auId;
    }
    
    /**
     * Returns substance state of task AU.
     * @return <code>true</code> if AU has no substance
     */
    public boolean hasNoAuSubstance() {
      return auNoSubstance;
    }
    /**
     * Returns <code>true<.code> if the AU is not yet indexed.
     * 
     * @return <code>true</code> if the AU is not yet indexed
     */
    public boolean isNewAu() {
      return isNewAu;
    }

    /**
     * Get the start time for indexing
     * 
     * @return the start time in miliseconds since epoch (0 if not started)
     */
    public long getStartTime() {
      return startClockTime;
    }

    public long getStartUpdateTime() {
      return startUpdateClockTime;
    }
    
    /**
     * Get the end time for indexing.
     * 
     * @return the end time in miliseconds since epoch (0 if not finished)
     */
    public long getEndTime() {
      return endClockTime;
    }

    /**
     * Returns the reindexing status of this task.
     * 
     * @return the reindexing status
     */
    public ReindexingStatus getReindexingStatus() {
      return status;
    }

    /**
     * Returns the number of articles extracted by this task.
     * 
     * @return the number of articles extracted by this task
     */
    public long getIndexedArticleCount() {
      return indexedArticleCount;
    }

    /**
     * Returns the number of articles extracted by this task.
     * 
     * @return the number of articles extracted by this task
     */
    public long getUpdatedArticleCount() {
      return updatedArticleCount;
    }

    /**
     * Extract metadata from the next group of articles.
     * 
     * @param n
     *          the amount of work to do
     * @todo: figure out what the amount of work means
     */
    public int step(int n) {
      final MutableInt extracted = new MutableInt(0);
      int steps = (n <= 0) ? default_steps : n;
      log.debug3("step: " + steps + ", has articles: "
          + articleIterator.hasNext());

      Emitter emitter = new Emitter() {
        @Override
        public void emitMetadata(ArticleFiles af, ArticleMetadata md) {
          if (log.isDebug3()) {
            log.debug3("field access url: "
                + md.get(MetadataField.FIELD_ACCESS_URL));
          }

          if (md.get(MetadataField.FIELD_ACCESS_URL) == null) {
            // temporary -- use full text url if not set
            // (should be set by metadata extractor)
            md.put(MetadataField.FIELD_ACCESS_URL, af.getFullTextUrl());
          }
          try {
            articleMetadataInfoBuffer.add(md);
          } catch (IOException ex) {
            throw new RuntimeException(ex);
          }
          extracted.increment();
          indexedArticleCount++;

        }
      };

      while (!isFinished() && (extracted.intValue() <= steps)
          && articleIterator.hasNext()) {
        ArticleFiles af = articleIterator.next();
        try {
          ae.extract(MetadataTarget.OpenURL, af, emitter);
        } catch (IOException ex) {
          log.error(
              "Failed to index metadata for full text URL: "
                  + af.getFullTextUrl(), ex);
          setFinished();
          if (status == ReindexingStatus.success) {
            status = ReindexingStatus.rescheduled;
            indexedArticleCount = 0;
          }
        } catch (PluginException ex) {
          log.error(
              "Failed to index metadata for full text URL: "
                  + af.getFullTextUrl(), ex);
          setFinished();
          if (status == ReindexingStatus.success) {
            status = ReindexingStatus.failed;
            indexedArticleCount = 0;
          }
        } catch (RuntimeException ex) {
          log.error(
              " Caught unexpected Throwable for full text URL: "
                  + af.getFullTextUrl(), ex);
          indexedArticleCount = 0;
        }
      }

      if (!isFinished()) {
        // finished if all articles handled
        if (!articleIterator.hasNext()) {
          setFinished();
        }
      }
      return extracted.intValue();
    }

    /**
     * Issue warning this reindexing task.
     * 
     * @param s
     *          the warning messsage
     */
    private void taskWarning(String s) {
      int hashcode = s.hashCode();
      if (auLogTable.add(hashcode)) {
        log.warning(s);
      }
    }

    /**
     * Validate metadata info fields.
     * 
     * @param mdinfo the ArticleMetadataInfo
     * @throws ValidationException if field is invalid
     */
    private void validateArticleMetadataInfo(ArticleMetadataInfo mdinfo)
      throws ValidationException {
      
      if (mdinfo.accessUrl != null) {
        if (mdinfo.accessUrl.length() > MAX_ACCESS_URL_FIELD) {
          throw new ValidationException(  
              "accessUrl too long '" + mdinfo.accessUrl
              + "' for title: '" + mdinfo.journalTitle
              + "' publisher: " + mdinfo.publisher +"'");
        }
      }

      if (mdinfo.isbn != null) {
        if (!MetadataUtil.isIsbn(mdinfo.isbn)) {
          throw new ValidationException(  
                "invalid isbn '" + mdinfo.isbn
                + "' for title: '" + mdinfo.journalTitle
                + "' publisher: " + mdinfo.publisher
                + "' accessUrl: " + mdinfo.accessUrl);
        }
      }
      
      if (mdinfo.eisbn != null) {
        if (!MetadataUtil.isIsbn(mdinfo.eisbn)) {
          throw new ValidationException(  
                "invalid eisbn '" + mdinfo.eisbn
                + "' for title: '" + mdinfo.journalTitle
                + "' publisher: " + mdinfo.publisher
                + "' accessUrl: " + mdinfo.accessUrl);
        }
      }

      if (mdinfo.issn != null) {
        if (!MetadataUtil.isIssn(mdinfo.issn)) {
          throw new ValidationException(  
                "invalid issn: '" + mdinfo.issn
              + "' for title: '" + mdinfo.journalTitle
              + "' publisher: " + mdinfo.publisher
              + "' accessUrl: " + mdinfo.accessUrl);
        }
      }
      
      if (mdinfo.eissn != null) {
        if (!MetadataUtil.isIssn(mdinfo.eissn)) {
          throw new ValidationException(  
                "invalid eissn '" + mdinfo.eissn
                + "' for title: '" + mdinfo.journalTitle
                + "' publisher: " + mdinfo.publisher
                + "' accessUrl: " + mdinfo.accessUrl);
        }
      }

      if (mdinfo.doi != null) {
        if (!MetadataUtil.isDoi(mdinfo.doi)) {
          throw new ValidationException(  
              "invalid doi '" + mdinfo.doi
              + "' for title: '" + mdinfo.journalTitle
              + "' publisher: " + mdinfo.publisher
              + "' accessUrl: " + mdinfo.accessUrl);
        }
      }

      if (mdinfo.pubDate != null) {
        if (mdinfo.pubDate.length() > MAX_DATE_FIELD) {
          throw new ValidationException(  
              "pubDate too long '" + mdinfo.pubDate
              + "' for title: '" + mdinfo.journalTitle
              + "' publisher: " + mdinfo.publisher
              + "' accessUrl: " + mdinfo.accessUrl);
        }
      }
      
      if (mdinfo.volume != null) {
        if (mdinfo.volume.length() > MAX_VOLUME_FIELD) {
          throw new ValidationException(  
              "volume too long '" + mdinfo.pubDate
              + "' for title: '" + mdinfo.journalTitle
              + "' publisher: " + mdinfo.publisher
              + "' accessUrl: " + mdinfo.accessUrl);
        }
      }

      if (mdinfo.issue != null) {
        if (mdinfo.pubDate.length() > MAX_DATE_FIELD) {
          throw new ValidationException(  
              "issue too long '" + mdinfo.issue
              + "' for title: '" + mdinfo.journalTitle
              + "' publisher: " + mdinfo.publisher
              + "' accessUrl: " + mdinfo.accessUrl);
        }
      }

      if (mdinfo.startPage != null) {
        if (mdinfo.startPage.length() > MAX_STARTPAGE_FIELD) {
          throw new ValidationException(  
              "startPage too long '" + mdinfo.startPage
              + "' for title: '" + mdinfo.journalTitle
              + "' publisher: " + mdinfo.publisher
              + "' accessUrl: " + mdinfo.accessUrl);
        }
      }

      if (mdinfo.articleTitle != null) {
        if (mdinfo.articleTitle.length() > MAX_ATITLE_FIELD) {
          throw new ValidationException(  
              "article title too long '" + mdinfo.articleTitle
              + "' for title: '" + mdinfo.journalTitle
              + "' publisher: " + mdinfo.publisher
              + "' accessUrl: " + mdinfo.accessUrl);
        }
      }

      if (mdinfo.authors != null) {
        if (mdinfo.authors.length() > MAX_AUTHOR_FIELD) {
          throw new ValidationException(  
              "authors too long '" + mdinfo.authors
              + "' for title: '" + mdinfo.journalTitle
              + "' publisher: " + mdinfo.publisher
              + "' accessUrl: " + mdinfo.accessUrl);
        }
      }
    }
    
    /**
     * Add metadata for this archival unit.
     * 
     * @param conn the connection
     * @param md the metadata
     * @throws SQLException if failed to add rows for metadata
     * @throws ValidationException if ArticleMetadataInfo has invalid data
     */
    private void addMetadata(ArticleMetadataInfo mdinfo) 
      throws SQLException, ValidationException {

      // first, validate all metadata info fields
      validateArticleMetadataInfo(mdinfo);
      
      HashSet<String> isbns = new HashSet<String>();
      if (mdinfo.isbn != null) {
        isbns.add(mdinfo.isbn);
      }
      
      if (mdinfo.eisbn != null) {
        isbns.add(mdinfo.eisbn);
      }
      
      HashSet<String> issns = new HashSet<String>();
      if (mdinfo.issn != null) {
        issns.add(mdinfo.issn);
      }
      
      if (mdinfo.eissn != null) {
        issns.add(mdinfo.eissn);
      }

      // validate data against TDB information
      if (tdbau != null) {
        // validate journal title against tdb journal title
        if (tdbauJournalTitle != null) {
          if (!tdbauJournalTitle.equals(mdinfo.journalTitle)) {
            if (mdinfo.journalTitle == null) {
              taskWarning("tdb title  is " + tdbauJournalTitle + " for "
                  + tdbauName + " -- metadata title is missing");
            } else {
              taskWarning("tdb title " + tdbauJournalTitle + " for "
                  + tdbauName + " -- does not match metadata journal title "
                  + mdinfo.journalTitle);
            }
          }
        }

        // validate isbn against tdb isbn
        if (tdbauIsbn != null) {
          if (!tdbauIsbn.equals(mdinfo.isbn)) {
            isbns.add(tdbauIsbn);
            if (mdinfo.isbn == null) {
              taskWarning("using tdb isbn " + tdbauIsbn + " for " + tdbauName
                  + " -- metadata isbn missing");
            } else {
              taskWarning("also using tdb isbn " + tdbauIsbn + " for "
                  + tdbauName + " -- different than metadata isbn: "
                  + mdinfo.isbn);
            }
          } else if (mdinfo.isbn != null) {
            taskWarning("tdb isbn missing for " + tdbauName + " -- should be: "
                + mdinfo.isbn);
          }
        } else if (mdinfo.isbn != null) {
          if (isTitleInTdb) {
            taskWarning("tdb isbn missing for " + tdbauName + " -- should be: "
                + mdinfo.isbn);
          }
        }

        // validate issn against tdb issn
        if (tdbauIssn != null) {
          if (tdbauIssn.equals(mdinfo.eissn) && (mdinfo.issn == null)) {
            taskWarning("tdb print issn " + tdbauIssn + " for " + tdbauName
                + " -- reported by metadata as eissn");
          } else if (!tdbauIssn.equals(mdinfo.issn)) {
            // add both ISSNs so it can be found either way
            issns.add(tdbauIssn);
            if (mdinfo.issn == null) {
              taskWarning("using tdb print issn " + tdbauIssn + " for "
                  + tdbauName + " -- metadata print issn is missing");
            } else {
              taskWarning("also using tdb print issn " + tdbauIssn + " for "
                  + tdbauName + " -- different than metadata print issn: "
                  + mdinfo.issn);
            }
          }
        } else if (mdinfo.issn != null) {
          if (mdinfo.issn.equals(tdbauEissn)) {
            taskWarning("tdb eissn " + tdbauEissn + " for " + tdbauName
                + " -- reported by metadata as print issn");
          } else if (isTitleInTdb) {
            taskWarning("tdb issn missing for " + tdbauName + " -- should be: "
                + mdinfo.issn);
          }
        }

        // validate eissn against tdb eissn
        if (tdbauEissn != null) {
          if (tdbauEissn.equals(mdinfo.issn) && (mdinfo.eissn == null)) {
            taskWarning("tdb eissn " + tdbauEissn + " for " + tdbauName
                + " -- reported by metadata as print issn");
          } else if (!tdbauEissn.equals(mdinfo.eissn)) {
            // add both ISSNs so it can be found either way
            issns.add(tdbauEissn);
            if (mdinfo.eissn == null) {
              taskWarning("using tdb eissn " + tdbauEissn + " for " + tdbauName
                  + " -- metadata eissn is missing");
            } else {
              taskWarning("also using tdb eissn " + tdbauEissn + " for "
                  + tdbauName + " -- different than metadata eissn: "
                  + mdinfo.eissn);
            }
          }
        } else if (mdinfo.eissn != null) {
          if (mdinfo.eissn.equals(tdbauIssn)) {
            taskWarning("tdb print issn " + tdbauIssn + " for " + tdbauName
                + " -- reported by metadata as print eissn");
          } else if (isTitleInTdb) {
            taskWarning("tdb eissn missing for " + tdbauName
                + " -- should be: " + mdinfo.eissn);
          }
        }

        // validate publication date against tdb year
        String pubYear = mdinfo.pubYear;
        if (pubYear != null) {
          if (!tdbau.includesYear(mdinfo.pubYear)) {
            if (tdbauYear != null) {
              taskWarning("tdb year " + tdbauYear + " for " + tdbauName
                  + " -- does not match metadata year " + pubYear);
            } else {
              taskWarning("tdb year missing for " + tdbauName
                  + " -- should include year " + pubYear);
            }
          }
        } else {
          pubYear = tdbauStartYear;
          if (mdinfo.pubYear != null) {
            taskWarning("using tdb start year " + mdinfo.pubYear + " for "
                + tdbauName + " -- metadata year is missing");
          }
        }
      }

      // insert common data into metadata table
      long mdid;
      PreparedStatement insertMetadata = conn.prepareStatement("insert into "
          + METADATA_TABLE + " " + "values (default,?,?,?,?,?,?,?,?,?)",
          Statement.RETURN_GENERATED_KEYS);
      ResultSet resultSet = null;
      try {
        // TODO PJG: Keywords???
        // skip auto-increment key field #0
        insertMetadata.setString(1, mdinfo.pubDate);
        insertMetadata.setString(2, mdinfo.volume);
        insertMetadata.setString(3, mdinfo.issue);
        insertMetadata.setString(4, mdinfo.startPage);
        insertMetadata.setString(5, mdinfo.articleTitle);
        insertMetadata.setString(6, mdinfo.authors);
        insertMetadata.setString(7, pluginId);
        insertMetadata.setString(8, auKey);
        insertMetadata.setString(9, mdinfo.accessUrl);
        insertMetadata.executeUpdate();
        resultSet = insertMetadata.getGeneratedKeys();
        if (!resultSet.next()) {
          log.error("Unable to create metadata entry for auid: " + auid);
          return;
        }
        mdid = resultSet.getLong(1);
        if (log.isDebug3()) {
          log.debug3("added [accessURL:" + mdinfo.accessUrl + ", md_id: " + mdid
              + ", date: " + mdinfo.pubDate + ", vol: " + mdinfo.volume
              + ", issue: " + mdinfo.issue + ", page: " + mdinfo.startPage
              + ", pluginId:" + pluginId + "]");
        }
      } finally {
        dbManager.safeCloseResultSet(resultSet);
        insertMetadata.close();
      }

      // insert row for DOI
      String doi = mdinfo.doi;
      if (doi != null) {
        if (StringUtil.startsWithIgnoreCase(doi, "doi:")) {
          doi = doi.substring(4);
        }

        /*
         * "doi VARCHAR(MAX_DOI_FIELD) PRIMARY KEY NOT NULL," +
         * "md_id INTEGER NOT NULL," +
         * "plugin_id VARCHAR(MAX_PLUGIN_ID_FIELD) NOT NULL" + // partition by
         */
        PreparedStatement insertDOI = conn.prepareStatement("insert into "
            + DOI_TABLE + " " + "values (?,?)");
        try {
          insertDOI.setString(1, doi);
          insertDOI.setLong(2, mdid);
          insertDOI.executeUpdate();
          log.debug3("added [doi:" + doi + ", md_id: " + mdid + ", pluginId:"
              + pluginId + "]");
        } finally {
          insertDOI.close();
        }
      }

      // insert row for ISBN
      if (!isbns.isEmpty()) {
        PreparedStatement insertISBN = conn.prepareStatement("insert into "
            + ISBN_TABLE + " " + "values (?,?)");
        try {
          insertISBN.setLong(2, mdid);
          for (String anIsbn : isbns) {
            anIsbn = anIsbn.replaceAll("-", "");
            insertISBN.setString(1, anIsbn);
            insertISBN.executeUpdate();
            log.debug3("added [isbn:" + anIsbn + ", md_id: " + mdid
                + ", pluginId:" + pluginId + "]");
          }
        } finally {
          insertISBN.close();
        }
      }

      // insert rows for ISSN
      if (!issns.isEmpty()) {
        PreparedStatement insertISSN = conn.prepareStatement("insert into "
            + ISSN_TABLE + " " + "values (?,?)");
        try {
          insertISSN.setLong(2, mdid);
          for (String anIssn : issns) {
            anIssn = anIssn.replaceAll("-", "");
            insertISSN.setString(1, anIssn);
            insertISSN.executeUpdate();
            log.debug3("added [issn:" + anIssn + ", md_id: " + mdid
                + ", pluginId:" + pluginId + "]");
          }
        } finally {
          insertISSN.close();
        }
      }

      // insert row for title only if the title is not available from tdbau
      if ((tdbauJournalTitle == null)
          && !StringUtil.isNullString(mdinfo.journalTitle)) {
        PreparedStatement insertTitle = conn.prepareStatement("insert into "
            + TITLE_TABLE + " " + "values (?,?)");
        try {
          // truncate to MAX_TITLE_FIELD for database
          String title = mdinfo.journalTitle.substring(0,
              Math.min(mdinfo.journalTitle.length(), MAX_TITLE_FIELD));
          insertTitle.setString(1, title);
          insertTitle.setLong(2, mdid);
          insertTitle.executeUpdate();
          log.debug3("added [title:'" + mdinfo.journalTitle + "', md_id: " + mdid
              + ", pluginId:" + pluginId + "]");
        } finally {
          insertTitle.close();
        }
      }
    }

    /**
     * Temporary
     * 
     * @param evt
     */
    protected void callCallback(Schedule.EventType evt) {
      callback.taskEvent(this, evt);
    }
  }

  /**
   * Remove all articles for an AU from the pending Aus table.
   * 
   * @param auId the pending AuId
   * @return the number of articles deleted
   * @throws SQLException
   *           if unable to delete pending AU
   */
  private int removeMetadata(Connection conn, String auId) throws SQLException {
    PreparedStatement deletePendingAu = conn.prepareStatement("delete from "
        + METADATA_TABLE + " where " + PLUGIN_ID_FIELD + " = ? and "
        + AU_KEY_FIELD + " = ?");
    try {
      String pluginId = PluginManager.pluginIdFromAuId(auId);
      String auKey = PluginManager.auKeyFromAuId(auId);
  
      deletePendingAu.setString(1, pluginId);
      deletePendingAu.setString(2, auKey);
      int count = deletePendingAu.executeUpdate();
      return count;
    } finally {
      deletePendingAu.close();
    }
  }

  /**
   * Notify listeners that an AU has been removed.
   * 
   * @param auId the AuId of the AU that was removed
   * @param articleCount the number of articles deleted
   */
  protected void notifyDeletedAu(String auId, int articleCount) {
  }

  /**
   * 
   * Notify listeners that an AU is being reindexed.
   * 
   * @param au the AU
   */
  protected void notifyStartReindexingAu(ArchivalUnit au) {
  }

  /**
   * Notify listeners that an AU is finshed being reindexed.
   * 
   * @param au
   */
  protected void notifyFinishReindexingAu(ArchivalUnit au,
      ReindexingStatus status) {
  }

  /**
   * Get list of AuIds that require reindexing.
   * 
   * @param conn the database connection
   * @param maxAus the maximum number of AuIds to return
   * @return list of AuIds
   */
  List<String> getAuIdsToReindex(Connection conn, int maxAuIds) {
    ArrayList<String> auIds = new ArrayList<String>();
    if (pluginMgr != null) {
      Statement selectPendingAus = null;
      ResultSet results = null;
      try {
        selectPendingAus = conn.createStatement();

        results = selectPendingAus.executeQuery("select * from "
            + PENDINGAUS_TABLE);
        while ((auIds.size() < maxAuIds) && results.next()) {
          String pluginId = results.getString(1);
          String auKey = results.getString(2);
          String auId = PluginManager.generateAuId(pluginId, auKey);
          if (!activeReindexingTasks.containsKey(auId)) {
            auIds.add(auId);
          }
        }
      } catch (SQLException ex) {
        log.error(ex.getMessage(), ex);
      } finally {
        dbManager.safeCloseResultSet(results);
        dbManager.safeCloseStatement(selectPendingAus);
      }
    }
    auIds.trimToSize();
    return auIds;
  }

  /**
   * Run the specified reindexing task.
   * <p>
   * Temporary implementation runs as a LockssRunnable in a thread rather than
   * using the SchedService.
   * 
   * @param task the reindexing task
   */
  private void runReindexingTask(final ReindexingTask task) {
    /*
     * Temporarily running task in its own thread rather than using SchedService
     * 
     * @todo Update SchedService to handle this case
     */
    LockssRunnable runnable = new LockssRunnable(makeThreadName(task)) {

      public void lockssRun() {
        task.callCallback(Schedule.EventType.START);
        while (!task.isFinished()) {
          task.step(Integer.MAX_VALUE);
        }
        task.callCallback(Schedule.EventType.FINISH);
      }
    };
    Thread runThread = new Thread(runnable);
    runThread.start();
  }

  private String makeThreadName(ReindexingTask task) {
    return AuUtil.getThreadNameFor("Reindexing", task.au);
  }

  /**
   * Cancel a reindexing task for the specified AU.
   * 
   * @param auId the AU id
   * @return <code>true</code> if task was canceled
   */
  private boolean cancelAuTask(String auId) {
    ReindexingTask task = activeReindexingTasks.get(auId);
    if (task != null) {
      // task cancellation will remove task and schedule next one
      log.debug2("Canceling pending reindexing task for auId " + auId);
      task.cancel();
      return true;
    }
    return false;
  }

  /**
   * Reschedule a reindexing task for the specified AU.
   * 
   * @param auId the AU id
   * @return <code>true</code> if task was rescheduled
   */
  private boolean rescheduleAuTask(String auId) {
    ReindexingTask task = activeReindexingTasks.get(auId);
    if (task != null) {
      // task rescheduling will remove task, and cause it to be rescheduled
      log.debug2("Rescheduling pending reindexing task for auId " + auId);
      task.reschedule();
      return true;
    }
    return false;
  }

  /**
   * This method cancels any running tasks associated with the Au, and then
   * deletes the metadata for the specified AU.
   * 
   * @param auId
   *          the Au id to delete
   * @return the number of articles deleted
   */
  private int deleteAu(Connection conn, String auId) throws SQLException {
    cancelAuTask(auId);

    // remove from the history list
    removeFromHistory(auId);

    // remove the metadata for this AU
    int articleCount = removeMetadata(conn, auId);

    // remove pending reindexing operations for deleted AU
    removeFromPendingAus(conn, auId);

    conn.commit();

    notifyDeletedAu(auId, articleCount);

    return articleCount;
  }

  /**
   * Set maximum reindexing task history list size.
   * 
   * @param maxSize
   *          the maximum reindexing task history list size
   */
  public void setMaxHistory(int maxSize) {
    maxReindexingTaskHistory = maxSize;
    synchronized (reindexingTaskHistory) {
      while (reindexingTaskHistory.size() > maxReindexingTaskHistory) {
        reindexingTaskHistory.remove(maxReindexingTaskHistory);
      }
    }
  }

  /**
   * Add task to history.
   * 
   * @param task
   *          the task
   */
  private void addToHistory(ReindexingTask task) {
    synchronized (reindexingTaskHistory) {
      reindexingTaskHistory.add(0, task);
      setMaxHistory(maxReindexingTaskHistory);
    }
  }

  /**
   * Remove tasks with specified auid from history
   * 
   * @param auId
   *          the auid
   * @return the number of items removed
   */
  private int removeFromHistory(String auId) {
    int count = 0;
    synchronized (reindexingTaskHistory) {
      // remove tasks with this auid from task history list
      for (Iterator<ReindexingTask> itr = reindexingTaskHistory.iterator(); itr
          .hasNext();) {
        ReindexingTask task = itr.next();
        if (auId.equals(task.getAu().getAuId())) {
          itr.remove();
          count++;
        }
      }
    }
    return count;
  }

  /**
   * This method adds an AU to be reindexed.
   * 
   * @param au
   *          the AU to add.
   * @return <code>true</code> if au was added for reindexing
   */
  public boolean addAuToReindex(ArchivalUnit au) {
    synchronized (activeReindexingTasks) {
      Connection conn = null;
      try {
        log.debug2("Adding au to reindex: " + au.getName());
        // add pending AU
        conn = dbManager.getConnection();
        if (conn == null) {
          log.error("Cannot connect to database"
              + " -- cannot add aus to pending aus");
          return false;
        }

        // if disabled crawl completion rescheduling
        // a running task, have this function report false;
        if (   disableCrawlRescheduleTask
            && activeReindexingTasks.containsKey(au.getAuId())) {
          return false;
        }
        
        // if can't reschedule current task, add AU to pending list
        if (!rescheduleAuTask(au.getAuId())) {
          addToPendingAus(conn, Collections.singleton(au));
          conn.commit();
          startReindexing(conn);
        }
        return true;
      } catch (SQLException ex) {
        log.error("Cannot add au to pending AUs: " + au.getName(), ex);
        return false;
      } finally {
        dbManager.safeRollbackAndClose(conn);
      }
    }
  }

  /**
   * This method removes an AU from being reindexed.
   * 
   * @param au
   *          the AU to add.
   * @return <code>true</code> if au was added for reindexing
   */
  public boolean removeAuForReindex(ArchivalUnit au) {
    synchronized (activeReindexingTasks) {
      Connection conn = null;
      try {
        log.debug2("Removing au to reindex: " + au.getName());
        // add pending AU
        conn = dbManager.getConnection();
        if (conn == null) {
          log.error("Cannot connect to database"
              + " -- cannot add aus to pending aus");
          return false;
        }
        deleteAu(conn, au.getAuId());
        conn.commit();
        // force reindexing to start next task
        startReindexing(conn);
        return true;
      } catch (SQLException ex) {
        log.error("Cannot add au to pending AUs: " + au.getName(), ex);
        return false;
      } finally {
        dbManager.safeRollbackAndClose(conn);
      }
    }
  }

  /**
   * Add AUs to list of pending AUs to reindex.
   * 
   * @param conn
   *          the connection
   * @param aus
   *          the AUs to add
   * @throws SQLException
   *           of unable to add AUs to pending AUs
   */
  private void addToPendingAus(Connection conn, Collection<ArchivalUnit> aus)
      throws SQLException {
    // prepare statement for inserting multiple AUs
    // (should this be saved forever?)
    PreparedStatement insertPendingAu = conn.prepareStatement("insert into "
        + PENDINGAUS_TABLE + " values (?,?)");
    PreparedStatement selectPendingAu = conn.prepareStatement("select * from "
        + PENDINGAUS_TABLE + " where " + PLUGIN_ID_FIELD + " = ? and "
        + AU_KEY_FIELD + " = ?");

    ResultSet results = null;
    log.debug2("number of pending aus to add: " + aus.size());
    try {
      // add an AU to the list of pending AUs
      for (ArchivalUnit au : aus) {
        // only add for extraction iff it has article metadata
        if (!hasArticleMetadata(au)) {
          log.debug3("not adding au " + au.getName()
              + " to pending list because it has no metadata");
        } else {
          String auid = au.getAuId();
          String pluginId = PluginManager.pluginIdFromAuId(auid);
          String auKey = PluginManager.auKeyFromAuId(auid);
  
          // only insert if entry does not exist
          selectPendingAu.setString(1, pluginId);
          selectPendingAu.setString(2, auKey);
          results = selectPendingAu.executeQuery();
          if (!results.next()) {
            log.debug3("adding au " + au.getName() + " to pending list");
            insertPendingAu.setString(1, pluginId);
            insertPendingAu.setString(2, auKey);
            insertPendingAu.addBatch();
          } else {
            log.debug3("Not adding au " + au.getName()
                + " to pending list becuase it is already on the list");
          }
        }
      }
      insertPendingAu.executeBatch();
    } finally {
      dbManager.safeCloseResultSet(results);
      insertPendingAu.close();
      selectPendingAu.close();
    }

    pendingAusCount = getPendingAusCount(conn);
  }

  /**
   * Remove an AU from the pending Aus table.
   * 
   * @param conn
   *          the connection
   * @param au
   *          the pending AU
   * @throws SQLException
   *           if unable to delete pending AU
   */
  private void removeFromPendingAus(Connection conn, String auId)
      throws SQLException {
    PreparedStatement deletePendingAu = conn.prepareStatement("delete from "
        + PENDINGAUS_TABLE + " where " + PLUGIN_ID_FIELD + " = ? and "
        + AU_KEY_FIELD + " = ?");
    try {
      String pluginId = PluginManager.pluginIdFromAuId(auId);
      String auKey = PluginManager.auKeyFromAuId(auId);
  
      deletePendingAu.setString(1, pluginId);
      deletePendingAu.setString(2, auKey);
      deletePendingAu.execute();
    } finally {
      deletePendingAu.close();
    }

    pendingAusCount = getPendingAusCount(conn);
  }
}