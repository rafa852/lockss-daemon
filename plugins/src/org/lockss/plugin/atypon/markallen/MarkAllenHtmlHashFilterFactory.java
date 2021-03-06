/*
 * $Id$
 */

/*

Copyright (c) 2018 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.plugin.atypon.markallen;

import java.io.InputStream;
import org.htmlparser.NodeFilter;
import org.lockss.filter.html.*;
import org.lockss.plugin.*;
import org.lockss.plugin.atypon.BaseAtyponHtmlHashFilterFactory;
import org.lockss.util.Logger;

public class MarkAllenHtmlHashFilterFactory 
  extends BaseAtyponHtmlHashFilterFactory {
  
  private static final Logger log = Logger.getLogger(MarkAllenHtmlHashFilterFactory.class);

  @Override
  public InputStream createFilteredInputStream(ArchivalUnit au,
      InputStream in, String encoding) {
    
    NodeFilter[] filters = new NodeFilter[] {
        // handled by parent: script, sfxlink, stylesheet
        
        // from toc - ad panel has link to other issue 
        // http://www.magonlinelibrary.com/toc/bjom/21/10
        HtmlNodeFilters.tagWithAttributeRegex("div", "class", 
                                              "genericSlideshow"),
        // for toc - social media
        HtmlNodeFilters.tagWithAttributeRegex("div", "class",
                                              "general-bookmark-share"),
        // middle column ad of an article - all article tools with 
        // class literatumArticleToolsWidget except Download Citations
        // http://www.magonlinelibrary.com/doi/abs/10.12968/bjom.2013.21.10.701
        HtmlNodeFilters.allExceptSubtree(
            HtmlNodeFilters.tagWithAttributeRegex( 
                "div", "class", "literatumArticleToolsWidget"),
            HtmlNodeFilters.tagWithAttributeRegex(
                "a", "href", "/action/showCitFormats\\?")),
        
        HtmlNodeFilters.tagWithAttributeRegex("span", "class", "article__(access|breadcrumb)"),
        HtmlNodeFilters.tagWithAttribute("section", "class", "article__keyword"),
        HtmlNodeFilters.tagWithAttribute("div", "class", "figure-viewer"),
    };
    // super.createFilteredInputStream adds filters to the baseAtyponFilters
    // and returns the filtered input stream using an array of NodeFilters that 
    // combine the two arrays of NodeFilters.
    return super.createFilteredInputStream(au, in, encoding, filters);
  }

  @Override
  public boolean doHttpsConversion() {
    return true;
  }

  @Override
  public boolean doTagRemovalFiltering() {
    return true;
  }

  @Override
  public boolean doWSFiltering() {
    return true;
  }

}
