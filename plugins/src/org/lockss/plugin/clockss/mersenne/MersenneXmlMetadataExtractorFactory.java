/*
 * $Id:$
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

package org.lockss.plugin.clockss.mersenne;

import java.util.ArrayList;
import java.util.List;
import org.lockss.util.*;
import org.apache.commons.io.FilenameUtils;
import org.lockss.daemon.*;
import org.lockss.extractor.*;
import org.lockss.plugin.CachedUrl;
import org.lockss.plugin.clockss.JatsPublishingSchemaHelper;
import org.lockss.plugin.clockss.SourceXmlMetadataExtractorFactory;
import org.lockss.plugin.clockss.SourceXmlSchemaHelper;


/*
 * If the xml is at AIF/AIF_2015__65_1/AIF_2015__65_1.xml
 * then the pdf is at ./AIF_2015__65_1_389_0/AIF_2015__65_1_389_0.pdf
 * 
 * In the JATS use the 
 * <self-uri content-type="application/pdf" xlink:href="AIF/AIF_2015__65_1/AIF_2015__65_1_101_0/AIF_2015__65_1_101_0.pdf">
 * although the XML is part way down that path - it hangs from base_url/year...
 * 
 */

public class MersenneXmlMetadataExtractorFactory extends SourceXmlMetadataExtractorFactory {
  private static final Logger log = Logger.getLogger(MersenneXmlMetadataExtractorFactory.class);

  private static SourceXmlSchemaHelper MersenneIssueHelper = null;
  @Override
  public FileMetadataExtractor createFileMetadataExtractor(MetadataTarget target,
      String contentType)
          throws PluginException {
	  return new JatsPublishingSourceXmlMetadataExtractor();
  }

  public class JatsPublishingSourceXmlMetadataExtractor extends SourceXmlMetadataExtractor {

	    @Override
	    protected SourceXmlSchemaHelper setUpSchema(CachedUrl cu) {
	      // Once you have it, just keep returning the same one. It won't change.
	      if (MersenneIssueHelper == null) {
	        MersenneIssueHelper = new MersenneIssueSchemaHelper();
	      }
	      return MersenneIssueHelper;
	    }


	    /* 
	     * filename is the articleID, but in a subdirectory of that name...
	     * fullpathname is pulled from the self-uri for the pdf href
	     */
	    @Override
	    protected List<String> getFilenamesAssociatedWithRecord(SourceXmlSchemaHelper helper, CachedUrl cu,
	    		ArticleMetadata oneAM) {

	    	// the XML location:
	    	// http://foo/sourcefiles/mersenne-released/2018/AIF/AIF_2015__65_1/AIF_2015__65_1.xml
	    	String url_string = cu.getUrl();
	    	// The path to it:
	    	//http://foo/sourcefiles/mersenne-released/2018/AIF/AIF_2015__65_1/AIF_2015__65_1.xml
	    	String xmlPath = FilenameUtils.getPath(url_string);
	    	// the PDF href:
	    	// AIF/AIF_2015__65_1/AIF_2015__65_1_397_0/AIF_2015__65_1_397_0.pdf
	    	// Take just the subdir and filename...
	    	String pdf_href = oneAM.getRaw(MersenneIssueSchemaHelper.Mersenne_pdf_uri);
	    	String pdfName = "";
	    	if (pdf_href != null) {
	    		String pdf_file = FilenameUtils.getName(pdf_href);
	    		String pdf_dir = FilenameUtils.getName(FilenameUtils.getPathNoEndSeparator(pdf_href));
	    		pdfName = xmlPath + pdf_dir + "/" + pdf_file;
	    		log.debug3("pdfName is " + pdfName);
	    	} else {
	    		// try this - not likely 
	    		pdfName = url_string.substring(0,url_string.length() - 3) + "pdf";
	    	}
	    	List<String> returnList = new ArrayList<String>();
	    	returnList.add(pdfName);
	    	return returnList;

	    }
	    
	    @Override
	    protected void postCookProcess(SourceXmlSchemaHelper schemaHelper, 
	        CachedUrl cu, ArticleMetadata thisAM) {

	      log.debug3("in Mersenne postCookProcess");

	      //If we didn't get a valid date value, use the copyright year if it's there
	      if (thisAM.get(MetadataField.FIELD_DATE) == null) {
	        if (thisAM.getRaw(JatsPublishingSchemaHelper.JATS_date) != null) {
	          thisAM.put(MetadataField.FIELD_DATE, thisAM.getRaw(JatsPublishingSchemaHelper.JATS_date));
	        } else {// last chance
	          thisAM.put(MetadataField.FIELD_DATE, thisAM.getRaw(JatsPublishingSchemaHelper.JATS_edate));
	        }
	      }
	    }
	    
	  }
	}
