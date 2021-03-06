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

package org.lockss.plugin.highwire;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.regex.Pattern;

import org.htmlparser.Node;
import org.htmlparser.NodeFilter;
import org.htmlparser.filters.*;
import org.htmlparser.nodes.TextNode;
import org.htmlparser.tags.CompositeTag;
import org.htmlparser.tags.BulletList;
import org.htmlparser.tags.Div;
import org.htmlparser.util.NodeList;
import org.lockss.daemon.PluginException;
import org.lockss.filter.FilterUtil;
import org.lockss.filter.HtmlTagFilter;
import org.lockss.filter.StringFilter;
import org.lockss.filter.WhiteSpaceFilter;
import org.lockss.filter.html.*;
import org.lockss.plugin.*;
import org.lockss.util.LineRewritingReader;
import org.lockss.util.ListUtil;
import org.lockss.util.Logger;
import org.lockss.util.ReaderInputStream;

public class HighWirePressH20HtmlFilterFactory implements FilterFactory {

  private static final Logger log = Logger.getLogger(HighWirePressH20HtmlFilterFactory.class);
  
  private static final NodeFilter[] filters = new NodeFilter[] {
      // Publisher adding meta tags often
      HtmlNodeFilters.tag("head"),
      HtmlNodeFilters.tag("script"),
      HtmlNodeFilters.tag("noscript"),
      HtmlNodeFilters.tag("iframe"),
      HtmlNodeFilters.comment(),
      HtmlNodeFilters.tagWithAttribute("div", "id", "header"),
      HtmlNodeFilters.tagWithAttribute("div", "id", "footer"),
      HtmlNodeFilters.tagWithAttribute("div", "id", "col-2"),
      HtmlNodeFilters.tagWithAttribute("div", "id", "col-3"),
      HtmlNodeFilters.tagWithAttribute("div", "class", "header-ac-elements"),
      HtmlNodeFilters.tagWithAttribute("div", "class", "banner-ads"),
      HtmlNodeFilters.tagWithAttribute("ul", "class", "banner-ads"),
      HtmlNodeFilters.tagWithAttribute("ul", "class", "tower-ads"),
      HtmlNodeFilters.tagWithAttribute("ul", "class", "col4-square"),
      HtmlNodeFilters.tagWithAttribute("ul", "class", "col4-tower"),
      HtmlNodeFilters.tagWithAttributeRegex("div", "class", "leaderboard-(ads|clear)"),
      HtmlNodeFilters.tagWithAttribute("div", "id", "sidebar-current-issue"),
      HtmlNodeFilters.tagWithAttribute("div", "id", "sidebar-global-nav"),
      HtmlNodeFilters.tagWithAttributeRegex("p", "class", "copyright"),
      HtmlNodeFilters.tagWithAttribute("div", "class", "copyright"),
      HtmlNodeFilters.tagWithAttributeRegex("div", "class", "(article|sidebar)-nav"),
      // May contain institution-specific data e.g. OUP
      HtmlNodeFilters.tagWithAttribute("div", "id", "secondary_footer"),
      HtmlNodeFilters.tagWithAttribute("div", "id", "cited-by"),
      // More footer & ad tags (but not filter "footer"
      HtmlNodeFilters.tagWithAttribute("div", "id", "primary_footer"),
      HtmlNodeFilters.tagWithAttribute("div", "id", "foot"),
      HtmlNodeFilters.tagWithAttribute("div", "class", "ad_hidden"),
      HtmlNodeFilters.tagWithAttribute("div", "class", "ad_unhidden"),
      HtmlNodeFilters.tagWithAttribute("div", "id", "disclaimer"),
      // e.g. PNAS
      HtmlNodeFilters.tagWithAttribute("div", "class", "science-jobs"),
      // e.g. PNAS
      HtmlNodeFilters.tagWithAttributeRegex("div", "class", "most-links-box"),
      // e.g. PNAS
      HtmlNodeFilters.tagWithAttribute("div", "id", "cb-art-recm"),
      // e.g. PNAS (optional 'sid' query arg in URLs)
      HtmlNodeFilters.tagWithAttribute("div", "id", "cb-art-svcs"),
      HtmlNodeFilters.tagWithAttribute("div", "id", "cb-art-cit"),
      // PNAS (aggressive filtering)
      HtmlNodeFilters.tagWithAttribute("div", "id", "sidebar"),
      HtmlNodeFilters.tagWithAttribute("ul", "id", "drop_menu"),
      new NodeFilter() {
        @Override public boolean accept(Node node) {
          if (!(node instanceof BulletList)) return false;
          String allText = ((CompositeTag)node).toPlainTextString();
          //using regex for case insensitive match on "current issue"
          // the "i" is for case insensitivity; the "s" is for accepting newlines
          return allText.matches("(?is).*current issue.*");
          }
      },
      // e.g. BMJ (optional 'sid' query arg in URLs)
      HtmlNodeFilters.tagWithAttribute("div", "id", "cb-art-rel"),
      HtmlNodeFilters.tagWithAttribute("div", "id", "cb-art-soc"),
      // e.g.  Europace tags
      HtmlNodeFilters.tagWithAttribute("div", "id", "primary_nav"),
      HtmlNodeFilters.tagWithAttribute("div", "id", "secondary_nav"),
      HtmlNodeFilters.tagWithAttribute("div", "id", "cb-art-cat"),
      HtmlNodeFilters.tagWithAttribute("div", "id", "related"),
      HtmlNodeFilters.tagWithAttribute("ul", "id", "site-breadcrumbs"),
      HtmlNodeFilters.tagWithAttributeRegex("ul", "class", "kwd-group"),
      HtmlNodeFilters.tagWithAttribute("ul", "class", "copyright-statement"),
      HtmlNodeFilters.tagWithAttributeRegex("span", "class", "^ccv "),
      // e.g. SWCS TOC pages
      HtmlNodeFilters.tagWithAttribute("div", "class", "cit-form-select"),
      // and <div id="cit-extra">
      HtmlNodeFilters.tagWithAttribute("div", "class", "cit-extra"),
      // Extreme filtering for http://aapnews.aappublications.org/content/34/3/22.short
      //  Some citation text changes over time
      HtmlNodeFilters.tagWithAttribute("span", "class", "cit-extra"),
      // For JBC pages
      HtmlNodeFilters.tagWithAttribute("div", "id", "ad-top"),
      HtmlNodeFilters.tagWithAttribute("div", "id", "ad-top2"),
      HtmlNodeFilters.tagWithAttribute("div", "id", "ad-footer"),
      HtmlNodeFilters.tagWithAttribute("div", "id", "ad-footer2"),
      // new jbc related div showed up
      HtmlNodeFilters.tagWithAttributeRegex("div", "class", "relmgr-relat"),
      // For Chest pages
      HtmlNodeFilters.tagWithAttribute("span", "class", "free"),
      // For American College of Physicians
      HtmlNodeFilters.tagWithAttribute("div", "class", "acp-menu"),
      // For Royal Society pages
      HtmlNodeFilters.tagWithAttribute("div", "id", "ac"),
      HtmlNodeFilters.tagWithAttribute("div", "id", "social_network"),
      // For biologists.org
      HtmlNodeFilters.tagWithAttribute("div", "id", "authstring"),
      HtmlNodeFilters.tagWithAttribute("div", "id", "cb-art-stats"),
      HtmlNodeFilters.tagWithAttribute("div", "id", "relmgr-related"),
      //For BMJ
      HtmlNodeFilters.tagWithAttribute("div", "id", "access"),
      HtmlNodeFilters.tagWithAttribute("div", "id", "feeds-widget1"),
      HtmlNodeFilters.tagWithAttribute("div", "class", "careers-widget"),
      // For JCB
      HtmlNodeFilters.tagWithAttributeRegex("div", "id", "leaderboard-ads"),
      HtmlNodeFilters.tagWithAttribute("div", "class", "current-issue"),
      // Optional institution-specific citation resolver (e.g. SAGE Publications)
      HtmlNodeFilters.tagWithAttributeRegex("a", "href", "^/cgi/openurl"),
      HtmlNodeFilters.tagWithAttributeRegex("a", "href", "^/openurl"),
      HtmlNodeFilters.tagWithAttributeRegex("a", "href", "^/external-ref"),   
      //For SAGE (at least).  Name of the institution. E.g. </a> INDIANA UNIV </div>
      HtmlNodeFilters.tagWithAttribute("div", "id", "header-Uni"),
      HtmlNodeFilters.tagWithAttributeRegex("div", "id", "header-initialNav"),
      HtmlNodeFilters.tagWithAttributeRegex("div", "id", "impact-factor"),
      //Project HOPE (at least).  <div class="in-this-issue">
      HtmlNodeFilters.tagWithAttribute("div", "class", "in-this-issue"),
      HtmlNodeFilters.tagWithAttributeRegex("div", "class", "sidebar-feed"),

      // Filter for <li class="subscr-ref">....</li>
      HtmlNodeFilters.tagWithAttribute("li", "class", "subscr-ref"),
      // Filter for <div class="col-3adverTower"> (SAGE)
      HtmlNodeFilters.tagWithAttribute("div", "class", "col-3adverTower"),        
      // Filter for <div class="social-bookmarking"> 
      HtmlNodeFilters.tagWithAttribute("div", "class", "social-bookmarking"), 
      // Related articles
      HtmlNodeFilters.tagWithAttribute("div", "id", "rel-related-article"),
      // Normalize the probabilistic substitution of .short for .abstract
      HtmlNodeFilters.tagWithAttributeRegex("a", "href", "\\.(abstract|short)(\\?|$)"),   
      // Ahead-of-print markers eventually disappear (e.g. JBC)
      HtmlNodeFilters.tagWithAttributeRegex("span", "class", "ahead-of-print"),
      // Sage Filter for session ids in meta tags
      HtmlNodeFilters.tagWithAttributeRegex("meta", "id", "^session-"),
      // For BMJ related links div 
      HtmlNodeFilters.tagWithAttribute("div", "id", "related-external-links"), 
      // For BMJ related articles div 
      HtmlNodeFilters.tagWithAttribute("div", "id", "rel-relevant-article"), 
      // For BMJ variable poll 
      HtmlNodeFilters.tagWithAttribute("div", "id", "polldaddy-bottom"),
      HtmlNodeFilters.tagWithAttributeRegex("div", "class", "pane-article-page-promo-column"),
      HtmlNodeFilters.tagWithAttribute("form", "id", "bmj-advanced-search-channel-form"),
      HtmlNodeFilters.tagWithAttribute("div", "id", "status"),
      HtmlNodeFilters.tagWithAttribute("div", "id", "AdSkyscraper"),
      
      // geoscienceworld.org (rather than filter "header"
      HtmlNodeFilters.tagWithAttribute("div", "id", "gsw-head"),
      // rmg.geoscienceworld.org
      HtmlNodeFilters.tagWithAttribute("a", "class", "hwac-institutional-logo"),
      //claymin.geoscienceworld.org
      HtmlNodeFilters.tagWithAttribute("img", "class", "hwac-institutional-logo"),
      HtmlNodeFilters.tagWithAttribute("input", "type", "hidden"),
      HtmlNodeFilters.tagWithAttributeRegex("span", "class", "^viewspecificaccesscheck"),   
      
      //parmrev.aspetjournals.org
      HtmlNodeFilters.tagWithAttribute("ul", "class", "toc-banner-ads"),
      // Filter for <div class="slugline-ads">
      HtmlNodeFilters.tagWithAttribute("div", "class", "slugline-ads"), 
      
      // The following four filters are needed on jultrasoundmed.org:
      // Empty and sporadic <div id="fragment-reference-display">
      HtmlNodeFilters.tagWithAttribute("div", "id", "fragment-reference-display"),
      // "Earn FREE CME Credit" link (wrapped in a list item)
      HtmlNodeFilters.tagWithText("li", "class=\"dslink-earn-free-cme-credit\""),
      // Variable list of links to PubMed, Google Scholar, other sites
      HtmlNodeFilters.tagWithAttribute("div", "class", "cb-section collapsible default-closed"),     
      //The following is also for jultrasoundmed.org  - possibly also need whitespace filter
      HtmlNodeFilters.tagWithAttribute("span", "id", "related-urls"),  
      // For American Journal of Epidemiology
      HtmlNodeFilters.tagWithAttribute("li", "id", "nav_current_issue"),
      // For lofe.dukejournals.org - shows the current viewing date
      HtmlNodeFilters.tagWithAttribute("div", "class", "site-date"),
      // Beginning and end markers were added, e.g. Royal Society Interface
      HtmlNodeFilters.tagWithAttribute("span", "class", "highwire-journal-article-marker-start"),
      HtmlNodeFilters.tagWithAttribute("span", "class", "highwire-journal-article-marker-end"),
      // sage - toc may have extra div block within this <div class="gca-buttons">
      // oup - <div class="gca-buttons-bulk-cit-toc">
      HtmlNodeFilters.tagWithAttributeRegex("div", "class", "gca-buttons"),
      // <div id="trendmd-suggestions"> http://cjasn.asnjournals.org/content/10/1/111.short
      HtmlNodeFilters.tagWithAttributeRegex("div", "id", "trend"),
      // Extreme filtering for http://heart.bmj.com/content/101/16/1309
      // 
      HtmlNodeFilters.tagWithAttributeRegex("ol", "class", "eletter"),
      HtmlNodeFilters.tagWithAttributeRegex("div", "id", "-nav"),
      HtmlNodeFilters.tagWithAttributeRegex("div", "id", "cookie"),
      HtmlNodeFilters.tagWithAttributeRegex("div", "id", "breadcrumbs"),
      HtmlNodeFilters.tagWithAttributeRegex("div", "id", "oas-ad"),
      HtmlNodeFilters.tagWithAttribute("div", "id", "rss"),
      HtmlNodeFilters.tagWithAttribute("div", "id", "responses"),
      HtmlNodeFilters.tagWithAttribute("div", "class", "crossmark-logo"),
      
      // found in http://mnrasl.oxfordjournals.org/content/433/1/25.full
      HtmlNodeFilters.tagWithAttribute("ul", "class", "history-list"),
      // There is an "Impact factor" but it is only ctext in an H3 tag
      // and the parent <div> is generic. Use a combination of the grandparent <div> plus the ctext
      // It's not ideal, but there is no better solution. Seen in occmed.oxfordjournals.org
      new NodeFilter() {
        @Override public boolean accept(Node node) {
          if (!(node instanceof Div)) return false;
          if (!("features".equals(((CompositeTag)node).getAttribute("class")))) return false;
          String allText = ((CompositeTag)node).toPlainTextString();
          //using regex for case insensitive match on "Impact factor"
          // the "i" is for case insensitivity; the "s" is for accepting newlines
          return allText.matches("(?is).*impact factor.*");
          }
      }
  };
  
  // HTML transform to convert all remaining nodes to plaintext nodes
  // cannot keep up with continual changes to css
  // http://fh.oxfordjournals.org/content/25/1/114.short on ingest1 & 3
  protected static HtmlTransform xformAllTags = new HtmlTransform() {
    @Override
    public NodeList transform(NodeList nodeList) throws IOException {
      NodeList nl = new NodeList();
      for (int sx = 0; sx < nodeList.size(); sx++) {
        Node snode = nodeList.elementAt(sx);
        TextNode tn = new TextNode(snode.toPlainTextString());
        nl.add(tn);
      }
      return nl;
    }
  };
  
  // remove document.write(....); as it can confuse html node filter
  private static final HtmlTagFilter.TagPair[] pairs = {
      new HtmlTagFilter.TagPair("document.write(", ");", true)
  };
  
  @Override
  public InputStream createFilteredInputStream(ArchivalUnit au,
                                               InputStream in,
                                               String encoding)
      throws PluginException {
    
    HtmlTagFilter tagfilter = HtmlTagFilter.makeNestedFilter(
        FilterUtil.getReader(in, encoding), ListUtil.fromArray(pairs));
    InputStream inb = new BufferedInputStream(new ReaderInputStream(tagfilter, encoding));
    InputStream filtered =  new HtmlFilterInputStream(inb, encoding,
        new HtmlCompoundTransform(
            HtmlNodeFilterTransform.exclude(new OrFilter(filters)), xformAllTags));
    Reader filteredReader = FilterUtil.getReader(filtered, encoding);
    LineRewritingReader rewritingReader = new LineRewritingReader(filteredReader) {
      @Override
      public String rewriteLine(String line) {
        // Markup changes over time [anywhere]
        line = PAT_NBSP.matcher(line).replaceAll(REP_NBSP);
        line = PAT_AMP.matcher(line).replaceAll(REP_AMP);
        line = PAT_PUNCTUATION.matcher(line).replaceAll(REP_PUNCTUATION); // e.g. \(, \-, during encoding glitch (or similar)
        line = PAT_WHITESPACE.matcher(line).replaceAll(EMPTY_STRING); // e.g. \(, \-, during encoding glitch (or similar)
        return line;
      }
    };
    Reader httpFilter = new StringFilter(rewritingReader, "http:", "https:");
    return new ReaderInputStream(new WhiteSpaceFilter(httpFilter));
  }

  public static final String EMPTY_STRING = "";

  public static final Pattern PAT_WHITESPACE = Pattern.compile("\\s", Pattern.CASE_INSENSITIVE);

  public static final Pattern PAT_NBSP = Pattern.compile("&nbsp;", Pattern.CASE_INSENSITIVE);
  public static final String REP_NBSP = " ";

  public static final Pattern PAT_AMP = Pattern.compile("&amp;", Pattern.CASE_INSENSITIVE);
  public static final String REP_AMP = "&";

  public static final Pattern PAT_PUNCTUATION = Pattern.compile("[,\\\\]", Pattern.CASE_INSENSITIVE);
  public static final String REP_PUNCTUATION = EMPTY_STRING;

}
