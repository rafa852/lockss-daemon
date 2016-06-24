/*
 * $Id$
 */

/*

Copyright (c) 2000-2016 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.plugin.igiglobal;


import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.lockss.config.Configuration;
import org.lockss.daemon.ConfigParamDescr;
import org.lockss.daemon.RangeCachedUrlSetSpec;
import org.lockss.plugin.*;
import org.lockss.plugin.ArchivalUnit.ConfigurationException;
import org.lockss.plugin.base.BaseCachedUrlSet;
import org.lockss.state.AuState;
import org.lockss.test.*;
import org.lockss.util.Constants;
import org.lockss.util.ListUtil;
import org.lockss.util.PatternFloatMap;
import org.lockss.util.RegexpUtil;
import org.lockss.util.TimeBase;

public class TestIgiGlobalPlugin extends LockssPluginTestCase {
	
	protected MockLockssDaemon daemon;
	private final String PLUGIN_NAME = "org.lockss.plugin.igiglobal.IgiGlobalPlugin";
	static final String BASE_URL_KEY = ConfigParamDescr.BASE_URL.getKey();
	static final String JOURNAL_ISSN_KEY = ConfigParamDescr.JOURNAL_ISSN.getKey();
	static final String VOLUME_NUMBER_KEY = ConfigParamDescr.VOLUME_NUMBER.getKey();
	private final String BASE_URL = "http://www.example.com/";
	private final String VOLUME = "21";
	private final String JOURNAL_ISSN = "1546-2234";
	private final Configuration AU_CONFIG = ConfigurationUtil.fromArgs(
										 		BASE_URL_KEY, BASE_URL,
										 		VOLUME_NUMBER_KEY, VOLUME,
										 		JOURNAL_ISSN_KEY, JOURNAL_ISSN);
	
	// from au_url_poll_result_weight in plugins/src/org/lockss/plugin/igiglobal/IgiGlobalPlugin.xml
	// if it changes in the plugin, you will likely need to change the test, so verify
	static final String  IGI_REPAIR_FROM_PEER_REGEXP1 = "[^/]+/(images|sourcecontent)/.*[.](bmp|gif|ico|jpe?g|png|tif?f)$";
	static final String  IGI_REPAIR_FROM_PEER_REGEXP2 = "[.](css|js)$";

  public void setUp() throws Exception {
    super.setUp();
  }
  
  public void tearDown() throws Exception {
	    super.tearDown();
	  }

  protected ArchivalUnit createAu() throws ArchivalUnit.ConfigurationException {
    return
      createAu(AU_CONFIG);
  }
  
  protected ArchivalUnit createAu(Configuration config) throws ArchivalUnit.ConfigurationException {
	return
	  PluginTestUtil.createAndStartAu(PLUGIN_NAME, config);
  }
  
  public void testCreateAu() {

	    try {
	      createAu(ConfigurationUtil.fromArgs(
	    		  						BASE_URL_KEY, BASE_URL,
	    		  						VOLUME_NUMBER_KEY, VOLUME));
	      fail("Bad AU configuration should throw configuration exception");
	    }
	    catch (ConfigurationException ex) {
	    }
	    try {
	    	createAu();
	    }
	    catch (ConfigurationException ex) {
	    	fail("Unable to creat AU from valid configuration");
	    }
  }
  
  public void testShouldCacheProperPages() throws Exception {
	    ArchivalUnit au = createAu();
	    BaseCachedUrlSet cus = new BaseCachedUrlSet(au, new RangeCachedUrlSetSpec(BASE_URL));

	    // start page
	    assertShouldCache(BASE_URL + "lockss/journal-issues.aspx?issn=" + JOURNAL_ISSN + "&volume=" + VOLUME, true, au, cus);
	    
	    assertShouldCache(BASE_URL + "lockss/journal-issues.aspx?issn=" + JOURNAL_ISSN + "&volume=", false, au, cus);
	    
	    // issue and article pages
	    assertShouldCache(BASE_URL + "gateway/contentowned/articles.aspx?titleid=55656", true, au, cus);
	    assertShouldCache(BASE_URL + "gateway/article/55656", true, au, cus);
	    assertShouldCache(BASE_URL + "gateway/issue/55656", true, au, cus);
	    
	    
	    assertShouldCache(BASE_URL + "gateway/contentowned/article.aspx?titleid=55656&accesstype=infosci", false, au, cus);
	    assertShouldCache(BASE_URL + "gateway/contentowned/issues.aspx?titleid=55656", false, au, cus);
	    // pdf page with iframe
	    assertShouldCache(BASE_URL + "gateway/article/full-text-pdf/55656", true, au, cus);
	    // pdf file displayed in iframe
	    assertShouldCache(BASE_URL + "viewtitle.aspx?titleid=55663", true, au, cus);
	    assertShouldCache(BASE_URL + "pdf.aspx?titleid=55663", true, au, cus);
	    
	    // images, css, js
	    assertShouldCache(BASE_URL + "jQuery/css/smoothness/images/ui-bg_flat_75_ffffff_40x100.png", true, au, cus);
	    assertShouldCache(BASE_URL + "Images/publish-with-igi-global.jpg", true, au, cus);
	    assertShouldCache(BASE_URL + "App_Themes/HeatherStyles/images/App_Master/favicon.ico", true, au, cus);
	    assertShouldCache(BASE_URL + "jQuery/js/jquery-ui-1.7.2.custom.min.js", true, au, cus);
	    
	    
	    // images, css, js with bad formatting
	    assertShouldCache(BASE_URL + "App_Themes/HeatherStyles/IGIMain.css?v=02242012b", true, au, cus);
	    assertShouldCache(BASE_URL + "Scripts/gateway.js?v=02162012", true, au, cus);
	    
	    // specified bad pages
	    assertShouldCache(BASE_URL + "membership/login.aspx?returnurl=%2fgateway%2fcontentowned%2farticle.aspx%3ftitleid%3d55656%26accesstype%3dinfosci", false, au, cus);
	    assertShouldCache(BASE_URL + "membership/login.aspx?jQuery%2css%2smoothness%2images%2ui-bg_flat_75_ffffff_40x100.png", false, au, cus);
	    assertShouldCache(BASE_URL + "App_Themes/HeatherStyles/images/App_Master/App_Master/App_Master/favicon.ico", false, au, cus);
	    assertShouldCache(BASE_URL + "gateway/edatabasetools/librariancorner.aspx", false, au, cus);
	    
	    // facebook
	    assertShouldCache("http://www.facebook.com/pages/IGI-Global/138206739534176?ref=sgm", false, au, cus);
	    
	    // LOCKSS
	    assertShouldCache("http://lockss.stanford.edu", false, au, cus);

	    // other site
	    assertShouldCache("http://exo.com/~noid/ConspiracyNet/satan.html", false, au, cus);
	    
	  }

	  private void assertShouldCache(String url, boolean shouldCache,
				       ArchivalUnit au, CachedUrlSet cus) {
	    assertEquals("AU crawl rules applied incorrectly to " + url + " ",
	        shouldCache, au.shouldBeCached(url));
	  }

	  public void testStartUrlConstruction() throws Exception {
	    String expectedStartUrl = BASE_URL + "lockss/journal-issues.aspx?issn=" + JOURNAL_ISSN + "&volume=" + VOLUME;
	    ArchivalUnit au = createAu();
	    assertEquals(ListUtil.list(expectedStartUrl), au.getStartUrls());
	  }

	  public void testGetUrlStems() throws Exception {
	    ArchivalUnit au = createAu();
	    assertEquals(ListUtil.list(BASE_URL), au.getUrlStems());
	  }

	  public void testShouldDoNewContentCrawlTooEarly() throws Exception {
	    ArchivalUnit au = createAu();
	    AuState aus = new MockAuState(null, TimeBase.nowMs(), -1, -1, null);
	    assertFalse(au.shouldCrawlForNewContent(aus));
	  }

	  public void testShouldDoNewContentCrawlForZero() throws Exception {
		  ArchivalUnit au = createAu();
	    AuState aus = new MockAuState(null, 0, -1, -1, null);
	    assertTrue(au.shouldCrawlForNewContent(aus));
	  }

	  public void testShouldDoNewContentCrawlEachMonth() throws Exception {
		  ArchivalUnit au = createAu();
	    AuState aus = new MockAuState(null, 4 * Constants.WEEK, -1, -1, null);
	    assertTrue(au.shouldCrawlForNewContent(aus));
	  }
	  
	  public void testGetName() throws Exception {
	    ArchivalUnit au = createAu();
	    assertEquals("IGI Global Journals Plugin, Base URL " + BASE_URL + ", Journal ISSN " + JOURNAL_ISSN + ", Volume " + VOLUME, au.getName());
	  }

	  public void testPollSpecial() throws Exception {
	    ArchivalUnit au = createAu();
	    
	    // if it changes in the plugin, you will likely need to change the test, so verify
	    assertEquals(ListUtil.list(
	        IGI_REPAIR_FROM_PEER_REGEXP1, IGI_REPAIR_FROM_PEER_REGEXP2),
	        RegexpUtil.regexpCollection(au.makeRepairFromPeerIfMissingUrlPatterns()));
	    
	    // make sure that's the regexp that will match to the expected url string
	    // this also tests the regexp (which is the same) for the weighted poll map
	    // Add to pattern these urls? Has not been seen as problem, yet
	    //  http://www.igi-global.com/favicon.ico
	    
	    List <String> repairList1 = ListUtil.list(
	        BASE_URL + "sourcecontent/9781466601161_58264/978-1-4666-0116-1.ch002.f01.png",
                BASE_URL + "images/workflow-wizard-hand-circle-medium-gray.png",
                BASE_URL + "jQuery/css/blitzer/images/ui-icons_004276_256x240.png",
                BASE_URL + "images/erl-2015.png");
	    Pattern p = Pattern.compile(IGI_REPAIR_FROM_PEER_REGEXP1);
	    for (String urlString : repairList1) {
	      Matcher m = p.matcher(urlString);
	      assertEquals(urlString, true, m.find());
	    }
	    List <String> repairList2 = ListUtil.list(
	        BASE_URL + "includes/gateway.61113.js",
	        BASE_URL + "includes/main.02052016.css",
	        BASE_URL + "Scripts/tipped/tipped.css",
	        BASE_URL + "Scripts/tipped/tipped.js");
	    p = Pattern.compile(IGI_REPAIR_FROM_PEER_REGEXP2);
	    for (String urlString : repairList2) {
	      Matcher m = p.matcher(urlString);
	      assertEquals(urlString, true, m.find());
	    }
	    
	    //and this one should fail - it needs to be weighted correctly and repaired from publisher if possible
	    String notString = BASE_URL + "favicon.ico";
	    Matcher m = p.matcher(notString);
	    assertEquals(false, m.find());
	    
	    PatternFloatMap urlPollResults = au.makeUrlPollResultWeightMap();
	    assertNotNull(urlPollResults);
	    for (String urlString : repairList2) {
	      assertEquals(0.0, urlPollResults.getMatch(urlString, (float) 1), .0001);
	    }
	    assertEquals(1.0, urlPollResults.getMatch(notString, (float) 1), .0001);
	  }
	  
}
