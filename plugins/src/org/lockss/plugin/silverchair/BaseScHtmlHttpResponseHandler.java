/*
 *
 *  * $Id: Template.jav,v 1.2 2005/10/07 23:46:50 clairetg Exp $
 *
 *
 *
 * Copyright (c) 2017 Board of Trustees of Leland Stanford Jr. University,
 * all rights reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL
 * STANFORD UNIVERSITY BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 * Except as contained in this notice, the name of Stanford University shall not
 * be used in advertising or otherwise to promote the sale, use or other dealings
 * in this Software without prior written authorization from Stanford University.
 *
 * /
 */

package org.lockss.plugin.silverchair;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.lockss.daemon.PluginException;
import org.lockss.plugin.ArchivalUnit;
import org.lockss.plugin.ContentValidationException;
import org.lockss.util.Logger;

import org.lockss.util.urlconn.CacheException;
import org.lockss.util.urlconn.CacheResultHandler;
import org.lockss.util.urlconn.CacheResultMap;
import org.lockss.util.urlconn.CacheSuccess;

public class BaseScHtmlHttpResponseHandler implements CacheResultHandler {
  private static final Logger log = Logger.getLogger(BaseScHtmlHttpResponseHandler.class);

  
  // Use a getter so that this can be overridden by a child plugin
  protected Pattern getNonFatalPattern() {
    return null;
  }
  
  @Override
  public void init(final CacheResultMap map) throws PluginException {
    log.warning("Unexpected call to init()");
    throw new UnsupportedOperationException("Unexpected call to ScHttpResponseHandler.init()");
  }

  @Override
  public CacheException handleResult(final ArchivalUnit au, final String url, final int code) throws PluginException {
    log.debug(code + ": " + url);
    
    Pattern pat = getNonFatalPattern();
    Matcher mat = pat == null ? null : pat.matcher(url);
    switch (code) {
      case 403:
        //Do not fail the crawl for 403 errors at URLs like the one below should not be fatal
        if (mat != null && mat.find()) {
          return new CacheException.NoRetryDeadLinkException("403 Forbidden (non-fatal)");
        } else {
          return new CacheException.RetrySameUrlException("403 Forbidden error");
        }
      case 405:
        return new CacheException.RetryableNetworkException_3_10S("405 Method Not Allowed");
      case 500:
        //Do not fail the crawl for 500 errors at URLs like the one below should not be fatal
        if (mat != null && mat.find()) {
          return new CacheException.NoRetryDeadLinkException("500 Internal server (non-fatal)");
        } else {
          return new CacheException.RetrySameUrlException("500 Internal server error");
        }
      default:
        log.warning("Unexpected responseCode (" + code + ") in handleResult(): AU " + au.getName() + "; URL " + url);
        throw new UnsupportedOperationException("Unexpected responseCode (" + code + ")");
    }
  }

  @Override
  public CacheException handleResult(final ArchivalUnit au, final String url, final Exception ex)
      throws PluginException {
    log.debug(ex.getMessage() + ": " + url);

    // this checks for the specific exceptions before going to the general case and retry
    if (ex instanceof ContentValidationException.WrongLength) {
      if (url.contains(".pdf")) {
        log.warning("Wrong length - not storing file " + url);
        // retry and no store cache exception
        return new ScRetryableNetworkException(ex);
      } else {
        log.debug3("Ignoring Wrong length - storing file");
        // ignore and continue
        return new CacheSuccess();
      }
    }
    
    // handle retryable exceptions ; URL MIME type mismatch 
    if (ex instanceof ContentValidationException) {
      if (url.contains(".pdf")) {
        log.warning("Warning - retry/no fail/no store " + url);
        // retry and no store cache exception
        return new ScRetryableNetworkException(ex);
      }
      // log.warning(url, ex);
      return new CacheException.WarningOnly(ex.getMessage() + " (non-fatal/storing)");
    }

    if (ex instanceof javax.net.ssl.SSLHandshakeException) {
      log.debug3("Retrying SSLHandshakeException", ex);
      return new ScRetryableNetworkException(ex);
    }
    
    // we should only get in here cases that we specifically map, report and retry/no fail/no store
    log.warning("Unexpected call to handleResult(): AU " + au.getName() + "; URL " + url, ex);
    return new ScRetryableNetworkException(ex);
  }

  public static class ScRetryableNetworkException extends CacheException.RetryableNetworkException_3_10S {

    public ScRetryableNetworkException() {
      super();
    }
    
    public ScRetryableNetworkException(String message) {
      super(message);
    }
    
    /** Create this if details of causal exception are more relevant. */
    public ScRetryableNetworkException(Exception e) {
      super(e);
    }
    
    @Override
    protected void setAttributes() {
      super.setAttributes();
      attributeBits.clear(ATTRIBUTE_FAIL);
      attributeBits.set(ATTRIBUTE_NO_STORE);
    }
  }

}
