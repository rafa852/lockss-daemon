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

package org.lockss.plugin.edinburgh;

import java.io.InputStream;

import org.htmlparser.NodeFilter;
import org.lockss.filter.html.*;
import org.lockss.plugin.*;
import org.lockss.plugin.atypon.BaseAtyponHtmlHashFilterFactory;

/*
 *  Edinburgh changed skins in 2016. Leaving in the old filtering so long as it doesn't
 *  break anything. Adding in new filtering to cover new content layout
 */

public class EdinburghUniversityPressHashHtmlFilterFactory extends BaseAtyponHtmlHashFilterFactory {

  @Override
  public InputStream createFilteredInputStream(ArchivalUnit au,
      InputStream in,
      String encoding) {
    NodeFilter[] edFilter = new NodeFilter[] {        
        //Implementing maximal filtering - leave old stuff for safety though already be filtered in larger chunks
        HtmlNodeFilters.tagWithAttribute("div",  "id", "masthead"),
        HtmlNodeFilters.tagWithAttribute("div", "id", "mainNavContainer"),
        HtmlNodeFilters.tagWithAttribute("div", "id", "advSearchNavBottom"),
        HtmlNodeFilters.tagWithAttribute("div", "id", "mainBreadCrumb"),
        HtmlNodeFilters.tagWithAttribute("div", "id", "journalTitleContainer"),
        
        // Contains name and logo of institution
        HtmlNodeFilters.tagWithAttributeRegex("div", "id", "^institutionBanner"),
        // Current web site seems to do it this way...
        HtmlNodeFilters.tagWithAttributeRegex("li", "class", "^institutionBanner"),
        // left column
        HtmlNodeFilters.tagWithAttribute("div", "id", "journalSidebar"),
        // from abstract
        // removes a couple of differences within this "authors" link:
        //   <div class="authors"><span class="author">...
        HtmlNodeFilters.tagWithAttribute("div", "class", "authors"),
        // removes <h2>Sep 2011</h2> vs <h2>Current Issue: Sep 2011</h2>
        HtmlNodeFilters.tagWithAttribute("div", "class", "panel_top"),
        // removes <a href="/action/addCitationAlert?doi=10.3366%2Fjobs.2011.0020">Track Citations</a>
        //     and <a href="#" class="citationsLink">Track Citations</a>
        HtmlNodeFilters.tagWithText("a", "Track Citations"),
        
        
        //NEW FILTERING to handle new skin - all both TOC and article text
        // also in crawl filter
        // navigation
        HtmlNodeFilters.tagWithAttributeRegex("div", "class", "literatumBrreadcrumbs"),
        // header section of page
        HtmlNodeFilters.tagWithAttributeRegex("div", "class", "page-header"),
        // tabbed info section below content
        HtmlNodeFilters.tagWithAttributeRegex("div", "id", "Publication_info_tabs"),
        // footer section of page
        HtmlNodeFilters.tagWithAttributeRegex("div", "class", "page-footer"),
        // right column - containing most read, etc
        HtmlNodeFilters.tagWithAttribute("div", "class", "col-sm-1-3 right-column"),
        // TOC tabbed section on TOC for listing all issues in journal
        HtmlNodeFilters.tagWithAttributeRegex("div", "class", "literatumListOfIssuesWidget"),
        
        // 10/2/18 - addition of hidden empy tab for view options
        // these aren't needed for comparison anyway
        HtmlNodeFilters.tagWithAttribute("ul","class", "tab-nav"),
        
        // 1/24/19 - html tagging appears to have changed
        //class="widget pageFooter none  widget-none  widget-compact-all"
        HtmlNodeFilters.tagWithAttributeRegex("div", "class", "pageFooter"),
        HtmlNodeFilters.tagWithAttributeRegex("div", "class", "pageHeader"),
        //verified specificity of right column tagging still in place

    };
    // super.createFilteredInputStream adds Edinburgh's filter to the baseAtyponFilters
    // and returns the filtered input stream using an array of NodeFilters that 
    // combine the two arrays of NodeFilters.
    return super.createFilteredInputStream(au, in, encoding, edFilter);

  }
  
  //10/2/2018 - remove white space which seems to be variable across versions
  @Override
  public boolean doWSFiltering() {
    return true;
  }
  

}
