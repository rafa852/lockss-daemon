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

package org.lockss.plugin.highwire;

import java.io.*;

import org.lockss.daemon.*;
import org.lockss.plugin.*;
import org.lockss.util.StringUtil;
import org.lockss.util.HeaderUtil;
import org.lockss.util.Logger;

public class HighWireContentValidator {
  
  private static final Logger log = Logger.getLogger(HighWireContentValidator.class);
  
  protected static final String PDF_EXT = ".pdf";
  protected static final String PNG_EXT = ".png";
  protected static final String JPG_EXT = ".jpg";
  protected static final String JPEG_EXT = ".jpeg";
  protected static final String MOV_EXT = ".mov";
  protected static final String MP4_EXT = ".mp4";
  protected static final String ZIP_EXT = ".zip";
  
  public static class TextTypeValidator implements ContentValidator {
    
    public void validate(CachedUrl cu)
        throws ContentValidationException, PluginException, IOException {
      // validate based on extension (ie .pdf or .jpg)
      String url = cu.getUrl();
      if (StringUtil.endsWithIgnoreCase(url, PDF_EXT) ||
          StringUtil.endsWithIgnoreCase(url, PNG_EXT) ||
          StringUtil.endsWithIgnoreCase(url, JPG_EXT) ||
          StringUtil.endsWithIgnoreCase(url, JPEG_EXT) ||
          StringUtil.endsWithIgnoreCase(url, MOV_EXT) ||
          StringUtil.endsWithIgnoreCase(url, MP4_EXT) ||
          StringUtil.endsWithIgnoreCase(url, ZIP_EXT)) {
        log.warning("URL MIME type mismatch: " + url);
        throw new ContentValidationException("URL MIME type mismatch");
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

