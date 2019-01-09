/*
 * $Id:$
 */

/*

 Copyright (c) 2000-2019 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.plugin.clockss.iop;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.lockss.util.*;
import org.lockss.daemon.*;
import org.lockss.extractor.*;

import org.lockss.plugin.CachedUrl;
import org.lockss.plugin.clockss.JatsPublishingSchemaHelper;
import org.lockss.plugin.clockss.SourceXmlMetadataExtractorFactory;
import org.lockss.plugin.clockss.SourceXmlSchemaHelper;


/*
 * If the xml is at 0022-3727/48/35/355104/d_48_35_355104.xml
 * then the pdf is at 0022-3727/48/35/355104/d_48_35_355104.pdf
 * and the author manuscript is at 0022-3727/48/35/355104/d_48_35_355104am.pdf
 * 
 * We are now seeing some delivered (on hard-drive) content where the ".xml" version
 * is missing but there is a 0022-3727/48/35/355104/d_48_35_355104.article
 * so adapting to fail over to this when necessary
 * 
 */

public class IopArticleXmlMetadataExtractorFactory extends SourceXmlMetadataExtractorFactory {
  private static final Logger log = Logger.getLogger(IopArticleXmlMetadataExtractorFactory.class);

  private static SourceXmlSchemaHelper JatsPublishingHelper = null;
  private static SourceXmlSchemaHelper ArticlePublishingHelper = null;
  private static final Map<String,String> IOPIssnTitleMap = new HashMap<String,String>();
  static {
	  IOPIssnTitleMap.put("0295-5075","Europhysics Letters");
	  IOPIssnTitleMap.put("0021-4922","Japanese Journal of Applied Physics");
	  IOPIssnTitleMap.put("0264-9381","Classical and Quantum Gravity");
	  IOPIssnTitleMap.put("0143-0807","European Journal of Physics");
	  IOPIssnTitleMap.put("0266-5611","Inverse Problems");
	  IOPIssnTitleMap.put("0960-1317","Journal of Micromechanics and Microengineering");
	  IOPIssnTitleMap.put("0953-4075","Journal of Physics B: Atomic, Molecular and Optical Physics");
	  IOPIssnTitleMap.put("0022-3727","Journal of Physics D: Applied Physics");
	  IOPIssnTitleMap.put("0954-3899","Journal of Physics G: Nuclear and Particle Physics");
	  IOPIssnTitleMap.put("0953-8984","Journal of Physics: Condensed Matter");
	  IOPIssnTitleMap.put("0952-4746","Journal of Radiological Protection");
	  IOPIssnTitleMap.put("0957-0233","Measurement Science and Technology");
	  IOPIssnTitleMap.put("0965-0393","Modelling and Simulation in Materials Science and Engineering");
	  IOPIssnTitleMap.put("0957-4484","Nanotechnology");
	  IOPIssnTitleMap.put("0951-7715","Nonlinearity");
	  IOPIssnTitleMap.put("0031-9120","Physics Education");
	  IOPIssnTitleMap.put("0031-9155","Physics in Medicine & Biology");
	  IOPIssnTitleMap.put("0967-3334","Physiological Measurement");
	  IOPIssnTitleMap.put("0741-3335","Plasma Physics and Controlled Fusion");
	  IOPIssnTitleMap.put("0963-0252","Plasma Sources Science and Technology");
	  IOPIssnTitleMap.put("0034-4885","Reports on Progress in Physics");
	  IOPIssnTitleMap.put("0268-1242","Semiconductor Science and Technology");
	  IOPIssnTitleMap.put("0964-1726","Smart Materials and Structures");
	  IOPIssnTitleMap.put("0953-2048","Superconductor Science and Technology");
	  IOPIssnTitleMap.put("1478-3975","Physical Biology");
	  IOPIssnTitleMap.put("1054-660X","Laser Physics");
	  IOPIssnTitleMap.put("1612-2011","Laser Physics Letters");
	  IOPIssnTitleMap.put("0026-1394","Metrologia");
	  IOPIssnTitleMap.put("1741-2560","Journal of Neural Engineering");
	  IOPIssnTitleMap.put("0256-307X","Chinese Physics Letters");
	  IOPIssnTitleMap.put("0029-5515","Nuclear Fusion");
	  IOPIssnTitleMap.put("1742-2132","Journal of Geophysics and Engineering");
	  IOPIssnTitleMap.put("1742-6588","Journal of Physics: Conference Series");
	  IOPIssnTitleMap.put("1748-3182","Bioinspiration & Biomimetics");
	  IOPIssnTitleMap.put("1748-6041","Biomedical Materials");
	  IOPIssnTitleMap.put("1749-4680","Computational Science & Discovery");
	  IOPIssnTitleMap.put("1751-8113","Journal of Physics A: Mathematical and Theoretical");
	  IOPIssnTitleMap.put("1752-7155","Journal of Breath Research");
	  IOPIssnTitleMap.put("1755-1307","IOP Conference Series: Earth and Environmental Science");
	  IOPIssnTitleMap.put("1757-8981","IOP Conference Series: Materials Science and Engineering");
	  IOPIssnTitleMap.put("1758-5082","Biofabrication");
	  IOPIssnTitleMap.put("0169-5983","Fluid Dynamics Research");
	  IOPIssnTitleMap.put("1882-0778","Applied Physics Express");
	  IOPIssnTitleMap.put("2040-8978","Journal of Optics");
	  IOPIssnTitleMap.put("2043-6262","Advances in Natural Sciences: Nanoscience and Nanotechnology");
	  IOPIssnTitleMap.put("0031-8949","Physica Scripta");
	  IOPIssnTitleMap.put("0253-6102","Communications in Theoretical Physics");
	  IOPIssnTitleMap.put("1009-0630","Plasma Science and Technology");
	  IOPIssnTitleMap.put("1674-1056","Chinese Physics B");
	  IOPIssnTitleMap.put("1674-1137","Chinese Physics C");
	  IOPIssnTitleMap.put("1674-4527","Research in Astronomy and Astrophysics");
	  IOPIssnTitleMap.put("1674-4926","Journal of Semiconductors");
	  //using eissn for these ones; issn is the same
	  IOPIssnTitleMap.put("1367-2630","New Journal of Physics");
	  IOPIssnTitleMap.put("2050-6120","Methods and Applications in Fluorescence");
	  IOPIssnTitleMap.put("2051-672X","Surface Topography: Metrology and Properties");
	  IOPIssnTitleMap.put("2053-1583","2D Materials");
	  IOPIssnTitleMap.put("2053-1591","Materials Research Express");
	  IOPIssnTitleMap.put("2053-1613","Translational Materials Research");
	  IOPIssnTitleMap.put("2057-1739","Convergent Science Physical Oncology");
	  IOPIssnTitleMap.put("2057-1976","Biomedical Physics & Engineering Express");
	  IOPIssnTitleMap.put("1748-9326","Environmental Research Letters");
	  IOPIssnTitleMap.put("1742-5468","Journal of Statistical Mechanics: Theory and Experiment");
  }  


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
		  if(cu.getUrl().endsWith("article")) {
			  if (ArticlePublishingHelper == null) {
				  ArticlePublishingHelper = new IopArticleXmlSchemaHelper();
			  }
			  return ArticlePublishingHelper;

		  }
		  if (JatsPublishingHelper == null) {
			  JatsPublishingHelper = new JatsPublishingSchemaHelper();
		  }
		  return JatsPublishingHelper;
	  }


    /* 
     * filename is the same as the xml, just change the suffix 
     */
    @Override
    protected List<String> getFilenamesAssociatedWithRecord(SourceXmlSchemaHelper helper, CachedUrl cu,
        ArticleMetadata oneAM) {

      // filename is just the same a the XML filename but with .pdf 
      // instead of .xml
      String url_string = cu.getUrl();
      int suffix_index = url_string.lastIndexOf(".");
      String pdfName;
      if (suffix_index > 0) {
          pdfName = url_string.substring(0,suffix_index+1) + "pdf";
      } else {
          pdfName = url_string.substring(0,url_string.length() - 3) + "pdf";
      }
      log.debug3("pdfName is " + pdfName);
      List<String> returnList = new ArrayList<String>();
      returnList.add(pdfName);
      return returnList;
    }
    
    @Override
    protected void postCookProcess(SourceXmlSchemaHelper schemaHelper, 
    		CachedUrl cu, ArticleMetadata thisAM) {

    	log.debug3("in IOP postCookProcess");
    	if(schemaHelper == JatsPublishingHelper ) {
    		//If we didn't get a valid date value, use the copyright year if it's there
    		if (thisAM.get(MetadataField.FIELD_DATE) == null) {
    			if (thisAM.getRaw(JatsPublishingSchemaHelper.JATS_edate) != null) {
    				thisAM.put(MetadataField.FIELD_DATE, thisAM.getRaw(JatsPublishingSchemaHelper.JATS_edate));
    			} else {
    				// well then try the print date
    				thisAM.put(MetadataField.FIELD_DATE, thisAM.getRaw(JatsPublishingSchemaHelper.JATS_date));
    			}
    		}
    		if ((thisAM.get(MetadataField.FIELD_ISSN) == null) && (thisAM.getRaw(JatsPublishingSchemaHelper.JATS_pissn) != null)){
    			//try the pissn version if raw value not null
    			thisAM.put(MetadataField.FIELD_ISSN, thisAM.getRaw(JatsPublishingSchemaHelper.JATS_pissn));
    		}
    	} else {
    		if (thisAM.get(MetadataField.FIELD_PUBLICATION_TITLE) == null) {
    			// try to set a publication.title from the IOP issn-title map
    			String mapTitle = IOPIssnTitleMap.get(thisAM.get(MetadataField.FIELD_ISSN)); 
    			if (mapTitle != null) {
    				thisAM.put(MetadataField.FIELD_PUBLICATION_TITLE, mapTitle);
    			}
    					
    					
    		}
    	}
    }

  }
}
