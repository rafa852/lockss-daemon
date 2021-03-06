package org.lockss.plugin.springer;

import org.lockss.daemon.PluginException;
import org.lockss.extractor.*;
import org.lockss.plugin.*;
import org.lockss.util.Logger;

import java.util.Iterator;
import java.util.regex.Pattern;

public class SpringerJatsSourceZipXmlArticleIteratorFactory implements ArticleIteratorFactory, ArticleMetadataExtractorFactory  {

    protected static Logger log = Logger.getLogger(SpringerJatsSourceZipXmlArticleIteratorFactory.class);
    private static String ARTICLE_METADATA_JATS_META_ROLE = "ArticleMetadataJatsMeta";
    private static String ARTICLE_METADATA_JATS_XML_ROLE = "ArticleMetadataJatsXml";

    protected static final String ALL_ZIP_XML_PATTERN_TEMPLATE =
            "\"%s[^/]+/.*\\.zip!/(.*)_(Article|OnlinePDF)(_\\d+)?\\.pdf$\", base_url";

    // Be sure to exclude all nested archives in case supplemental data is provided this way
    protected static final Pattern SUB_NESTED_ARCHIVE_PATTERN =
            Pattern.compile(".*/[^/]+\\.zip!/.+\\.(zip|tar|gz|tgz|tar\\.gz)$",
                    Pattern.CASE_INSENSITIVE);

    protected Pattern getExcludeSubTreePattern() {
        return SUB_NESTED_ARCHIVE_PATTERN;
    }

    protected String getIncludePatternTemplate() {
        return ALL_ZIP_XML_PATTERN_TEMPLATE;
    }

    protected static final Pattern PDF_PATTERN = Pattern.compile("/BodyRef/PDF/(.*)_(Article|OnlinePDF)(_\\d+)?\\.pdf$");
    protected static final String PDF_REPLACEMENT = "/BodyRef/PDF/$1.pdf";

    protected static final String XML_REPLACEMENT = "/$1_Article$3_nlm.xml";

    protected static final String XML_META_REPLACEMENT = "/$1_Article$3_nlm.xml.Meta";

    @Override
    public Iterator<ArticleFiles> createArticleIterator(ArchivalUnit au,
                                                        MetadataTarget target)
            throws PluginException {
        SubTreeArticleIteratorBuilder builder = new SubTreeArticleIteratorBuilder(au);

        builder.setSpec(builder.newSpec()
                .setTarget(target)
                .setPatternTemplate(getIncludePatternTemplate(), Pattern.CASE_INSENSITIVE)
                .setExcludeSubTreePattern(getExcludeSubTreePattern())
                .setVisitArchiveMembers(getIsArchive()));

        //The order of how Aspect defined is important here.
        
        builder.addAspect(PDF_PATTERN,
                          PDF_REPLACEMENT,
                          ArticleFiles.ROLE_FULL_TEXT_PDF);

        builder.addAspect(XML_REPLACEMENT,
                          ARTICLE_METADATA_JATS_XML_ROLE);

        builder.addAspect(XML_META_REPLACEMENT,
                          ARTICLE_METADATA_JATS_META_ROLE);

        builder.setFullTextFromRoles(ArticleFiles.ROLE_FULL_TEXT_PDF);
        
        //ArticleMetadata may be provided by both .xml and .xml.Meta file in case of Journals
        //For book/book series, ArticleMetadata is provided by .xml
        builder.setRoleFromOtherRoles(ArticleFiles.ROLE_ARTICLE_METADATA,
                                      ARTICLE_METADATA_JATS_META_ROLE,
                                      ARTICLE_METADATA_JATS_XML_ROLE);

        return builder.getSubTreeArticleIterator();
    }

    // NOTE - for a child to create their own version of this
    // indicates if the iterator should descend in to archives (for tar/zip deliveries)
    protected boolean getIsArchive() {
        return true;
    }

    @Override
    public ArticleMetadataExtractor createArticleMetadataExtractor(MetadataTarget target)
            throws PluginException {
        return new BaseArticleMetadataExtractor(ArticleFiles.ROLE_ARTICLE_METADATA);
    }

}
