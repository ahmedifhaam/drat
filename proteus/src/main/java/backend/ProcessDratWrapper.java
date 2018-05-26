/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package backend;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;

import drat.proteus.workflow.rest.DynamicWorkflowRequestWrapper;
import drat.proteus.workflow.rest.WorkflowRestResource;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.time.DurationFormatUtils;
import org.apache.oodt.cas.filemgr.structs.Product;
import org.apache.oodt.cas.filemgr.structs.ProductPage;
import org.apache.oodt.cas.filemgr.structs.ProductType;
import org.apache.oodt.cas.filemgr.tools.DeleteProduct;
import org.apache.oodt.cas.filemgr.tools.SolrIndexer;
import org.apache.oodt.cas.metadata.util.PathUtils;

import org.apache.oodt.cas.workflow.system.WorkflowManagerClient;
import org.apache.oodt.cas.workflow.system.rpc.RpcCommunicationFactory;
import org.apache.oodt.pcs.util.FileManagerUtils;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrServer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import java.util.logging.Logger;

public class ProcessDratWrapper extends GenericProcess
    implements AbstractDratWrapper {
  private static final Logger LOG = Logger
      .getLogger(ProcessDratWrapper.class.getName());
  private static final String DRAT = FileConstants.DRAT_PATH;
  private static final long DRAT_PROCESS_WAIT_DURATION = 3000;
  private static final int MAX_RESET_TRIES = 10;

  private static final String GO_CMD = "go";
  private static final String CRAWL_CMD = "crawl";
  private static final String INDEX_CMD = "index";
  private static final String MAP_CMD = "map";
  private static final String REDUCE_CMD = "reduce";
  private static final String RESET_CMD = "reset";
  private static final String STATUS_IDLE = "idle";

  private static final String MAPPER_TASK = "urn:drat:RatCodeAudit";
  private static final String[] WIPE_TYPES = { "RatLog", "GenericFile",
      "RatAggregateLog" };

  private String status;
  private FileManagerUtils fm;
  private String path;
  private static ProcessDratWrapper singletonDratWrapper = new ProcessDratWrapper();

  public static ProcessDratWrapper getInstance() {
    return singletonDratWrapper;
  }

  private ProcessDratWrapper() {
    super(DRAT);
    this.path = "";
    this.status = "IDLE";
    this.fm = new FileManagerUtils(
        PathUtils.replaceEnvVariables("[FILEMGR_URL]"));
  }

  public void setIndexablePath(String canonicalPath) {
    this.path = canonicalPath;
  }

  public String getIndexablePath() {
    return this.path;
  }

  public String getStatus() {
    return this.status;
  }

  public synchronized void setStatus(String status) {
    this.status = status;
  }

  @Override
  public void crawl() throws Exception {
    LOG.info("Starting crawling");
    simpleDratExec(CRAWL_CMD, this.path);
    LOG.info("Crawling finished");
  }

  @Override
  public void index() throws IOException, DratWrapperException, InstantiationException, SolrServerException {
      solrIndex();
  }

  private synchronized void solrIndex() throws InstantiationException, SolrServerException, IOException {
      setStatus(INDEX_CMD);
      DratLog idl = new DratLog("INDEXING");
      idl.logInfo("Starting", null);
      System.setProperty(FileConstants.SOLR_INDEXER_CONFIG,FileConstants.SOLR_INDEXER_CONFIG_PATH);
      SolrIndexer sIndexer = new SolrIndexer(FileConstants.SOLR_DRAT_URL,FileConstants.FILEMGR_URL);
      
      sIndexer.indexAll(false);
      sIndexer.commit();
      sIndexer.optimize();
      idl.logInfo("Completed",null);

  }

  @Override
  public void map() throws IOException, DratWrapperException {
//    simpleDratExec(MAP_CMD);
    setStatus(MAP_CMD);
    DratLog mapLog = new DratLog("MAPPING");
    WorkflowRestResource restResource = new WorkflowRestResource();
    DynamicWorkflowRequestWrapper requestBody = new DynamicWorkflowRequestWrapper();
    requestBody.taskIds = new ArrayList<>();
    requestBody.taskIds.add("urn:drat:MimePartitioner");
    LOG.info("STARTING MAPPING");
    mapLog.logInfo("STARTING", " (dynamic workflow with task urn:drat:MimePartitioner");
    String resp = (String)restResource.performDynamicWorkFlow(requestBody);
    if(resp.equals("OK")) {
        mapLog.logInfo("STARTED SUCCESSFULLY, urn:drat:MimePartitioner dynamic workflow");
    }else {
        mapLog.logSevere("FAILED", "Dynamic workflow starting failed "+resp);
    }   

  }
  

  @Override
  public void reduce() throws IOException, DratWrapperException {

//    simpleDratExec(REDUCE_CMD);
    setStatus(REDUCE_CMD);
    DratLog mapLog = new DratLog("REDUCING");
    WorkflowRestResource restResource = new WorkflowRestResource();
    DynamicWorkflowRequestWrapper requestBody = new DynamicWorkflowRequestWrapper();
    requestBody.taskIds = new ArrayList<>();
    requestBody.taskIds.add("urn:drat:RatAggregator");
    LOG.info("STARTING REDUCING");
    mapLog.logInfo("STARTING", " (dynamic workflow with task urn:drat:RatAggregator");
    String resp = (String)restResource.performDynamicWorkFlow(requestBody);
    if(resp.equals("OK")) {
        mapLog.logInfo("STARTED SUCCESSFULLY, urn:drat:RatAggregator dynamic workflow");
    }else {
        mapLog.logSevere("FAILED", "Dynamic workflow starting failed "+resp);
        throw new IOException(resp);
    }  
  }

  @Override
  public void reset() throws Exception {
    LOG.info("DRAT: reset: wiping FM product catalog");

    for (String type : WIPE_TYPES) {
      int numTries = 0;
      ProductType pType = fm.safeGetProductTypeByName(type);
      // make sure all products are actually deleted in case there
      // are references issues or XML-RPC issues.
      while (this.fm.safeGetNumProducts(pType) > 0
          && numTries <= MAX_RESET_TRIES) {
        this.wipeProductType(type);
        numTries++;
      }

      if (numTries == MAX_RESET_TRIES
          && this.fm.safeGetNumProducts(pType) > 0) {
        LOG.warning("Unable to fully wipe type: [" + type + "]. Tried ["
            + String.valueOf(numTries) + "] times. Max attempts: ["
            + String.valueOf(MAX_RESET_TRIES)
            + "]. Is your File Manager corrupt?");
      }
    }

    LOG.info("DRAT: reset: wiping WM instance repository.");
    String wmUrl = PathUtils.replaceEnvVariables("[WORKFLOW_URL]");
    this.wipeInstanceRepo(wmUrl);

    String coreName = "drat";
    LOG.info("DRAT: reset: wiping Solr core: [" + coreName + "]");
    this.wipeSolrCore(coreName);

    LOG.info("DRAT: reset: recursively removed: [" + Utils.getResetDirectories()
        + "]");
    for (String dir : Utils.getResetDirectories()) {
      File file = new File(dir);
      if (file.exists()) {
        try {
          LOG.info(
              "DRAT: reset: removing dir: [" + file.getAbsolutePath() + "]");
          FileUtils.forceDelete(file);
        } catch (FileNotFoundException e) {
          LOG.warning("Error removing: [" + file.getAbsolutePath()
              + "]: Message: " + e.getLocalizedMessage());
        } catch (IOException e) {
          LOG.warning("Unable to remove file: [" + file.getAbsolutePath()
              + "]: Message: " + e.getLocalizedMessage());
        }
      }
    }

  }

  public void go() throws Exception {
    // before go, always reset
    this.reset();
    this.crawl();
    this.index();
    this.map();

    //Logs for understanding 
    LOG.info("SYSTEM PROPS");
    LOG.info("java.util.logging.config.file" +" : "+ System.getProperty("java.util.logging.config.file"));
    LOG.info("org.apache.oodt.cas.cli.action.spring.config : "+System.getProperty("org.apache.oodt.cas.cli.action.spring.config"));
    LOG.info("org.apache.oodt.cas.cli.option.spring.config : "+System.getProperty("org.apache.oodt.cas.cli.option.spring.config"));
    
    
    
    System.setProperty("org.apache.oodt.cas.cli.action.spring.config","file:/home/xelvias/drat/deploy/workflow/policy/cmd-line-actions.xml");
    System.setProperty("org.apache.oodt.cas.cli.option.spring.config", "file:/home/xelvias/drat/deploy/workflow/policy/cmd-line-options.xml");
    
    LOG.info("org.apache.oodt.cas.cli.action.spring.config : "+System.getProperty("org.apache.oodt.cas.cli.action.spring.config"));
    LOG.info("org.apache.oodt.cas.cli.option.spring.config : "+System.getProperty("org.apache.oodt.cas.cli.option.spring.config"));
    //undestanding logs ends
    
    
    // don't run reduce until all maps are done
    while (mapsStillRunning()) {
      Thread.sleep(DRAT_PROCESS_WAIT_DURATION);
      LOG.info("MAP STILL RUNNING");
    }
    // you're not done until the final log is generated.
    while (!hasAggregateRatLog()) {
      try {
        reduce();
        LOG.info("REDUCE STILL RUNNING");
      } catch (IOException e) {
        LOG.warning("Fired reduce off before mappers were done. Sleeping: ["
            + String.valueOf(DRAT_PROCESS_WAIT_DURATION / 1000)
            + "] seconds and will try again.");
      }
      Thread.sleep(DRAT_PROCESS_WAIT_DURATION);
    }

    setStatus(STATUS_IDLE);
  }

  public synchronized void simpleDratExec(String command, String... options)
      throws IOException, DratWrapperException {
    setStatus(command);
    String args[] = { FileConstants.DRAT_PATH, command };
    String all[] = (String[]) ArrayUtils.addAll(args, options);
    String cmd = Joiner.on(" ").join(all);

    LOG.fine(String.format("Running command [%s]", cmd));
    String output = null;
    try {
      output = execToString(cmd);
    } catch (IOException e) {
      LOG.warning("Executing DRAT cmd: [" + command + "]: command line: [" + cmd
          + "] generated non-zero exit status. output is: [" + output
          + "]: Message: " + e.getLocalizedMessage());
      throw e;
    } catch (Exception e) {
      LOG.warning("Exception executing " + command + ". Output: [" + output
          + "]: Message: " + e.getLocalizedMessage());
      throw new IOException(e.getLocalizedMessage());
    }

    LOG.info(
        "Command: [" + command + "] completed normally. Output is: " + output);
  }

  private synchronized boolean hasAggregateRatLog() {
    int numLogs = -1;
    ProductType type = this.fm.safeGetProductTypeByName("RatAggregateLog");
    numLogs = this.fm.safeGetNumProducts(type);
    String breakStatus = (numLogs > 0) ? "breaking" : "looping";
    LOG.info("Checking for RatAggregateLog: num: [" + String.valueOf(numLogs)
        + "]: " + breakStatus);
    return numLogs > 0;
  }

  private boolean mapsStillRunning() throws Exception {
    String args[] = { FileConstants.WORKFLOW_PATH, "--url",
        "http://localhost:9001", "--operation", "--getWorkflowInsts" };
    String cmd = Joiner.on(" ").join(args);
    LOG.info("Maps Still Running: Executing: " + cmd);
    String output = execToString(cmd);
    LOG.info("Output from maps still running: " + output);
    List<WorkflowItem> items = parseWorkflows(output);
    return stillRunning(items);
  }


  @VisibleForTesting
  protected List<WorkflowItem> parseWorkflows(String cmdOutput) {
    List<WorkflowItem> items = new ArrayList<WorkflowItem>();

    String lines[] = cmdOutput.split("\\r?\\n");
    if (lines != null && lines.length > 0) {
      int lineNo = 1;
      for (String line : lines) {
        if (line == null || (line != null && line.trim().equals(""))) {
          LOG.info("Blank line in evaluating workflow instance response: ["
              + line + "]: skipping lineNo: [" + String.valueOf(lineNo) + "]");
          continue;
        }

        if (!line.startsWith("Instance:")) {
          LOG.info("Skipping line: does not begin with Instance: [" + line
              + "]: lineNo: [" + String.valueOf(lineNo) + "]");
          continue;
        }

        String[] tmpLine = line.split("\\[");
        if (tmpLine != null && tmpLine.length > 0) {
          String instInfo = tmpLine[1].trim();
          instInfo = instInfo.substring(0, instInfo.length() - 1); // chop ']'
          String[] instToks = instInfo.split(",");
          if (instToks != null && instToks.length > 0) {
            WorkflowItem item = new WorkflowItem();
            item.setId(cleanAndSplit(instToks[0]));
            item.setStatus(cleanAndSplit(instToks[1]));
            item.setCurrentTask(cleanAndSplit(instToks[2]));
            item.setWorkflowName(cleanAndSplit(instToks[3]));
            item.setWallClockTime(cleanAndSplit(instToks[4]));
            item.setCurrentTaskWallClock(cleanAndSplit(instToks[5]));
            items.add(item);
          }
        }

        lineNo++;
      }
    }
    return items;
  }

  @VisibleForTesting
  protected boolean stillRunning(List<WorkflowItem> items) {
    List<WorkflowItem> mapperItems = filterMappers(items);
    LOG.info("Checking mappers: inspecting ["
        + String.valueOf(mapperItems.size()) + "] mappers.");
    for (WorkflowItem mapperItem : mapperItems) {
      if (isRunning(mapperItem.getStatus())) {
        LOG.info("Mapper: [" + mapperItem.getId() + "] still running.");
        return true;
      }
    }

    return false;
  }

  @VisibleForTesting
  protected List<WorkflowItem> filterMappers(List<WorkflowItem> items) {
    List<WorkflowItem> mappers = new ArrayList<WorkflowItem>();
    if (items != null && items.size() > 0) {
      for (WorkflowItem item : items) {
        if (item.getCurrentTask().equals(MAPPER_TASK)) {
          LOG.info("Adding mapper: [" + item.getCurrentTask() + "]");
          mappers.add(item);
        } else {
          LOG.info("Filtering task: [" + item.getCurrentTask() + "]");
        }
      }
    }

    return mappers;
  }

  @VisibleForTesting
  protected boolean isRunning(String status) {
    List<String> runningStates = Arrays.asList("CREATED", "QUEUED", "STARTED",
        "RSUBMIT", "PGE EXEC", "STAGING INPUT", "CRAWLING");
    List<String> finishedStates = Arrays.asList("PAUSED", "METMISS",
        "FINISHED");
    if (finishedStates.contains(status)) {
      return false;
    } else {
      if (runningStates.contains(status)) {
        return true;
      } else {
        // it's not in running or finished, so we'll assume
        // it's not running (dead)
        LOG.info("Unknown status: [" + status + "]: assuming finished.");
        return false;
      }
    }
  }

  private String cleanAndSplit(String s) {
    String[] sToks = s.split("=");
    if (sToks != null && sToks.length == 2) {
      return sToks[1].trim();
    } else
      return "";
  }

  private synchronized String execToString(String command) throws Exception {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    CommandLine commandline = CommandLine.parse(command);
    DefaultExecutor exec = new DefaultExecutor();
    PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream);
    int status = -1;
    exec.setStreamHandler(streamHandler);
    status = exec.execute(commandline);
    String output = outputStream.toString(Charset.defaultCharset().name());
    if (status != 0) {
      throw new IOException("Non-zero status message from executing: ["
          + command + "]: output: [" + output + "]");
    }
    return output;
  }

  private synchronized void wipeProductType(String productTypeName) {
    DeleteProduct dp = new DeleteProduct(this.fm.getFmUrl().toString(), true);
    ProductType type = this.fm.safeGetProductTypeByName(productTypeName);
    if (type == null) {
      LOG.warning("Unable to get product type definition for: ["
          + productTypeName + "]: FM wipe fails.");
      return;
    }
    LOG.info("Paging through products for product type: " + productTypeName);
    ProductPage page = this.fm.safeFirstPage(type);

    while (page != null) {
      LOG.info("Cleaning File Manager: Product Type: [" + productTypeName
          + "]: wiping [" + String.valueOf(page.getTotalPages())
          + "] pages of products: pageSize: [" + page.getPageSize() + "].");
      for (Product product : page.getPageProducts()) {
        dp.remove(product.getProductId());
      }

      if (page.isLastPage()) {
        break;
      }
      try {
        page = this.fm.getFmgrClient().getNextPage(type, page);
      } catch (Exception e) {
        e.printStackTrace();
        LOG.warning("Unable to obtain next page. Message: "
            + e.getLocalizedMessage() + " breaking loop.");
        break;
      }
    }

  }

  private synchronized void wipeInstanceRepo(String wmUrl) {
    try (WorkflowManagerClient wm = RpcCommunicationFactory.createClient(new URL(wmUrl))) {
      wm.refreshRepository();
    } catch (Exception e) {
      e.printStackTrace();
      LOG.warning("DRAT: reset: error communicating with the WM. Message: "
              + e.getLocalizedMessage());
    }
  }

  private synchronized void wipeSolrCore(String coreName) {
    String baseUrl = "http://localhost:8080/solr";
    String finalUrl = baseUrl + "/" + coreName;
    SolrServer server = null;
    try {
      server = new HttpSolrServer(finalUrl);
      server.deleteByQuery("*:*");
      server.commit();
    } catch (Exception e) {
      e.printStackTrace();
      LOG.warning("Error wiping Solr core: [" + coreName + "]: Message: "
          + e.getLocalizedMessage());
    }
  }

  private class DratLog{
      private static final String MODULE = "DRAT_LOG";
      long startTime =0;
      private long lastActionTime=-1L;
      private long timeDiff  =-1L;
      private ZonedDateTime zdt;
      private String action;
      public DratLog(String action) {
          this.action = action;

      }

      private void logWarning(String status,String desc) {
          LOG.warning(getMsg(status,desc));
      }

      private void logWarning(String desc) {
          LOG.warning(MODULE+" : "+desc);
      }

      private void logInfo(String status,String desc) {
          LOG.info(getMsg(status,desc));
      }

      private void logInfo(String desc) {
          LOG.info(MODULE+" : "+desc);
      }

      private void logSevere(String status,String desc) {
          LOG.fine(getMsg(status,desc));
      }

      private String getMsg(String status,String desc) {
          String basic = "";
          if(startTime==0) {
              startTime = System.currentTimeMillis();
              zdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(startTime), ZoneId.systemDefault());
              basic = String.format("%1$s : %2$s : %3$s, at time %4$s", MODULE,action,status,
                      zdt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
          }else {
              lastActionTime = System.currentTimeMillis();
              timeDiff = lastActionTime - startTime;
              zdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(startTime), ZoneId.systemDefault());
              basic =  String.format("%1$s : %2$s : %3$s, at time %4$s with duration %5$s", MODULE,action,status,
                      zdt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),DurationFormatUtils.formatDuration(timeDiff,"MM-dd T HH-mm-ss"));
          }

          if(desc==null) {
              return basic;
          }else {
              return String.format("%1$s : %2$s", basic,desc);
          }
      }


  }

}
