package org.lockss.plugin.clockss.gigascience;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.lockss.test.LockssTestCase;
import org.lockss.util.Constants;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.*;
import java.util.ArrayList;
import java.util.List;

public class TestGigaScienceAPIHelper extends LockssTestCase {

    public void testAPIXmlXPathAlone() throws Exception {

        String fname = "sample_single_doi.xml";

        String fileName= System.getProperty("user.dir") +
                "/plugins/test/src/org/lockss/plugin/clockss/gigascience/" + fname;
        Document document = getDocument(fileName);

        String xpathTitleExpression = "/gigadb_entry/dataset/title";
        String xpathDoiExpression = "/gigadb_entry/dataset/links/manuscript_links/manuscript_link/manuscript_DOI";
        String xpathAuthorExpression = "/gigadb_entry/dataset/authors/author";
        String xpathPubTitleExpression = "/gigadb_entry/dataset/publication/publisher/@name";
        String xpathPubDateExpression = "/gigadb_entry/dataset/publication/@date";

        evaluateXPath(document, xpathTitleExpression);
        evaluateXPath(document, xpathDoiExpression);
        evaluateXPath(document, xpathPubTitleExpression );
        evaluateXPath(document, xpathPubDateExpression );
        evaluateAuthoNameXPath(document, xpathAuthorExpression );

    }

    private void  evaluateXPath(Document document, String xpathExpression) throws Exception
    {
        XPathFactory xpathFactory = XPathFactory.newInstance();
        XPath xpath = xpathFactory.newXPath();

        List<String> values = new ArrayList<>();
        int count = 0;

        try
        {
            XPathExpression expr = xpath.compile(xpathExpression);

            NodeList nodes = (NodeList) expr.evaluate(document, XPathConstants.NODESET);
            count = nodes.getLength();
            assertNotEquals(nodes.getLength(), 0);

            log.info("Expression is " + xpathExpression + ", count ======  " + count);

            for (int i = 0; i < count ; i++) {
                String value = nodes.item(i).getTextContent();
                log.info("Expression is " + xpathExpression + ", value ===== " + value);
                assertNotNull(value);
            }

        } catch (XPathExpressionException e) {
            e.printStackTrace();
        }
    }

    private void  evaluateAuthoNameXPath(Document document, String xpathExpression) throws Exception
    {
        XPathFactory xpathFactory = XPathFactory.newInstance();
        XPath xpath = xpathFactory.newXPath();

        List<String> values = new ArrayList<>();
        int count = 0;

        try
        {
            XPathExpression expr = xpath.compile(xpathExpression);

            NodeList nodes = (NodeList) expr.evaluate(document, XPathConstants.NODESET);
            count = nodes.getLength();
            assertNotEquals(nodes.getLength(), 0);

            log.info("Expression is " + xpathExpression + ", count ======  " + count);

            for (int i = 0; i < count ; i++) {
                NodeList nameChildren = nodes.item(i).getChildNodes();
                if (nameChildren == null) {
                    log.info("nameChildren is null");
                }

                String surname = null;
                String firstname = null;

                for (int p = 0; p < nameChildren.getLength(); p++) {
                    Node partNode = nameChildren.item(p);
                    String partName = partNode.getNodeName();

                    if ("surname".equals(partName)) {
                        surname = partNode.getTextContent();
                        log.info("surname is " + surname);
                    }  else if ("firstname".equals(partName)) {
                        firstname = partNode.getTextContent();
                        log.info("firstname is " + firstname);
                    }
                }
                StringBuilder valbuilder = new StringBuilder();
                if (!StringUtils.isBlank(firstname)) {
                    valbuilder.append(firstname);
                    if (!StringUtils.isBlank(surname)) {
                        valbuilder.append("  " + surname);
                    }
                    log.info("author name is " + valbuilder.toString());
                }
                assertNotNull(valbuilder.toString());
            }

        } catch (XPathExpressionException e) {
            e.printStackTrace();
        }
    }

    private Document getDocument(String fileName) throws Exception
    {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(fileName);
        return doc;
    }

}

