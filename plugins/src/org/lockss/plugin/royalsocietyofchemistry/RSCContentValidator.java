/*
 * $Id$
 */

/*

Copyright (c) 2017 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.plugin.royalsocietyofchemistry;

import java.io.*;
import org.lockss.daemon.*;
import org.lockss.plugin.*;
import org.lockss.util.StringUtil;
import org.lockss.util.HeaderUtil;
import org.lockss.util.IOUtil;
import org.lockss.util.Logger;

public class RSCContentValidator {
	
	private static final Logger log = Logger.getLogger(RSCContentValidator.class);

	protected static final String PDF_1 = "/articlepdf/";
	protected static final String PDF_2 = "/chapterpdf/";

	//http://pubs.rsc.org/en/content/articlelanding/2016/fo/c6fo00030d
	//http://pubs.rsc.org/en/journals/lockss?journalcode=fo&volume=7&year=2016&issue=10  


	/*
  <title>  Access Denied</title><link rel="shortcut icon" href="https://www.rsc-cdn....">
</head><body><div>Access Denied</div>
...
	 */
	private static final String ACCESS_DENIED_STRING = "Access Denied";


	public static class TextTypeValidator implements ContentValidator {

		public void validate(CachedUrl cu)
				throws ContentValidationException, PluginException, IOException {
			// validate based on pdf type url (ie .pdf or .jpg)
			String url = cu.getUrl();
			if ((StringUtil.indexOfIgnoreCase(url, PDF_1) > 0) ||
					(StringUtil.indexOfIgnoreCase(url, PDF_2) > 0)) {
				//We want this to try and fetch again - this exception gets mapped to retry
				throw new ContentValidationException("URL MIME type mismatch");
			}

			/* if this is a TOC or article landing page */

			if (url.contains("/articlelanding/") || url.contains("&issue=")) { 
				Reader reader = new BufferedReader(cu.openForReading());
				try {
					if (StringUtil.containsString(reader,ACCESS_DENIED_STRING,true)) {
						throw new ContentValidationException("Found access denied page");
					}
				} finally {
					IOUtil.safeClose(reader);
					cu.release();
				}
			}			
		}
	}

  public static class Factory implements ContentValidatorFactory {
    public ContentValidator createContentValidator(ArchivalUnit au, String contentType) {
      switch (HeaderUtil.getMimeTypeFromContentType(contentType)) {
      case "text/html":
      case "text/*":
        return new TextTypeValidator();
      default:
        return null;
      }
    }
  }
  
}

