package org.lockss.plugin.wroclawmedicaluniversity;

import org.lockss.daemon.PluginException;
import org.lockss.extractor.ArticleMetadataExtractor;
import org.lockss.extractor.ArticleMetadataExtractorFactory;
import org.lockss.extractor.BaseArticleMetadataExtractor;
import org.lockss.extractor.MetadataTarget;
import org.lockss.plugin.ArchivalUnit;
import org.lockss.plugin.ArticleFiles;
import org.lockss.plugin.ArticleIteratorFactory;
import org.lockss.plugin.SubTreeArticleIteratorBuilder;
import org.lockss.plugin.anu.AnuArticleIteratorFactory;
import org.lockss.util.Logger;

import java.util.Iterator;
import java.util.regex.Pattern;

public class WroclawMedicalUniversityArticleIteratorFactory implements ArticleIteratorFactory,
        ArticleMetadataExtractorFactory {

    protected static Logger log = Logger.getLogger(WroclawMedicalUniversityArticleIteratorFactory.class);

    protected static final String ROOT_TEMPLATE = "\"^%s\", base_url";
    protected static final String PATTERN_TEMPLATE =
            "\"^%s((en/article)|pdf)/%d/%s/\", base_url, year, volume_number";


    private static final Pattern HTML_PATTERN = Pattern.compile(
            "en/article/([^/]+/[^/]+/[^/]+)/",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PDF_PATTERN = Pattern.compile(
            "pdf/(.*)/[^/]+.pdf$",
            Pattern.CASE_INSENSITIVE);


    //It has only html and PDF, no separate pages other aspects, like  abstract/full_tex
    //http://www.dmp.umed.wroc.pl/en/article/2019/56/3/317
    //http://www.dmp.umed.wroc.pl/pdf/2019/56/3/285.pdf
    @Override
    public Iterator<ArticleFiles> createArticleIterator(ArchivalUnit au, MetadataTarget target)
            throws PluginException {
        SubTreeArticleIteratorBuilder builder = new SubTreeArticleIteratorBuilder(au);

        builder.setSpec(target,
                ROOT_TEMPLATE,
                PATTERN_TEMPLATE, Pattern.CASE_INSENSITIVE);

        builder.addAspect(
                HTML_PATTERN,
                ArticleFiles.ROLE_ABSTRACT,
                ArticleFiles.ROLE_ARTICLE_METADATA);

        builder.addAspect(
                PDF_PATTERN,
                ArticleFiles.ROLE_FULL_TEXT_PDF);


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

