/*
 * $Id: $
 */

/*

Copyright (c) 2018 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.plugin.dividedsociety;

import java.util.Iterator;
import java.util.regex.Pattern;

import org.lockss.daemon.*;
import org.lockss.extractor.ArticleMetadataExtractor;
import org.lockss.extractor.ArticleMetadataExtractorFactory;
import org.lockss.extractor.BaseArticleMetadataExtractor;
import org.lockss.extractor.MetadataTarget;
import org.lockss.plugin.*;
import org.lockss.util.Logger;

public class DividedSocietyCollectionArticleIteratorFactory
implements ArticleIteratorFactory,
           ArticleMetadataExtractorFactory {
  
  protected static Logger log = Logger.getLogger(DividedSocietyCollectionArticleIteratorFactory.class);
  
  // params from tdb file corresponding to AU
  protected static final String ROOT_TEMPLATE = "\"%s%s/\", base_url,collection_id";
  // find landing pages for each item 
  // https://www.dividedsociety.org/posters/barnes-mccormack-50th-anniversary
  // https://www.dividedsociety.org/essays/religion-northern-irelands-conflict-and-peace
  // https://www.dividedsociety.org/outreach/toolkits/1994-ceasefire
  // https://www.dividedsociety.org/outreach/audio-interviews/black-humour-kept-people-going
  // We've already checked the collection_id so fine to not check in pattern
  // outreach has an additional level
  protected static final String PATTERN_TEMPLATE =
      "\"/(essays|posters|outreach/[^/]+)/[^/]+$\"";
  
  private static final Pattern LANDING_PATTERN = Pattern.compile(
      "^(https?://[^/]+/(essays|posters|outreach/[^/]+)/[^/]+$)", Pattern.CASE_INSENSITIVE);
  private static final String LANDING_REPLACEMENT = "$1";

  @Override
  public Iterator<ArticleFiles> createArticleIterator(ArchivalUnit au, MetadataTarget target) 
      throws PluginException {
    SubTreeArticleIteratorBuilder builder = new SubTreeArticleIteratorBuilder(au);
    
    builder.setSpec(target,
        ROOT_TEMPLATE,
        PATTERN_TEMPLATE, Pattern.CASE_INSENSITIVE);
    
    builder.addAspect(
        LANDING_PATTERN,
        LANDING_REPLACEMENT,
        ArticleFiles.ROLE_ABSTRACT, 
        ArticleFiles.ROLE_ARTICLE_METADATA);

    builder.setFullTextFromRoles(
        ArticleFiles.ROLE_ABSTRACT);
    
    return builder.getSubTreeArticleIterator();
  }
  
  @Override
  public ArticleMetadataExtractor createArticleMetadataExtractor(MetadataTarget target)
    throws PluginException {
    return new BaseArticleMetadataExtractor(ArticleFiles.ROLE_ARTICLE_METADATA);
  }

}
