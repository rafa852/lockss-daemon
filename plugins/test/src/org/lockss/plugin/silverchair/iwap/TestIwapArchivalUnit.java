/*
 * $Id$
 */

/*

Copyright (c) 2017 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.plugin.silverchair.iwap;

import java.net.URL;
import java.util.List;
import java.util.Properties;

import org.apache.oro.text.regex.Pattern;
import org.apache.oro.text.regex.PatternMatcher;
import org.lockss.config.Configuration;
import org.lockss.daemon.*;
import org.lockss.plugin.*;
import org.lockss.plugin.definable.*;
import org.lockss.test.*;
import org.lockss.util.*;

//
// This plugin test framework is set up to run the same tests in two variants - CLOCKSS
// without having to actually duplicate any of the written tests
//
public class TestIwapArchivalUnit extends LockssTestCase {
  private MockLockssDaemon theDaemon;
  static final String BASE_URL_KEY = ConfigParamDescr.BASE_URL.getKey();
  static final String JID_KEY = "journal_id";
  static final String YEAR_KEY = ConfigParamDescr.YEAR.getKey();
  
  static Logger log = Logger.getLogger(TestIwapArchivalUnit.class);
  
  static final String PLUGIN_ID = "org.lockss.plugin.silverchair.iwap.ClockssIwapSilverchairPlugin";
  static final String ROOT_URL = "https://iwaponline.com/";
  
  public void setUp() throws Exception {
    super.setUp();
    setUpDiskSpace();
    theDaemon = getMockLockssDaemon();
    theDaemon.getHashService();
  }

  public void tearDown() throws Exception {
    super.tearDown();
  }
  
  private DefinableArchivalUnit makeAu(String journal_id, String resource_id, String year)
      throws Exception {
    
    Properties props = new Properties();
    props.setProperty(JID_KEY, journal_id);
    props.setProperty(YEAR_KEY, year);
    props.setProperty(BASE_URL_KEY, ROOT_URL);
    Configuration config = ConfigurationUtil.fromProps(props);
    
    DefinablePlugin ap = new DefinablePlugin();
    ap.initPlugin(theDaemon,PLUGIN_ID);
    DefinableArchivalUnit au = (DefinableArchivalUnit)ap.createAu(config);
    return au;
  }

//https://iwaponline.com/jwh/article-pdf/9/1/59/397639/59.pdf

  List<String> substanceList = ListUtil.list(
      ROOT_URL+"jid/article-pdf/1/2/3/1763959/34.pdf");
  
  List<String> notSubstanceList = ListUtil.list(
      ROOT_URL+"jid/article/9/1/10/31364/Incorporating-parameter-uncertainty-into",
      ROOT_URL+"iwa/content_public/journal/jid/9/1/10.2166_wh.2010.103/4/103.pdf");
  
  public void testCheckSubstanceRules() throws Exception {
    boolean found;
    URL base = new URL(ROOT_URL);
    ArchivalUnit jsAu = makeAu("jid","9","2012");
    PatternMatcher matcher = RegexpUtil.getMatcher();
    List<Pattern> patList = jsAu.makeSubstanceUrlPatterns();

log.setLevel("debug3");
    for (String nextUrl : substanceList) {
      log.debug3("testing for substance: "+ nextUrl);
      found = false;
      for (Pattern nextPat : patList) {
        found = matcher.matches(nextUrl, nextPat);
        if (found) break;
      }
      assertEquals(true,found);
    }
    
    for (String nextUrl : notSubstanceList) {
      log.debug3("testing for not substance: "+ nextUrl);
      found = false;
      for (Pattern nextPat : patList) {
        found = matcher.matches(nextUrl, nextPat);
        if (found) break;
      }
      assertEquals(false,found);
    }
  }
  
}

