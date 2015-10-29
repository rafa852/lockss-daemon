/*
 * $Id$
 */

/*

 Copyright (c) 2000-2015 Board of Trustees of Leland Stanford Jr. University,
 all rights reserved.

 Permission is hereby granted, free of charge, to any person obtaining a copy
 of his software and associated documentation files (the "Software"), to deal
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

package org.lockss.plugin.atypon;

import java.io.IOException;

import org.lockss.daemon.*;

import org.lockss.extractor.*;
import org.lockss.plugin.ArchivalUnit;
import org.lockss.plugin.CachedUrl;
import org.lockss.util.Logger;

/*
 *  NOTE: I have found the following:
 *  JO is often used (incorrectly) but consistently as the abbreviated form of the journal title, use JF and then T2 in preference
 *  Y1 usually is the same as DA, but not always, use DA if it's there
 *  DO NOT pick up the "UR" because it often points to dx.doi.org stable URL which 
 *  will not exist in the AU.  We must manually set the access_url
TY  - JOUR/BOOK/CHAP/ECHAP/EBOOK/EDBOOK
T1  - <article title>
AU  - <author>
AU  - <other author>
Y1  - <date, often same as DA, often slightly later>
PY  - <year of pub>
DA  - <date of pub>
N1  - doi: 10.1137/100798910
DO  - 10.1137/100798910 
T2  - <journal title>
JF  - <journal title>
JO  - <abbreviated journal title>
SP  - <start page>
EP  - <end page>
VL  - <volume>
IS  - <issue>
PB  - <publisher but possibly imprint>
SN  - <issn> or <isbn>
M3  - doi: 10.1137/100798910
UR  - http://dx.doi.org/10.1137/100798910
Y2  - <later date - meaning?>
ER  -  
 * 
 */
public class BaseAtyponRisMetadataExtractorFactory
implements FileMetadataExtractorFactory {
  private static final Logger log = Logger.getLogger(BaseAtyponRisMetadataExtractorFactory.class);

  public FileMetadataExtractor createFileMetadataExtractor(MetadataTarget target,
      String contentType)
          throws PluginException {

    log.debug3("Inside Base Atypon Metadata extractor factory for RIS files");

    BaseAtyponRisMetadataExtractor ba_ris = new BaseAtyponRisMetadataExtractor();

    ba_ris.addRisTag("A1", MetadataField.FIELD_AUTHOR);
    // Do not use UR listed in the ris file! It will get set to full text CU by daemon
    return ba_ris;
  }

  public static class BaseAtyponRisMetadataExtractor
  extends RisMetadataExtractor {

    // override this to do some additional attempts to get valid data before emitting
    @Override
    public void extract(MetadataTarget target, CachedUrl cu, FileMetadataExtractor.Emitter emitter) 
        throws IOException, PluginException {
      ArticleMetadata am = extract(target, cu); 

      /* 
       * if, due to overcrawl, we got to a page that didn't have anything
       * valid, eg "this page not found" html page
       * don't emit empty metadata (because defaults would get put in
       * Must do this after cooking, because it checks size of cooked info
       */
      if (am.isEmpty()) {
        return;
      }

      /*
       * RIS data can be variable.  We don't have any way to add priority to
       * the cooking of data, so fallback to alternate values manually
       *
       * There are differences between books and journals, so fork for titles
       * and metadata check 
       */
      if (am.get(MetadataField.FIELD_DATE) == null) {
        if (am.getRaw("Y1") != null) { // if DA wasn't there, use Y1
          am.put(MetadataField.FIELD_DATE, am.getRaw("Y1"));
        }
      }      
      /*
       * set the appropriate article type once the daemon passes along the TY
       */
      String ris_type;
      // TODO1.69 - once 1.69 is generally available, use the commented out code instead...
      if( ((ris_type = am.getRaw("TY")) == null) ) {
        ris_type = "JOUR";
        if (am.get(MetadataField.FIELD_ISBN) != null) {
          ris_type = "BOOK"; //it could be a chapter but until TY is passed through...
        }
      }
      if ( ris_type.contains("BOOK") || ris_type.contains("CHAP") ) {
        // could be CHAP, BOOK, EBOOK, ECHAP, or EDBOOK according to RIS spec
      //START TODO1.69 REPLACEMENT BIT -replace the above best guess chunk with this 
      //    if( ((ris_type = am.getRaw("TY"))!= null) && 
      //    ( ris_type.contains("BOOK") || ris_type.contains("CHAP")) ) {
      //END TODO1.69 REPLACEMENT BIT

        //BOOK in some form
        // T1 is the primary title - of the chapter for a book chapter, or book for a complete book
        // T2 is the next title up - of the book for a chapter, of the series for a book
        // T3 is the uppermost - of the series for a chapter
        //sometimes they use TI instead of T1... 
        if (am.get(MetadataField.FIELD_ARTICLE_TITLE) == null) {
          if (am.getRaw("TI") != null) { // if T1 wasn't there, use TI
            am.put(MetadataField.FIELD_ARTICLE_TITLE, am.getRaw("TI"));
          }
        }

        if (ris_type.contains("CHAP")) {
          // just one chapter - set the article type correctly
          am.put(MetadataField.FIELD_ARTICLE_TYPE, MetadataField.ARTICLE_TYPE_BOOKCHAPTER);
          if ((am.get(MetadataField.FIELD_PUBLICATION_TITLE) == null) && (am.getRaw("T2") != null)) {
            // the publication and the article titles are just the name of the book
            am.put(MetadataField.FIELD_PUBLICATION_TITLE, am.getRaw("T2"));
          }
          if ((am.get(MetadataField.FIELD_SERIES_TITLE) == null) && (am.getRaw("T3") != null)) {
            // the publication and the article titles are just the name of the book
            am.put(MetadataField.FIELD_SERIES_TITLE, am.getRaw("T3"));
          }
        } else {
          // We're a full book volume - articletitle = publicationtitle
          am.put(MetadataField.FIELD_ARTICLE_TYPE, MetadataField.ARTICLE_TYPE_BOOKVOLUME);
          if (am.get(MetadataField.FIELD_PUBLICATION_TITLE) == null) {
            // the publication and the article titles are just the name of the book
            am.put(MetadataField.FIELD_PUBLICATION_TITLE, am.get(MetadataField.FIELD_ARTICLE_TITLE));
          }
          // series title can be from T2
          if ((am.get(MetadataField.FIELD_SERIES_TITLE) == null) && (am.getRaw("T2") != null)) {
            // the publication and the article titles are just the name of the book
            am.put(MetadataField.FIELD_SERIES_TITLE, am.getRaw("T2"));
          }
        }
        // Only emit if this item is likely to be from this AU
        // protect against counting overcrawled articles
        ArchivalUnit au = cu.getArchivalUnit();
        if (!BaseAtyponMetadataUtil.metadataMatchesBookTdb(au, am)) {
          return;
        }

      } else {
        // JOURNAL default is to assume it's a journal for backwards compatibility

        if (am.get(MetadataField.FIELD_PUBLICATION_TITLE) == null) {
          if (am.getRaw("T2") != null) {
            am.put(MetadataField.FIELD_PUBLICATION_TITLE, am.getRaw("T2"));
          } else if (am.getRaw("JO") != null) {
            am.put(MetadataField.FIELD_PUBLICATION_TITLE, am.getRaw("JO")); // might be unabbreviated version
          }
        } 

        // Only emit if this item is likely to be from this AU
        // protect against counting overcrawled articles
        ArchivalUnit au = cu.getArchivalUnit();
        if (!BaseAtyponMetadataUtil.metadataMatchesTdb(au, am)) {
          return;
        }
      }

      /*
       * Fill in DOI, publisher, other information available from
       * the URL or TDB 
       * CORRECT the access.url if it is not in the AU
       */
      BaseAtyponMetadataUtil.completeMetadata(cu, am);
      emitter.emitMetadata(cu, am);
    }

  }

}
