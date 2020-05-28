/*

Copyright (c) 2017-2020 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.plugin.respediatrica;

import org.dspace.xoai.model.oaipmh.Record;
import org.dspace.xoai.serviceprovider.exceptions.BadArgumentException;
import org.dspace.xoai.serviceprovider.exceptions.InvalidOAIResponse;
import org.dspace.xoai.serviceprovider.model.Context;
import org.dspace.xoai.serviceprovider.model.Context.KnownTransformer;
import org.dspace.xoai.serviceprovider.parameters.ListRecordsParameters;
import org.dspace.xoai.services.api.MetadataSearch;
import org.lockss.daemon.Crawler.CrawlerFacade;
import org.lockss.daemon.PluginException;
import org.lockss.plugin.ArchivalUnit.ConfigurationException;
import org.lockss.plugin.UrlCacher;
import org.lockss.plugin.UrlData;
import org.lockss.util.CIProperties;
import org.lockss.util.Constants;
import org.lockss.util.Logger;
import org.lockss.util.UrlUtil;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class ResPediatricaOaiCrawlSeed extends RecordFilteringOaiPmhCrawlSeed {
  public static final String DEFAULT_DATE_TAG = "dc.date";
  public static final String DEFAULT_IDENTIFIER_TAG = "dc.identifier";
  protected Collection<String> startUrls;
  protected int year;
  protected Pattern yearPattern = Pattern.compile("^([0-9]{4})-[0-9]{2}-[0-9]{2}");
  public static final String OAI_DC_METADATA_PREFIX = "oai_dc";
  private static Logger logger =
	      Logger.getLogger(ResPediatricaOaiCrawlSeed.class);

  public ResPediatricaOaiCrawlSeed(CrawlerFacade cf) {
    super(cf);
    setMetadataPrefix(OAI_DC_METADATA_PREFIX);
    setUrlPostfix("oai");
  }

  @Override
  protected Context buildContext(String url) {
    Context con = super.buildContext(url);
    con.withMetadataTransformer(OAI_DC_METADATA_PREFIX, KnownTransformer.OAI_DC);
    return con;
  }
  
  @Override
  protected void initialize() 
      throws PluginException, ConfigurationException, IOException {
    super.initialize();
  }

  /*
  15:04:08.520: Warning: 9-FollowLinkCrawler: Unexpected exception, should have been caught lower: org.dspace.xoai.serviceprovider.exceptions.InvalidOAIResponse: OAI responded with code: badArgument
    	at org.dspace.xoai.serviceprovider.parsers.ListRecordsParser.hasNext(ListRecordsParser.java:47)
	at org.dspace.xoai.serviceprovider.handler.ListRecordHandler.getNextIteration(ListRecordHandler.java:96)
	at org.dspace.xoai.serviceprovider.handler.ListRecordHandler.nextIteration(ListRecordHandler.java:56)
	at org.dspace.xoai.serviceprovider.lazy.ItemIterator.hasNext(ItemIterator.java:32)
	at org.lockss.plugin.respediatrica.ResPediatricaOaiCrawlSeed.getRecordList(ResPediatricaOaiCrawlSeed.java:103)
	at org.lockss.plugin.respediatrica.RecordFilteringOaiPmhCrawlSeed.doGetStartUrls(RecordFilteringOaiPmhCrawlSeed.java:165)
	at org.lockss.crawler.BaseCrawlSeed.getStartUrls(BaseCrawlSeed.java:85)
	at org.lockss.crawler.FollowLinkCrawler.enqueueStartUrls(FollowLinkCrawler.java:494)
	at org.lockss.crawler.FollowLinkCrawler.doCrawl0(FollowLinkCrawler.java:313)
	at org.lockss.crawler.BaseCrawler.doCrawl(BaseCrawler.java:403)
	at org.lockss.crawler.CrawlManagerImpl$CrawlRunner.lockssRun(CrawlManagerImpl.java:1470)
	at org.lockss.daemon.LockssRunnable.run(LockssRunnable.java:437)
	at EDU.oswego.cs.dl.util.concurrent.PooledExecutor$Worker.run(Unknown Source)
	at java.lang.Thread.run(Thread.java:748)
15:04:08.520: Debug3: 9-FollowLinkCrawler: Start URLs: []
   */
  @Override
  protected Collection<String> getRecordList(ListRecordsParameters params)
		  throws ConfigurationException, IOException {
      logger.debug3("Fei - auid: " + au.getAuId() + ", encoded auid:" + UrlUtil.encodeUrl(au.getAuId()));

      String url = UrlUtil.encodeUrl(au.getAuId());

      String storeUrl = baseUrl + "auid=" + UrlUtil.encodeUrl(au.getAuId());

      logger.debug3("Fei: baseUrl = " + baseUrl + ", url = " + url + ", storeUrl = " + storeUrl);

      String link;
      Boolean error = false;
      Set<String> idSet = new HashSet<String>();
      try {
          logger.debug3("Fei: - inside Try ");
          Iterator<Record> recIter = getServiceProvider().listRecords(params);
          if (recIter != null) {
              logger.debug3("Fei: - recIter is not null");
              while (recIter.hasNext()) {
                  Record rec = recIter.next();
                  if (rec != null) {
                      MetadataSearch<String> metaSearch =
                              rec.getMetadata().getValue().searcher();
                      logger.debug3("Fei: - inside Try , inside for");
                      if (checkMetaRules(metaSearch)) {
                          link = findRecordArticleLink(rec);
                          if (link != null) {
                              logger.debug3("Fei: - link = %s" + link);
                              idSet.add(link);
                          } else {
                              logger.debug3("Fei: - empty link");
                          }
                      }
                  } else {
                      logger.debug3("Fei: - recIter is not null, but rec is null");
                  }
              }
          }  else {
              logger.debug3("Fei: - recIter is null");
          }
      } catch (InvalidOAIResponse e) {
    	  if(e.getCause() != null && e.getCause().getMessage().contains("LOCKSS")) {
    		  error = true;
    		  logger.debug3("OAI result errored due to LOCKSS audit proxy. Trying alternate start Url", e);
    	  } else {
    		  throw e;
    	  }
      } catch (BadArgumentException e) {
    	  throw new ConfigurationException("Incorrectly formatted OAI parameter", e);
      }  catch (Exception e) {
          logger.debug3("Fei: Unexpected exception when trying to getRecordList"); 
          logger.debug("Unexpected exception when trying to getRecordList");
      }
      
      List<String> idList = new ArrayList<String>();
	  if(error) {
		  idList.add(storeUrl);
	  } else if(!idSet.isEmpty()) {
		  idList.addAll(idSet);
		  Collections.sort(idList);
		  storeStartUrls(idList, storeUrl);
	  }
	  return idList;
  }
  
  protected void storeStartUrls(Collection<String> urlList, String url) throws IOException {
	  StringBuilder sb = new StringBuilder();
	  sb.append("<html>\n");
	  for (String u : urlList) {
		  sb.append("<a href=\"" + u + "\">" + u + "</a><br/>\n");
	  }
	  sb.append("</html>");
	  CIProperties headers = new CIProperties();
	  //Should use a constant here
	  headers.setProperty("content-type", "text/html; charset=utf-8");
      UrlData ud = new UrlData(new ByteArrayInputStream(sb.toString().getBytes(Constants.ENCODING_UTF_8)), headers, url);
      UrlCacher cacher = facade.makeUrlCacher(ud);
      cacher.storeContent();
  }
  
  protected String findRecordArticleLink(Record rec) { 
    MetadataSearch<String> recSearcher = rec.getMetadata().getValue().searcher();
    List<String> idTags = recSearcher.findAll(DEFAULT_IDENTIFIER_TAG);
    if(idTags != null && !idTags.isEmpty()) {
      for(String value : idTags) {
        if (value.startsWith(baseUrl)) {
          logger.debug3("Fei To Follow: " + value);
          return value;
        }
      }
    }
    return null;
  }
  
  
  @Override
  protected void parseRules(String rule) throws ConfigurationException {
    if(rule.length() == 4) {
      try {
        year = Integer.parseInt(rule);
      } catch(NumberFormatException ex) {
        throw new ConfigurationException("OAI date must be a 4 digit year");
      }
    } else {
      throw new ConfigurationException("OAI date must be a 4 digit year");
    }
    
  }

  @Override
  protected boolean checkMetaRules(MetadataSearch<String> metaSearch) {
    List<String> matchingTags;
    matchingTags = metaSearch.findAll(DEFAULT_DATE_TAG);
    if(matchingTags!= null && !matchingTags.isEmpty()) {
      for(String value : matchingTags) {
        try{
          String subYear;
          Matcher yearMatch = yearPattern.matcher(value);
          if(yearMatch.find()) {
            subYear = yearMatch.group(1);
            logger.debug3("Fei: subYear = " + subYear + " value = " + value + ", expected year = " + year);
            /*
            Comment out year comparision
            if(year == Integer.parseInt(subYear)) {
              return true;
            }
            */
              return true;
          }
        } catch(NumberFormatException|IllegalStateException ex) {
            logger.debug3("Fei: yearPattern match does not expectation");
        }
      }
    } else if (matchingTags!= null) {
        logger.debug3("Fei: matchingTags is not null, checkMetaRules metaSearch = " + metaSearch);
        for(String value : matchingTags) {
            logger.debug3("Fei: checkMetaRules metaSearch value = " + value);
        }
    } else if (matchingTags == null) {
        logger.debug3("Fei: matchingTags is NULL, checkMetaRules metaSearch = " + metaSearch);
    }
    return false;
  }
}
