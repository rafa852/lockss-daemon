/*
 * $Id: OJS2ArticleIteratorFactory.java,v 1.3 2012-06-18 23:20:38 thib_gc Exp $
 */

/*

Copyright (c) 2000-2011 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.plugin.ojs2;

import java.io.*;
import java.net.MalformedURLException;
import java.util.*;
import java.util.regex.*;

import org.htmlparser.*;
import org.htmlparser.lexer.*;
import org.htmlparser.tags.*;
import org.htmlparser.util.*;
import org.lockss.daemon.*;
import org.lockss.extractor.*;
import org.lockss.filter.html.HtmlNodeFilters;
import org.lockss.plugin.*;
import org.lockss.plugin.ojs2.OJS2ArticleIteratorFactory.OJS2ArticleIterator.LoggerAdapter;
import org.lockss.util.*;

public class OJS2ArticleIteratorFactory
    implements ArticleIteratorFactory,
               ArticleMetadataExtractorFactory {

  public static class Role {
    public static final String FULL_TEXT_EPUB = "FullTextEpub";
    public static final String SOURCE_XML = "SourceXml";
  }
  
  protected static Logger log = Logger.getLogger("OJS2ArticleIteratorFactory");
  
  @Override
  public Iterator<ArticleFiles> createArticleIterator(ArchivalUnit au,
                                                      MetadataTarget target)
      throws PluginException {
    String base_url = au.getConfiguration().get(ConfigParamDescr.BASE_URL.getKey());
    String journal_id = au.getConfiguration().get(ConfigParamDescr.JOURNAL_ID.getKey());
    List roots = ListUtil.list(String.format("%sindex.php/%s/issue/view", base_url, journal_id.toLowerCase()),
                               String.format("%sindex.php/%s/issue/view", base_url, journal_id.toUpperCase()));
    return new OJS2ArticleIterator(au,
                                   new SubTreeArticleIterator.Spec()
                                       .setTarget(target)
                                       .setRoots(roots),
                                   target);
  }

  protected static class OJS2ArticleIterator extends SubTreeArticleIterator {

    protected Pattern SHOW_TOC_PATTERN = Pattern.compile("/issue/view/([^/]+)/showToc$", Pattern.CASE_INSENSITIVE);
    
    protected Pattern PLAIN_TOC_PATTERN = Pattern.compile("/issue/view/([^/]+)$", Pattern.CASE_INSENSITIVE);
    
    protected MetadataTarget target;
    
    protected Set<String> alreadyEmitted;
    
    public OJS2ArticleIterator(ArchivalUnit au,
                               SubTreeArticleIterator.Spec spec,
                               MetadataTarget target) {
      super(au, spec);
      this.target = target;
      this.alreadyEmitted = new HashSet<String>();
    }
    
    @Override
    protected ArticleFiles createArticleFiles(CachedUrl cu) {
      String url = cu.getUrl();
      Matcher mat;

      mat = SHOW_TOC_PATTERN.matcher(url);
      if (mat.find()) {
        processShowToc(cu, mat);
        return null;
      }

      mat = PLAIN_TOC_PATTERN.matcher(url);
      if (mat.find()) {
        processPlainToc(cu, mat);
        return null;
      }

      log.warning("Mismatch between article iterator factory and article iterator: " + url);
      return null;
    }
    
    protected void processShowToc(CachedUrl tocCu, Matcher tocMat) {
      processToc(tocCu.getUnfilteredInputStream(), tocCu.getEncoding(), tocCu.getUrl());
    }
    
    protected void processPlainToc(CachedUrl tocCu, Matcher tocMat) {
      CachedUrl showCu = au.makeCachedUrl(tocMat.replaceFirst("/issue/view/$1/showToc"));
      if (showCu != null && showCu.hasContent()) {
        AuUtil.safeRelease(showCu);
        return;
      }
      processToc(tocCu.getUnfilteredInputStream(), tocCu.getEncoding(), tocCu.getUrl());
    }
    
    protected void processToc(InputStream in, String encoding, String url) {
      try {
        Lexer.STRICT_REMARKS = false; // Accept common variants of HTML comments
        InputStreamSource source = new InputStreamSource(in, encoding);
        Parser parser = new Parser(new Lexer(new Page(source)), new LoggerAdapter(url));
        NodeList nodeList = parser.extractAllNodesThatMatch(HtmlNodeFilters.tagWithAttribute("table", "class", "tocArticle"));
        SimpleNodeIterator iter = nodeList.elements();
        log.debug3("Processing articles");
        while (iter.hasMoreNodes()) {
          log.debug3("Processing one article");
          processArticle(iter.nextNode());
        }
      }
      catch (UnsupportedEncodingException e) {
        e.printStackTrace();
      }
      catch (ParserException e) {
        e.printStackTrace();
      }
    }

    protected void processArticle(Node node) {
      Map<String, CachedUrl> map = new HashMap<String, CachedUrl>();
      NodeList links = new NodeList();
      node.collectInto(links, HtmlNodeFilters.tagWithAttribute("a", "href"));
      SimpleNodeIterator iter = links.elements();
      while (iter.hasMoreNodes()) {
        LinkTag link = (LinkTag)iter.nextNode();
        CachedUrl linkCu = null;
        try {
          linkCu = au.makeCachedUrl(UrlUtil.normalizeUrl(link.extractLink(), au));
        }
        catch (PluginBehaviorException pbe) {
          log.debug3("Plugin behavior exception", pbe);
          continue; // Ignore
        }
        catch (MalformedURLException mue) {
          log.debug3("Malformed URL exception", mue);
          continue; // Ignore
        }
        if (linkCu != null) {
          if (linkCu.hasContent()) {
            String linkUrl = linkCu.getUrl();
            if (linkUrl.endsWith("/0")) {
              AuUtil.safeRelease(linkCu);
              continue; // Ignore (dupe)
            }
            String label = link.toPlainTextString().trim().toLowerCase();
            if (label == null || label.length() == 0) {
              label = link.getText().trim().toLowerCase();
            }
            if (!map.containsKey(label)) {
              log.debug3(label + " -> " + linkUrl);
              map.put(label, linkCu);
            }
          }
          else {
            AuUtil.safeRelease(linkCu);
          }
        }
      }
      
      ArticleFiles af = new ArticleFiles();
      guessAbstract(af, map);
      guessFullTextPdf(af, map);
      doGuess(af, map, "html", ArticleFiles.ROLE_FULL_TEXT_HTML, "Full-text HTML");
      doGuess(af, map, "epub", Role.FULL_TEXT_EPUB, "Full-text EPUB");
      doGuess(af, map, "xml", Role.SOURCE_XML, "Source XML");
      chooseFullTextCu(af);

      // Emit
      CachedUrl cu = af.getFullTextCu();
      if (cu != null) {
        if (!alreadyEmitted.contains(cu.getUrl())) {
          alreadyEmitted.add(cu.getUrl());
          emitArticleFiles(af);
        }
      }
      
      // Clean up
      for (CachedUrl releaseCu : map.values()) {
        AuUtil.safeRelease(releaseCu);
      }
    }

    protected void guessAbstract(ArticleFiles af, Map<String, CachedUrl> map) {
      // Try labels
      CachedUrl cu = doGuessFromLabels(map,
                                       new String[] {
                                           "abstract",
                                           "r\u00e9sum\u00e9",
                                       });
      if (cu != null) {
        log.debug2("Abstract: " + cu.getUrl());
        af.setRoleCu(ArticleFiles.ROLE_ABSTRACT, cu);
        return;
      }
      
      // Find a single undecorated link
      cu = doGuessSingleUrl(map, "/article/view/[^/]+$");
      if (cu == null) {
        // Alternatively, try a single full link
        cu = doGuessSingleUrl(map, "/article/view/[^/]+/[^/]+$");
      }
      if (cu != null) {
        log.debug2("Abstract candidate: " + cu.getUrl());
        af.setRoleCu(ArticleFiles.ROLE_ABSTRACT, cu);
        return;
      }
    }

    protected void guessFullTextPdf(ArticleFiles af, Map<String, CachedUrl> map) {
      // Try labels
      CachedUrl cu = doGuessFromLabels(map,
                                       new String[] {
                                           "high-resolution pdf",
                                           "high resolution pdf",
                                           "high-res pdf",
                                           "high res pdf",
                                           "pdf",
                                       });
      if (cu != null) {
        log.debug2("Full-text PDF: " + cu.getUrl());
        guessPdfLanding(af, cu);
        return;
      }
      
      // Alternatively, find a single link decorated with correct suffix
      cu = doGuessSingleUrl(map, "/article/view/[^/]+/pdf(_[^/]+)?$");
      if (cu != null) {
        log.debug2("Full-text PDF candidate: " + cu.getUrl());
        guessPdfLanding(af, cu);
        return;
      }
      
      // Pick the largest "PDF (2.3MB)" (or whatever) label if applicable
      Pattern pat = Pattern.compile("^pdf *\\(([0-9]+(\\.[0-9]+)?) *[a-z]+\\)$", Pattern.CASE_INSENSITIVE);
      float largestFloat = 0.0f;
      for (String str : map.keySet()) {
        Matcher mat = pat.matcher(str);
        if (mat.find()) {
          try {
            float candidateFloat = Float.parseFloat(mat.group(1));
            if (largestFloat < candidateFloat) {
              largestFloat = candidateFloat;
              cu = map.get(str);
            }
          }
          catch (NumberFormatException nfe) {
            // Just move on
            log.debug3("Bad float conversion in: " + str);
          }
        }
      }
      if (cu != null) {
        log.debug2("Full-text PDF candidate: " + cu.getUrl());
        guessPdfLanding(af, cu);
        return;
      }
      
      // Still nothing? Try a single link that has a PDF variant
      cu = doGuessSingleUrl(map, "/article/view/([^/]+)/([^/]+)$");
      if (cu != null) {
        guessPdfLanding(af, cu);
        return;
      }
    }

    protected void guessPdfLanding(ArticleFiles af, CachedUrl cu) {
      if (cu.getContentType().startsWith("application/pdf")) {
        af.setRoleCu(ArticleFiles.ROLE_FULL_TEXT_PDF, cu);
        return;
      }
      
      Pattern pat = Pattern.compile("/article/view/([^/]+)/(pdf_)?([^/]+)$", Pattern.CASE_INSENSITIVE);
      Matcher mat = pat.matcher(cu.getUrl());
      if (mat.find()) {
        CachedUrl pdfCu = au.makeCachedUrl(mat.replaceFirst("/article/view/$1/$3"));
        if (pdfCu != null && pdfCu.hasContent() && pdfCu.getContentType().startsWith("application/pdf")) {
          af.setRoleCu(ArticleFiles.ROLE_FULL_TEXT_PDF_LANDING_PAGE, cu);
          af.setRoleCu(ArticleFiles.ROLE_FULL_TEXT_PDF, pdfCu);
        }
        if (pdfCu != null) {
          AuUtil.safeRelease(pdfCu);
        }
        return;
      }

      // Ideally this doesn't happen
      af.setRoleCu(ArticleFiles.ROLE_FULL_TEXT_PDF, cu);
    }
    
    protected void doGuess(ArticleFiles af,
                           Map<String, CachedUrl> map,
                           String label,
                           String role,
                           String debugWords) {
      // Find the right label
      CachedUrl labelCu = map.get(label);
      if (labelCu != null) {
        log.debug2(debugWords + ": " + labelCu.getUrl());
        af.setRoleCu(role, labelCu);
        return;
      }
      
      // Alternatively, find a single link decorated with correct suffix
      Pattern pat = Pattern.compile("/article/view/[^/]+/" + label + "(_[^/]+)?$", Pattern.CASE_INSENSITIVE);
      CachedUrl candidate = null;
      for (CachedUrl cu : map.values()) {
        if (pat.matcher(cu.getUrl()).find()) {
          if (candidate == null) {
            candidate = cu; // Remember candidate
          }
          else if (!candidate.getUrl().equalsIgnoreCase(cu.getUrl())) {
            log.debug3("Competing " + debugWords + "candidate: " + candidate.getUrl());
            candidate = null; // More than one candidate -- forget it
            break; // Bail
          }
        }
      }
      if (candidate != null) {
        log.debug2(debugWords + " candidate: " + candidate.getUrl());
        af.setRoleCu(role, candidate);
      }
    }
      
    protected CachedUrl doGuessFromLabels(Map<String, CachedUrl> map,
                                         String[] labels) {
      for (String str : labels) {
        if (map.containsKey(str)) {
          CachedUrl cu = map.get(str);
          log.debug3("Found candidate: " + cu.getUrl());
          return cu;
        }
      }
      return null;
    }
    
    protected CachedUrl doGuessSingleUrl(Map<String, CachedUrl> map,
                                         String patternString) {
      CachedUrl candidate = null;
      Pattern pat = Pattern.compile(patternString, Pattern.CASE_INSENSITIVE);
      for (CachedUrl cu : map.values()) {
        if (pat.matcher(cu.getUrl()).find()) {
          if (candidate == null) {
            log.debug3("Found candidate: " + cu.getUrl());
            candidate = cu; // Remember candidate
          }
          else if (!candidate.getUrl().equalsIgnoreCase(cu.getUrl())) {
            log.debug3("Competing candidate: " + cu.getUrl());
            return null; // Bail
          }
        }
      }
      if (candidate != null) {
        log.debug3("Final candidate: " + candidate.getUrl());
        return candidate;
      }
      return null; // No candidate found
    }
    
    protected void chooseFullTextCu(ArticleFiles af) {
      final String[] ORDER = new String[] {
          ArticleFiles.ROLE_FULL_TEXT_HTML,
          ArticleFiles.ROLE_FULL_TEXT_PDF,
          ArticleFiles.ROLE_FULL_TEXT_PDF_LANDING_PAGE,
          ArticleFiles.ROLE_ABSTRACT,
      };
      for (String role : ORDER) {
        CachedUrl cu = af.getRoleCu(role);
        if (cu != null) {
          af.setFullTextCu(cu);
          return;
        }
      }
      log.debug2("No full-text CU");
    }
    
    protected static class LoggerAdapter implements ParserFeedback {
      protected String url;
      public LoggerAdapter(String url) {
        this.url = url;
      }
      @Override
      public void error(String msg, ParserException pe) {
        log.error(String.format("While processing %s: %s", url, msg), pe);
      }
      @Override
      public void info(String msg) {
        log.info(String.format("While processing %s: %s", url, msg));
      }
      @Override
      public void warning(String msg) {
        log.warning(String.format("While processing %s: %s", url, msg));
      }
    }
  
  }

  @Override
  public ArticleMetadataExtractor createArticleMetadataExtractor(MetadataTarget target)
      throws PluginException {
    return new BaseArticleMetadataExtractor(null);
  }

  public static void main(String[] args) throws Exception {
    Lexer.STRICT_REMARKS = false;
    InputStreamSource source = new InputStreamSource(new FileInputStream("/tmp/h0"), "UTF-8");
    Parser parser = new Parser(new Lexer(new Page(source)), new OJS2ArticleIterator.LoggerAdapter("http://www.nano-reviews.net/index.php/nano/issue/view/431"));
//    NodeList nodeList = parser.extractAllNodesThatMatch(HtmlNodeFilters.tagWithAttribute("table", "class", "tocArticle"));
    for (SimpleNodeIterator iter = parser.extractAllNodesThatMatch(HtmlNodeFilters.tagWithAttribute("table", "class", "tocArticle")).elements() ; iter.hasMoreNodes() ; ) {
      NodeList links = new NodeList();
      ((Node)iter.nextNode()).collectInto(links, HtmlNodeFilters.tagWithAttribute("a", "href"));
      System.out.println(links);
    }
  }
  
}
