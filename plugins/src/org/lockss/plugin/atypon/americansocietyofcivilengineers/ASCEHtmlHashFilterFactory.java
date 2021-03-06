/*
 * $Id$
 */

/*

Copyright (c) 2000-2017 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.plugin.atypon.americansocietyofcivilengineers;

import java.io.InputStream;

import org.htmlparser.NodeFilter;
import org.lockss.filter.html.*;
import org.lockss.plugin.*;
import org.lockss.plugin.atypon.BaseAtyponHtmlHashFilterFactory;

public class ASCEHtmlHashFilterFactory extends BaseAtyponHtmlHashFilterFactory {
  // include a whitespace filter
  @Override
  public boolean doWSFiltering() {
    return true;
  }
  // include a tag filter - some pages for ASCE changed their html like so:
  // new:<li class="articleToolLi showPDF">
  // old:<li class="articleToolLi">
  // Polls would eventually match, as pages updated, but removing tags
  // would make the hashing go smoother, especially as they switch others of 
  // their journals
  @Override
  public boolean doTagRemovalFiltering() {
    return true;
  }
  @Override
  public InputStream createFilteredInputStream(ArchivalUnit au,
      InputStream in, String encoding) {
    
    NodeFilter[] asceFilters = new NodeFilter[] {
        /*
         * This section is from < 2017
         */
        // <header> filtered in BaseAtypon
        HtmlNodeFilters.tagWithAttribute("div", "id", "issueNav"),
        HtmlNodeFilters.tagWithAttributeRegex("div", "id", "tocTools"),
        HtmlNodeFilters.tagWithAttributeRegex("td", "class", "toggle"),
        //article, toc: <div class="dropzone ui-corner-all " 
        // id="dropzone-Left-Sidebar"> - tornados ad, session history.
        HtmlNodeFilters.tagWithAttribute("div", "id", "dropzone-Left-Sidebar"),	
        //toc: <div class="citation tocCitation">
        HtmlNodeFilters.tagWithAttribute("div", "class", "citation tocCitation"),
        // footer and footer_message filtered in BaseAtypon
        // removing keywords section, author names from html page 
        //  - some versions have "action/doSearch..."
        HtmlNodeFilters.tagWithAttribute("div", "class", "abstractKeywords"),
        HtmlNodeFilters.tagWithAttribute("div", "class", "artAuthors"),
        // removing a doi link that sometimes has a class name
        HtmlNodeFilters.tagWithAttribute("a", "class", "ShowPdfGa"),
        // the addition of "Abstract:" between authors and actual text, seems the only usage
        // oddly, it doesn't always show on the screen, but it's there
        HtmlNodeFilters.tagWithAttribute("h2", "class", "display"),
        /*
         * This section is for 2017+
         */
        // TOC - links to all other issues
        HtmlNodeFilters.tagWithAttributeRegex("div", "class", "journalMetaBackground"),
        // Article landing - ajax tabs
        HtmlNodeFilters.tagWithAttributeRegex("div", "id", "recommendedtabcontent"),
        HtmlNodeFilters.tagWithAttributeRegex("div", "class", "editorialRelated"),

    };
    
    // super.createFilteredInputStream adds asceFilters to the baseAtyponFilters
    // and returns the filtered input stream using an array of NodeFilters that 
    // combine the two arrays of NodeFilters.
    return super.createFilteredInputStream(au, in, encoding, asceFilters);
    }
    
}
