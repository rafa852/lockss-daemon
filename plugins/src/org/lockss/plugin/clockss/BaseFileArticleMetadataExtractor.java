/*
 * $Id$
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

package org.lockss.plugin.clockss;

import java.io.IOException;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.apache.commons.io.FilenameUtils;

// Make a custom AF object so that we can pass the already scraped DOI to the extractor
// then just fill in the metadata from what is known by the landing url
// there is nothing more to get

import org.lockss.config.TdbAu;
import org.lockss.daemon.PluginException;
import org.lockss.daemon.PublicationDate;
import org.lockss.extractor.ArticleMetadata;
import org.lockss.extractor.BaseArticleMetadataExtractor;
import org.lockss.extractor.FileMetadataExtractor;
import org.lockss.extractor.MetadataField;
import org.lockss.extractor.MetadataTarget;
import org.lockss.plugin.ArchivalUnit;
import org.lockss.plugin.ArticleFiles;
import org.lockss.plugin.AuUtil;
import org.lockss.plugin.CachedUrl;
import org.lockss.util.Logger;


/*
 *  Despite the awkward name, this is an ArticleMetadata class
 *  which is designed to provide support for a class of content called "Files"
 *  
 *  A shared ArticleMetadataExtractor for "file" objects.
 *  File objects (as opposed to article, book, or proceeding require 
 *  only an access_url and a publisher
 *  Additional data is stored in a map in the MD table for the item.
 *  This class adds standardized support to add the size, and mimetype of the item
 *  The sub class extends this to provide a file identifier and the file type (what it is),
 *  and any additional custom key/value pairs for the specific type of file.
 *  
 */  
 public class BaseFileArticleMetadataExtractor extends BaseArticleMetadataExtractor{

	private static Logger log = 
			Logger.getLogger(BaseFileArticleMetadataExtractor.class);
	private static final String DEFAULT_FILE_TYPE = "file";
	private static final String DEFAULT_PUBLICATION_TITLE = "File Publication";
	

	public BaseFileArticleMetadataExtractor(String role) {
		super(role);
	}


	@Override
	public void extract(MetadataTarget target, ArticleFiles af, Emitter emitter)
			throws IOException, PluginException {

		BaseFileEmitter emit = new BaseFileEmitter(af, emitter);

		/*
		 * An individual FILE type child plugin an override these to 
		 * better meet the needs of their layout. 
		 * The fileCu defines the item whose mimetype and size define the object
		 * The metadataCu is used for any optional additional FileMetadataExtractor work
		 * if it's null (default), basic information about the file object is emitted
		 */
		CachedUrl metadataCu = getMetadataUrl(af);  // null if no additional metadata extraction 
		CachedUrl fileCu = getFileUrl(af); //the item that is considered the access url for this FILE type object
		if (metadataCu != null) {
			try {
				// additional FILE item information added in the emitter
				FileMetadataExtractor me = metadataCu.getFileMetadataExtractor(target);
				if (me != null) {
					// This will return through the BaseFileEmitter and applied the FileObjectMetadata defaults there
					me.extract(target, metadataCu, emit);
					return;
				}
			} catch (IOException ex) {
				log.warning("Error in FileMetadataExtractor", ex);
			} finally {
				AuUtil.safeRelease(metadataCu);
			}
		}
		// If we didn't use a FileMetdataExtractor, simply proceed with additional file item information in the emitter
		ArticleMetadata fileAM = new ArticleMetadata();
		emit.emitMetadata(fileCu, fileAM);
	}



    /*
     * Default is null
     * Set this CU if there is a file that should have a FileMetadataExtractor applied to it
     * Otherwise the metadata will come from the getFileUrl - size, mimetype, etc.
     * 
     */
	protected CachedUrl getMetadataUrl(ArticleFiles af) {
		return null;
	}

	/*
     * Default is the ROLE_ARTICLE_METADATA but no additional
     * metadata is extracted beyond the file characteristics - size, mimeType, etc.
     * as well as tdb information off the AU.
     * plugin can override to handle differently
     * 
     */
	protected CachedUrl getFileUrl(ArticleFiles af) {
		return af.getRoleCu(ArticleFiles.ROLE_ARTICLE_METADATA);
	}



	/**
	 * For a FILE item (as opposed to article, book, or proceeding)
	 * the metadata may be very limited and does not require parsing the
	 * contents of the object.
	 * Set consistent basic information before emitting.
	 * A child plugin can override as necessary.
	 * The am object might come in pre-filled with information from file metadata extraction
	 * It is assumed that the cu here is that of the File object
	 * @param cu - The CU for which we are generating metadata
	 * @param am - the AM in to which to put the generated metadata 
	 */
	protected void setFileObjectMetadata(ArticleFiles af, ArticleMetadata am) {
		CachedUrl fileCu = getFileUrl(af); //this might not be the same as the metadata url
		String file_url = fileCu.getUrl();
		log.debug3("generate MD for generic file object url " + file_url);
		
		
		// use getters so a child plugin can override
		// default values come from tdbau if it's available
		// am is passed in case it contains metadata parsed by an
		// option FileMetadataExtractor
		String year = getContentYear(fileCu,am);
		String publisher = getContentPublisher(fileCu,am);
		String provider = getContentProvider(fileCu, am);
		String pTitle = getPublicationTitle(fileCu,am);
		
		am.put(MetadataField.FIELD_ACCESS_URL, file_url);
		am.put(MetadataField.FIELD_PROVIDER, provider);
		am.put(MetadataField.FIELD_PUBLISHER, publisher);
		am.put(MetadataField.FIELD_DATE, year);
		// Neither an article, book, nor proceeding - "other"
		am.put(MetadataField.FIELD_ARTICLE_TYPE, MetadataField.ARTICLE_TYPE_FILE);
		am.put(MetadataField.FIELD_PUBLICATION_TYPE, MetadataField.PUBLICATION_TYPE_FILE);
		// Not explicitly necessary, would be inferred
		am.put(MetadataField.FIELD_PUBLICATION_TITLE, pTitle);

		// Add a custom map to the generic am table 
		// Allow a child to override FileType
		Map<String, String> FILE_MAP = new HashMap<String,String>();

		//default is "file"
		FILE_MAP.put("FileType", getFileObjectType(fileCu));
		// default is base filename
		FILE_MAP.put("FileIdentifier", getFileIdentifier(fileCu));
		FILE_MAP.put("FileSizeBytes", getFileSize(fileCu));
		FILE_MAP.put("FileMime", getFileMime(fileCu));
		// default is no additional k-v pairs; child can add specific items
		am.putRaw(MetadataField.FIELD_MD_MAP.getKey(), FILE_MAP);
		
		// in case there are any other am items that can be set
		setAdditionalArticleMetadata(fileCu,am);

		
	}

	/**
	 * Set an identifier value to associate with this object
	 * The default is the filename (not the full path) of the url.
	 * The cu is available for pattern matching
	 * @param cu  
	 * @return
	 */
	protected String getFileIdentifier(CachedUrl cu) {
		// we know cu isn't null
		return FilenameUtils.getBaseName(cu.getUrl());
	}


	/**
	 * Return the "publication" year for the file object
	 * By default it is the year associated with the AU in the TDB
	 * The child plugin can override this method to set a different
	 * way to identify the plugin, for example from information in the url pattern
	 * @param cu
	 * @param tdbau
	 * @return
	 */
	protected String getContentYear(CachedUrl cu, ArticleMetadata am) {
		// Get limited information from the TDB file
		ArchivalUnit au = cu.getArchivalUnit();
		TdbAu tdbau = au.getTdbAu();
		return (tdbau != null) ? tdbau.getEndYear() : null;
	}
	
	/**
	 * Return the "publication" title for the file object
	 * By default it is the title associated with the AU in the TDB
	 * The child plugin can override this method to set a different
	 * way to identify the content.
	 * @param cu
	 * @param tdbau
	 * @return
	 */
	protected String getPublicationTitle(CachedUrl cu,ArticleMetadata am) {
		// Get limited information from the TDB file
		ArchivalUnit au = cu.getArchivalUnit();
		TdbAu tdbau = au.getTdbAu();
		return (tdbau != null) ? tdbau.getPublicationTitle() : DEFAULT_PUBLICATION_TITLE;
	}	
	
	/**
	 * Return the publisher associated with this content
	 * Take the value defined in the tdb file if available.
	 * If not, use a default value which can be set by the sub class
	 * @param cu
	 * @param tdbau
	 * @return
	 */
	protected String getContentPublisher(CachedUrl cu, ArticleMetadata am) {
		ArchivalUnit au = cu.getArchivalUnit();
		TdbAu tdbau = au.getTdbAu();
		return (tdbau != null) ? tdbau.getPublisherName() : getDefaultPublisherName();
	}
	
	/**
	 *  When the publisher isn't available from the TDB
	 *  use this value
	 *  A subclass can override this method to set an appropriate value
	 *  though the TDB should be available and should have publisher set
	 */
	protected String getDefaultPublisherName() {
		return null;
	}


	protected String getContentProvider(CachedUrl cu, ArticleMetadata am) {
		ArchivalUnit au = cu.getArchivalUnit();
		TdbAu tdbau = au.getTdbAu();		
		return (tdbau != null) ? tdbau.getProviderName() : getContentPublisher(cu,am);
	}
	
	/**
	 * Define the specific type of file object being preserved
	 * which could be a publisher specific description, like "code capsule"
	 * or something more generic like "image" or "tar archive"
	 * A sub class can override this method and has the cu if
	 * the url pattern holds identifying information.
	 * @param cu
	 * @return the string to associate with the FileType key in the MD map
	 */
	protected String getFileObjectType(CachedUrl cu) {
		return DEFAULT_FILE_TYPE;
	}	
	
	/*
	 * A child can override this
	 * By default use the size of the metadata cu
	 * but this might not be true if the object is an archive
	 * from which the metadata file is accessed
	 */
	protected String getFileSize(CachedUrl cu) {
		long content_size = cu.getContentSize();
		return Long.toString(content_size);
		
	}
	
	/*
	 * A child can override this
	 * By default use the mime type of the metadata cu
	 * but this might not be true if the object is an archive
	 * from which the metadata file is accessed
	 */
	protected String getFileMime(CachedUrl cu) {
		String content_mime = cu.getContentType();
		// needed after the getContentType
		AuUtil.safeRelease(cu);
		return content_mime;
		
	}

	/**
	 * A child plugin might extend this class to add more information appropriate to
	 * the file object being preserved.  
	 * @param cu
	 * @param fILE_MAP
	 */
	protected void setAdditionalFileData(CachedUrl cu, Map<String, String> FILE_MAP,ArticleMetadata am) {
		log.debug3("In empty default setAdditionalFileData");
	}
	
	/*
	 * Most FILE objects won't need more than what is set in the setFileMetadata method
	 * but in case there is some other metadata that maps to standard article metadata fields
	 * The FILE_MAP is already on the ArticleMetadata object in case more needs to be added there
	 * a child can override this
	 */
	protected void setAdditionalArticleMetadata(CachedUrl metadataCu, ArticleMetadata fileAM) {
		log.debug3("In empty default setAdditionalArticleMetadata");
	}

	


	/*
	 * Do not use the emitter from BaseArticleMetadata because
	 * some of the default behaviors do not make sense for File objects
	 */
	 class BaseFileEmitter implements FileMetadataExtractor.Emitter {
		private Emitter parent;
		private ArticleFiles af;

		BaseFileEmitter(ArticleFiles af, Emitter parent) {
			this.af = af;
			this.parent = parent;
		}

		
		
		/*
		 * It isn't necessary for a FileMetadataExtractor to be applied
		 * These methods will be called to fill in data based on the information
		 * of the FILE object - as determined by a child plugin
		 * In the case where extraction is done on a file this comes after that step
		 * and allows use of what was found by that extractor
		 * The CU in the arguments should be that of the FILE object itself
		 * any metadata extracted from a separate metadataCu should already be in the am
		 */
		public void emitMetadata(CachedUrl fileCu, ArticleMetadata am) {
			
			setFileObjectMetadata(af, am);
			parent.emitMetadata(af, am);
		}  

		void setParentEmitter(Emitter parent) {
			this.parent = parent;
		}
	}

}