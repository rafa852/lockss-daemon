/*
 * $Id$
 */

/*

Copyright (c) 2000-2018 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.plugin.atypon.sage;

import java.io.InputStream;
import java.util.Vector;

import org.htmlparser.Node;
import org.htmlparser.NodeFilter;
import org.htmlparser.tags.Bullet;
import org.htmlparser.tags.BulletList;
import org.lockss.filter.html.HtmlNodeFilters;
import org.lockss.plugin.ArchivalUnit;
import org.lockss.plugin.atypon.BaseAtyponHtmlHashFilterFactory;
import org.lockss.util.Logger;


//5/9/18 changed to include/exclude
// Keeps contents only (includeNodes), then hashes out unwanted nodes 
//within the content (excludeNodes).
public class SageAtyponHtmlHashFilterFactory 
  extends BaseAtyponHtmlHashFilterFactory {

  private static final Logger log = Logger.getLogger(SageAtyponHtmlHashFilterFactory.class);
  
  @Override
  public InputStream createFilteredInputStream(ArchivalUnit au,
                                               InputStream in,
                                               String encoding) {
	  
	NodeFilter[] includeNodes = new NodeFilter[] {
			//HtmlNodeFilters.tag("body"),
		//manifest
	    new NodeFilter() {
		  @Override
		  public boolean accept(Node node) {
		    if (HtmlNodeFilters.tagWithAttributeRegex("a", "href", "/toc/").accept(node)) {
			  Node liParent = node.getParent();
			  if (liParent instanceof Bullet) {
			    Bullet li = (Bullet)liParent;
				Vector liAttr = li.getAttributesEx();
				if (liAttr != null && liAttr.size() == 1) {
				  Node ulParent = li.getParent();
				  if (ulParent instanceof BulletList) {
				    BulletList ul = (BulletList)ulParent;
					Vector ulAttr = ul.getAttributesEx();
					return ulAttr != null && ulAttr.size() == 1;
			      }
				}
			  }
		    } 
			return false;
	      }
	    },
     //toc   <div class="tocContent">
	   HtmlNodeFilters.tagWithAttribute("div", "class","tocContent"),
	 //article - doi/(ref|figure|full|abs...)/ 
     //<div class="widget literatumPublicationContentWidget none articleContent
	   HtmlNodeFilters.tagWithAttributeRegex("div", "class", "literatumPublicationContentWidget"),
     //meeting abstracts seem to use standard TOC, not search argument url
	 //see - http://journals.sagepub.com/toc/faib/38/1_suppl
	 //showCitation - included on article page - not a standalone for this plugin
	 //showPopup&citart <body class="popupBody">
	   HtmlNodeFilters.tagWithAttributeRegex("body","class","popupBody"),
	   

	};
    NodeFilter[] excludeNodes = new NodeFilter[] {
        // handled by parent: script, sfxlink, stylesheet

        HtmlNodeFilters.tag("noscript"),
        HtmlNodeFilters.tag("style"),
        // toc - first top block ad
        HtmlNodeFilters.tagWithAttributeRegex("div", "class", "literatumAd"),
        // page header: login, register, etc., and journal menu such as
        // subscribe, alerts, ...
        HtmlNodeFilters.tagWithAttribute("header", "class", "page-header"),
        HtmlNodeFilters.tagWithAttributeRegex("div", "class", "journalNavContainer"),
        // page footer
        HtmlNodeFilters.tagWithAttributeRegex("div", "class", "pageFooter"),
        // toc - Right column
        HtmlNodeFilters.tagWithAttributeRegex("div", "class", 
                                              "TOCRightColumn"),
        // article right column
        HtmlNodeFilters.tagWithAttributeRegex("div",  "class", "articleRightColumn"),

        // invisible jump to form whose choice labels have changed
        HtmlNodeFilters.tagWithAttribute("div", "class", "sectionJumpTo"),
        // toc - access icon container - haven't seen but common for Atypon
        HtmlNodeFilters.tagWithAttribute("td", "class", "accessIconContainer"),
        // toc - article type seems to change and this isn't important
        HtmlNodeFilters.tagWithAttribute("span", "class", "ArticleType"),
        // on full text and referenes page the ways to linkout to the reference get                                                                                                                   
        // added to (GoogleScholar, Medline, ISI, abstract, etc)                                                                                                                                      
        // leave the content (NLM_article-title, NLM_year, etc),                                                                                                                                      
        // but remove everything else (links and punctuation between options)  
        HtmlNodeFilters.allExceptSubtree(
            HtmlNodeFilters.tagWithAttribute(
                "table", "class", "references"),
                HtmlNodeFilters.tagWithAttributeRegex(
                    "span", "class", "NLM_")),
        //5/10/18 - some additions due to changes; some would repair in time but will slow finishing
        //keywords have been added to all abs,etc
        HtmlNodeFilters.tagWithAttribute("div","class","hlFld-KeywordText"),
        //change to format of doi information - remove "DOI:" and http://dx.doi.org --> https://doi.org/
        HtmlNodeFilters.tagWithAttribute("a","class","doiWidgetLnk"),
        HtmlNodeFilters.tagWithAttribute("div","class","publicationContentDoi"),
        HtmlNodeFilters.tagWithAttribute("div","id","articleInfo"),
 
    };
    
    //1. First remove all comments because the use of comments with nested <script> blocks is
    //causing problems for the parser.
    //<!--script>                                                                                                                                                                                             
    //</script><script>                                                                                                                                                                                       
    //</script-->
    InputStream noComment = filterComments(in, encoding);
    return super.createFilteredInputStream(au, noComment, encoding, includeNodes, excludeNodes);

  }

  
  public boolean doTagRemovalFiltering() {
    return true;
  }
   
  @Override
  public boolean doWSFiltering() {
    return true;
  }
  
}




